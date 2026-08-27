"""NoteFile ORM model — 笔记关联的图片/文件。"""
from __future__ import annotations
from typing import Optional

from sqlalchemy import DateTime, ForeignKey, Integer, String, func
from sqlalchemy.orm import Mapped, mapped_column

from app.database import Base


class NoteFile(Base):
    __tablename__ = "note_files"

    file_id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    note_id: Mapped[int] = mapped_column(
        Integer, ForeignKey("notes.note_id", ondelete="CASCADE"), nullable=False
    )
    user_id: Mapped[int] = mapped_column(Integer, nullable=False)
    file_name: Mapped[str] = mapped_column(String, nullable=False)
    original_path: Mapped[str] = mapped_column(String, nullable=False)
    thumbnail_path: Mapped[Optional[str]] = mapped_column(String, nullable=True)
    width: Mapped[Optional[int]] = mapped_column(Integer, nullable=True)
    height: Mapped[Optional[int]] = mapped_column(Integer, nullable=True)
    sort_index: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    kind: Mapped[str] = mapped_column(String, nullable=False, default="original")
    parent_file_id: Mapped[Optional[int]] = mapped_column(
        Integer, ForeignKey("note_files.file_id", ondelete="SET NULL"), nullable=True
    )
    created_at: Mapped[Optional[DateTime]] = mapped_column(
        DateTime, server_default=func.current_timestamp()
    )
