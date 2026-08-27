package com.ai_photo.data.local;
import androidx.room.*;
import java.util.List;

@Dao
public interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY sortIndex")
    List<CategoryEntity> all();

    @Query("SELECT * FROM categories WHERE categoryId = :id")
    CategoryEntity byId(long id);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(CategoryEntity c);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertAll(List<CategoryEntity> cs);

    @Query("DELETE FROM categories WHERE categoryId = :id")
    void deleteById(long id);

    @Query("DELETE FROM categories")
    void clear();
}