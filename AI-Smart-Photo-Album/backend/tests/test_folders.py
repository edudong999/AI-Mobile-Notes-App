"""文件夹服务测试：列表/创建/删除/重名检测。"""
import pytest
from app.services.folder_service import (
    list_folders, create_folder, delete_folder,
)
from app.database import AsyncSessionLocal


async def _make_user(db, username: str) -> int:
    """插入一个用户并返回 user_id（不触发密码哈希；纯 FK 锚点）。"""
    from app.models import User
    from app.security import hash_password
    u = User(username=username, password_hash=hash_password("x"), status=1)
    db.add(u)
    await db.commit()
    await db.refresh(u)
    return u.user_id


@pytest.mark.asyncio
async def test_create_list_delete():
    async with AsyncSessionLocal() as db:
        uid = await _make_user(db, "folder_user_a")

        f1 = await create_folder(db, uid, "学习")
        f2 = await create_folder(db, uid, "工作")
        folders = await list_folders(db, uid)
        names = [f.name for f in folders]
        assert "学习" in names and "工作" in names

        await delete_folder(db, f1.folder_id, uid)
        folders = await list_folders(db, uid)
        assert all(f.folder_id != f1.folder_id for f in folders)


@pytest.mark.asyncio
async def test_duplicate_name():
    from app.exceptions import BizException
    async with AsyncSessionLocal() as db:
        uid = await _make_user(db, "folder_user_b")

        await create_folder(db, uid, "学习")
        with pytest.raises(BizException):
            await create_folder(db, uid, "学习")