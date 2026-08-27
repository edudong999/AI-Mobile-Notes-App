package com.ai_photo.data.local;
import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "note_files")
public class NoteFileEntity {
    @PrimaryKey public long fileId;
    public long noteId;
    @NonNull public String localPath = "";
    public String remoteUrl;
    public String thumbUrl;
    public Integer width;     // nullable (Integer for nullable)
    public Integer height;
    public int sortIndex;
}