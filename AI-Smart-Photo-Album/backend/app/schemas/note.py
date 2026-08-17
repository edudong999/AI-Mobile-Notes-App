from pydantic import BaseModel
from typing import Literal


class NoteCreate(BaseModel):
    folderId: int | None = None
    title: str | None = None
    textContent: str | None = None


class NoteUpdate(BaseModel):
    folderId: int | None = None
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
    folderId: int | None
    thumbCount: int
    updatedAt: str | None


class NoteDetail(BaseModel):
    noteId: int
    folderId: int | None
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
