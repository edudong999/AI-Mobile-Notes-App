"""笔记导出服务测试：md / zip 两种格式。"""
import io
import zipfile
from pathlib import Path

import pytest
from sqlalchemy import delete

from app.database import AsyncSessionLocal
from app.models import Note, NoteFile, User
from app.security import hash_password
from app.services import export_service, note_service
from app.services.note_file_service import upload_note_files


async def _make_user(db, uid: int) -> None:
    existing = await db.get(User, uid)
    if existing is None:
        db.add(User(
            user_id=uid,
            username=f"test_export_{uid}",
            password_hash=hash_password("x"),
            status=1,
        ))
        await db.commit()


async def _fresh_note(db, uid: int) -> int:
    await db.execute(delete(NoteFile).where(NoteFile.user_id == uid))
    await db.execute(delete(Note).where(Note.user_id == uid))
    await db.commit()
    n = await note_service.create_note(db, uid, title="导出测试", text_content="正文内容")
    return n.note_id


@pytest.mark.asyncio
async def test_export_md_creates_markdown_file():
    """format=md：导出 /static/exports/note_<id>.md，文件存在、含标题/正文。"""
    async with AsyncSessionLocal() as db:
        uid = 60001
        await _make_user(db, uid)
        note_id = await _fresh_note(db, uid)

        url = await export_service.build_export(db, note_id, uid, "md")
        assert url.startswith("/static/exports/note_")
        assert url.endswith(".md")

        path = Path(export_service.EXPORT_DIR) / f"note_{note_id}.md"
        assert path.exists()
        body = path.read_text(encoding="utf-8")
        assert "导出测试" in body
        assert "正文内容" in body


@pytest.mark.asyncio
async def test_export_zip_contains_md_and_images():
    """format=zip：导出 .zip，包含 note.md 与 images/<原图名>。"""
    async with AsyncSessionLocal() as db:
        uid = 60002
        await _make_user(db, uid)
        note_id = await _fresh_note(db, uid)

        # 挂一张图，让 zip 里有 images/
        fake = io.BytesIO(b"\x89PNG\r\n\x1a\n" + b"1" * 64)
        await upload_note_files(db, uid, note_id, [("test.png", fake.getvalue())])

        url = await export_service.build_export(db, note_id, uid, "zip")
        assert url.endswith(".zip")
        zip_path = Path(export_service.EXPORT_DIR) / f"note_{note_id}.zip"
        assert zip_path.exists()

        with zipfile.ZipFile(zip_path, "r") as zf:
            names = zf.namelist()
            assert "note.md" in names
            # 至少一张图被打进 images/
            assert any(n.startswith("images/") for n in names)
            with zf.open("note.md") as f:
                md = f.read().decode("utf-8")
            assert "导出测试" in md


@pytest.mark.asyncio
async def test_export_unknown_format_falls_back_to_zip():
    """非 md/zip 格式：默认按 zip 处理，便于容错。"""
    async with AsyncSessionLocal() as db:
        uid = 60003
        await _make_user(db, uid)
        note_id = await _fresh_note(db, uid)

        url = await export_service.build_export(db, note_id, uid, "docx")
        assert url.endswith(".zip")
        zip_path = Path(export_service.EXPORT_DIR) / f"note_{note_id}.zip"
        assert zip_path.exists()
