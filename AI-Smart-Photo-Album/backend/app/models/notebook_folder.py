"""NotebookFolder ORM model — 用户的笔记分组文件夹。"""
from __future__ import annotations
from typing import Optional

from sqlalchemy import DateTime, ForeignKey, Integer, String, UniqueConstraint, func
from sqlalchemy.orm import Mapped, mapped_column

from app.database import Base


class NotebookFolder(Base):
    __tablename__ = "notebook_folders"
    __table_args__ = (UniqueConstraint("user_id", "name"),)

    folder_id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    user_id: Mapped[int] = mapped_column(
        Integer, ForeignKey("users.user_id", ondelete="CASCADE"), nullable=False
    )
    name: Mapped[str] = mapped_column(String, nullable=False)
    color: Mapped[str] = mapped_column(String, nullable=False, default="#4A90E2")
    sort_index: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    created_at: Mapped[Optional[DateTime]] = mapped_column(
        DateTime, server_default=func.current_timestamp()
    )
