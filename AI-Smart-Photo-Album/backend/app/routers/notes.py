"""笔记路由：list / detail / create / patch / delete / export。"""
from fastapi import APIRouter, Depends, Query
from sqlalchemy.ext.asyncio import AsyncSession
from app.database import get_db
from app.deps import get_current_user
from app.models import User
from app.response import ok
from app.schemas.note import (
    NoteCreate, NoteUpdate, NoteListItem, NoteDetail, NoteFileItem, QuestionItem,
    NoteExportRequest,
)
from app.services import note_service, export_service
from app.utils.time import to_iso
import json

router = APIRouter(prefix="/api/v1/notes", tags=["notes"])


def _file_url(f) -> str:
    """Best-effort URL for a note file. Task 8 提供了正确的 origin/thumb 工具；
    此处返回空串不会破坏 detail 响应（前端会忽略空 url）。"""
    return ""


def _thumb_url(f) -> str | None:
    """缩略图占位：Task 8 替换为真实 URL 构造。"""
    return None


@router.get("")
async def list_(
    folderId: int | None = Query(None),
    page: int = Query(1, ge=1),
    pageSize: int = Query(20, ge=1, le=100),
    archived: bool | None = Query(None),
    db: AsyncSession = Depends(get_db),
    user: User = Depends(get_current_user),
):
    """分页列出当前用户的笔记。"""
    rows, total = await note_service.list_notes(
        db, user.user_id, page, pageSize, folderId, archived
    )
    items = [
        NoteListItem(
            noteId=n.note_id,
            title=n.title,
            summary=n.summary,
            aiStatus=n.ai_status.value,
            folderId=n.folder_id,
            thumbCount=await note_service.thumb_count(db, n.note_id),
            updatedAt=to_iso(n.updated_at),
        ).model_dump()
        for n in rows
    ]
    return ok(data={"list": items, "total": total, "page": page, "pageSize": pageSize})


@router.get("/{note_id}")
async def detail(
    note_id: int,
    db: AsyncSession = Depends(get_db),
    user: User = Depends(get_current_user),
):
    """笔记详情：基础字段 + 关联文件 + AI 生成的题目。"""
    n = await note_service.get_note(db, note_id, user.user_id)
    files = await note_service.list_files(db, note_id)
    qs = await note_service.list_questions(db, note_id)

    file_items = [
        NoteFileItem(
            fileId=f.file_id,
            url=_file_url(f),
            thumbUrl=_thumb_url(f),
            width=f.width,
            height=f.height,
            sortIndex=f.sort_index,
        ).model_dump()
        for f in files
    ]
    q_items = [
        QuestionItem(
            questionId=q.question_id,
            questionType=q.question_type,
            stem=q.stem,
            options=json.loads(q.options_json) if q.options_json else None,
            answer=q.answer,
            explanation=q.explanation,
            difficulty=q.difficulty,
        ).model_dump()
        for q in qs
    ]
    return ok(data=NoteDetail(
        noteId=n.note_id,
        folderId=n.folder_id,
        title=n.title,
        textContent=n.text_content,
        summary=n.summary,
        aiStatus=n.ai_status.value,
        ocrEngine=n.ocr_engine,
        files=file_items,
        questions=q_items,
        isArchived=bool(n.is_archived),
        createdAt=to_iso(n.created_at),
        updatedAt=to_iso(n.updated_at),
    ).model_dump())


@router.post("")
async def create(
    body: NoteCreate,
    db: AsyncSession = Depends(get_db),
    user: User = Depends(get_current_user),
):
    """创建空白笔记；后续通过 patch 写标题 / 文本 / 触发 AI。"""
    n = await note_service.create_note(
        db, user.user_id, body.folderId, body.title or "", body.textContent or ""
    )
    return ok(data={"noteId": n.note_id})


@router.patch("/{note_id}")
async def update(
    note_id: int,
    body: NoteUpdate,
    db: AsyncSession = Depends(get_db),
    user: User = Depends(get_current_user),
):
    """增量更新笔记字段。"""
    await note_service.update_note(
        db, note_id, user.user_id,
        body.folderId, body.title, body.textContent, body.isArchived,
    )
    return ok(message="已保存")


@router.delete("/{note_id}")
async def delete_(
    note_id: int,
    db: AsyncSession = Depends(get_db),
    user: User = Depends(get_current_user),
):
    """软删除笔记。"""
    await note_service.soft_delete(db, note_id, user.user_id)
    return ok(message="已删除")


@router.post("/{note_id}/export")
async def export(
    note_id: int,
    body: NoteExportRequest,
    db: AsyncSession = Depends(get_db),
    user: User = Depends(get_current_user),
):
    """触发笔记导出（Task 12 替换为真实 md/zip 生成）。"""
    url = await export_service.build_export(db, note_id, user.user_id, body.format)
    return ok(data={"downloadUrl": url})
