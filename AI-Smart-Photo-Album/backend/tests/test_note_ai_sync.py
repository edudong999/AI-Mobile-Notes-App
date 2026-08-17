"""笔记 AI 同步路由（润色 / 翻译）端到端测试。

走 note_ai_service 的 run_polish / run_translate，验证 MockProvider 的同步分支。
题目生成测试已包含在 test_note_worker 链路里。
"""
import pytest
from sqlalchemy import delete

from app.database import AsyncSessionLocal
from app.models import Note, User
from app.security import hash_password
from app.services import note_ai_service, note_service
from app.services.llm import get_provider

# 清掉 get_provider 的 lru_cache，避免上一组测试注入 mock 后影响本测试
get_provider.cache_clear()


@pytest.mark.asyncio
async def test_polish_and_translate_with_mock():
    """验证同步润色 / 翻译 路径走通；mock provider 在测试环境返回带 [polish]/[translate] 标记的字符串。"""
    async with AsyncSessionLocal() as db:
        uid = 44444
        existing = await db.get(User, uid)
        if existing is None:
            db.add(User(
                user_id=uid,
                username=f"test_ai_sync_{uid}",
                password_hash=hash_password("x"),
            ))
            await db.commit()
        await db.execute(delete(Note).where(Note.user_id == uid))
        await db.commit()

        n = await note_service.create_note(db, uid, title="t", text_content="原始文本")
        out = await note_ai_service.run_polish(db, n.note_id, "polish", "hello")
        assert "[polish]" in out
        out2 = await note_ai_service.run_translate(db, n.note_id, "en", "你好")
        assert "Translated to English" in out2