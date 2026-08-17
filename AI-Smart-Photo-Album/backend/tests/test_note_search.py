"""笔记搜索服务测试：关键字 + 语义搜索。"""
import pytest
from sqlalchemy import delete

from app.database import AsyncSessionLocal
from app.models import Note, NoteEmbedding, User
from app.security import hash_password
from app.services import note_embedding_service, note_search_service, note_service


@pytest.mark.asyncio
async def test_keyword_and_semantic():
    """两条笔记：一条匹配关键字「极限」，另一条匹配「英语」。
    关键字搜索能找到「极限」对应笔记，语义搜索也能至少命中 1 条。
    """
    async with AsyncSessionLocal() as db:
        uid = 55555
        existing = await db.get(User, uid)
        if existing is None:
            db.add(User(
                user_id=uid,
                username=f"test_search_{uid}",
                password_hash=hash_password("x"),
            ))
            await db.commit()

        # 测试隔离：清掉所有以该 user_id 关联的笔记 / embedding
        await db.execute(delete(NoteEmbedding).where(NoteEmbedding.user_id == uid))
        await db.execute(delete(Note).where(Note.user_id == uid))
        await db.commit()

        n1 = await note_service.create_note(
            db, uid,
            title="高数极限",
            text_content="极限的定义：x→a 时 f(x)→L，则称 L 是 f(x) 在 a 处的极限。",
        )
        n2 = await note_service.create_note(
            db, uid,
            title="英语语法",
            text_content="过去完成时：had + 过去分词，用于过去两个动作的先后。",
        )

        # 关键字搜索
        kw_hits = await note_search_service.keyword_search(db, uid, "极限")
        assert any(h["noteId"] == n1.note_id for h in kw_hits)

        # 语义搜索：先嵌入两条笔记
        await note_embedding_service.run_embed(db, n1.note_id)
        await note_embedding_service.run_embed(db, n2.note_id)
        sem_hits = await note_search_service.semantic_search(db, uid, "高数")
        assert len(sem_hits) >= 1