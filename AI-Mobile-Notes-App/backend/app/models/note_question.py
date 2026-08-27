"""NoteQuestion ORM model — AI 从笔记生成的题目。"""
from __future__ import annotations
from typing import Optional

from sqlalchemy import DateTime, ForeignKey, Integer, String, Text, func
from sqlalchemy.orm import Mapped, mapped_column

from app.database import Base


class NoteQuestion(Base):
    __tablename__ = "note_questions"

    question_id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    note_id: Mapped[int] = mapped_column(
        Integer, ForeignKey("notes.note_id", ondelete="CASCADE"), nullable=False
    )
    user_id: Mapped[int] = mapped_column(Integer, nullable=False)
    question_type: Mapped[str] = mapped_column(String, nullable=False)
    stem: Mapped[str] = mapped_column(Text, nullable=False)
    options_json: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    answer: Mapped[str] = mapped_column(Text, nullable=False)
    explanation: Mapped[str] = mapped_column(Text, nullable=False)
    difficulty: Mapped[Optional[str]] = mapped_column(String, nullable=True)
    sort_index: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    created_at: Mapped[Optional[DateTime]] = mapped_column(
        DateTime, server_default=func.current_timestamp()
    )
