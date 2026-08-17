"""笔记搜索路由：/api/v1/note-search。

支持 mode=auto|semantic|keyword：
- semantic 失败时回退到 keyword；
- auto 在语义成功且有命中时返回 'semantic'，否则 'fallback'；
- keyword 强制返回 'keyword'。
"""
from fastapi import APIRouter, Depends
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_db
from app.deps import get_current_user
from app.models import User
from app.response import ok
from app.schemas.note_search import SearchHit, SearchRequest, SearchResponse
from app.services import note_search_service
from app.services.llm.errors import LLMError

router = APIRouter(prefix="/api/v1/note-search", tags=["note-search"])


@router.post("")
async def search(
    body: SearchRequest,
    db: AsyncSession = Depends(get_db),
    user: User = Depends(get_current_user),
):
    """执行搜索：auto 模式默认走 semantic，失败回退 keyword。"""
    q = body.query.strip()
    if not q:
        return ok(data=SearchResponse(list=[], engine="keyword").model_dump())

    mode = body.mode
    if mode in ("auto", "semantic"):
        try:
            hits = await note_search_service.semantic_search(
                db, user.user_id, q, body.folderId, body.topK,
            )
            if hits:
                return ok(data=SearchResponse(
                    list=[SearchHit(**h).model_dump() for h in hits],
                    engine="semantic",
                ).model_dump())
        except LLMError:
            if mode == "semantic":
                # 强制 semantic 模式失败时直接返回 fallback
                return ok(data=SearchResponse(list=[], engine="fallback").model_dump())

    hits = await note_search_service.keyword_search(
        db, user.user_id, q, body.folderId, body.topK,
    )
    return ok(data=SearchResponse(
        list=[SearchHit(**h).model_dump() for h in hits],
        engine="keyword" if mode != "auto" else "fallback",
    ).model_dump())