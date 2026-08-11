package com.ai_photo.data.model.ai;

import java.util.List;

public class AiStatusResponse {
    public int total;
    public int done;
    public int pending;
    public double progress;
}

public class ReanalyzeRequest {
    public List<Long> photoIds;
}

public class ReanalyzeResponse {
    public int queuedCount;
    public String message;
}