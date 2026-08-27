package com.ai_photo.data.model.category;

import java.util.List;

public class CategoryReorderRequest {
    public List<Integer> orderedIds;

    public CategoryReorderRequest(List<Integer> orderedIds) {
        this.orderedIds = orderedIds;
    }
}