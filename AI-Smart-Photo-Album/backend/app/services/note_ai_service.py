"""笔记 AI 服务：enqueue + OCR / 摘要 / 题目 / 润色 / 翻译 同步执行入口。

异步任务（OCR / 摘要）由 AIWorker 在后台调用本模块的 run_* 函数；
同步路由（润色 / 翻译 / 题目生成）在 Task 12 由 note_ai 路由调用。
"""
from __future__ import annotations

import json
from pathlib import Path

from sqlalchemy import delete as sa_delete, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.config import settings
from app.exceptions import BizException
from app.models import AIStatus, AITask, JobKind, Note, NoteFile, NoteQuestion
from app.services import llm


# ---------- 队列入口 ----------

async def enqueue_note_ai(db: AsyncSession, note_id: int, sub_kind: str) -> int:
    """创建 AITask 行（kind=note, sub_kind=…）并触发 worker。

    返回 task_id；调用方（router）随后应调用 notify_new_task()。
    """
    task = AITask(note_id=note_id, kind=JobKind.note, sub_kind=sub_kind)
    db.add(task)
    await db.flush()
    return task.task_id


# ---------- 异步子任务：worker 调用 ----------

async def run_ocr(db: AsyncSession, note_id: int) -> dict:
    """对笔记关联的图片逐张 OCR（最多 5 张），合并写入 text_content。"""
    n = await db.get(Note, note_id)
    if n is None:
        raise BizException(404, "笔记不存在")

    files = list((await db.execute(
        select(NoteFile)
        .where(NoteFile.note_id == note_id)
        .order_by(NoteFile.sort_index, NoteFile.file_id)
    )).scalars().all())

    if not files:
        raise BizException(400, "笔记无附图，无法 OCR")

    provider = llm.get_provider()
    merged_text: list[str] = []
    key_points: list[str] = []

    public_base = getattr(settings, "PUBLIC_BASE_URL", "") or ""
    for f in files[:5]:
        url = f"/static/note_files/{Path(f.original_path).name}"
        full = f"{public_base}{url}" if public_base else url
        result = await provider.analyze_image(full, "")
        merged_text.append(result.get("ocr_text", ""))
        key_points.extend(result.get("key_points", []) or [])

    text = "\n\n".join(merged_text)
    n.text_content = text
    n.ocr_engine = provider.name
    n.ai_status = AIStatus.processing
    await db.commit()
    return {"text": text, "key_points": key_points[:8]}


async def run_summary(db: AsyncSession, note_id: int) -> str:
    """基于 text_content 生成摘要，写到 summary 字段。"""
    n = await db.get(Note, note_id)
    if n is None:
        raise BizException(404, "笔记不存在")

    provider = llm.get_provider()
    summary = await provider.summarize(n.text_content or "", max_words=120)
    n.summary = summary
    n.ai_status = AIStatus.done
    await db.commit()
    return summary


async def run_questions(
    db: AsyncSession, note_id: int, count: int, types: list[str] | None
) -> list[NoteQuestion]:
    """基于 text_content 生成题目；先清空旧题再插入新题。"""
    n = await db.get(Note, note_id)
    if n is None:
        raise BizException(404, "笔记不存在")

    provider = llm.get_provider()
    raw = await provider.generate_questions(
        n.text_content or "", count=count, types=types
    )

    await db.execute(sa_delete(NoteQuestion).where(NoteQuestion.note_id == note_id))
    out: list[NoteQuestion] = []
    for i, q in enumerate(raw):
        opts = q.get("options")
        opts_json = json.dumps(opts, ensure_ascii=False) if opts else None
        nq = NoteQuestion(
            note_id=note_id,
            user_id=n.user_id,
            question_type=q["question_type"],
            stem=q["stem"],
            options_json=opts_json,
            answer=str(q["answer"]),
            explanation=q["explanation"],
            difficulty=q.get("difficulty"),
            sort_index=i,
        )
        db.add(nq)
        out.append(nq)
    await db.commit()
    return out


# ---------- 同步子任务：路由直接调用 ----------

async def run_polish(db: AsyncSession, note_id: int, action: str, text: str) -> str:
    """润色 / 改写一段文本，不写库（调用方按需落盘）。"""
    provider = llm.get_provider()
    return await provider.polish(text, action)


async def run_translate(
    db: AsyncSession, note_id: int, target_lang: str, text: str | None
) -> str:
    """翻译一段文本：text 缺省时取笔记的 text_content。不写库。"""
    n = await db.get(Note, note_id)
    if n is None:
        raise BizException(404, "笔记不存在")
    src = text if text is not None else (n.text_content or "")
    provider = llm.get_provider()
    return await provider.translate(src, target_lang)