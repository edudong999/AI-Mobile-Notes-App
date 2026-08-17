"""AI 进度查询 + 重新分析请求/响应。"""
from pydantic import BaseModel


class AIStatusResponse(BaseModel):
    total: int
    done: int
    pending: int
    processing: int
    failed: int
    progress: float


class AIReanalyzeRequest(BaseModel):
    photoIds: list[int]


class AIReanalyzeResponse(BaseModel):
    queuedCount: int
    message: str


class AIQueueItem(BaseModel):
    """AI 队列中单张照片的展示信息。"""

    photoId: int
    fileName: str
    thumbnailUrl: str | None
    status: str          # pending / processing / done / failed
    errorMessage: str | None
    retryCount: int
    updatedAt: str | None


class AIQueueResponse(BaseModel):
    pending: list[AIQueueItem]
    processing: list[AIQueueItem]
    failed: list[AIQueueItem]
    done: list[AIQueueItem]