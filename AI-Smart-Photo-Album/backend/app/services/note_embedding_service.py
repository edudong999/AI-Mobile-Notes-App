"""笔记 embedding 服务：写入 NoteEmbedding 行。

Task 10 实现 run_embed：调用 LLM provider.embed() 把文本转向量，
并存到 note_embeddings 表（每条笔记一行，upsert by note_id）。

Task 11 在本文件上增加 search / semantic_search 以及分块嵌入逻辑。
"""
from __future__ import annotations

import json

from sqlalchemy import delete
from sqlalchemy.ext.asyncio import AsyncSession

from app.models import Note, NoteEmbedding
from app.services import llm


def _chunk_text(text: str, chunk_size: int = 800, max_chunks: int = 4) -> list[str]:
    """把长文本切成最多 max_chunks 段，每段 chunk_size 字符。

    - 空字符串返回空列表。
    - 不在中间做语义切分，简单按字符数切片。
    """
    text = text.strip()
    if not text:
        return []
    chunks = [text[i:i + chunk_size] for i in range(0, len(text), chunk_size)]
    return chunks[:max_chunks]


async def run_embed(db: AsyncSession, note_id: int) -> None:
    """把笔记的 text_content 转 embedding 并 upsert 到 note_embeddings。

    - 笔记不存在或 text_content 为空：直接返回。
    - 分块后取平均向量并 L2 归一化，作为最终向量存储。
    - provider.embed() 失败由调用方（_process_note）的外层 try 捕获，
      走 _on_failure 退避重试。
    """
    n = await db.get(Note, note_id)
    if n is None:
        return

    text = (n.summary or "") + "\n" + (n.text_content or "")
    chunks = _chunk_text(text)
    if not chunks:
        return

    provider = llm.get_provider()
    vectors: list[list[float]] = []
    for c in chunks:
        vec = await provider.embed(c)
        vectors.append(vec)
    if not vectors:
        return

    dim = len(vectors[0])
    avg = [sum(v[i] for v in vectors) / len(vectors) for i in range(dim)]
    norm = sum(x * x for x in avg) ** 0.5 or 1.0
    avg = [x / norm for x in avg]

    # upsert：删除原行后再插入（note_id 是主键）
    await db.execute(delete(NoteEmbedding).where(NoteEmbedding.note_id == note_id))
    db.add(NoteEmbedding(
        note_id=note_id,
        user_id=n.user_id,
        vector_json=json.dumps(avg, ensure_ascii=False),
        dim=dim,
        model=provider.name,
    ))
    await db.commit()


def _cosine(a: list[float], b: list[float]) -> float:
    """两个等长向量的余弦相似度（向量已在 embedding 写入时 L2 归一化）。

    两个 L2 归一化向量的余弦相似度等价于它们的点积。
    """
    return sum(x * y for x, y in zip(a, b))