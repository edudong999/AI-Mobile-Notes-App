package com.ai_photo.data.model.note_search;
import java.util.List;

public class SearchHit {
    public long noteId;
    public String title;
    public String snippet;
    public double score;
}
public class SearchResponse {
    public String mode;
    public List<SearchHit> hits;
    public int total;
}