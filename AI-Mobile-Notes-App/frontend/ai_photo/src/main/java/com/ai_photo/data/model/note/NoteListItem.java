package com.ai_photo.data.model.note;

import java.util.List;

public class NoteListItem {
    public long noteId;
    public String title;
    public String summary;
    public String aiStatus;
    public List<Integer> categories;
    public String thumbUrl;
    public int thumbCount;
    public String updatedAt;
}