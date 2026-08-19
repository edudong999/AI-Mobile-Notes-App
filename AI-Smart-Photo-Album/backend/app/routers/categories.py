"""Category router: list / create / update / delete / reorder + per-note set / list."""
from fastapi import APIRouter, Depends
from sqlalchemy.ext.asyncio import AsyncSession
from app.database import get_db
from app.deps import get_current_user
from app.models import User
from app.response import ok
from app.schemas.category import (
    CategoryCreate, CategoryUpdate, CategoryReorderRequest, CategoryItem,
)
from app.services import category_service, note_service

router = APIRouter(prefix="/api/v1/categories", tags=["categories"])


@router.get("")
async def list_(db: AsyncSession = Depends(get_db), user: User = Depends(get_current_user)):
    """List user's categories with note counts."""
    cats = await category_service.list_categories(db, user.user_id)
    items = []
    for c in cats:
        items.append(CategoryItem(
            categoryId=c.category_id, name=c.name, color=c.color,
            sortIndex=c.sort_index,
            noteCount=await category_service.count_notes(db, user.user_id, c.category_id),
        ).model_dump())
    return ok(data={"list": items})


@router.post("")
async def create(body: CategoryCreate, db: AsyncSession = Depends(get_db),
                 user: User = Depends(get_current_user)):
    """Create a category; 400 if name is duplicate."""
    c = await category_service.create_category(db, user.user_id, body.name, body.color or "#4A90E2")
    return ok(data={"categoryId": c.category_id})


@router.patch("/{category_id}")
async def update(category_id: int, body: CategoryUpdate,
                 db: AsyncSession = Depends(get_db),
                 user: User = Depends(get_current_user)):
    await category_service.update_category(db, category_id, user.user_id,
                                           body.name, body.color, body.sortIndex)
    return ok(message="已保存")


@router.delete("/{category_id}")
async def delete(category_id: int, db: AsyncSession = Depends(get_db),
                 user: User = Depends(get_current_user)):
    await category_service.delete_category(db, category_id, user.user_id)
    return ok(message="已删除")


@router.post("/reorder")
async def reorder(body: CategoryReorderRequest,
                  db: AsyncSession = Depends(get_db),
                  user: User = Depends(get_current_user)):
    await category_service.reorder(db, user.user_id, body.orderedIds)
    return ok(message="已排序")


@router.get("/{category_id}/notes")
async def notes_in(category_id: int,
                   db: AsyncSession = Depends(get_db),
                   user: User = Depends(get_current_user)):
    """List notes belonging to this category."""
    items = await note_service.list_notes_in_category(db, user.user_id, category_id)
    return ok(data={"list": [n.model_dump() for n in items]})