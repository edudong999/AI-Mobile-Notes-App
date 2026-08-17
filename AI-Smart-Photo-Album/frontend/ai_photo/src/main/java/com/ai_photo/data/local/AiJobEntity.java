package com.ai_photo.data.local;
import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "ai_jobs")
public class AiJobEntity {
    @PrimaryKey public long jobId;
    public long noteId;
    @NonNull public String kind = "";     // ocr/summary/questions/polish/translate/embed
    @NonNull public String status = "";   // queued/processing/succeeded/failed
    public String errorMessage;
    public int retryCount;
    public long updatedAt;
}