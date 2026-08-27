package com.ai_photo.data.api;

import com.ai_photo.data.model.Envelope;
import com.ai_photo.data.model.auth.*;
import com.ai_photo.data.model.category.*;
import com.ai_photo.data.model.note.*;
import com.ai_photo.data.model.note_ai.*;
import com.ai_photo.data.model.note_image_cleanup.*;
import com.ai_photo.data.model.note_search.*;
import okhttp3.MultipartBody;
import retrofit2.Call;
import retrofit2.http.*;

import java.util.List;

/** Note endpoints backing the 7 AI Note Assistant capabilities. */
public interface ApiService {

    // ===== Auth (3) =====
    @POST("api/v1/auth/register")
    Call<Envelope<AuthResponse>> register(@Body RegisterRequest req);

    @POST("api/v1/auth/login")
    Call<Envelope<AuthResponse>> login(@Body LoginRequest req);

    @POST("api/v1/auth/logout")
    Call<Envelope<Object>> logout();

    // ===== Categories (6) =====
    @GET("api/v1/categories")
    Call<Envelope<CategoryListResponse>> listCategories();

    @POST("api/v1/categories")
    Call<Envelope<CategoryCreateResponse>> createCategory(@Body CategoryCreateRequest req);

    @PATCH("api/v1/categories/{id}")
    Call<Envelope<Object>> updateCategory(@Path("id") long id, @Body CategoryUpdateRequest req);

    @DELETE("api/v1/categories/{id}")
    Call<Envelope<Object>> deleteCategory(@Path("id") long id);

    @POST("api/v1/categories/reorder")
    Call<Envelope<Object>> reorderCategory(@Body CategoryReorderRequest req);

    @GET("api/v1/categories/{id}/notes")
    Call<Envelope<NoteListResponse>> notesInCategory(@Path("id") long id);

    // ===== Notes: CRUD (6) =====
    @GET("api/v1/notes")
    Call<Envelope<NoteListResponse>> listNotes(
        @Query("categoryId") Long categoryId,
        @Query("page") int page,
        @Query("pageSize") int pageSize);

    @GET("api/v1/notes/{id}")
    Call<Envelope<NoteDetailResponse>> getNote(@Path("id") long id);

    @POST("api/v1/notes")
    Call<Envelope<NoteCreateResponse>> createNote(@Body NoteCreateRequest req);

    @PATCH("api/v1/notes/{id}")
    Call<Envelope<Object>> updateNote(@Path("id") long id, @Body NoteUpdateRequest req);

    @DELETE("api/v1/notes/{id}")
    Call<Envelope<Object>> deleteNote(@Path("id") long id);

    @POST("api/v1/notes/{id}/export")
    Call<Envelope<ExportResponse>> exportNote(@Path("id") long id, @Body ExportRequest req);

    @POST("api/v1/notes/{id}/categories")
    Call<Envelope<NoteCategoriesResponse>> setNoteCategories(
        @Path("id") long id, @Body NoteCategoriesUpdate req);

    // ===== Notes: Files (2) =====
    @Multipart
    @POST("api/v1/note-files/upload")
    Call<Envelope<NoteFileUploadResponse>> uploadNoteFiles(
        @Part List<MultipartBody.Part> files,
        @Part("noteId") okhttp3.RequestBody noteId);

    @DELETE("api/v1/note-files/{fileId}")
    Call<Envelope<Object>> deleteNoteFile(@Path("fileId") long fileId);

    // ===== Notes: Image Cleanup (1) =====
    @POST("api/v1/note-image-cleanup")
    Call<Envelope<ImageCleanupResponse>> cleanupImage(@Body ImageCleanupRequest req);

    // ===== Notes: AI (7) =====
    @POST("api/v1/note-ai/ocr")
    Call<Envelope<EnqueueResponse>> aiOcr(@Body OcrRequest req);

    @POST("api/v1/note-ai/summary")
    Call<Envelope<EnqueueResponse>> aiSummary(@Body SummaryRequest req);

    @POST("api/v1/note-ai/questions")
    Call<Envelope<QuestionsResponse>> aiQuestions(@Body QuestionsRequest req);

    @POST("api/v1/note-ai/polish")
    Call<Envelope<PolishResponse>> aiPolish(@Body PolishRequest req);

    @POST("api/v1/note-ai/translate")
    Call<Envelope<TranslateResponse>> aiTranslate(@Body TranslateRequest req);

    @POST("api/v1/note-ai/mindmap")
    Call<Envelope<MindmapResponse>> aiMindmap(@Body MindmapRequest req);

    @POST("api/v1/note-ai/retry")
    Call<Envelope<EnqueueResponse>> aiNoteRetry(@Body NoteRetryRequest req);

    // ===== Notes: Search (1) =====
    @POST("api/v1/note-search")
    Call<Envelope<SearchResponse>> noteSearch(@Body SearchRequest req);
}