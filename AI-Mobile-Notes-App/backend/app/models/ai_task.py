"""AI 任务队列 + 进程内 AIWorker：抢占、重试、崩溃恢复。

设计要点（详见 docs/project_introduction.md 第 5.1 节"难点 2"）：
- 抢占：`UPDATE ... WHERE status='queued'` 在 SQLite WAL 中天然原子，避免双 claim
- 重试：指数退避 base=10s / cap=300s / max=3；超过置 failed
- 心跳：处理中定期写 heartbeat_at
- 恢复：启动时 claimed_at < now()-120s 的 processing 视为 orphan，重置回 queued
- 唤醒：notify_new_task() 通过 _wakeup Event 退出 idle poll
- sub_kind 调度：见 _process_note
"""
from __future__ import annotations

import asyncio
import enum
import logging
import os
from datetime import timedelta
from typing import Any, Optional

from sqlalchemy import (
    DateTime,
    Enum as SAEnum,
    ForeignKey,
    Integer,
    String,
    func,
    or_,
    select,
    update,
)
from sqlalchemy.orm import Mapped, mapped_column

from app.database import AsyncSessionLocal, Base
from app.models.note import AIStatus, Note
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
    """任务主类型：note（笔记 AI 处理）。"""

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
    note_id: Mapped[Optional[int]] = mapped_column(
        Integer,
        ForeignKey("notes.note_id", ondelete="CASCADE"),
        nullable=True,
    )
    kind: Mapped[JobKind] = mapped_column(
        SAEnum(JobKind, name="ai_task_kind"),
        nullable=False,
        default=JobKind.note,
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
    retry_count: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    max_retries: Mapped[int] = mapped_column(Integer, nullable=False, default=3)
    next_retry_at: Mapped[Optional[DateTime]] = mapped_column(DateTime, nullable=True)
    claimed_at: Mapped[Optional[DateTime]] = mapped_column(DateTime, nullable=True)
    claimed_by: Mapped[Optional[str]] = mapped_column(String, nullable=True)
    heartbeat_at: Mapped[Optional[DateTime]] = mapped_column(DateTime, nullable=True)
    finished_at: Mapped[Optional[DateTime]] = mapped_column(DateTime, nullable=True)


# ---------- Claim 形状 ----------
# _claim / _process_one / _on_failure 之间约定的字典结构。

Claim = dict[str, Any]  # keys: task_id, note_id, sub_kind


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


# ---------- 笔记子任务处理（模块级，便于同步路由/测试复用） ----------

async def _process_note(note_id: int, sub_kind: str) -> None:
    """处理单条笔记的 AI 子任务。

    sub_kind 调度：
      - ocr:     调 run_ocr，成功后串联入队 summary + embed
      - summary: 调 run_summary
      - embed:   调 note_embedding_service.run_embed
      - 其他子任务（questions/polish/translate）：由同步路由直接调用，
        不应入队本 worker。
    """
    async with AsyncSessionLocal() as db:
        if sub_kind == NoteSubKind.ocr.value:
            from app.services.note_ai_service import run_ocr
            await run_ocr(db, note_id)
            # 串联 summary + embed：同一会话里新增两个 AITask 让 worker 后续 claim
            db.add_all([
                AITask(
                    note_id=note_id,
                    kind=JobKind.note,
                    sub_kind=NoteSubKind.summary.value,
                ),
                AITask(
                    note_id=note_id,
                    kind=JobKind.note,
                    sub_kind=NoteSubKind.embed.value,
                ),
            ])
            await db.commit()
        elif sub_kind == NoteSubKind.summary.value:
            from app.services.note_ai_service import run_summary
            await run_summary(db, note_id)
        elif sub_kind == NoteSubKind.embed.value:
            from app.services.note_embedding_service import run_embed
            await run_embed(db, note_id)
        else:
            # 其他 sub_kind（questions / polish / translate）由同步路由处理，不入 worker。
            # worker 误 claim 到时直接置 succeeded 避免循环。
            task = await db.get(AITask, _claim_task_id_for(db, note_id=note_id, sub_kind=sub_kind))
            if task is not None:
                task.status = AITaskStatus.succeeded
                task.finished_at = utcnow_naive()
                task.claimed_at = None
                task.claimed_by = None
                task.heartbeat_at = None
                await db.commit()


async def _claim_task_id_for(
    db: AsyncSession, *, note_id: int, sub_kind: str
) -> Optional[int]:
    """取当前 processing 中、note_id+sub_kind 匹配的最新 AITask.task_id（辅助函数）。"""
    row = (await db.execute(
        select(AITask.task_id)
        .where(
            AITask.note_id == note_id,
            AITask.sub_kind == sub_kind,
            AITask.status == AITaskStatus.processing,
        )
        .order_by(AITask.created_at.desc())
        .limit(1)
    )).scalars().first()
    return row


# ---------- 失败处理 ----------

async def _on_failure(claim: Claim, error_message: str) -> None:
    """单次任务失败：retry_count < max_retries 则退避重试，否则置 failed。

    claim 必须含 note_id，否则无法定位失败上下文。
    """
    now = utcnow_naive()
    async with AsyncSessionLocal() as db:
        task = await _latest_task_for_claim(db, claim)
        if task is None:
            return

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
            note = await db.get(Note, claim["note_id"])
            if note is not None:
                note.ai_status = AIStatus.pending
        else:
            task.status = AITaskStatus.failed
            task.error_message = error_message
            task.finished_at = now
            task.claimed_at = None
            task.claimed_by = None
            task.heartbeat_at = None
            note = await db.get(Note, claim["note_id"])
            if note is not None:
                note.ai_status = AIStatus.failed
        await db.commit()


async def _latest_task_for_claim(
    db: AsyncSession, claim: Claim
) -> Optional[AITask]:
    """根据 claim 取最近一条 AITask。"""
    note_id = claim.get("note_id")
    if note_id is None:
        return None
    return (await db.execute(
        select(AITask)
        .where(AITask.note_id == note_id)
        .order_by(AITask.created_at.desc())
        .limit(1)
    )).scalars().first()


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

    async def _claim(self, n: int = CLAIM_BATCH_SIZE) -> list[Claim]:
        """原子抢占最多 n 个 queued 任务，返回 claim 字典列表。

        每个 dict 含 task_id / note_id / sub_kind。
        """
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
            rows = (await db.execute(
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
                .returning(
                    AITask.task_id,
                    AITask.note_id,
                    AITask.sub_kind,
                )
            )).all()
            await db.commit()
            return [
                {
                    "task_id": task_id,
                    "note_id": note_id,
                    "sub_kind": sub_kind,
                }
                for (task_id, note_id, sub_kind) in rows
            ]

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
                claims = await self._claim()
            except Exception:
                log.exception("_claim 失败，进入 idle")
                claims = []
            for claim in claims:
                if not self._running:
                    break
                await self._process_one(claim)
            if not claims and self._running:
                # idle：等 wakeup 或超时
                try:
                    await asyncio.wait_for(self._wakeup.wait(), timeout=IDLE_POLL_TIMEOUT_SECONDS)
                except asyncio.TimeoutError:
                    pass

    async def _process_one(self, claim: Claim) -> None:
        """处理单个 claim：note 走 _process_note。

        失败由 _on_failure 统一处理（重试或置 failed）。
        """
        try:
            note_id = claim.get("note_id")
            if note_id is None:
                raise RuntimeError(f"claim {claim} missing note_id")
            await _process_note(
                note_id=note_id,
                sub_kind=claim.get("sub_kind") or "",
            )
            await self._mark_task_succeeded(claim)
        except Exception as e:
            await _on_failure(claim, f"{type(e).__name__}: {e}")

    async def _mark_task_succeeded(self, claim: Claim) -> None:
        """把 claim 对应的 AITask 置为 succeeded（不触碰 note 字段）。"""
        async with AsyncSessionLocal() as db:
            task = await _latest_task_for_claim(db, claim)
            if task is None:
                return
            task.status = AITaskStatus.succeeded
            task.finished_at = utcnow_naive()
            task.claimed_at = None
            task.claimed_by = None
            task.heartbeat_at = None
            task.error_message = None
            await db.commit()