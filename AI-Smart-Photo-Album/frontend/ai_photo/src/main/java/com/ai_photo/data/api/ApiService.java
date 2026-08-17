package com.ai_photo.data.api;

import com.ai_photo.data.model.Envelope;
import com.ai_photo.data.model.admin.*;
import com.ai_photo.data.model.ai.*;
import com.ai_photo.data.model.auth.*;
import com.ai_photo.data.model.category.*;
import com.ai_photo.data.model.photo.*;
import com.ai_photo.data.model.user.*;
import okhttp3.MultipartBody;
import retrofit2.Call;
import retrofit2.http.*;

import java.util.List;

/** All 27 backend endpoints from docs/interface.md. */
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
}