"""ORM 模型包入口。

导入所有模型以确保 SQLAlchemy Base.metadata 能注册到全部表，方便
`create_all` / `drop_all` 操作。`import app.models` 即触发全量注册。
"""
from app.models.ai_task import AITask, AITaskStatus, JobKind, NoteSubKind
from app.models.category import Category
from app.models.note import AIStatus, Note
from app.models.note_category import NoteCategory
from app.models.note_embedding import NoteEmbedding
from app.models.note_file import NoteFile
from app.models.note_question import NoteQuestion
from app.models.user import User

__all__ = [
    "AITask",
    "AITaskStatus",
    "AIStatus",
    "Category",
    "JobKind",
    "Note",
    "NoteCategory",
    "NoteEmbedding",
    "NoteFile",
    "NoteQuestion",
    "NoteSubKind",
    "User",
]