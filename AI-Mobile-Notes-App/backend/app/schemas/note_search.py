"""笔记搜索相关 Pydantic schema。"""
from pydantic import BaseModel


class SearchRequest(BaseModel):
    """搜索请求体。"""

    query: str
    mode: str = "auto"          # 'semantic' | 'keyword' | 'auto'
    topK: int = 20
    folderId: int | None = None


class SearchHit(BaseModel):
    """单条搜索结果。"""

    noteId: int
    title: str
    score: float
    snippet: str
    matchedSnippet: str


class SearchResponse(BaseModel):
    """搜索响应。"""

    hits: list[SearchHit]
    mode: str  # 'semantic' | 'keyword' | 'fallback'