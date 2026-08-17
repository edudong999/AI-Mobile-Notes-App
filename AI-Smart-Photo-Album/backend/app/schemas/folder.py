"""Folder 相关 Pydantic schema：增删改、排序、列表项。"""
from pydantic import BaseModel


class FolderCreate(BaseModel):
    name: str
    color: str | None = None


class FolderUpdate(BaseModel):
    name: str | None = None
    color: str | None = None
    sortIndex: int | None = None


class FolderReorderRequest(BaseModel):
    orderedIds: list[int]


class FolderItem(BaseModel):
    folderId: int
    name: str
    color: str
    sortIndex: int
    noteCount: int