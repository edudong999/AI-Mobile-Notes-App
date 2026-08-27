package com.ai_photo.data.model.note_image_cleanup;

public class ImageCleanupRequest {
    public long noteId;
    public long fileId;
    public String mode;

    public ImageCleanupRequest(long noteId, long fileId, String mode) {
        this.noteId = noteId;
        this.fileId = fileId;
        this.mode = mode;
    }
}