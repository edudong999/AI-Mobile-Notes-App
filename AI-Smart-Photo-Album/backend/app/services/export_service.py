"""笔记导出 — 占位；Task 12 实现 build_export。

当前 stub 只返回 `/tmp/notes/{note_id}.{fmt}`，足以让
`app.routers.notes` 在 import 时拿到一个 awaitable。
Task 12 将替换为真正的 md/zip 生成逻辑（拼装 markdown / 打包图片附件）。
"""
from sqlalchemy.ext.asyncio import AsyncSession


async def build_export(db: AsyncSession, note_id: int, user_id: int, fmt: str) -> str:
    """占位实现：Task 12 替换为真正的 md/zip 导出逻辑。"""
    return f"/tmp/notes/{note_id}.{fmt}"
