package com.ai_photo.data.repo;
import com.ai_photo.data.model.note_ai.*;
import java.util.List;

// Stub — Task 15 wires Retrofit + ApiService methods.
public class NoteAiRepo {
    public com.ai_photo.util.Result<QuestionsResponse> generateQuestions(long noteId, int count, List<String> types) {
        throw new UnsupportedOperationException("NoteAiRepo.generateQuestions pending Task 15 ApiService wiring");
    }
    public com.ai_photo.util.Result<PolishResponse> polish(long noteId, String action, String text) {
        throw new UnsupportedOperationException("NoteAiRepo.polish pending Task 15 ApiService wiring");
    }
    public com.ai_photo.util.Result<TranslateResponse> translate(long noteId, String targetLang, String text) {
        throw new UnsupportedOperationException("NoteAiRepo.translate pending Task 15 ApiService wiring");
    }
    public com.ai_photo.util.Result<EnqueueResponse> ocr(long noteId) {
        throw new UnsupportedOperationException("NoteAiRepo.ocr pending Task 15 ApiService wiring");
    }
    public com.ai_photo.util.Result<EnqueueResponse> summary(long noteId) {
        throw new UnsupportedOperationException("NoteAiRepo.summary pending Task 15 ApiService wiring");
    }
    public com.ai_photo.util.Result<NoteAiStatusResponse> status() {
        throw new UnsupportedOperationException("NoteAiRepo.status pending Task 15 ApiService wiring");
    }
}