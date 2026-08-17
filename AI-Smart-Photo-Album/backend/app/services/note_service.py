from __future__ import annotations
from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession
from app.exceptions import BizException
from app.models import Note, AIStatus, NoteFile, NoteQuestion
from app.utils.time import to_iso


async def create_note(db: AsyncSession, user_id: int,
                      folder_id: int | None = None,
                      title: str = "",
                      text_content: str = "") -> Note:
    """创建一条笔记，初始 AI 状态 pending。"""
    n = Note(user_id=user_id, folder_id=folder_id, title=title or "",
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
                     folder_id: int | None = None,
                     archived: bool | None = None) -> tuple[list[Note], int]:
    """分页列出当前用户的笔记，支持按文件夹 / 归档过滤。"""
    base = [Note.user_id == user_id, Note.deleted_at.is_(None)]
    if folder_id is not None:
        base.append(Note.folder_id == folder_id)
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
                      folder_id: int | None = None,
                      title: str | None = None,
                      text_content: str | None = None,
                      is_archived: bool | None = None) -> Note:
    """增量更新笔记字段。text_content 修改会重置 AI 状态为 pending。"""
    n = await get_note(db, note_id, user_id)
    if folder_id is not None:
        n.folder_id = folder_id
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
