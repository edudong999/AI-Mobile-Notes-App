from __future__ import annotations
from pathlib import Path

from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession
from app.exceptions import BizException
from app.models import Note, AIStatus, NoteFile, NoteQuestion, NoteCategory
from app.schemas.note import NoteListItem
from app.utils.time import to_iso


async def create_note(db: AsyncSession, user_id: int,
                      title: str = "",
                      text_content: str = "") -> Note:
    """创建一条笔记，初始 AI 状态 pending。"""
    n = Note(user_id=user_id, title=title or "",
             text_content=text_content or "", ai_status=AIStatus.pending)
    db.add(n)
    await db.commit()
    await db.refresh(n)
    return n


async def get_note(db: AsyncSession, note_id: int, user_id: int) -> Note:
    """按 note_id 取笔记；跨用户或软删除则抛 404。"""
    n = (await db.execute(
        select(Note).where(
            Note.note_id == note_id,
            Note.user_id == user_id,
            Note.deleted_at.is_(None),
        )
    )).scalars().first()
    if n is None:
        raise BizException(404, "笔记不存在")
    return n


async def list_notes(db: AsyncSession, user_id: int,
                     page: int = 1, page_size: int = 20,
                     archived: bool | None = None) -> tuple[list[Note], int]:
    """分页列出当前用户的笔记，支持按归档过滤。"""
    base = [Note.user_id == user_id, Note.deleted_at.is_(None)]
    if archived is not None:
        base.append(Note.is_archived == (1 if archived else 0))

    total = (await db.execute(
        select(func.count(Note.note_id)).where(*base)
    )).scalar_one()
    rows = (await db.execute(
        select(Note).where(*base)
        .order_by(Note.updated_at.desc())
        .offset((page - 1) * page_size).limit(page_size)
    )).scalars().all()
    return list(rows), total


async def update_note(db: AsyncSession, note_id: int, user_id: int,
                      title: str | None = None,
                      text_content: str | None = None,
                      is_archived: bool | None = None) -> Note:
    """增量更新笔记字段。text_content 修改会重置 AI 状态为 pending。"""
    n = await get_note(db, note_id, user_id)
    if title is not None:
        n.title = title
    if text_content is not None:
        n.text_content = text_content
        if n.ai_status == AIStatus.done:
            n.ai_status = AIStatus.pending
    if is_archived is not None:
        n.is_archived = 1 if is_archived else 0
    await db.commit()
    await db.refresh(n)
    return n


async def soft_delete(db: AsyncSession, note_id: int, user_id: int) -> None:
    """软删除：写入 deleted_at。"""
    n = await get_note(db, note_id, user_id)
    n.deleted_at = func.current_timestamp()
    await db.commit()


async def list_files(db: AsyncSession, note_id: int) -> list[NoteFile]:
    """取笔记的全部文件，按 sort_index/file_id 升序。"""
    return list((await db.execute(
        select(NoteFile)
        .where(NoteFile.note_id == note_id)
        .order_by(NoteFile.sort_index, NoteFile.file_id)
    )).scalars().all())


async def list_questions(db: AsyncSession, note_id: int) -> list[NoteQuestion]:
    """取笔记的全部 AI 生成的题目，按 sort_index/question_id 升序。"""
    return list((await db.execute(
        select(NoteQuestion)
        .where(NoteQuestion.note_id == note_id)
        .order_by(NoteQuestion.sort_index, NoteQuestion.question_id)
    )).scalars().all())


async def thumb_count(db: AsyncSession, note_id: int) -> int:
    """统计某笔记关联的文件数（用于列表页 thumbCount）。"""
    return (await db.execute(
        select(func.count(NoteFile.file_id)).where(NoteFile.note_id == note_id)
    )).scalar_one()


async def first_thumb_url(db: AsyncSession, note_id: int) -> str | None:
    """取某笔记第一张图的缩略图 URL（list 视图用）。无文件或缩略图返回 None。"""
    p = (await db.execute(
        select(NoteFile.thumbnail_path)
        .where(NoteFile.note_id == note_id, NoteFile.thumbnail_path.isnot(None))
        .order_by(NoteFile.sort_index, NoteFile.file_id)
        .limit(1)
    )).scalars().first()
    if not p:
        return None
    return f"/static/note_thumbs/{Path(p).name}"


async def list_notes_in_category(db: AsyncSession, user_id: int, category_id: int) -> list[NoteListItem]:
    """List active notes in a given category. Returns NoteListItem schemas."""
    rows = (await db.execute(
        select(Note, func.count(NoteFile.file_id))
        .join(NoteCategory, NoteCategory.note_id == Note.note_id)
        .outerjoin(NoteFile, NoteFile.note_id == Note.note_id)
        .where(
            Note.user_id == user_id,
            Note.deleted_at.is_(None),
            NoteCategory.category_id == category_id,
        )
        .group_by(Note.note_id)
        .order_by(Note.updated_at.desc())
    )).all()
    out: list[NoteListItem] = []
    for note, file_count in rows:
        cat_ids = [c.category_id for c in note.categories]
        out.append(NoteListItem(
            noteId=note.note_id,
            title=note.title or "",
            summary=note.summary or "",
            aiStatus=(note.ai_status.value if hasattr(note.ai_status, "value") else str(note.ai_status)),
            categories=cat_ids,
            thumbCount=file_count,
            thumbUrl=None,
            updatedAt=note.updated_at.isoformat() if note.updated_at else None,
        ))
    return out