package com.ai_photo.data.local;
import androidx.room.*;
import java.util.List;

@Dao
public interface NoteDao {
    @Query("SELECT * FROM notes WHERE (:folderId IS NULL OR folderId = :folderId) AND isArchived = 0 ORDER BY updatedAt DESC LIMIT :limit")
    List<NoteEntity> byFolder(Long folderId, int limit);

    @Query("SELECT * FROM notes WHERE noteId = :id")
    NoteEntity byId(long id);

    @Query("SELECT * FROM notes WHERE dirty = 1")
    List<NoteEntity> dirty();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(NoteEntity n);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertAll(List<NoteEntity> ns);

    @Query("DELETE FROM notes WHERE noteId = :id")
    void deleteById(long id);

    @Query("DELETE FROM notes")
    void clear();
}