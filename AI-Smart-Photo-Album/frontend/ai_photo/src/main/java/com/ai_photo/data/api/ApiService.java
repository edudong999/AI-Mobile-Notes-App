package com.ai_photo.data.api;

import com.ai_photo.data.model.Envelope;
import com.ai_photo.data.model.admin.*;
import com.ai_photo.data.model.ai.*;
import com.ai_photo.data.model.auth.*;
import com.ai_photo.data.model.category.*;
import com.ai_photo.data.model.note.*;
import com.ai_photo.data.model.note_ai.*;
import com.ai_photo.data.model.note_search.*;
import com.ai_photo.data.model.photo.*;
import com.ai_photo.data.model.user.*;
import okhttp3.MultipartBody;
import retrofit2.Call;
import retrofit2.http.*;

import java.util.List;

/** All 47 backend endpoints from docs/interface.md. */
public interface ApiService {

    // ===== Auth (3) =====
    @POST("api/v1/auth/register")
    Call<Envelope<AuthResponse>> register(@Body RegisterRequest req);

    @POST("api/v1/auth/login")
    Call<Envelope<AuthResponse>> login(@Body LoginRequest req);

    @POST("api/v1/auth/logout")
    Call<Envelope<Object>> logout();

    // ===== Users (3) =====
    @GET("api/v1/users/me")
    Call<Envelope<UserMeResponse>> me();

    @GET("api/v1/users/me/statistics")
    Call<Envelope<StatisticsResponse>> statistics();

    @GET("api/v1/users/me/favorites")
    Call<Envelope<FavoriteResponse>> favorites(@Query("page") int page, @Query("pageSize") int pageSize);

    // ===== Photos (12) =====
    @Multipart
    @POST("api/v1/photos/upload")
    Call<Envelope<PhotoUploadResponse>> uploadPhotos(@Part List<MultipartBody.Part> files);

    @GET("api/v1/photos")
    Call<Envelope<PhotoListResponse>> listPhotos(@Query("page") int page, @Query("pageSize") int pageSize);

    @GET("api/v1/photos/recent")
    Call<Envelope<PhotoRecentResponse>> recentPhotos(@Query("limit") int limit);

    @GET("api/v1/photos/{id}")
    Call<Envelope<PhotoDetailResponse>> photoDetail(@Path("id") long id);

    @PATCH("api/v1/photos/{id}")
    Call<Envelope<Object>> updatePhoto(@Path("id") long id, @Body PhotoUpdateRequest req);

    @HTTP(method = "DELETE", path = "api/v1/photos/batch", hasBody = true)
    Call<Envelope<BatchDeleteResponse>> deleteBatch(@Body BatchDeleteRequest req);

    @DELETE("api/v1/photos/{id}")
    Call<Envelope<Object>> deletePhoto(@Path("id") long id);

    @POST("api/v1/photos/{id}/favorite")
    Call<Envelope<Object>> favorite(@Path("id") long id);

    @DELETE("api/v1/photos/{id}/favorite")
    Call<Envelope<Object>> unfavorite(@Path("id") long id);

    @POST("api/v1/photos/search")
    Call<Envelope<SearchResponse>> search(@Body SearchRequest req);

    @POST("api/v1/photos/filter")
    Call<Envelope<SearchResponse>> filter(@Body FilterRequest req);

    // ===== Categories (3) =====
    @GET("api/v1/categories/preview")
    Call<Envelope<CategoryPreviewResponse>> previewCategories(@Query("previewSize") int size);

    @GET("api/v1/categories")
    Call<Envelope<CategoryListResponse>> listCategories(@Query("type") String type);

    @GET("api/v1/categories/{id}/photos")
    Call<Envelope<CategoryPhotosResponse>> categoryPhotos(
        @Path("id") long id, @Query("page") int page, @Query("pageSize") int pageSize);

    // ===== AI (3) =====
    @GET("api/v1/ai/status")
    Call<Envelope<AiStatusResponse>> aiStatus();

    @GET("api/v1/ai/queue")
    Call<Envelope<AiQueueResponse>> aiQueue();

    @POST("api/v1/ai/reanalyze")
    Call<Envelope<ReanalyzeResponse>> reanalyze(@Body ReanalyzeRequest req);

    // ===== Admin (4) =====
    @GET("api/v1/admin/categories")
    Call<Envelope<AdminCategoryListResponse>> adminListCategories(@Query("type") String type);

    @POST("api/v1/admin/categories")
    Call<Envelope<AdminCreateResponse>> adminCreateCategory(@Body AdminCreateRequest req);

    @PATCH("api/v1/admin/categories/{id}")
    Call<Envelope<Object>> adminUpdateCategory(@Path("id") long id, @Body AdminUpdateRequest req);

    @DELETE("api/v1/admin/categories/{id}")
    Call<Envelope<Object>> adminDeleteCategory(@Path("id") long id);

    @POST("api/v1/admin/categories/reset")
    Call<Envelope<AdminResetResponse>> adminResetCategories(@Body AdminResetRequest req);

    // ===== Notes: Folders (4) =====
    @GET("api/v1/folders")
    Call<Envelope<FolderListResponse>> listFolders();

    @POST("api/v1/folders")
    Call<Envelope<FolderCreateResponse>> createFolder(@Body FolderCreateRequest req);

    @PATCH("api/v1/folders/{id}")
    Call<Envelope<Object>> updateFolder(@Path("id") long id, @Body FolderUpdateRequest req);

    @DELETE("api/v1/folders/{id}")
    Call<Envelope<Object>> deleteFolder(@Path("id") long id);

    // ===== Notes: Notes CRUD (6) =====
    @GET("api/v1/notes")
    Call<Envelope<NoteListResponse>> listNotes(
        @Query("folderId") Long folderId,
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

    // ===== Notes: Files (1) =====
    @Multipart
    @POST("api/v1/note-files/upload")
    Call<Envelope<NoteFileUploadResponse>> uploadNoteFiles(
        @Part List<MultipartBody.Part> files,
        @Part("noteId") okhttp3.RequestBody noteId);

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

    @GET("api/v1/note-ai/status")
    Call<Envelope<NoteAiStatusResponse>> aiNoteStatus();

    @POST("api/v1/note-ai/retry")
    Call<Envelope<EnqueueResponse>> aiNoteRetry(@Body NoteRetryRequest req);

    // ===== Notes: Search (1) =====
    @POST("api/v1/note-search")
    Call<Envelope<SearchResponse>> noteSearch(@Body SearchRequest req);
}