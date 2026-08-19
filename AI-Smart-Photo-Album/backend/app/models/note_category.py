"""NoteCategory association table — M:N between Note and Category."""
from __future__ import annotations
from sqlalchemy import ForeignKey, Integer
from sqlalchemy.orm import Mapped, mapped_column
from app.database import Base


class NoteCategory(Base):
    __tablename__ = "note_categories"

    note_id: Mapped[int] = mapped_column(
        Integer, ForeignKey("notes.note_id", ondelete="CASCADE"), primary_key=True
    )
    category_id: Mapped[int] = mapped_column(
        Integer, ForeignKey("categories.category_id", ondelete="CASCADE"), primary_key=True
    )