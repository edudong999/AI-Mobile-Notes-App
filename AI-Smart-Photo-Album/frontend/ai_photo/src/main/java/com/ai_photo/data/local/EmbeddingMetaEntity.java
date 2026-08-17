package com.ai_photo.data.local;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "embedding_meta")
public class EmbeddingMetaEntity {
    @PrimaryKey public long noteId;
    public long syncedAt;
}