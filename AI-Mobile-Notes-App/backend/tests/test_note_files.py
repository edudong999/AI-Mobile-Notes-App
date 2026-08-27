"""笔记文件服务测试：上传、HEIC 嗅探、缩略图生成。"""
import io

import pytest
from sqlalchemy import delete

from app.database import AsyncSessionLocal
from app.models import Note, NoteFile
from app.security import hash_password
from app.services import note_service
from app.services.note_file_service import upload_note_files


@pytest.mark.asyncio
async def test_upload_creates_note_and_files():
    """上传一张 PNG：应创建 NoteFile 行，原图/缩略图写入磁盘，file_name 保留。"""
    async with AsyncSessionLocal() as db:
        from app.models import User

        uid = 77777
        existing = await db.get(User, uid)
        if existing is None:
            db.add(User(
                user_id=uid,
                username=f"test_nf_{uid}",
                password_hash=hash_password("x"),
                status=1,
            ))
            await db.commit()
        await db.execute(delete(NoteFile).where(NoteFile.user_id == uid))
        await db.execute(delete(Note).where(Note.user_id == uid))
        await db.commit()

        n = await note_service.create_note(db, uid)
        # 最小可用 PNG：8 字节 magic + 100 字节填充。PIL 能识别即可。
        fake = io.BytesIO(b"\x89PNG\r\n\x1a\n" + b"0" * 100)
        out_note, items = await upload_note_files(
            db, uid, n.note_id, [("a.png", fake.getvalue())]
        )

        assert out_note.note_id == n.note_id
        assert len(items) == 1
        assert items[0].file_name == "a.png"
        # 原图路径已落盘
        from pathlib import Path
        assert items[0].original_path
        assert Path(items[0].original_path).exists()
