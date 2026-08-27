"""题目生成测试：纯文本出题 + 列表读取。"""
import pytest
from sqlalchemy import delete

from app.database import AsyncSessionLocal
from app.models import Note, NoteQuestion, User
from app.security import hash_password
from app.services import note_service
from app.services.llm import get_provider
from app.services.note_ai_service import run_questions

# 确保拿到 mock provider（沙箱环境无 DASHSCOPE_API_KEY 时也会落回 mock）
get_provider.cache_clear()


async def _make_user(db, uid: int) -> None:
    existing = await db.get(User, uid)
    if existing is None:
        db.add(User(
            user_id=uid,
            username=f"test_q_{uid}",
            password_hash=hash_password("x"),
        ))
        await db.commit()


@pytest.mark.asyncio
async def test_generate_questions_from_text_writes_rows():
    """无图场景：基于 text_content 出题，落库 N 道且字段齐全。"""
    async with AsyncSessionLocal() as db:
        uid = 50001
        await _make_user(db, uid)
        await db.execute(delete(NoteQuestion).where(NoteQuestion.user_id == uid))
        await db.execute(delete(Note).where(Note.user_id == uid))
        await db.commit()

        n = await note_service.create_note(
            db, uid, title="考点", text_content="极限定义：函数在某点的邻域内任意接近。"
        )

        out = await run_questions(db, n.note_id, count=3, types=["choice"])
        assert len(out) == 3
        for i, q in enumerate(out):
            assert q.question_type == "choice"
            assert q.stem and q.stem.strip()
            assert q.answer and q.answer.strip()
            assert q.explanation and q.explanation.strip()
            assert q.options_json, "choice 题应写 options_json"
            assert q.sort_index == i


@pytest.mark.asyncio
async def test_list_questions_returns_in_order():
    """note_service.list_questions 按 sort_index/question_id 升序。"""
    async with AsyncSessionLocal() as db:
        uid = 50002
        await _make_user(db, uid)
        await db.execute(delete(NoteQuestion).where(NoteQuestion.user_id == uid))
        await db.execute(delete(Note).where(Note.user_id == uid))
        await db.commit()

        n = await note_service.create_note(
            db, uid, title="排序", text_content="第一行\n第二行\n第三行"
        )
        await run_questions(db, n.note_id, count=5, types=["choice"])

        rows = await note_service.list_questions(db, n.note_id)
        assert len(rows) == 5
        # 顺序应是递增的 sort_index
        indices = [r.sort_index for r in rows]
        assert indices == sorted(indices)
        assert indices[0] == 0
        assert indices[-1] == 4


@pytest.mark.asyncio
async def test_generate_questions_overwrites_existing():
    """再次调用 run_questions 会先清旧题再写入，避免重复堆叠。"""
    async with AsyncSessionLocal() as db:
        uid = 50003
        await _make_user(db, uid)
        await db.execute(delete(NoteQuestion).where(NoteQuestion.user_id == uid))
        await db.execute(delete(Note).where(Note.user_id == uid))
        await db.commit()

        n = await note_service.create_note(
            db, uid, title="覆盖", text_content="line1\nline2"
        )
        await run_questions(db, n.note_id, count=4, types=["choice"])
        first = await note_service.list_questions(db, n.note_id)
        assert len(first) == 4

        await run_questions(db, n.note_id, count=2, types=["choice"])
        second = await note_service.list_questions(db, n.note_id)
        assert len(second) == 2
        # 重新生成后 sort_index 会重排
        assert [r.sort_index for r in second] == [0, 1]
