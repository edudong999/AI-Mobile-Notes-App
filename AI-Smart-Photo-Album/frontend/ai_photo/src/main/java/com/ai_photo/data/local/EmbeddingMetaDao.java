package com.ai_photo.data.local;
import androidx.room.*;

@Dao
public interface EmbeddingMetaDao {
    @Query("SELECT * FROM embedding_meta WHERE noteId = :id")
    EmbeddingMetaEntity byId(long id);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(EmbeddingMetaEntity e);

    @Query("DELETE FROM embedding_meta WHERE noteId = :noteId")
    void deleteByNote(long noteId);

    @Query("DELETE FROM embedding_meta")
    void clear();
}