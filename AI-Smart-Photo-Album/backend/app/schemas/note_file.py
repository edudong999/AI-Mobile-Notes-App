"""笔记文件相关 Pydantic schemas。"""
from pydantic import BaseModel


class NoteFileItem(BaseModel):
    """笔记上传文件条目（响应用）。"""

    fileId: int
    url: str
    thumbUrl: str | None
    width: int | None
    height: int | None
    sortIndex: int


class NoteFileUploadResponse(BaseModel):
    """笔记文件批量上传响应。"""

    files: list[NoteFileItem]
    noteId: int
