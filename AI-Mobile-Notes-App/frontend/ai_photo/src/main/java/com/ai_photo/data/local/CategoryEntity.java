package com.ai_photo.data.local;
import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "categories")
public class CategoryEntity {
    @PrimaryKey public long categoryId;
    @NonNull public String name = "";
    @NonNull public String color = "#4A90E2";
    public int sortIndex;
    public int noteCount;
}