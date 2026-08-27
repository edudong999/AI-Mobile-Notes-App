from pydantic import BaseModel


class OcrRequest(BaseModel):
    noteId: int


class SummaryRequest(BaseModel):
    noteId: int


class QuestionsRequest(BaseModel):
    noteId: int
    count: int = 5
    types: list[str] | None = None


class PolishRequest(BaseModel):
    noteId: int
    action: str  # 'polish'|'expand'|'shorten'
    text: str


class TranslateRequest(BaseModel):
    noteId: int
    targetLang: str  # 'en'|'zh'
    text: str | None = None  # None = 用 note.text_content


class PolishResponse(BaseModel):
    result: str


class TranslateResponse(BaseModel):
    result: str


class QuestionItem(BaseModel):
    questionType: str
    stem: str
    options: list | None = None
    answer: str
    explanation: str
    difficulty: str | None = None


class QuestionsResponse(BaseModel):
    questions: list[QuestionItem]


class EnqueueResponse(BaseModel):
    queuedCount: int
    jobIds: list[int] | None = None
    message: str


class NoteAiJobItem(BaseModel):
    jobId: int
    noteId: int
    subKind: str
    status: str
    errorMessage: str | None
    updatedAt: str | None


class NoteAiQueueResponse(BaseModel):
    pending: list[NoteAiJobItem]
    processing: list[NoteAiJobItem]
    failed: list[NoteAiJobItem]
    done: list[NoteAiJobItem]


class NoteRetryRequest(BaseModel):
    jobIds: list[int]


class MindmapRequest(BaseModel):
    noteId: int
    maxDepth: int = 3


class MindmapNode(BaseModel):
    label: str
    children: list["MindmapNode"] = []


class MindmapResponse(BaseModel):
    noteId: int
    tree: MindmapNode


MindmapNode.model_rebuild()
