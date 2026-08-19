"""笔记路由：list / detail / create / patch / delete / export + per-note categories."""
from pathlib import Path
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
from app.schemas.note_category import NoteCategoriesUpdate, NoteCategoriesResponse
from app.services import note_service, export_service, note_category_service
from app.utils.time import to_iso
import json

router = APIRouter(prefix="/api/v1/notes", tags=["notes"])


def _file_url(f) -> str:
    """Note file static URL backed by /static/note_files/<filename>."""
    if not getattr(f, "original_path", None):
        return ""
    return f"/static/note_files/{Path(f.original_path).name}"


def _thumb_url(f) -> str | None:
    """Thumbnail static URL backed by /static/note_thumbs/<filename>."""
    if not getattr(f, "thumbnail_path", None):
        return None
    return f"/static/note_thumbs/{Path(f.thumbnail_path).name}"


@router.get("")
async def list_(
    page: int = Query(1, ge=1),
    pageSize: int = Query(20, ge=1, le=100),
    archived: bool | None = Query(None),
    db: AsyncSession = Depends(get_db),
    user: User = Depends(get_current_user),
):
    """分页列出当前用户的笔记。"""
    rows, total = await note_service.list_notes(
        db, user.user_id, page, pageSize, archived
    )
    items = [
        NoteListItem(
            noteId=n.note_id,
            title=n.title,
            summary=n.summary,
            aiStatus=n.ai_status.value,
            categories=[c.category_id for c in n.categories],
            thumbCount=await note_service.thumb_count(db, n.note_id),
            thumbUrl=await note_service.first_thumb_url(db, n.note_id),
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
    """笔记详情：基础字段 + 关联文件 + AI 生成的题目 + 关联分类。"""
    n = await note_service.get_note(db, note_id, user.user_id)
    files = await note_service.list_files(db, note_id)
    qs = await note_service.list_questions(db, note_id)
    cat_ids = [c.category_id for c in n.categories]

    file_items = [
        NoteFileItem(
            fileId=f.file_id,
            url=_file_url(f),
            thumbUrl=_thumb_url(f),
            width=f.width,
            height=f.height,
            sortIndex=f.sort_index,
            kind=getattr(f, "kind", "original") or "original",
            parentFileId=getattr(f, "parent_file_id", None),
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
        categories=cat_ids,
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
        db, user.user_id, body.title or "", body.textContent or ""
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
        body.title, body.textContent, body.isArchived,
    )
    return ok(message="已保存")


@router.post("/{note_id}/categories")
async def set_categories(
    note_id: int,
    body: NoteCategoriesUpdate,
    db: AsyncSession = Depends(get_db),
    user: User = Depends(get_current_user),
):
    """Replace the note's full category set. Empty list = remove all."""
    cat_ids = await note_category_service.set_note_categories(
        db, note_id, user.user_id, body.categoryIds or []
    )
    return ok(data=NoteCategoriesResponse(categoryIds=cat_ids).model_dump())


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