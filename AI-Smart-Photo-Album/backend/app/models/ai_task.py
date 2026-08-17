"""AI 任务队列 + 进程内 AIWorker：抢占、重试、崩溃恢复。

设计要点（详见 docs/project_introduction.md 第 5.1 节"难点 2"）：
- 抢占：`UPDATE ... WHERE status='queued'` 在 SQLite WAL 中天然原子，避免双 claim
- 重试：指数退避 base=10s / cap=300s / max=3；超过置 failed
- 心跳：处理中定期写 heartbeat_at
- 恢复：启动时 claimed_at < now()-120s 的 processing 视为 orphan，重置回 queued
- 唤醒：notify_new_task() 通过 _wakeup Event 退出 idle poll
"""
from __future__ import annotations

import asyncio
import enum
import logging
import os
from datetime import timedelta
from typing import Optional

from sqlalchemy import (
    DateTime,
    Enum as SAEnum,
    ForeignKey,
    Integer,
    String,
    delete,
    func,
    or_,
    select,
    update,
)
from sqlalchemy.orm import Mapped, mapped_column

from app.database import AsyncSessionLocal, Base
from app.models.photo import AnalysisStatus, Photo
from app.utils.time import utcnow_naive

log = logging.getLogger(__name__)


# ---------- 常量 ----------

RETRY_BACKOFF_BASE_SECONDS = 10
RETRY_BACKOFF_CAP_SECONDS = 300
ORPHAN_TIMEOUT_SECONDS = 120
IDLE_POLL_TIMEOUT_SECONDS = 5.0
CLAIM_BATCH_SIZE = 10


def _backoff_seconds(retry_count: int) -> int:
    """指数退避：n=1→10s, n=2→20s, n=3→40s, ... 上限 300s。"""
    return min(RETRY_BACKOFF_CAP_SECONDS, RETRY_BACKOFF_BASE_SECONDS * (2 ** (retry_count - 1)))


# ---------- 枚举与 ORM ----------

class AITaskStatus(str, enum.Enum):
    queued = "queued"
    processing = "processing"
    succeeded = "succeeded"
    failed = "failed"


class JobKind(str, enum.Enum):
    """任务主类型：photo（图片 AI 分析）或 note（笔记 AI 处理）。"""

    photo = "photo"
    note = "note"


class NoteSubKind(str, enum.Enum):
    """笔记子任务类型：OCR / 摘要 / 题目 / 润色 / 翻译 / 嵌入。"""

    ocr = "ocr"
    summary = "summary"
    questions = "questions"
    polish = "polish"
    translate = "translate"
    embed = "embed"


class AITask(Base):
    __tablename__ = "ai_tasks"

    task_id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    photo_id: Mapped[Optional[int]] = mapped_column(
        Integer,
        ForeignKey("photos.photo_id", ondelete="CASCADE"),
        nullable=True,
    )
    note_id: Mapped[Optional[int]] = mapped_column(
        Integer,
        ForeignKey("notes.note_id", ondelete="CASCADE"),
        nullable=True,
    )
    kind: Mapped[JobKind] = mapped_column(
        SAEnum(JobKind, name="ai_task_kind"),
        nullable=False,
        default=JobKind.photo,
    )
    sub_kind: Mapped[Optional[str]] = mapped_column(String, nullable=True)
    status: Mapped[AITaskStatus] = mapped_column(
        SAEnum(AITaskStatus, name="ai_task_status"),
        nullable=False,
        default=AITaskStatus.queued,
    )
    error_message: Mapped[Optional[str]] = mapped_column(String, nullable=True)
    created_at: Mapped[DateTime] = mapped_column(
        DateTime, nullable=False, server_default=func.current_timestamp()
    )
    # 003 migration 新增字段
    retry_count: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    max_retries: Mapped[int] = mapped_column(Integer, nullable=False, default=3)
    next_retry_at: Mapped[Optional[DateTime]] = mapped_column(DateTime, nullable=True)
    claimed_at: Mapped[Optional[DateTime]] = mapped_column(DateTime, nullable=True)
    claimed_by: Mapped[Optional[str]] = mapped_column(String, nullable=True)
    heartbeat_at: Mapped[Optional[DateTime]] = mapped_column(DateTime, nullable=True)
    finished_at: Mapped[Optional[DateTime]] = mapped_column(DateTime, nullable=True)


# ---------- 模块内单例 ----------

_worker: "AIWorker | None" = None


async def start_worker() -> None:
    """启动 worker 主循环（后台 asyncio 任务）。"""
    global _worker
    if _worker is not None:
        return
    _worker = AIWorker(worker_id=f"w-{os.getpid()}")
    await _worker.start()


