"""Note↔Category M:N operations."""
from __future__ import annotations
from sqlalchemy import delete, insert, select
from sqlalchemy.ext.asyncio import AsyncSession
from app.exceptions import BizException
from app.models import Category, Note, NoteCategory


async def set_note_categories(db: AsyncSession, note_id: int, user_id: int,
                              category_ids: list[int]) -> list[int]:
    """Replace the note's full category set. Empty list = remove all."""
    n = await db.get(Note, note_id)
    if n is None or n.user_id != user_id or n.deleted_at is not None:
        raise BizException(404, "笔记不存在")
    if category_ids:
        owned = (await db.execute(
            select(Category).where(
                Category.category_id.in_(category_ids),
                Category.user_id == user_id,
            )
        )).scalars().all()
        if len(owned) != len(set(category_ids)):
            raise BizException(404, "分类不存在")
    await db.execute(delete(NoteCategory).where(NoteCategory.note_id == note_id))
    if category_ids:
        await db.execute(
            insert(NoteCategory),
            [{"note_id": note_id, "category_id": cid} for cid in set(category_ids)],
        )
    await db.commit()
    return list(set(category_ids))


async def get_note_categories(db: AsyncSession, note_id: int) -> list[int]:
    rows = (await db.execute(
        select(NoteCategory.category_id).where(NoteCategory.note_id == note_id)
    )).all()
    return [r[0] for r in rows]