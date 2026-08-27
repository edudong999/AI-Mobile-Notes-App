package com.ai_photo.data.model.note;

import java.util.List;

public class NoteCategoriesUpdate {
    public List<Integer> categoryIds;

    public NoteCategoriesUpdate(List<Integer> categoryIds) {
        this.categoryIds = categoryIds;
    }
}