package com.ai_photo.data.repo;

import com.ai_photo.data.api.RetrofitClient;
import com.ai_photo.data.model.admin.*;
import com.ai_photo.util.Result;

public class AdminRepo {
    public Result<AdminCategoryListResponse> list(String type) {
        return RetrofitClient.exec(RetrofitClient.api().adminListCategories(type));
    }

    public Result<AdminCreateResponse> create(String type, String name, String iconUrl) {
        AdminCreateRequest req = new AdminCreateRequest();
        req.type = type; req.name = name; req.iconUrl = iconUrl;
        return RetrofitClient.exec(RetrofitClient.api().adminCreateCategory(req));
    }

    public Result<Object> update(long id, String name, String iconUrl) {
        AdminUpdateRequest req = new AdminUpdateRequest();
        req.name = name; req.iconUrl = iconUrl;
        return RetrofitClient.execVoid(RetrofitClient.api().adminUpdateCategory(id, req));
    }

    public Result<Object> delete(long id) {
        return RetrofitClient.execVoid(RetrofitClient.api().adminDeleteCategory(id));
    }

    public Result<AdminResetResponse> reset() {
        AdminResetRequest req = new AdminResetRequest();
        req.confirm = true;
        return RetrofitClient.exec(RetrofitClient.api().adminResetCategories(req));
    }
}
