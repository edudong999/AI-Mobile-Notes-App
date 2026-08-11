package com.ai_photo.data.model.photo;

import java.util.List;

public class AIAnalysisBlock {
    public String description;
    public AITagResult scene;
    public AITagResult emotion;
    public List<AITagResult> tags;
}
