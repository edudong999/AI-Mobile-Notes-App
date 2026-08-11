package com.ai_photo.data.model.category;

import java.util.List;

public class CategoryPhotosResponse {
    public long categoryId;
    public String categoryName;
    public List<CategoryPhotoItem> list;
    public int total;
    public int page;
    public int pageSize;
}
