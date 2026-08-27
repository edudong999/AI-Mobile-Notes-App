package com.ai_photo.data.model.note;
import java.util.List;

public class NoteDetailResponse {
    public long noteId;
    public List<Integer> categories;
    public String title;
    public String textContent;
    public String summary;
    public String aiStatus;
    public String ocrEngine;
    public boolean isArchived;
    public String updatedAt;
    public List<NoteFileItem> files;
    public List<QuestionItem> questions;
}