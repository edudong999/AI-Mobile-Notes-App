package com.ai_photo.data.repo;

import android.content.Context;
import com.ai_photo.data.local.*;

public class NoteRepo {
    private final AppDatabase db;
    public NoteRepo(Context ctx) { this.db = AppDatabase.get(ctx); }
    public NoteDao noteDao() { return db.noteDao(); }
    public FolderDao folderDao() { return db.folderDao(); }
    public NoteFileDao noteFileDao() { return db.noteFileDao(); }
    public AiJobDao aiJobDao() { return db.aiJobDao(); }
    public EmbeddingMetaDao embeddingMetaDao() { return db.embeddingMetaDao(); }
}