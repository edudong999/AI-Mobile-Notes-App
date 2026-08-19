"""Category service: list, create, rename, reorder, delete, count notes by category."""
from __future__ import annotations
from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession
from app.exceptions import BizException
from app.models import Category


async def list_categories(db: AsyncSession, user_id: int) -> list[Category]:
    """List a user's categories, ordered by sort_index / category_id."""
    rows = (await db.execute(
        select(Category)
        .where(Category.user_id == user_id)
        .order_by(Category.sort_index, Category.category_id)
    )).scalars().all()
    return list(rows)


async def create_category(db: AsyncSession, user_id: int, name: str,
                           color: str = "#4A90E2") -> Category:
    """Create category; rejects duplicate (user_id, name)."""
    name = name.strip()
    if not name:
        raise BizException(400, "分类名不能为空")
    existing = (await db.execute(
        select(Category).where(
            Category.user_id == user_id, Category.name == name,
        )
    )).scalars().first()
    if existing:
        raise BizException(400, "分类名已存在")
    max_idx = (await db.execute(
        select(func.coalesce(func.max(Category.sort_index), -1))
        .where(Category.user_id == user_id)
    )).scalar_one()
    c = Category(user_id=user_id, name=name, color=color, sort_index=max_idx + 1)
    db.add(c)
    await db.commit()
    await db.refresh(c)
    return c


async def update_category(db: AsyncSession, category_id: int, user_id: int,
                          name: str | None, color: str | None,
                          sort_index: int | None) -> Category:
    """Update category (only non-None fields); 404 if not owned."""
    c = await db.get(Category, category_id)
    if c is None or c.user_id != user_id:
        raise BizException(404, "分类不存在")
    if name is not None:
        new_name = name.strip()
        if new_name and new_name != c.name:
            dup = (await db.execute(
                select(Category).where(
                    Category.user_id == user_id,
                    Category.name == new_name,
                    Category.category_id != category_id,
                )
            )).scalars().first()
            if dup:
                raise BizException(400, "分类名已存在")
            c.name = new_name
    if color is not None:
        c.color = color
    if sort_index is not None:
        c.sort_index = sort_index
    await db.commit()
    await db.refresh(c)
    return c


async def delete_category(db: AsyncSession, category_id: int, user_id: int) -> None:
    """Delete category; 404 if not owned."""
    c = await db.get(Category, category_id)
    if c is None or c.user_id != user_id:
        raise BizException(404, "分类不存在")
    await db.delete(c)
    await db.commit()


async def reorder(db: AsyncSession, user_id: int, ordered_ids: list[int]) -> None:
    """Rewrite sort_index 0..n-1 in the order given."""
    for i, cid in enumerate(ordered_ids):
        await update_category(db, cid, user_id, None, None, i)


async def count_notes(db: AsyncSession, user_id: int, category_id: int) -> int:
    """Count this user's active notes that belong to this category."""
    from app.models import Note, NoteCategory
    return (await db.execute(
        select(func.count(Note.note_id))
        .join(NoteCategory, NoteCategory.note_id == Note.note_id)
        .where(
            Note.user_id == user_id,
            Note.deleted_at.is_(None),
            NoteCategory.category_id == category_id,
        )
    )).scalar_one()