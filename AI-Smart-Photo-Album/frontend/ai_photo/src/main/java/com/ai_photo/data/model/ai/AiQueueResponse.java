package com.ai_photo.data.model.ai;

import java.util.List;

public class AiQueueResponse {
    public List<AiQueueItem> pending;
    public List<AiQueueItem> processing;
    public List<AiQueueItem> failed;
    public List<AiQueueItem> done;
}
