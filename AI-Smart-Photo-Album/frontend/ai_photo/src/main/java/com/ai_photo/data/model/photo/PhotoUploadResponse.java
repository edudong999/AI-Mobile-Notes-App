package com.ai_photo.data.model.photo;

import java.util.List;

public class PhotoUploadResponse {
    public int successCount;
    public int failCount;
    public List<PhotoUploadItem> uploadedPhotos;
    public List<Object> failedFiles;
}
