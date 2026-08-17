"""笔记文件上传 / 删除路由。"""
from pathlib import Path

from fastapi import APIRouter, Depends, File, UploadFile
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_db
from app.deps import get_current_user
from app.models import NoteFile, User
from app.models.ai_task import NoteSubKind, notify_new_task
from app.response import ok
from app.schemas.note_file import NoteFileItem, NoteFileUploadResponse
from app.services import note_file_service
from app.services.note_ai_service import enqueue_note_ai

router = APIRouter(prefix="/api/v1/note-files", tags=["note-files"])


@router.post("/upload")
async def upload(
    noteId: int | None = None,
    files: list[UploadFile] = File(...),
    db: AsyncSession = Depends(get_db),
    user: User = Depends(get_current_user),
):
    """上传一个或多个文件到笔记；noteId 为空则自动建新 note。"""
    payload = []
    for f in files:
        data = await f.read()
        payload.append((f.filename or "file", data))

    n, items = await note_file_service.upload_note_files(
        db, user.user_id, noteId, payload
    )

    file_items = [
        NoteFileItem(
            fileId=it.file_id,
            url=f"/static/note_files/{Path(it.original_path).name}",
            thumbUrl=f"/static/note_thumbs/{Path(it.thumbnail_path).name}"
            if it.thumbnail_path else None,
            width=it.width,
            height=it.height,
            sortIndex=it.sort_index,
        ).model_dump()
        for it in items
    ]

    await enqueue_note_ai(db, n.note_id, sub_kind=NoteSubKind.ocr)
    await db.commit()
    notify_new_task()

    return ok(data=NoteFileUploadResponse(
        files=file_items, noteId=n.note_id
    ).model_dump())


@router.delete("/{file_id}")
async def delete_file(
    file_id: int,
    db: AsyncSession = Depends(get_db),
    user: User = Depends(get_current_user),
):
    """删除单个笔记文件；非本用户或不存在则幂等返回 ok。"""
    nf = await db.get(NoteFile, file_id)
    if nf is None or nf.user_id != user.user_id:
        return ok(message="ok")
    await db.delete(nf)
    await db.commit()
    return ok(message="已删除")
