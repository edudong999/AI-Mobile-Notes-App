"""笔记搜索服务：关键字 + 语义。

- keyword_search：LIKE 匹配 title / text_content / summary。
- semantic_search：用 embedding 行做余弦相似度排序。

共用工具：
- _find_snippet：在 text 里找 query 的位置，返回带上下文窗口的片段。
"""
from __future__ import annotations

import json

from sqlalchemy import or_, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models import Note, NoteEmbedding
from app.services import llm
from app.services.note_embedding_service import _cosine


def _find_snippet(text: str, query: str, ctx: int = 30) -> str:
    """返回 query 在 text 里首次出现位置的 ±ctx 字符片段。"""
    pos = text.lower().find(query.lower())
    if pos < 0:
        return text[: ctx * 2]
    return text[max(0, pos - ctx): pos + len(query) + ctx]


async def keyword_search(db: AsyncSession, user_id: int, query: str,
                         folder_id: int | None = None,
                         top_k: int = 20) -> list[dict]:
    """关键字搜索：LIKE 匹配标题/正文/摘要。"""
    pat = f"%{query}%"
    # 修复 spec 中 `* if folder_id is not None else ()` 的语法错误：
    # 改成先把所有条件放到列表里再统一 unpack。
    conds = [
        Note.user_id == user_id,
        Note.deleted_at.is_(None),
        or_(
            Note.title.like(pat),
            Note.text_content.like(pat),
            Note.summary.like(pat),
        ),
    ]
    if folder_id is not None:
        conds.append(Note.folder_id == folder_id)
    rows = (await db.execute(
        select(Note).where(*conds)
        .order_by(Note.updated_at.desc()).limit(top_k)
    )).scalars().all()
    return [{
        "noteId": n.note_id,
        "title": n.title or "",
        "score": 1.0,
        "snippet": (n.summary or n.text_content or "")[:120],
        "matchedSnippet": _find_snippet(n.text_content or n.summary or "", query),
    } for n in rows]


async def semantic_search(db: AsyncSession, user_id: int, query: str,
                          folder_id: int | None = None,
                          top_k: int = 20) -> list[dict]:
    """语义搜索：用笔记 embedding 与 query embedding 做余弦相似度。"""
    provider = llm.get_provider()
    qvec = await provider.embed(query)

    emb_rows = list((await db.execute(
        select(NoteEmbedding).where(NoteEmbedding.user_id == user_id)
    )).scalars().all())
    if not emb_rows:
        return []

    scored: list[tuple[int, float]] = []
    for e in emb_rows:
        v = json.loads(e.vector_json)
        scored.append((e.note_id, _cosine(qvec, v)))
    scored.sort(key=lambda x: x[1], reverse=True)

    top_ids = [nid for nid, _ in scored[:top_k]]
    if not top_ids:
        return []

    # 同样修复 spec 的语法错误：用列表先收集条件再 unpack。
    conds = [
        Note.note_id.in_(top_ids),
        Note.deleted_at.is_(None),
        Note.user_id == user_id,
    ]
    if folder_id is not None:
        conds.append(Note.folder_id == folder_id)
    notes = list((await db.execute(
        select(Note).where(*conds)
    )).scalars().all())
    by_id = {n.note_id: n for n in notes}

    out: list[dict] = []
    for nid, score in scored[:top_k]:
        n = by_id.get(nid)
        if n is None:
            continue
        out.append({
            "noteId": nid,
            "title": n.title or "",
            "score": round(float(score), 4),
            "snippet": (n.summary or n.text_content or "")[:120],
            "matchedSnippet": _find_snippet(n.text_content or n.summary or "", query),
        })
    return out