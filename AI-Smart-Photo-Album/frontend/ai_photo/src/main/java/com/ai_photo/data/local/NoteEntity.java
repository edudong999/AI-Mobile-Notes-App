package com.ai_photo.data.local;
import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "notes")
public class NoteEntity {
    @PrimaryKey public long noteId;
    public Long folderId;  // nullable
    @NonNull public String title = "";
    @NonNull public String textContent = "";
    @NonNull public String summary = "";
    @NonNull public String aiStatus = "pending"; // pending/processing/done/failed
    public String ocrEngine;
    public boolean isArchived;
    public long updatedAt;   // epoch ms
    public long createdAt;
    public boolean dirty;    // true = 等待同步
}