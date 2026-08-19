"""Image cleanup service: call LLM, persist cleaned file as a new NoteFile."""
from __future__ import annotations
import asyncio
import os

from sqlalchemy.ext.asyncio import AsyncSession

from app.exceptions import BizException
from app.models import Note, NoteFile
from app.services import note_service
from app.services.file_storage import (
    note_origin_path, note_thumbs_dir, save_bytes, public_url_for,
    absolute_url_for,
)
from app.services.llm import get_provider
from app.utils.image import make_thumbnail

CLEANUP_TIMEOUT_SEC = 60
MAX_OUTPUT_BYTES = 10 * 1024 * 1024


async def cleanup_image(
    db: AsyncSession,
    user_id: int,
    note_id: int,
    file_id: int,
    mode: str,
) -> tuple[NoteFile, str]:
    """Run the LLM on the original file, persist a new NoteFile.

    Returns (new_file, public_url).
    Raises BizException for 404/400/502/504.
    """
    n = await note_service.get_note(db, note_id, user_id)
    original = await db.get(NoteFile, file_id)
    if original is None or original.note_id != note_id:
        raise BizException(404, "图片不存在")

    abs_url = absolute_url_for(original.original_path)

    provider = get_provider()
    try:
        cleaned_bytes = await asyncio.wait_for(
            provider.cleanup_image(abs_url),
            timeout=CLEANUP_TIMEOUT_SEC,
        )
    except asyncio.TimeoutError:
        raise BizException(504, "图像清理超时,请重试")
    except Exception as e:
        msg = str(e)
        low = msg.lower()
        if "auth" in low or "key" in low or "balance" in low or "quota" in low:
            raise BizException(502, "图像编辑服务不可用")
        if "format" in low or "size" in low or "image" in low or "unsupported" in low:
            raise BizException(400, "图片格式不支持,请使用 JPEG/PNG,不超过 10MB")
        raise BizException(502, f"图像编辑服务错误: {msg}")

    if not cleaned_bytes:
        raise BizException(502, "图像编辑服务返回空结果")
    if len(cleaned_bytes) > MAX_OUTPUT_BYTES:
        raise BizException(400, "图片格式不支持,请使用 JPEG/PNG,不超过 10MB")

    parent_id = file_id if mode == "commit_insert" else None
    kind = "cleaned" if mode == "commit_insert" else "original"
    new_name = f"cleaned_{file_id}_{os.urandom(3).hex()}.png"
    ext = "png"
    path = note_origin_path(note_id, ext)
    await save_bytes(path, cleaned_bytes)

    thumb_dir = note_thumbs_dir()
    thumb_dir.mkdir(parents=True, exist_ok=True)
    thumb_path = thumb_dir / (path.stem + ".webp")
    try:
        make_thumbnail(path, thumb_path)
    except Exception:
        thumb_path = None

    nf = NoteFile(
        note_id=note_id,
        user_id=user_id,
        file_name=new_name,
        original_path=str(path),
        thumbnail_path=str(thumb_path) if thumb_path else None,
        width=None,
        height=None,
        sort_index=original.sort_index + 1,
        kind=kind,
        parent_file_id=parent_id,
    )
    db.add(nf)
    await db.commit()
    await db.refresh(nf)
    return nf, public_url_for(str(path))