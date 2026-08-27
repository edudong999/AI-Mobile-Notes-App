package com.ai_photo.data.model.note;

import java.util.List;

public class QuestionItem {
    public long questionId;
    public String questionType;
    public String stem;
    public List<String> options;
    public String answer;
    public String explanation;
    public String difficulty;
}