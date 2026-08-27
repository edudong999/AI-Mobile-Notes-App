package com.ai_photo.data.model.note;

import java.util.List;

public class NoteUpdateRequest {
    public String title;
    public String textContent;
    public Boolean isArchived;
    public List<Integer> categories;
}