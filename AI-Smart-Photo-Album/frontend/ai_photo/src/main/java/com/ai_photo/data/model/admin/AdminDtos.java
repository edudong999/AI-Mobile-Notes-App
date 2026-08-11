package com.ai_photo.data.model.admin;

import java.util.List;

public class AdminCategoryListResponse {
    public List<AdminCategoryItem> list;
    public int total;
}

public class AdminCategoryItem {
    public long categoryId;
    public String type;
    public String name;
    public String iconUrl;
    public int photoCount;
    public String createdAt;
}

public class AdminCreateRequest {
    public String type;
    public String name;
    public String iconUrl;
}

public class AdminCreateResponse {
    public long categoryId;
}

public class AdminUpdateRequest {
    public String name;
    public String iconUrl;
}

public class AdminResetRequest {
    public boolean confirm;
}

public class AdminResetResponse {
    public int resetCount;
    public int removedCount;
}