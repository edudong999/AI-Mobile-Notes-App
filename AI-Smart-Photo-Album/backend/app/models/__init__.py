"""ORM 模型包入口。

导入所有模型以确保 SQLAlchemy Base.metadata 能注册到全部表，方便
`create_all` / `drop_all` 操作。`import app.models` 即触发全量注册。
"""
from app.models.ai_task import AITask, AITaskStatus, JobKind, NoteSubKind
from app.models.category import Category, CategoryType
from app.models.favorite import Favorite
from app.models.note import AIStatus, Note
from app.models.note_embedding import NoteEmbedding
from app.models.note_file import NoteFile
from app.models.note_question import NoteQuestion
from app.models.notebook_folder import NotebookFolder
from app.models.photo import AnalysisStatus, Photo
from app.models.photo_ai_analysis import PhotoAIAnalysis
from app.models.photo_category import CategorySource, PhotoCategory
from app.models.user import User

__all__ = [
    "AITask",
    "AITaskStatus",
    "AIStatus",
    "AnalysisStatus",
    "Category",
    "CategorySource",
    "CategoryType",
    "Favorite",
    "JobKind",
    "Note",
    "NoteEmbedding",
    "NoteFile",
    "NoteQuestion",
    "NoteSubKind",
    "NotebookFolder",
    "Photo",
    "PhotoAIAnalysis",
    "PhotoCategory",
    "User",
]