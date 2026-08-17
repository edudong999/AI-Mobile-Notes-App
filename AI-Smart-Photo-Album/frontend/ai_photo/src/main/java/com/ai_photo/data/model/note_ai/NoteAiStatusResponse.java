package com.ai_photo.data.model.note_ai;

public class NoteAiStatusResponse {
    public long noteId;
    public String ocr;        // pending/processing/done/failed
    public String summary;
    public String questions;
    public int pending;
    public int processing;
    public int done;
    public int failed;
}