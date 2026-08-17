package com.ai_photo.data.model.ai;

public class AiQueueItem {
    public long photoId;
    public String fileName;
    public String thumbnailUrl;
    /** pending / processing / done / failed */
    public String status;
    public String errorMessage;
    public int retryCount;
    public String updatedAt;
}
