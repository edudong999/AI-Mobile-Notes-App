"""笔记 AI 服务：enqueue + OCR / 摘要 / 题目 / 润色 / 翻译 同步执行入口。

异步任务（OCR / 摘要）由 AIWorker 在后台调用本模块的 run_* 函数；
同步路由（润色 / 翻译 / 题目生成）在 Task 12 由 note_ai 路由调用。
"""
from __future__ import annotations

import json
from pathlib import Path

from sqlalchemy import delete as sa_delete, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.exceptions import BizException
from app.models import AIStatus, AITask, JobKind, Note, NoteFile, NoteQuestion
from app.services import llm
from app.utils.image import to_jpeg_data_uri


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

    # DashScope 等远程 LLM 没法访问内网 /static，必须把图片读出来
    # 转 base64 内联过去。MockProvider 同样接受 data URI。
    for f in files[:5]:
        src = Path(f.original_path)
        if not src.exists():
            continue
        data_uri = to_jpeg_data_uri(src)
        if not data_uri:
            continue
        result = await provider.analyze_image(data_uri, "")
        # 优先用 description（对风景/物体图片也能给文字），
        # OCR 文字作为补充一起写到 text_content
        parts: list[str] = []
        desc = (result.get("description") or "").strip()
        ocr = (result.get("ocr_text") or "").strip()
        if desc:
            parts.append(desc)
        if ocr:
            parts.append(ocr)
        if not parts:
            parts.append("（未识别到内容）")
        merged_text.append("\n".join(parts))
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
    """基于笔记附图生成题目；无图时回退到 text_content。

    出题的目标是「分析图片中的题目/知识点后出相关题」，
    所以优先级：note_files 多模态 > text_content 文本。
    """
    n = await db.get(Note, note_id)
    if n is None:
        raise BizException(404, "笔记不存在")

    provider = llm.get_provider()

    # 收集附图 data URI（最多 5 张，与 OCR 一致）
    files = list((await db.execute(
        select(NoteFile)
        .where(NoteFile.note_id == note_id)
        .order_by(NoteFile.sort_index, NoteFile.file_id)
    )).scalars().all())

    images: list[str] = []
    for f in files[:5]:
        src = Path(f.original_path)
        if not src.exists():
            continue
        data_uri = to_jpeg_data_uri(src)
        if data_uri:
            images.append(data_uri)

    if images:
        raw = await provider.generate_questions_from_images(
            images, count=count, types=types,
            fallback_text=n.text_content or "",
        )
    else:
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


async def run_mindmap(
    db: AsyncSession, note_id: int, max_depth: int = 3,
) -> dict:
    """基于笔记生成思维导图树，写入 notes.mindmap_json。"""
    n = await db.get(Note, note_id)
    if n is None:
        raise BizException(404, "笔记不存在")
    content = (n.text_content or n.summary or "").strip()
    if not content and not (n.title or "").strip():
        raise BizException(400, "笔记无内容，无法生成思维导图")
    provider = llm.get_provider()
    tree = await provider.generate_mindmap(n.title, content, max_depth=max_depth)
    n.mindmap_json = json.dumps(tree, ensure_ascii=False)
    n.ai_status = AIStatus.done
    await db.commit()
    return tree