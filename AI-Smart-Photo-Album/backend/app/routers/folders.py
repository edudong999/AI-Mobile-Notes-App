"""文件夹路由：list / create / update / delete / reorder。"""
from fastapi import APIRouter, Depends
from sqlalchemy.ext.asyncio import AsyncSession
from app.database import get_db
from app.deps import get_current_user
from app.models import User
from app.response import ok
from app.schemas.folder import (
    FolderCreate, FolderUpdate, FolderReorderRequest, FolderItem,
)
from app.services import folder_service

router = APIRouter(prefix="/api/v1/folders", tags=["folders"])


@router.get("")
async def list_(db: AsyncSession = Depends(get_db), user: User = Depends(get_current_user)):
    """列出当前用户的所有文件夹，含每个文件夹下的笔记数。"""
    folders = await folder_service.list_folders(db, user.user_id)
    items = [
        FolderItem(
            folderId=f.folder_id, name=f.name, color=f.color, sortIndex=f.sort_index,
            noteCount=await folder_service.count_notes_by_folder(db, user.user_id, f.folder_id),
        ).model_dump()
        for f in folders
    ]
    return ok(data={"list": items})


@router.post("")
async def create(body: FolderCreate, db: AsyncSession = Depends(get_db),
                 user: User = Depends(get_current_user)):
    """新建文件夹；color 默认 #4A90E2。"""
    f = await folder_service.create_folder(db, user.user_id, body.name, body.color or "#4A90E2")
    return ok(data={"folderId": f.folder_id})


@router.patch("/{folder_id}")
async def update(folder_id: int, body: FolderUpdate,
                 db: AsyncSession = Depends(get_db),
                 user: User = Depends(get_current_user)):
    """更新文件夹（改名 / 改颜色 / 调整 sortIndex）。"""
    await folder_service.update_folder(db, folder_id, user.user_id, body.name, body.color, body.sortIndex)
    return ok(message="已保存")


@router.delete("/{folder_id}")
async def delete(folder_id: int, db: AsyncSession = Depends(get_db),
                 user: User = Depends(get_current_user)):
    """删除文件夹（Note.folder_id 会通过 SET NULL 自动解除关联）。"""
    await folder_service.delete_folder(db, folder_id, user.user_id)
    return ok(message="已删除")


@router.post("/reorder")
async def reorder(body: FolderReorderRequest, db: AsyncSession = Depends(get_db),
                  user: User = Depends(get_current_user)):
    """批量重排序：按 orderedIds 顺序写回 sortIndex。"""
    await folder_service.reorder(db, user.user_id, body.orderedIds)
    return ok(message="已排序")