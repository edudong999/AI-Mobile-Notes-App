package com.ai_photo.data.repo;

import com.ai_photo.data.api.RetrofitClient;
import com.ai_photo.data.model.category.*;
import com.ai_photo.data.model.note.*;
import com.ai_photo.util.Result;
import java.util.List;

/** Wraps the 6 category endpoints + per-note set. */
public class CategoryRepo {
    public Result<CategoryListResponse> list() {
        return RetrofitClient.exec(RetrofitClient.api().listCategories());
    }

    public Result<CategoryCreateResponse> create(String name, String color) {
        return RetrofitClient.exec(RetrofitClient.api().createCategory(
            new CategoryCreateRequest(name, color)));
    }

    public Result<Object> update(long id, String name, String color, Integer sortIndex) {
        CategoryUpdateRequest req = new CategoryUpdateRequest();
        req.name = name; req.color = color; req.sortIndex = sortIndex;
        return RetrofitClient.exec(RetrofitClient.api().updateCategory(id, req));
    }

    public Result<Object> delete(long id) {
        return RetrofitClient.exec(RetrofitClient.api().deleteCategory(id));
    }

    public Result<Object> reorder(List<Integer> orderedIds) {
        return RetrofitClient.exec(RetrofitClient.api().reorderCategory(
            new CategoryReorderRequest(orderedIds)));
    }

    public Result<NoteListResponse> notesIn(long categoryId) {
        return RetrofitClient.exec(RetrofitClient.api().notesInCategory(categoryId));
    }

    public Result<NoteCategoriesResponse> setForNote(long noteId, List<Integer> categoryIds) {
        return RetrofitClient.exec(RetrofitClient.api().setNoteCategories(
            noteId, new NoteCategoriesUpdate(categoryIds)));
    }
}