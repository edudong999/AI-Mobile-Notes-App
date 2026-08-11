package com.ai_photo.data.model.photo;

import java.util.List;

public class SearchItem {
    public long photoId;
    public String thumbnailUrl;
    public List<String> matchedTags;
    public double score;
}
