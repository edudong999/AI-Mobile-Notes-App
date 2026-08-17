"""笔记 AI 路由：OCR / 摘要 入队，题目 / 润色 / 翻译 同步调用。

- POST /ocr, /summary: 入 AITask 队列（worker 后台处理）。
- POST /questions, /polish, /translate: 直接调 note_ai_service.run_* 同步执行。
- GET /status: 当前用户笔记 AI 进度统计。
- POST /retry: 把失败任务重置回 queued。

注：spec 中提到的 /queue 端点在本任务跳过 —— 笔记的 AI 进度已由
GET /api/v1/note-ai/status 提供，按状态分桶展示需求由前端处理。
（照片侧的 /api/v1/ai/queue 不在本路由范围内。）
"""
import asyncio
import json as _json

from fastapi import APIRouter, Depends
from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_db
from app.deps import get_current_user
from app.exceptions import BizException
from app.models import AITask, AITaskStatus, AIStatus, JobKind, Note, User
from app.models.ai_task import NoteSubKind, notify_new_task
from app.response import ok
from app.schemas.note_ai import (
    EnqueueResponse,
    NoteAiStatusResponse,
    NoteRetryRequest,
    OcrRequest,
    PolishRequest,
    PolishResponse,
    QuestionItem,
    QuestionsRequest,
    QuestionsResponse,
    SummaryRequest,
    TranslateRequest,
    TranslateResponse,
)
from app.services import note_ai_service
from app.services.note_service import get_note

router = APIRouter(prefix="/api/v1/note-ai", tags=["note-ai"])


@router.post("/ocr")
async def ocr(
    body: OcrRequest,
    db: AsyncSession = Depends(get_db),
    user: User = Depends(get_current_user),
):
    """对笔记关联图片做 OCR（异步入队）。"""
    await get_note(db, body.noteId, user.user_id)
    job_id = await note_ai_service.enqueue_note_ai(db, body.noteId, NoteSubKind.ocr.value)
    await db.commit()
    notify_new_task()
    return ok(
        data=EnqueueResponse(
            queuedCount=1, jobIds=[job_id], message="已入队"
        ).model_dump()
    )


@router.post("/summary")
async def summary(
    body: SummaryRequest,
    db: AsyncSession = Depends(get_db),
    user: User = Depends(get_current_user),
):
    """对笔记正文生成摘要（异步入队）。"""
    await get_note(db, body.noteId, user.user_id)
    job_id = await note_ai_service.enqueue_note_ai(db, body.noteId, NoteSubKind.summary.value)
    await db.commit()
    notify_new_task()
    return ok(
        data=EnqueueResponse(
            queuedCount=1, jobIds=[job_id], message="已入队"
        ).model_dump()
    )


@router.post("/questions")
async def questions(
    body: QuestionsRequest,
    db: AsyncSession = Depends(get_db),
    user: User = Depends(get_current_user),
):
    """同步生成练习题并落库。"""
    await get_note(db, body.noteId, user.user_id)
    try:
        items = await asyncio.wait_for(
            note_ai_service.run_questions(db, body.noteId, body.count, body.types),
            timeout=60,
        )
    except asyncio.TimeoutError:
        raise BizException(504, "LLM 生成题目超时")
    return ok(
        data=QuestionsResponse(
            questions=[
                QuestionItem(
                    questionType=q.question_type,
                    stem=q.stem,
                    options=_json.loads(q.options_json) if q.options_json else None,
                    answer=q.answer,
                    explanation=q.explanation,
                    difficulty=q.difficulty,
                ).model_dump()
                for q in items
            ]
        ).model_dump()
    )


@router.post("/polish")
async def polish(
    body: PolishRequest,
    db: AsyncSession = Depends(get_db),
    user: User = Depends(get_current_user),
):
    """同步润色 / 改写 / 缩写一段文本。"""
    await get_note(db, body.noteId, user.user_id)
    try:
        r = await asyncio.wait_for(
            note_ai_service.run_polish(db, body.noteId, body.action, body.text),
            timeout=30,
        )
    except asyncio.TimeoutError:
        raise BizException(504, "LLM 调用超时")
    return ok(data=PolishResponse(result=r).model_dump())


@router.post("/translate")
async def translate(
    body: TranslateRequest,
    db: AsyncSession = Depends(get_db),
    user: User = Depends(get_current_user),
):
    """同步翻译一段文本（缺省时翻译整篇笔记）。"""
    await get_note(db, body.noteId, user.user_id)
    try:
        r = await asyncio.wait_for(
            note_ai_service.run_translate(db, body.noteId, body.targetLang, body.text),
            timeout=30,
        )
    except asyncio.TimeoutError:
        raise BizException(504, "LLM 调用超时")
    return ok(data=TranslateResponse(result=r).model_dump())


@router.get("/status")
async def status(
    db: AsyncSession = Depends(get_db),
    user: User = Depends(get_current_user),
):
    """当前用户笔记 AI 处理进度（按 AIStatus 分桶）。"""
    base = [Note.user_id == user.user_id, Note.deleted_at.is_(None)]
    total = (await db.execute(
        select(func.count(Note.note_id)).where(*base)
    )).scalar_one()
    done = (await db.execute(
        select(func.count(Note.note_id)).where(
            *base, Note.ai_status == AIStatus.done,
        )
    )).scalar_one()
    pending = (await db.execute(
        select(func.count(Note.note_id)).where(
            *base, Note.ai_status == AIStatus.pending,
        )
    )).scalar_one()
    processing = (await db.execute(
        select(func.count(Note.note_id)).where(
            *base, Note.ai_status == AIStatus.processing,
        )
    )).scalar_one()
    failed = (await db.execute(
        select(func.count(Note.note_id)).where(
            *base, Note.ai_status == AIStatus.failed,
        )
    )).scalar_one()
    return ok(
        data=NoteAiStatusResponse(
            total=total,
            done=done,
            pending=pending,
            processing=processing,
            failed=failed,
            progress=(done / total) if total else 0.0,
        ).model_dump()
    )


@router.post("/retry")
async def retry(
    body: NoteRetryRequest,
    db: AsyncSession = Depends(get_db),
    user: User = Depends(get_current_user),
):
    """把指定任务 ID 重置回 queued 状态并唤醒 worker。"""
    tasks = list((await db.execute(
        select(AITask).where(
            AITask.task_id.in_(body.jobIds),
            AITask.kind == JobKind.note,
        )
    )).scalars().all())
    for t in tasks:
        t.status = AITaskStatus.queued
        t.retry_count = 0
        t.error_message = None
        t.next_retry_at = None
    await db.commit()
    notify_new_task()
    return ok(
        data=EnqueueResponse(
            queuedCount=len(tasks), message="已重试"
        ).model_dump()
    )