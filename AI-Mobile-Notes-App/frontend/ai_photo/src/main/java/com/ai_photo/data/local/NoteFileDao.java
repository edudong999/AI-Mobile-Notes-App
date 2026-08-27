package com.ai_photo.data.local;
import androidx.room.*;
import java.util.List;

@Dao
public interface NoteFileDao {
    @Query("SELECT * FROM note_files WHERE noteId = :noteId ORDER BY sortIndex")
    List<NoteFileEntity> byNote(long noteId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertAll(List<NoteFileEntity> fs);

    @Query("DELETE FROM note_files WHERE noteId = :noteId")
    void deleteByNote(long noteId);

    @Query("DELETE FROM note_files")
    void clear();
}