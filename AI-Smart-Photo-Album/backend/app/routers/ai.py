"""AI 路由：分析进度查询 + 队列详情 + 手动重新分析入队。"""
from fastapi import APIRouter, Depends
from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_db
from app.deps import get_current_user
from app.exceptions import BizException
from app.models import (
    AnalysisStatus, AITask, AITaskStatus, Photo, User,
)
from app.response import ok
from app.schemas.ai import (
    AIQueueItem, AIQueueResponse, AIReanalyzeRequest, AIStatusResponse,
)
from app.services.file_storage import thumb_url
from app.utils.time import to_iso
from app.models.ai_task import notify_new_task

router = APIRouter(prefix="/api/v1/ai", tags=["ai"])


@router.get("/status")
async def status(
    db: AsyncSession = Depends(get_db),
    user: User = Depends(get_current_user),
):
    """返回当前用户的 AI 分析进度（按状态分桶：pending / processing / done / failed）。"""
    base = (Photo.user_id == user.user_id, Photo.deleted_at.is_(None))
    total = (await db.execute(
        select(func.count(Photo.photo_id)).where(*base)
    )).scalar_one()
    done = (await db.execute(
        select(func.count(Photo.photo_id)).where(
            *base, Photo.analysis_status == AnalysisStatus.done,
        )
    )).scalar_one()
    pending = (await db.execute(
        select(func.count(Photo.photo_id)).where(
            *base, Photo.analysis_status == AnalysisStatus.pending,
        )
    )).scalar_one()
    processing = (await db.execute(
        select(func.count(Photo.photo_id)).where(
            *base, Photo.analysis_status == AnalysisStatus.processing,
        )
    )).scalar_one()
    failed = (await db.execute(
        select(func.count(Photo.photo_id)).where(
            *base, Photo.analysis_status == AnalysisStatus.failed,
        )
    )).scalar_one()
    return ok(data=AIStatusResponse(
        total=total,
        done=done,
        pending=pending,
        processing=processing,
        failed=failed,
        progress=(done / total) if total else 0.0,
    ).model_dump())


@router.get("/queue")
async def queue(
    db: AsyncSession = Depends(get_db),
    user: User = Depends(get_current_user),
):
    """按状态分桶返回当前用户各照片（最新任务），便于前端列表展示。

    每张照片只看其 *最新一条* AITask：pending/processing/failed 各取对应任务信息，
    done 只看 photo.analysis_status == done 的照片（无需看 task）。
    """
    base = (Photo.user_id == user.user_id, Photo.deleted_at.is_(None))

    # 最新一条 AITask per photo_id（取最大 task_id）
    latest_task_subq = (
        select(
            AITask.photo_id,
            func.max(AITask.task_id).label("max_task_id"),
        )
        .group_by(AITask.photo_id)
        .subquery()
    )
    latest_tasks = (await db.execute(
        select(AITask)
        .join(
            latest_task_subq,
            (AITask.photo_id == latest_task_subq.c.photo_id)
            & (AITask.task_id == latest_task_subq.c.max_task_id),
        )
    )).scalars().all()
    task_by_photo: dict[int, AITask] = {t.photo_id: t for t in latest_tasks}

    def _build_item(p: Photo) -> AIQueueItem:
        task = task_by_photo.get(p.photo_id)
        err = task.error_message if task is not None else None
        retry = task.retry_count if task is not None else 0
        updated = (
            task.heartbeat_at or task.finished_at or task.next_retry_at
        ) if task is not None else None
        return AIQueueItem(
            photoId=p.photo_id,
            fileName=p.file_name,
            thumbnailUrl=thumb_url(p.photo_id),
            status=p.analysis_status.value,
            errorMessage=err,
            retryCount=retry,
            updatedAt=to_iso(updated) if updated else None,
        )

    async def _photos_with_status(status_value: str) -> list[AIQueueItem]:
        rows = (await db.execute(
            select(Photo).where(
                *base, Photo.analysis_status == AnalysisStatus(status_value),
            ).order_by(Photo.created_at.desc())
        )).scalars().all()
        return [_build_item(p) for p in rows]

    pending_list = await _photos_with_status(AnalysisStatus.pending.value)
    processing_list = await _photos_with_status(AnalysisStatus.processing.value)
    failed_list = await _photos_with_status(AnalysisStatus.failed.value)
    done_list = await _photos_with_status(AnalysisStatus.done.value)

    return ok(data=AIQueueResponse(
        pending=pending_list,
        processing=processing_list,
        failed=failed_list,
        done=done_list,
    ).model_dump())


@router.post("/reanalyze")
async def reanalyze(
    body: AIReanalyzeRequest,
    db: AsyncSession = Depends(get_db),
    user: User = Depends(get_current_user),
):
    """将指定照片重新加入 AI 分析队列并唤醒 worker。"""
    if not body.photoIds:
        raise BizException(400, "photoIds 必须为非空数组")
    owned = (await db.execute(
        select(Photo.photo_id).where(
            Photo.photo_id.in_(body.photoIds),
            Photo.user_id == user.user_id,
            Photo.deleted_at.is_(None),
        )
    )).scalars().all()
    if not owned:
        raise BizException(404, "未找到有效照片")

    for pid in owned:
        p = await db.get(Photo, pid)
        p.analysis_status = AnalysisStatus.pending
        db.add(AITask(photo_id=pid, status=AITaskStatus.queued))
    await db.commit()
    # 一次唤醒 worker，worker 会去抢所有 N 个
    notify_new_task()
    return ok(data={"queuedCount": len(owned), "message": "已加入分析队列"})
