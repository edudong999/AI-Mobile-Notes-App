from pydantic import BaseModel
from typing import Literal


class NoteCreate(BaseModel):
    title: str | None = None
    textContent: str | None = None


class NoteUpdate(BaseModel):
    title: str | None = None
    textContent: str | None = None
    isArchived: bool | None = None


class NoteExportRequest(BaseModel):
    format: Literal["md", "zip"] = "md"


class NoteFileItem(BaseModel):
    fileId: int
    url: str
    thumbUrl: str | None
    width: int | None
    height: int | None
    sortIndex: int
    kind: str = "original"
    parentFileId: int | None = None


class QuestionItem(BaseModel):
    questionId: int
    questionType: str
    stem: str
    options: list | None = None
    answer: str
    explanation: str
    difficulty: str | None


class NoteListItem(BaseModel):
    noteId: int
    title: str
    summary: str
    aiStatus: str
    categories: list[int] = []
    thumbCount: int
    thumbUrl: str | None = None
    updatedAt: str | None


class NoteDetail(BaseModel):
    noteId: int
    categories: list[int] = []
    title: str
    textContent: str
    summary: str
    aiStatus: str
    ocrEngine: str | None
    files: list[NoteFileItem]
    questions: list[QuestionItem]
    isArchived: bool
    createdAt: str | None
    updatedAt: str | None