"""文件夹服务：列表、创建、改名、排序、删除、按文件夹统计笔记数。"""
from __future__ import annotations
from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession
from app.exceptions import BizException
from app.models import Note, NotebookFolder


async def list_folders(db: AsyncSession, user_id: int) -> list[NotebookFolder]:
    """列出用户的所有文件夹，按 sort_index / folder_id 升序。"""
    rows = (await db.execute(
        select(NotebookFolder)
        .where(NotebookFolder.user_id == user_id)
        .order_by(NotebookFolder.sort_index, NotebookFolder.folder_id)
    )).scalars().all()
    return list(rows)


async def create_folder(db: AsyncSession, user_id: int, name: str,
                        color: str = "#4A90E2") -> NotebookFolder:
    """创建文件夹：重名抛 422；sort_index 自动取 max+1。"""
    name = name.strip()
    if not name:
        raise BizException(400, "文件夹名不能为空")
    existing = (await db.execute(
        select(NotebookFolder).where(
            NotebookFolder.user_id == user_id, NotebookFolder.name == name,
        )
    )).scalars().first()
    if existing:
        raise BizException(422, "FOLDER_NAME_DUPLICATE: 文件夹已存在")
    max_idx = (await db.execute(
        select(func.coalesce(func.max(NotebookFolder.sort_index), -1))
        .where(NotebookFolder.user_id == user_id)
    )).scalar_one()
    f = NotebookFolder(user_id=user_id, name=name, color=color, sort_index=max_idx + 1)
    db.add(f)
    await db.commit()
    await db.refresh(f)
    return f


async def update_folder(db: AsyncSession, folder_id: int, user_id: int,
                        name: str | None, color: str | None,
                        sort_index: int | None) -> NotebookFolder:
    """更新文件夹（仅修改非 None 字段）；越权或不存在抛 404。"""
    f = await db.get(NotebookFolder, folder_id)
    if f is None or f.user_id != user_id:
        raise BizException(404, "文件夹不存在")
    if name is not None:
        f.name = name.strip() or f.name
    if color is not None:
        f.color = color
    if sort_index is not None:
        f.sort_index = sort_index
    await db.commit()
    await db.refresh(f)
    return f


async def delete_folder(db: AsyncSession, folder_id: int, user_id: int) -> None:
    """删除文件夹；越权或不存在抛 404。Note.folder_id 为 SET NULL 不阻塞。"""
    f = await db.get(NotebookFolder, folder_id)
    if f is None or f.user_id != user_id:
        raise BizException(404, "文件夹不存在")
    await db.delete(f)
    await db.commit()


async def reorder(db: AsyncSession, user_id: int, ordered_ids: list[int]) -> None:
    """按 ordered_ids 顺序把 sort_index 重写为 0..n-1。"""
    for i, fid in enumerate(ordered_ids):
        await update_folder(db, fid, user_id, None, None, i)


async def count_notes_by_folder(db: AsyncSession, user_id: int, folder_id: int) -> int:
    """统计某文件夹下当前用户未删除的笔记数。"""
    return (await db.execute(
        select(func.count(Note.note_id)).where(
            Note.user_id == user_id, Note.folder_id == folder_id, Note.deleted_at.is_(None),
        )
    )).scalar_one()