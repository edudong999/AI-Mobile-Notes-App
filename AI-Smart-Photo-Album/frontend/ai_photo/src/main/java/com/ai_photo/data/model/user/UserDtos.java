package com.ai_photo.data.model.user;

import java.util.List;

public class UserMeResponse {
    public long userId;
    public String username;
    public String email;
    public String avatarUrl;
    public String createdAt;
}

public class StatisticsResponse {
    public int totalPhotos;
    public int analyzedPhotos;
    public int favoriteCount;
    public CategoryDistribution categoryDistribution;
}

public class CategoryDistribution {
    public List<DistributionItem> scene;
    public List<DistributionItem> emotion;
    public List<DistributionItem> tag;
}

public class DistributionItem {
    public String name;
    public int count;
    public double percentage;
}