async def stop_worker() -> None:
    """停止 worker。"""
    global _worker
    if _worker is None:
        return
    await _worker.stop()
    _worker = None


def notify_new_task() -> None:
    """唤醒 worker 退出 idle poll（路由在 enqueue 新任务后调用）。"""
    if _worker is not None:
        _worker.notify_new_task()


# ---------- 失败处理 ----------

async def _on_failure(photo_id: int, error_message: str) -> None:
    """单次任务失败：retry_count < max_retries 则退避重试，否则置 failed。"""
    now = utcnow_naive()
    async with AsyncSessionLocal() as db:
        task = (await db.execute(
            select(AITask)
            .where(AITask.photo_id == photo_id)
            .order_by(AITask.created_at.desc())
            .limit(1)
        )).scalars().first()
        if task is None:
            return

        photo = await db.get(Photo, photo_id)

        if task.retry_count < task.max_retries:
            new_retry_count = task.retry_count + 1
            backoff = _backoff_seconds(new_retry_count)
            task.retry_count = new_retry_count
            task.status = AITaskStatus.queued
            task.next_retry_at = now + timedelta(seconds=backoff)
            task.claimed_at = None
            task.claimed_by = None
            task.heartbeat_at = None
            task.error_message = error_message
            if photo is not None:
                photo.analysis_status = AnalysisStatus.pending
        else:
            task.status = AITaskStatus.failed
            task.error_message = error_message
            task.finished_at = now
            task.claimed_at = None
            task.claimed_by = None
            task.heartbeat_at = None
            if photo is not None:
                photo.analysis_status = AnalysisStatus.failed
        await db.commit()


# ---------- Worker ----------

