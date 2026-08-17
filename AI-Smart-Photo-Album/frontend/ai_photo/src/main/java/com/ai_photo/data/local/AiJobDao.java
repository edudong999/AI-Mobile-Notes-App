package com.ai_photo.data.local;
import androidx.room.*;
import java.util.List;

@Dao
public interface AiJobDao {
    @Query("SELECT * FROM ai_jobs WHERE noteId = :noteId ORDER BY updatedAt DESC")
    List<AiJobEntity> byNote(long noteId);

    @Query("SELECT * FROM ai_jobs WHERE kind = :kind AND noteId = :noteId LIMIT 1")
    AiJobEntity latestByKind(String kind, long noteId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(AiJobEntity j);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertAll(List<AiJobEntity> js);

    @Query("DELETE FROM ai_jobs")
    void clear();
}