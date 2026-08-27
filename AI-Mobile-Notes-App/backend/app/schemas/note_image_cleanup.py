"""Image cleanup request/response shapes."""
from pydantic import BaseModel
from typing import Literal


class ImageCleanupRequest(BaseModel):
    noteId: int
    fileId: int
    mode: Literal["commit_insert", "commit_new"] = "commit_insert"


class ImageCleanupResponse(BaseModel):
    cleanedFileId: int
    cleanedUrl: str
    cleanedThumbUrl: str | None = None
    kind: str
    parentFileId: int | None = None