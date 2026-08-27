"""图片处理：扩展名、缩略图、EXIF 信息。"""
from datetime import datetime
from io import BytesIO
from pathlib import Path
import base64

from PIL import Image, ImageOps, UnidentifiedImageError

try:
    from pillow_heif import register_heif_opener
    register_heif_opener()
except Exception:
    pass  # pillow-heif 缺失时仅 HEIC 不支持


SUPPORTED_EXTS = {"jpg", "jpeg", "png", "heic", "webp"}

# 把 PIL 检测到的 format 映射到本项目使用的扩展名
_PIL_FORMAT_TO_EXT = {
    "JPEG": "jpg",
    "PNG": "png",
    "WEBP": "webp",
    "HEIF": "heic",
    "HEIC": "heic",
}


def detect_format(path: Path) -> str | None:
    """读取文件 magic bytes，识别实际图像格式。

    用于客户端传错扩展名（例如把 HEIF 标成 .png）时的修正。
    返回小写扩展名（不带点）；识别失败返回 None。
    """
    try:
        with Image.open(path) as im:
            fmt = (im.format or "").upper()
            return _PIL_FORMAT_TO_EXT.get(fmt)
    except (UnidentifiedImageError, FileNotFoundError, OSError):
        return None


def normalize_to_supported_ext(path: Path) -> str | None:
    """按真实内容重新确定扩展名（必要时重命名文件）。

    返回最终的扩展名（小写，无点）；识别失败返回 None（调用方应放弃或报错）。
    """
    detected = detect_format(path)
    if not detected or detected not in SUPPORTED_EXTS:
        return None
    current_ext = path.suffix.lstrip(".").lower()
    if current_ext != detected:
        new_path = path.with_suffix(f".{detected}")
        try:
            path.rename(new_path)
        except OSError:
            return None
    return detected


def normalize_ext(filename: str) -> str:
    """从文件名提取小写扩展名；无扩展名时返回 jpg。"""
    if "." not in filename:
        return "jpg"
    return filename.rsplit(".", 1)[-1].lower()


def make_thumbnail(src: Path, dst: Path, size: int = 200) -> None:
    """生成 size×size 居中裁剪的 WebP 缩略图（短边等比放大后中心裁切）。"""
    with Image.open(src) as im:
        im = ImageOps.exif_transpose(im)
        if im.mode in ("RGBA", "P", "LA"):
            bg = Image.new("RGB", im.size, (255, 255, 255))
            mask = im.split()[-1] if im.mode in ("RGBA", "LA") else None
            bg.paste(im, mask=mask)
            im = bg
        elif im.mode != "RGB":
            im = im.convert("RGB")

        w, h = im.size
        if w == 0 or h == 0:
            raise ValueError(f"invalid image size: {w}x{h}")
        scale = size / min(w, h)
        new_w, new_h = max(int(round(w * scale)), size), max(int(round(h * scale)), size)
        im = im.resize((new_w, new_h), Image.Resampling.LANCZOS)

        left = (new_w - size) // 2
        top = (new_h - size) // 2
        im = im.crop((left, top, left + size, top + size))

        dst.parent.mkdir(parents=True, exist_ok=True)
        im.save(dst, "WEBP", quality=85)


def read_image_info(src: Path) -> dict:
    """读取尺寸与 EXIF 拍摄时间；失败时返回空值。"""
    info = {"width": None, "height": None, "shot_at": None}
    try:
        with Image.open(src) as im:
            info["width"], info["height"] = im.size
            raw = im.getexif().get(36867)  # DateTimeOriginal
            if raw:
                try:
                    info["shot_at"] = datetime.strptime(raw, "%Y:%m:%d %H:%M:%S")
                except ValueError:
                    pass
    except Exception:
        pass
    return info


def to_jpeg_data_uri(src: Path, max_side: int = 1600, quality: int = 85) -> str | None:
    """把图片读成 JPEG 字节并打包成 `data:image/jpeg;base64,...` URI。

    用于把磁盘图片直接传给多模态 LLM。读不到或不是图片返回 None。
    长边超过 max_side 时按比例缩小（保持纵横比）。
    """
    try:
        with Image.open(src) as im:
            im = ImageOps.exif_transpose(im)
            if im.mode in ("RGBA", "P", "LA"):
                bg = Image.new("RGB", im.size, (255, 255, 255))
                mask = im.split()[-1] if im.mode in ("RGBA", "LA") else None
                bg.paste(im, mask=mask)
                im = bg
            elif im.mode != "RGB":
                im = im.convert("RGB")

            w, h = im.size
            if w == 0 or h == 0:
                return None
            long_side = max(w, h)
            if long_side > max_side:
                scale = max_side / long_side
                im = im.resize((max(int(w * scale), 1), max(int(h * scale), 1)),
                               Image.Resampling.LANCZOS)

            buf = BytesIO()
            im.save(buf, format="JPEG", quality=quality)
            b64 = base64.b64encode(buf.getvalue()).decode("ascii")
            return f"data:image/jpeg;base64,{b64}"
    except (UnidentifiedImageError, FileNotFoundError, OSError):
        return None