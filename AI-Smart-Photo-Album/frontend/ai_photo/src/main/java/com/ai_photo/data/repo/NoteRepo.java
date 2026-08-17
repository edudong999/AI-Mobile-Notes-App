package com.ai_photo.data.repo;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okio.BufferedSink;
import com.ai_photo.data.api.RetrofitClient;
import com.ai_photo.data.local.*;
import com.ai_photo.data.model.note.*;
import com.ai_photo.util.Result;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class NoteRepo {
    private final AppDatabase db;
    public NoteRepo(Context ctx) { this.db = AppDatabase.get(ctx); }

    public NoteDao noteDao() { return db.noteDao(); }
    public FolderDao folderDao() { return db.folderDao(); }
    public NoteFileDao noteFileDao() { return db.noteFileDao(); }
    public AiJobDao aiJobDao() { return db.aiJobDao(); }
    public EmbeddingMetaDao embeddingMetaDao() { return db.embeddingMetaDao(); }

    // Folders
    public Result<FolderListResponse> listFolders() {
        return RetrofitClient.exec(RetrofitClient.api().listFolders());
    }
    public Result<FolderCreateResponse> createFolder(String name, String color) {
        FolderCreateRequest req = new FolderCreateRequest();
        req.name = name; req.color = color;
        return RetrofitClient.exec(RetrofitClient.api().createFolder(req));
    }
    public Result<Object> updateFolder(long id, String name, String color, Integer sortIndex) {
        FolderUpdateRequest req = new FolderUpdateRequest();
        req.name = name; req.color = color; req.sortIndex = sortIndex;
        return RetrofitClient.exec(RetrofitClient.api().updateFolder(id, req));
    }
    public Result<Object> deleteFolder(long id) {
        return RetrofitClient.exec(RetrofitClient.api().deleteFolder(id));
    }

    // Notes CRUD
    public Result<NoteListResponse> listNotes(Long folderId, int page, int pageSize) {
        return RetrofitClient.exec(RetrofitClient.api().listNotes(folderId, page, pageSize));
    }
    public Result<NoteDetailResponse> getNote(long id) {
        return RetrofitClient.exec(RetrofitClient.api().getNote(id));
    }
    public Result<NoteCreateResponse> createNote(Long folderId, String title, String textContent) {
        NoteCreateRequest req = new NoteCreateRequest();
        req.folderId = folderId; req.title = title; req.textContent = textContent;
        return RetrofitClient.exec(RetrofitClient.api().createNote(req));
    }
    public Result<Object> updateNote(long id, Long folderId, String title, String textContent, Boolean isArchived) {
        NoteUpdateRequest req = new NoteUpdateRequest();
        req.folderId = folderId; req.title = title; req.textContent = textContent; req.isArchived = isArchived;
        return RetrofitClient.exec(RetrofitClient.api().updateNote(id, req));
    }
    public Result<Object> deleteNote(long id) {
        return RetrofitClient.exec(RetrofitClient.api().deleteNote(id));
    }
    public Result<ExportResponse> exportNote(long id, String format) {
        ExportRequest req = new ExportRequest();
        req.format = format;
        return RetrofitClient.exec(RetrofitClient.api().exportNote(id, req));
    }

    public Result<NoteFileUploadResponse> uploadNoteFiles(Context ctx, long noteId, List<Uri> uris) {
        ContentResolver cr = ctx.getContentResolver();
        List<MultipartBody.Part> parts = new ArrayList<>();
        for (Uri uri : uris) {
            try {
                String mime = cr.getType(uri);
                if (mime == null) mime = "image/jpeg";
                MediaType mt = MediaType.parse(mime);
                byte[] bytes = readAll(cr.openInputStream(uri));
                String filename = "note_" + System.currentTimeMillis() + ".jpg";
                RequestBody rb = new RequestBody() {
                    @Override public MediaType contentType() { return mt; }
                    @Override public long contentLength() { return bytes.length; }
                    @Override public void writeTo(BufferedSink sink) throws IOException { sink.write(bytes); }
                };
                parts.add(MultipartBody.Part.createFormData("files", filename, rb));
            } catch (Exception e) {
                return Result.net(e);
            }
        }
        RequestBody noteIdBody = RequestBody.create(
            MediaType.parse("text/plain"), String.valueOf(noteId));
        return RetrofitClient.exec(RetrofitClient.api().uploadNoteFiles(parts, noteIdBody));
    }

    private byte[] readAll(InputStream is) throws IOException {
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        byte[] buf = new byte[8192]; int n;
        while ((n = is.read(buf)) > 0) sink.write(buf, 0, n);
        return sink.toByteArray();
    }
}
