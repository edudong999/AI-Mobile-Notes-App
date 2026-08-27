"""笔记导出：md / zip 两种格式。

- md: 单文件 Markdown（摘要 + 正文 + 附图引用 + 练习题）。
- zip: 在 md 之上打包所有本地图片附件。

产物写到 `<DATA_DIR>/exports/`，由 main.py 的 `/static/exports` mount 提供下载。
"""
from __future__ import annotations

import json
import zipfile
from pathlib import Path

from sqlalchemy.ext.asyncio import AsyncSession

from app.config import settings
from app.models import Note, NoteFile
from app.services import note_service

EXPORT_DIR = Path(settings.DATA_DIR) / "exports"


async def build_export(db: AsyncSession, note_id: int, user_id: int, fmt: str) -> str:
    """生成 md 或 zip 导出文件，返回相对 URL `/static/exports/<file>`。

    fmt 仅支持 "md" / "zip"，其他值当作 zip 处理。
    """
    EXPORT_DIR.mkdir(parents=True, exist_ok=True)
    n = await note_service.get_note(db, note_id, user_id)
    files = await note_service.list_files(db, note_id)
    qs = await note_service.list_questions(db, note_id)

    if fmt == "md":
        out_path = EXPORT_DIR / f"note_{n.note_id}.md"
        out_path.write_text(_render_md(n, files, qs), encoding="utf-8")
        return f"/static/exports/{out_path.name}"

    # 默认 zip：包含 note.md + images/*
    out_path = EXPORT_DIR / f"note_{n.note_id}.zip"
    with zipfile.ZipFile(out_path, "w", zipfile.ZIP_DEFLATED) as zf:
        zf.writestr("note.md", _render_md(n, files, qs))
        for f in files:
            p = Path(f.original_path)
            if p.exists():
                zf.write(p, arcname=f"images/{p.name}")
    return f"/static/exports/{out_path.name}"


def _render_md(n: Note, files: list[NoteFile], qs: list) -> str:
    """将笔记内容渲染成 Markdown 字符串。"""
    lines: list[str] = [
        f"# {n.title or '未命名笔记'}\n",
        "## 摘要\n",
        n.summary or "（无）",
        "\n",
        "## 正文\n",
        n.text_content or "（无）\n",
    ]
    if files:
        lines.append("\n## 附图\n")
        for f in files:
            lines.append(f"- ![](images/{Path(f.original_path).name})")
    if qs:
        lines.append("\n## 练习题\n")
        for i, q in enumerate(qs, 1):
            lines.append(f"\n### {i}. {q.stem}")
            if q.options_json:
                opts = json.loads(q.options_json)
                for k, v in enumerate(opts):
                    lines.append(f"- ({chr(65 + k)}) {v}")
            lines.append(f"**答案**: {q.answer}\n")
            lines.append(f"**解析**: {q.explanation}\n")
    return "\n".join(lines)