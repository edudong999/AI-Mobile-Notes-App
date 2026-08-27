"""笔记文件上传服务：保存原图、生成缩略图、写 NoteFile 行、置 AI pending。"""
from __future__ import annotations

from sqlalchemy.ext.asyncio import AsyncSession

from app.exceptions import BizException
from app.models import AIStatus, Note, NoteFile
from app.services import note_service
from app.services.file_storage import (
    note_origin_path,
    note_thumbs_dir,
    save_bytes,
)
from app.utils.image import (
    SUPPORTED_EXTS,
    make_thumbnail,
    normalize_ext,
    normalize_to_supported_ext,
    read_image_info,
)


async def upload_note_files(
    db: AsyncSession,
    user_id: int,
    note_id: int | None,
    files: list[tuple[str, bytes]],
    max_bytes: int = 20 * 1024 * 1024,
    file_kind: str = "original",
    parent_file_id: int | None = None,
) -> tuple[Note, list[NoteFile]]:
    """批量上传笔记文件：自动建 note、落盘原图、生成 webp 缩略图。

    返回 (note, files)。每个 file 已 flush，file_id 可读。
    file_kind: "original" (default) or "cleaned" — set "cleaned" with parent_file_id
    when saving a cleaned image produced by image-cleanup endpoint.
    """
    if note_id is None:
        n = await note_service.create_note(db, user_id)
        note_id = n.note_id
    else:
        n = await note_service.get_note(db, note_id, user_id)

    out: list[NoteFile] = []
    for name, data in files:
        if len(data) > max_bytes:
            raise BizException(
                413, f"文件 {name} 超过 {max_bytes // (1024 * 1024)}MB"
            )
        ext = normalize_ext(name)
        if ext not in SUPPORTED_EXTS:
            raise BizException(415, f"不支持的格式: {ext}")

        path = note_origin_path(note_id, ext)
        await save_bytes(path, data)
        real_ext = normalize_to_supported_ext(path) or ext
        if real_ext != ext:
            path = path.with_suffix(f".{real_ext}")

        thumb_dir = note_thumbs_dir()
        thumb_dir.mkdir(parents=True, exist_ok=True)
        thumb_path = thumb_dir / (path.stem + ".webp")
        try:
            make_thumbnail(path, thumb_path)
        except Exception:
            thumb_path = None  # noqa: F841 失败置 None，URL 字段将不返回

        info = read_image_info(path)
        nf = NoteFile(
            note_id=note_id,
            user_id=user_id,
            file_name=name,
            original_path=str(path),
            thumbnail_path=str(thumb_path) if thumb_path else None,
            width=info.get("width"),
            height=info.get("height"),
            kind=file_kind,
            parent_file_id=parent_file_id,
        )
        db.add(nf)
        await db.flush()
        out.append(nf)

    n.ai_status = AIStatus.pending
    await db.commit()
    for nf in out:
        await db.refresh(nf)
    return n, out


async def create_file(
    db: AsyncSession,
    user_id: int,
    note_id: int,
    file_name: str,
    data: bytes,
    *,
    file_kind: str = "original",
    parent_file_id: int | None = None,
) -> NoteFile:
    """Single-file upload: save to disk, generate thumbnail, write NoteFile row."""
    n, files = await upload_note_files(
        db, user_id, note_id, [(file_name, data)],
        file_kind=file_kind, parent_file_id=parent_file_id,
    )
    return files[0]


async def delete_file(db: AsyncSession, file_id: int, user_id: int) -> None:
    """Delete a NoteFile by id, scoped to the current user; 404 if not owned."""
    from sqlalchemy import select
    nf = (await db.execute(
        select(NoteFile).where(
            NoteFile.file_id == file_id,
            NoteFile.user_id == user_id,
        )
    )).scalars().first()
    if nf is None:
        raise BizException(404, "图片不存在")
    await db.delete(nf)
    await db.commit()
