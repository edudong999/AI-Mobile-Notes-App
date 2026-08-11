package com.ai_photo.data.model.category;

import java.util.List;

public class CategoryPreviewResponse {
    public List<CategoryPreviewGroup> scene;
    public List<CategoryPreviewGroup> emotion;
    public List<CategoryPreviewGroup> tag;
}

public class CategoryPreviewGroup {
    public long categoryId;
    public String categoryName;
    public int photoCount;
    public List<PreviewPhoto> previewPhotos;
}

public class PreviewPhoto {
    public long photoId;
    public String thumbnailUrl;
}

public class CategoryListResponse {
    public String type;
    public List<CategoryListItem> list;
}

public class CategoryListItem {
    public long categoryId;
    public String categoryName;
    public int photoCount;
    public String coverThumbnail;
}

public class CategoryPhotosResponse {
    public long categoryId;
    public String categoryName;
    public List<CategoryPhotoItem> list;
    public int total;
    public int page;
    public int pageSize;
}

public class CategoryPhotoItem {
    public long photoId;
    public String thumbnailUrl;
    public String createdAt;
}