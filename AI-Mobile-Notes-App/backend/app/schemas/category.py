"""Category Pydantic schemas: create / update / reorder / list item."""
from pydantic import BaseModel


class CategoryCreate(BaseModel):
    name: str
    color: str | None = None


class CategoryUpdate(BaseModel):
    name: str | None = None
    color: str | None = None
    sortIndex: int | None = None


class CategoryReorderRequest(BaseModel):
    orderedIds: list[int]


class CategoryItem(BaseModel):
    categoryId: int
    name: str
    color: str
    sortIndex: int
    noteCount: int