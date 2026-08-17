"""笔记 embedding 服务：写入 NoteEmbedding 行。

Task 10 实现 run_embed：调用 LLM provider.embed() 把文本转向量，
并存到 note_embeddings 表（每条笔记一行，upsert by note_id）。

Task 11 在本文件上增加 search / semantic_search。
"""
from __future__ import annotations

import json

from sqlalchemy.ext.asyncio import AsyncSession

from app.models import Note, NoteEmbedding
from app.services import llm


async def run_embed(db: AsyncSession, note_id: int) -> None:
    """把笔记的 text_content 转 embedding 并 upsert 到 note_embeddings。

    - 笔记不存在或 text_content 为空：直接返回。
    - provider.embed() 失败由调用方（_process_note）的外层 try 捕获，
      走 _on_failure 退避重试。
    """
    n = await db.get(Note, note_id)
    if n is None:
        return

    text = n.text_content or ""
    if not text.strip():
        return

    provider = llm.get_provider()
    vec = await provider.embed(text)

    existing = await db.get(NoteEmbedding, note_id)
    if existing is None:
        db.add(NoteEmbedding(
            note_id=note_id,
            user_id=n.user_id,
            vector_json=json.dumps(vec, ensure_ascii=False),
            dim=len(vec),
            model=provider.name,
        ))
    else:
        existing.vector_json = json.dumps(vec, ensure_ascii=False)
        existing.dim = len(vec)
        existing.model = provider.name
    await db.commit()