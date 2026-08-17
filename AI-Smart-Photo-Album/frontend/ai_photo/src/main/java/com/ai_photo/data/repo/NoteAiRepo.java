package com.ai_photo.data.repo;

import com.ai_photo.data.api.RetrofitClient;
import com.ai_photo.data.model.note_ai.*;
import com.ai_photo.util.Result;
import java.util.List;

public class NoteAiRepo {
    public Result<QuestionsResponse> generateQuestions(long noteId, int count, List<String> types) {
        QuestionsRequest req = new QuestionsRequest();
        req.noteId = noteId; req.count = count; req.types = types;
        return RetrofitClient.exec(RetrofitClient.api().aiQuestions(req));
    }
    public Result<PolishResponse> polish(long noteId, String action, String text) {
        PolishRequest req = new PolishRequest();
        req.noteId = noteId; req.action = action; req.text = text;
        return RetrofitClient.exec(RetrofitClient.api().aiPolish(req));
    }
    public Result<TranslateResponse> translate(long noteId, String targetLang, String text) {
        TranslateRequest req = new TranslateRequest();
        req.noteId = noteId; req.targetLang = targetLang; req.text = text;
        return RetrofitClient.exec(RetrofitClient.api().aiTranslate(req));
    }
    public Result<EnqueueResponse> ocr(long noteId) {
        OcrRequest req = new OcrRequest();
        req.noteId = noteId;
        return RetrofitClient.exec(RetrofitClient.api().aiOcr(req));
    }
    public Result<EnqueueResponse> summary(long noteId) {
        SummaryRequest req = new SummaryRequest();
        req.noteId = noteId;
        return RetrofitClient.exec(RetrofitClient.api().aiSummary(req));
    }
    public Result<NoteAiStatusResponse> status() {
        return RetrofitClient.exec(RetrofitClient.api().aiNoteStatus());
    }
}