class AIWorker:
    """单进程内的 AI 分析 worker。

    主循环：等待 wakeup → claim 一批任务 → 逐个 _process_one → 重复。
    任务失败由 _on_failure 统一处理（重试或置 failed）。
    """

    def __init__(self, worker_id: str):
        self.worker_id = worker_id
        self._wakeup = asyncio.Event()
        self._running = False
        self._task: Optional[asyncio.Task] = None

    # --- public API ---

    def notify_new_task(self) -> None:
        """设置 wakeup，让主循环退出 idle 等待。"""
        self._wakeup.set()

    async def start(self) -> None:
        """启动主循环为后台 asyncio 任务。"""
        if self._running:
            return
        self._running = True
        self._task = asyncio.create_task(self._run(), name=f"ai-worker-{self.worker_id}")

    async def stop(self) -> None:
        """停止主循环。"""
        self._running = False
        self._wakeup.set()
        if self._task is not None:
            self._task.cancel()
            try:
                await self._task
            except (asyncio.CancelledError, Exception):
                pass
            self._task = None

    # --- 队列操作（也供测试直接调用） ---

    async def _recover_orphans(self) -> None:
        """启动时把 claimed_at 超时的 processing 任务重置回 queued。"""
        cutoff = utcnow_naive() - timedelta(seconds=ORPHAN_TIMEOUT_SECONDS)
        async with AsyncSessionLocal() as db:
            await db.execute(
                update(AITask)
                .where(
                    AITask.status == AITaskStatus.processing,
                    AITask.claimed_at.is_not(None),
                    AITask.claimed_at < cutoff,
                )
                .values(
                    status=AITaskStatus.queued,
                    claimed_at=None,
                    claimed_by=None,
                    heartbeat_at=None,
                )
            )
            await db.commit()

    async def _claim(self, n: int = CLAIM_BATCH_SIZE) -> list[int]:
        """原子抢占最多 n 个 queued 任务，返回它们的 photo_id 列表。"""
        now = utcnow_naive()
        async with AsyncSessionLocal() as db:
            candidate_ids = list((await db.execute(
                select(AITask.task_id)
                .where(
                    AITask.status == AITaskStatus.queued,
                    or_(AITask.next_retry_at.is_(None), AITask.next_retry_at <= now),
                )
                .order_by(AITask.created_at)
                .limit(n)
            )).scalars().all())
            if not candidate_ids:
                return []
            # 关键：UPDATE WHERE 同时带 status='queued'，保证两个并发 worker 不会双 claim
            result = await db.execute(
                update(AITask)
                .where(
                    AITask.task_id.in_(candidate_ids),
                    AITask.status == AITaskStatus.queued,
                )
                .values(
                    status=AITaskStatus.processing,
                    claimed_at=now,
                    claimed_by=self.worker_id,
                    heartbeat_at=now,
                )
                .returning(AITask.photo_id)
            )
            await db.commit()
            return [pid for pid in result.scalars().all()]

    # --- 主循环与单任务处理 ---

    async def _run(self) -> None:
        """主循环入口。"""
        try:
            await self._recover_orphans()
        except Exception:
            log.exception("recover_orphans 失败，继续运行")

        while self._running:
            self._wakeup.clear()
            try:
                photo_ids = await self._claim()
            except Exception:
                log.exception("_claim 失败，进入 idle")
                photo_ids = []
            for pid in photo_ids:
                if not self._running:
                    break
                await self._process_one(pid)
            if not photo_ids and self._running:
                # idle：等 wakeup 或超时
                try:
                    await asyncio.wait_for(self._wakeup.wait(), timeout=IDLE_POLL_TIMEOUT_SECONDS)
                except asyncio.TimeoutError:
                    pass

    async def _process_one(self, photo_id: int) -> None:
        """处理单张照片。失败转 _on_failure。"""
        try:
            async with AsyncSessionLocal() as db:
                photo = await db.get(Photo, photo_id)
                if photo is None or not photo.original_path:
                    raise RuntimeError(f"photo {photo_id} missing or no original_path")
                photo_path = photo.original_path

            # 调 AI（mock/real 由 AI_SERVICE 决定）
            from app.services.ai import analyze_photo
            result = await analyze_photo(photo_path, photo_id)

            # 写库：analysis + ai-source categories + task done
            async with AsyncSessionLocal() as db:
                from app.models.category import Category, CategoryType
                from app.models.photo_ai_analysis import PhotoAIAnalysis
                from app.models.photo_category import CategorySource, PhotoCategory

                scene_cat = (await db.execute(
                    select(Category).where(
                        Category.name == result.scene_category_name,
                        Category.type == CategoryType.scene,
                    )
                )).scalars().first() if result.scene_category_name else None
                emotion_cat = (await db.execute(
                    select(Category).where(
                        Category.name == result.emotion_category_name,
                        Category.type == CategoryType.emotion,
                    )
                )).scalars().first() if result.emotion_category_name else None

                analysis_row = await db.get(PhotoAIAnalysis, photo_id)
                if analysis_row is None:
                    analysis_row = PhotoAIAnalysis(photo_id=photo_id)
                    db.add(analysis_row)
                analysis_row.description = result.description
                analysis_row.dominant_scene_id = scene_cat.category_id if scene_cat else None
                analysis_row.scene_confidence = result.scene_confidence
                analysis_row.dominant_emotion_id = emotion_cat.category_id if emotion_cat else None
                analysis_row.emotion_confidence = result.emotion_confidence
                analysis_row.analyzed_at = utcnow_naive()

                # 替换 ai 源标签；保留 user 源
                await db.execute(
                    delete(PhotoCategory).where(
                        PhotoCategory.photo_id == photo_id,
                        PhotoCategory.source == CategorySource.ai,
                    )
                )
                if scene_cat is not None:
                    db.add(PhotoCategory(
                        photo_id=photo_id,
                        category_id=scene_cat.category_id,
                        confidence=result.scene_confidence,
                        source=CategorySource.ai,
                        is_primary=1,
                    ))
                if emotion_cat is not None:
                    db.add(PhotoCategory(
                        photo_id=photo_id,
                        category_id=emotion_cat.category_id,
                        confidence=result.emotion_confidence,
                        source=CategorySource.ai,
                        is_primary=0,
                    ))
                for tag_name, tag_conf in result.tag_category_names:
                    tag_cat = (await db.execute(
                        select(Category).where(
                            Category.name == tag_name,
                            Category.type == CategoryType.tag,
                        )
                    )).scalars().first()
                    if tag_cat is not None:
                        db.add(PhotoCategory(
                            photo_id=photo_id,
                            category_id=tag_cat.category_id,
                            confidence=tag_conf,
                            source=CategorySource.ai,
                            is_primary=0,
                        ))

                task = (await db.execute(
                    select(AITask)
                    .where(AITask.photo_id == photo_id)
                    .order_by(AITask.created_at.desc())
                    .limit(1)
                )).scalars().first()
                if task is not None:
                    task.status = AITaskStatus.succeeded
                    task.finished_at = utcnow_naive()
                    task.claimed_at = None
                    task.claimed_by = None
                    task.heartbeat_at = None
                    task.error_message = None

                photo = await db.get(Photo, photo_id)
                if photo is not None:
                    photo.analysis_status = AnalysisStatus.done

                await db.commit()
        except Exception as e:
            await _on_failure(photo_id, f"{type(e).__name__}: {e}")