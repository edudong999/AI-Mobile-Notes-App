package com.ai_photo.data.repo;

import com.ai_photo.data.api.RetrofitClient;
import com.ai_photo.data.model.note_image_cleanup.ImageCleanupRequest;
import com.ai_photo.data.model.note_image_cleanup.ImageCleanupResponse;
import com.ai_photo.util.Result;

/** Wraps the multimodal image cleanup endpoint (qwen-image-edit). */
public class ImageCleanupRepo {
    public Result<ImageCleanupResponse> cleanup(long noteId, long fileId, String mode) {
        return RetrofitClient.exec(RetrofitClient.api().cleanupImage(
            new ImageCleanupRequest(noteId, fileId, mode)));
    }

    public Result<Object> deleteCleanedFile(long fileId) {
        return RetrofitClient.exec(RetrofitClient.api().deleteNoteFile(fileId));
    }
}