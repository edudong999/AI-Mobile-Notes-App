"""Image cleanup router: POST /api/v1/note-image-cleanup."""
from fastapi import APIRouter, Depends
from sqlalchemy.ext.asyncio import AsyncSession
from app.database import get_db
from app.deps import get_current_user
from app.models import User
from app.response import ok
from app.schemas.note_image_cleanup import (
    ImageCleanupRequest, ImageCleanupResponse,
)
from app.services.file_storage import public_url_for
from app.services.image_cleanup_service import cleanup_image

router = APIRouter(prefix="/api/v1/note-image-cleanup", tags=["note-image-cleanup"])


@router.post("")
async def cleanup(body: ImageCleanupRequest,
                  db: AsyncSession = Depends(get_db),
                  user: User = Depends(get_current_user)):
    nf, public = await cleanup_image(db, user.user_id,
                                    body.noteId, body.fileId, body.mode)
    return ok(data=ImageCleanupResponse(
        cleanedFileId=nf.file_id,
        cleanedUrl=public,
        cleanedThumbUrl=public_url_for(nf.thumbnail_path) if nf.thumbnail_path else None,
        kind=nf.kind,
        parentFileId=nf.parent_file_id,
    ).model_dump())