package com.ai_photo.data.model.photo;

import java.util.List;

public class PhotoUploadResponse {
    public int successCount;
    public int failCount;
    public List<PhotoUploadItem> uploadedPhotos;
    public List<Object> failedFiles;
}

public class PhotoUploadItem {
    public long photoId;
    public String originalName;
    public String thumbnailUrl;
    public long size;
    public String analysisStatus;
}

public class PhotoListResponse {
    public List<PhotoListItem> list;
    public int total;
    public int page;
    public int pageSize;
}

public class PhotoListItem {
    public long photoId;
    public String thumbnailUrl;
    public int width;
    public int height;
    public String createdAt;
    public boolean isFavorite;
    public String analysisStatus;
}

public class PhotoRecentResponse {
    public List<PhotoRecentItem> list;
}

public class PhotoRecentItem {
    public long photoId;
    public String thumbnailUrl;
    public String createdAt;
}

public class PhotoDetailResponse {
    public long photoId;
    public String originalUrl;
    public String thumbnailUrl;
    public PhotoDetailMetadata metadata;
    public AIAnalysisBlock aiAnalysis;
    public boolean isFavorite;
    public String createdAt;
}

public class PhotoDetailMetadata {
    public String fileName;
    public long size;
    public int width;
    public int height;
    public String shotAt;
}

public class AIAnalysisBlock {
    public String description;
    public AITagResult scene;
    public AITagResult emotion;
    public List<AITagResult> tags;
}

public class AITagResult {
    public String name;
    public double confidence;
}

public class PhotoUpdateRequest {
    public List<String> tags;
    public String description;
}

public class BatchDeleteRequest {
    public List<Long> photoIds;
}

public class BatchDeleteResponse {
    public int successCount;
    public int failCount;
}

public class SearchRequest {
    public String query;
    public int page = 1;
    public int pageSize = 20;
}

public class FilterRequest {
    public Long sceneId;
    public Long emotionId;
    public Long tagId;
    public int page = 1;
    public int pageSize = 20;
}

public class SearchResponse {
    public List<SearchItem> list;
    public int total;
    public int page;
    public int pageSize;
}

public class SearchItem {
    public long photoId;
    public String thumbnailUrl;
    public List<String> matchedTags;
    public double score;
}

public class FavoriteResponse {
    public List<FavoriteItem> list;
    public int total;
    public int page;
    public int pageSize;
}

public class FavoriteItem {
    public long photoId;
    public String thumbnailUrl;
    public String favoritedAt;
}