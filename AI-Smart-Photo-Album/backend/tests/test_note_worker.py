"""笔记 worker 链路测试：OCR → 摘要 → embedding 一条龙。"""
import pytest

from app.database import AsyncSessionLocal
from app.models import AIStatus, AITask, Note, NoteEmbedding, NoteFile, User
from app.security import hash_password
from app.services import note_embedding_service, note_service
from app.services.llm import get_provider
from app.services.note_ai_service import run_ocr, run_summary
from app.services.note_file_service import upload_note_files
from sqlalchemy import delete, select


@pytest.mark.asyncio
async def test_full_pipeline_with_mock():
    """验证 OCR / 摘要 / embed 三步全部跑通，且落库正确。"""
    # 测试隔离：清掉所有以该 user_id 关联的笔记数据
    get_provider.cache_clear()

    uid = 66666
    async with AsyncSessionLocal() as db:
        existing = await db.get(User, uid)
        if existing is None:
            db.add(User(
                user_id=uid,
                username=f"test_worker_{uid}",
                password_hash=hash_password("x"),
            ))
            await db.commit()

        await db.execute(delete(NoteEmbedding).where(NoteEmbedding.user_id == uid))
        sub = select(Note.note_id).where(Note.user_id == uid).scalar_subquery()
        await db.execute(delete(AITask).where(AITask.note_id.in_(sub)))
        await db.execute(delete(NoteFile).where(NoteFile.user_id == uid))
        await db.execute(delete(Note).where(Note.user_id == uid))
        await db.commit()

        # 上传一个文件触发 OCR
        n = await note_service.create_note(db, uid)
        n2, files = await upload_note_files(
            db, uid, n.note_id,
            [("a.png", b"\x89PNG\r\n\x1a\n" + b"x" * 100)],
        )
        assert len(files) == 1

        # OCR
        await run_ocr(db, n.note_id)
        await db.refresh(n2)
        assert (n2.text_content or "") != ""
        assert n2.ocr_engine  # mock provider 名字

        # 摘要
        await run_summary(db, n.note_id)
        await db.refresh(n2)
        assert n2.ai_status == AIStatus.done
        assert (n2.summary or "") != ""

        # 嵌入
        await note_embedding_service.run_embed(db, n.note_id)

        emb = (await db.execute(
            select(NoteEmbedding).where(NoteEmbedding.note_id == n.note_id)
        )).scalars().first()
        assert emb is not None
        assert emb.dim == 768
        assert emb.user_id == uid
        assert emb.model  # provider.name