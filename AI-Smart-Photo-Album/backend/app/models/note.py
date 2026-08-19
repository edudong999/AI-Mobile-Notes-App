"""Note ORM model + AI 处理状态枚举。"""
from __future__ import annotations

import enum
from typing import Optional

from sqlalchemy import DateTime, Enum as SAEnum, ForeignKey, Integer, String, Text, func
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.database import Base


class AIStatus(str, enum.Enum):
    """笔记的 AI 处理状态。"""

    pending = "pending"
    processing = "processing"
    done = "done"
    failed = "failed"


class Note(Base):
    __tablename__ = "notes"

    note_id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    user_id: Mapped[int] = mapped_column(
        Integer, ForeignKey("users.user_id", ondelete="CASCADE"), nullable=False
    )
    title: Mapped[str] = mapped_column(String, nullable=False, default="")
    text_content: Mapped[str] = mapped_column(Text, nullable=False, default="")
    summary: Mapped[str] = mapped_column(Text, nullable=False, default="")
    ai_status: Mapped[AIStatus] = mapped_column(
        SAEnum(AIStatus, name="note_ai_status"),
        nullable=False,
        default=AIStatus.pending,
    )
    ocr_engine: Mapped[Optional[str]] = mapped_column(String, nullable=True)
    mindmap_json: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    is_archived: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    created_at: Mapped[Optional[DateTime]] = mapped_column(
        DateTime, server_default=func.current_timestamp()
    )
    updated_at: Mapped[Optional[DateTime]] = mapped_column(
        DateTime,
        server_default=func.current_timestamp(),
        onupdate=func.current_timestamp(),
    )
    deleted_at: Mapped[Optional[DateTime]] = mapped_column(DateTime, nullable=True)

    categories: Mapped[list["Category"]] = relationship(
        "Category",
        secondary="note_categories",
        back_populates="notes",
        lazy="selectin",
    )
