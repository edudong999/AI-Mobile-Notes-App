package com.ai_photo.data.local;
import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(entities = {
    CategoryEntity.class, NoteEntity.class, NoteFileEntity.class,
    AiJobEntity.class, EmbeddingMetaEntity.class,
}, version = 4, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {
    private static volatile AppDatabase INSTANCE;
    public abstract CategoryDao categoryDao();
    public abstract NoteDao noteDao();
    public abstract NoteFileDao noteFileDao();
    public abstract AiJobDao aiJobDao();
    public abstract EmbeddingMetaDao embeddingMetaDao();

    public static AppDatabase get(Context ctx) {
        AppDatabase local = INSTANCE;
        if (local == null) {
            synchronized (AppDatabase.class) {
                local = INSTANCE;
                if (local == null) {
                    INSTANCE = local = Room.databaseBuilder(
                            ctx.getApplicationContext(),
                            AppDatabase.class, "note-assistant.db")
                        .fallbackToDestructiveMigration()
                        .build();
                }
            }
        }
        return local;
    }
}