"""笔记搜索服务测试：关键字 + 语义搜索 + 路由响应字段。"""
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
        # 每条 hit 必须包含前端依赖的 title 字段（之前因 schema 缺失导致 UI 永远空标题）
        for h in kw_hits:
            assert "title" in h
            assert isinstance(h["title"], str)

        # 语义搜索：先嵌入两条笔记
        await note_embedding_service.run_embed(db, n1.note_id)
        await note_embedding_service.run_embed(db, n2.note_id)
        sem_hits = await note_search_service.semantic_search(db, uid, "高数")
        assert len(sem_hits) >= 1


@pytest.mark.asyncio
async def test_search_route_response_shape():
    """端到端：POST /api/v1/note-search 响应字段必须与前端 SearchResponse 模型对齐。

    关键字段：data.hits[] / data.mode，每个 hit 含 noteId/title/score/snippet/matchedSnippet。
    之前 list→hits、engine→mode 改名前后端漂移，前端读 data.hits 永远拿到 null。
    """
    from app.main import app
    from httpx import ASGITransport, AsyncClient
    from app.security import create_access_token

    uid = 55556
    token = create_access_token(uid)

    async with AsyncSessionLocal() as db:
        existing = await db.get(User, uid)
        if existing is None:
            db.add(User(
                user_id=uid, username=f"test_search_route_{uid}",
                password_hash=hash_password("x"),
            ))
            await db.commit()
        await db.execute(delete(NoteEmbedding).where(NoteEmbedding.user_id == uid))
        await db.execute(delete(Note).where(Note.user_id == uid))
        await db.commit()
        await note_service.create_note(
            db, uid, title="路由测试笔记", text_content="漂移测试 keyword",
        )

    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test",
                           headers={"Authorization": f"Bearer {token}"}) as ac:
        r = await ac.post("/api/v1/note-search", json={"query": "漂移", "mode": "keyword"})
    assert r.status_code == 200, r.text
    body = r.json()
    assert body["code"] == 200
    data = body["data"]
    # 关键：字段名必须叫 hits，不是 list
    assert "hits" in data, f"missing 'hits' key, got {list(data.keys())}"
    assert "mode" in data
    assert isinstance(data["hits"], list)
    if data["hits"]:
        hit = data["hits"][0]
        for k in ("noteId", "title", "score", "snippet", "matchedSnippet"):
            assert k in hit, f"hit missing {k}: {hit}"