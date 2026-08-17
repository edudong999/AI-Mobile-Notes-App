"""笔记 AI 服务 — 占位；Task 10 实现 enqueue_note_ai + 同步路由。"""
from sqlalchemy.ext.asyncio import AsyncSession


async def enqueue_note_ai(db: AsyncSession, note_id: int, sub_kind=None) -> int:
    """占位：Task 10 替换为真实入队逻辑（创建 AITask 行 + 触发 worker）。"""
    return 0
