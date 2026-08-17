package com.ai_photo.data.local;
import androidx.room.*;
import java.util.List;

@Dao
public interface FolderDao {
    @Query("SELECT * FROM folders ORDER BY sortIndex")
    List<FolderEntity> all();

    @Query("SELECT * FROM folders WHERE folderId = :id")
    FolderEntity byId(long id);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(FolderEntity f);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertAll(List<FolderEntity> fs);

    @Query("DELETE FROM folders WHERE folderId = :id")
    void deleteById(long id);

    @Query("DELETE FROM folders")
    void clear();
}