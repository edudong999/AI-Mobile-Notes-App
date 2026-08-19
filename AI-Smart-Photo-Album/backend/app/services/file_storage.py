"""磁盘文件存储：原图、缩略图、URL 构造、哈希。"""
import hashlib
import os
from pathlib import Path

import aiofiles

from app.config import settings


def data_dir() -> Path:
    """返回数据根目录，并确保 origin/thumb 子目录存在。"""
    p = Path(settings.DATA_DIR)
    (p / "origin").mkdir(parents=True, exist_ok=True)
    (p / "thumb").mkdir(parents=True, exist_ok=True)
    return p


def origin_path(photo_id: int, ext: str) -> Path:
    """原图磁盘路径。"""
    return data_dir() / "origin" / f"{photo_id}.{ext.lstrip('.')}"


def thumb_path(photo_id: int) -> Path:
    """缩略图磁盘路径，统一 webp。"""
    return data_dir() / "thumb" / f"{photo_id}.webp"


def origin_url(photo_id: int, ext: str) -> str:
    """原图静态访问 URL。"""
    return f"{settings.STATIC_URL_PREFIX}/origin/{photo_id}.{ext.lstrip('.')}"


def thumb_url(photo_id: int) -> str | None:
    """缩略图静态访问 URL；缩略图不存在时返回 None（避免前端 404 触发 placeholder）。"""
    p = thumb_path(photo_id)
    return f"{settings.STATIC_URL_PREFIX}/thumb/{photo_id}.webp" if p.exists() else None


def sha256_of_bytes(data: bytes) -> str:
    """计算字节流的 sha256 十六进制摘要。"""
    return hashlib.sha256(data).hexdigest()


async def save_bytes(path: Path, data: bytes) -> None:
    """异步将字节写入磁盘，自动创建父目录。"""
    path.parent.mkdir(parents=True, exist_ok=True)
    async with aiofiles.open(path, "wb") as f:
        await f.write(data)


def note_data_dir() -> Path:
    """笔记原图存储目录。"""
    p = Path(settings.DATA_DIR) / "note_files"
    p.mkdir(parents=True, exist_ok=True)
    return p


def note_thumbs_dir() -> Path:
    """笔记缩略图存储目录。"""
    p = Path(settings.DATA_DIR) / "note_thumbs"
    p.mkdir(parents=True, exist_ok=True)
    return p


def note_origin_path(note_id: int, ext: str) -> Path:
    """笔记原图磁盘路径，文件名带随机后缀避免冲突。"""
    return note_data_dir() / f"{note_id}_{int.from_bytes(os.urandom(2), 'big')}.{ext.lstrip('.')}"


def public_url_for(disk_path: str | Path | None) -> str:
    """Map a disk path under DATA_DIR to its public `/static/...` URL.

    Returns "" if path is empty/None or outside the data dir.
    """
    if not disk_path:
        return ""
    p = Path(disk_path)
    try:
        rel = p.relative_to(Path(settings.DATA_DIR))
    except ValueError:
        return ""
    return f"{settings.STATIC_URL_PREFIX}/{rel.as_posix()}"


def absolute_url_for(disk_path: str | Path | None, base: str | None = None) -> str:
    """Build absolute http(s) URL for a disk file, suitable for LLM provider fetch."""
    rel = public_url_for(disk_path)
    if not rel:
        return ""
    if rel.startswith("http://") or rel.startswith("https://"):
        return rel
    base = (base or os.environ.get("PUBLIC_BASE_URL") or "http://localhost:8000").rstrip("/")
    return base + rel