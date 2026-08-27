"""笔记服务测试：CRUD + 跨用户隔离 + 软删除。"""
import pytest
from app.database import AsyncSessionLocal
from app.services.note_service import (
    create_note, list_notes, get_note, soft_delete, update_note,
)
from app.models import AIStatus, Note


async def _make_user(db, username: str) -> int:
    """插入一个用户并返回 user_id（不触发密码哈希校验；纯 FK 锚点）。"""
    from app.models import User
    from app.security import hash_password
    u = User(username=username, password_hash=hash_password("x"), status=1)
    db.add(u)
    await db.commit()
    await db.refresh(u)
    return u.user_id


async def _ensure_user(db, username: str) -> int:
    """已存在则返回 user_id，否则创建。供跨多个测试重复使用同一 uid 时复用。"""
    from app.models import User
    existing = await db.get(User, _user_id_from_username(username))
    if existing is not None:
        return existing.user_id
    return await _make_user(db, username)


def _user_id_from_username(username: str) -> int:
    """test_notes_<uid> -> uid。"""
    # 约定测试用户名以 test_notes_<uid> 开头
    if username.startswith("test_notes_"):
        suffix = username[len("test_notes_"):]
        return int(suffix)
    return -1


@pytest.mark.asyncio
async def test_create_and_list():
    async with AsyncSessionLocal() as db:
        from sqlalchemy import delete
        from app.models import User
        uid = 88888
        existing = await db.get(User, uid)
        if existing is None:
            db.add(User(user_id=uid, username=f"test_notes_{uid}",
                        password_hash=__import__("app.security", fromlist=["hash_password"]).hash_password("x")))
            await db.commit()
        await db.execute(delete(Note).where(Note.user_id == uid))
        await db.commit()

        n = await create_note(db, uid, title="高数笔记", text_content="极限定义...")
        rows, total = await list_notes(db, uid, page=1, page_size=20)

        assert any(x.note_id == n.note_id for x in rows)
        assert total == 1


@pytest.mark.asyncio
async def test_cross_user_isolation():
    async with AsyncSessionLocal() as db:
        from sqlalchemy import delete
        from app.models import User
        from app.security import hash_password
        uid = 88888
        existing = await db.get(User, uid)
        if existing is None:
            db.add(User(user_id=uid, username=f"test_notes_{uid}_b",
                        password_hash=hash_password("x")))
            await db.commit()
        # 确保隔离测试起点干净
        await db.execute(delete(Note).where(Note.user_id == uid))
        await db.commit()

        n = await create_note(db, uid, title="A")

        # 用 88889（不存在的用户）跨用户访问应被 BizException 挡住
        with pytest.raises(Exception):
            await get_note(db, n.note_id, user_id=88889)


@pytest.mark.asyncio
async def test_soft_delete():
    async with AsyncSessionLocal() as db:
        from sqlalchemy import delete
        from app.models import User
        from app.security import hash_password
        uid = 88888
        existing = await db.get(User, uid)
        if existing is None:
            db.add(User(user_id=uid, username=f"test_notes_{uid}_c",
                        password_hash=hash_password("x")))
            await db.commit()
        # 清掉上次测试可能留下的 note
        await db.execute(delete(Note).where(Note.user_id == uid))
        await db.commit()

        n = await create_note(db, uid, title="B")
        await soft_delete(db, n.note_id, uid)

        rows, total = await list_notes(db, uid, page=1, page_size=20)
        assert all(x.note_id != n.note_id for x in rows)
