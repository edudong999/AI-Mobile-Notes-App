package com.ai_photo.data.model.photo;

public class PhotoDetailResponse {
    public long photoId;
    public String originalUrl;
    public String thumbnailUrl;
    public PhotoDetailMetadata metadata;
    public AIAnalysisBlock aiAnalysis;
    public boolean isFavorite;
    public String createdAt;
}
