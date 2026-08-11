package com.ai_photo.data.repo;

import com.ai_photo.data.api.RetrofitClient;
import com.ai_photo.data.model.category.*;
import com.ai_photo.data.model.photo.*;
import com.ai_photo.util.Result;

public class CategoryRepo {
    public Result<CategoryPreviewResponse> preview(int previewSize) {
        return RetrofitClient.exec(RetrofitClient.api().previewCategories(previewSize));
    }

    public Result<CategoryListResponse> list(String type) {
        return RetrofitClient.exec(RetrofitClient.api().listCategories(type));
    }

    public Result<CategoryPhotosResponse> photos(long categoryId, int page, int pageSize) {
        return RetrofitClient.exec(RetrofitClient.api().categoryPhotos(categoryId, page, pageSize));
    }
}
