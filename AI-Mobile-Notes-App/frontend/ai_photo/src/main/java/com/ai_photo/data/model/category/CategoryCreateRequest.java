package com.ai_photo.data.model.category;

public class CategoryCreateRequest {
    public String name;
    public String color;

    public CategoryCreateRequest(String name, String color) {
        this.name = name;
        this.color = color;
    }
}