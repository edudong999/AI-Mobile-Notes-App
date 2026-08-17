"""NoteEmbedding ORM model — 笔记向量（每条笔记一行）。"""
from __future__ import annotations
from typing import Optional

from sqlalchemy import DateTime, ForeignKey, Integer, String, Text, func
from sqlalchemy.orm import Mapped, mapped_column

from app.database import Base


class NoteEmbedding(Base):
    __tablename__ = "note_embeddings"

    note_id: Mapped[int] = mapped_column(
        Integer,
        ForeignKey("notes.note_id", ondelete="CASCADE"),
        primary_key=True,
    )
    user_id: Mapped[int] = mapped_column(Integer, nullable=False)
    vector_json: Mapped[str] = mapped_column(Text, nullable=False)
    dim: Mapped[int] = mapped_column(Integer, nullable=False)
    model: Mapped[str] = mapped_column(String, nullable=False)
    created_at: Mapped[Optional[DateTime]] = mapped_column(
        DateTime, server_default=func.current_timestamp()
    )
