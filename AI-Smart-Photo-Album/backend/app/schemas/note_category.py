"""NoteCategories router-level request/response shapes."""
from pydantic import BaseModel


class NoteCategoriesUpdate(BaseModel):
    categoryIds: list[int]


class NoteCategoriesResponse(BaseModel):
    categoryIds: list[int]