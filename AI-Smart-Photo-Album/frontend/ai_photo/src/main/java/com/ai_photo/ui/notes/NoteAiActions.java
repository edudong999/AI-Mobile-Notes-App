package com.ai_photo.ui.notes;

import com.ai_photo.data.model.note_ai.*;
import com.ai_photo.data.repo.NoteAiRepo;
import com.ai_photo.util.BgExecutor;
import com.ai_photo.util.Result;

import java.util.Arrays;
import java.util.List;

public class NoteAiActions {
    public interface OnTextResult { void onResult(String text); void onError(String err); }
    public interface OnQuestionsResult { void onResult(List<com.ai_photo.data.model.note.QuestionItem> items); void onError(String err); }

    private final NoteAiRepo repo = new NoteAiRepo();

    private static String err(Result<?> r) {
        if (r instanceof Result.Error) return ((Result.Error<?>) r).message;
        return "网络错误";
    }

    public void polish(long noteId, String action, String text, OnTextResult cb) {
        BgExecutor.execute(() -> {
            Result<PolishResponse> r = repo.polish(noteId, action, text);
            if (r instanceof Result.Success) {
                PolishResponse d = (PolishResponse) ((Result.Success<?>) r).data;
                runOnMain(() -> cb.onResult(d != null && d.polished != null ? d.polished : text));
            } else { runOnMain(() -> cb.onError(err(r))); }
        });
    }

    public void translate(long noteId, String targetLang, String text, OnTextResult cb) {
        BgExecutor.execute(() -> {
            Result<TranslateResponse> r = repo.translate(noteId, targetLang, text);
            if (r instanceof Result.Success) {
                TranslateResponse d = (TranslateResponse) ((Result.Success<?>) r).data;
                runOnMain(() -> cb.onResult(d != null && d.translated != null ? d.translated : text));
            } else { runOnMain(() -> cb.onError(err(r))); }
        });
    }

    public void generateQuestions(long noteId, OnQuestionsResult cb) {
        BgExecutor.execute(() -> {
            Result<QuestionsResponse> r = repo.generateQuestions(noteId, 5,
                Arrays.asList("choice", "truefalse", "short"));
            if (r instanceof Result.Success) {
                QuestionsResponse d = (QuestionsResponse) ((Result.Success<?>) r).data;
                runOnMain(() -> cb.onResult(d != null && d.questions != null ? d.questions : java.util.Collections.emptyList()));
            } else { runOnMain(() -> cb.onError(err(r))); }
        });
    }

    public void runOcr(long noteId, com.ai_photo.util.ResultCallback<?> cb) {
        BgExecutor.execute(() -> {
            Result<EnqueueResponse> r = repo.ocr(noteId);
            runOnMain(() -> {
                if (r instanceof Result.Success) cb.onSuccess(((Result.Success<?>) r).data);
                else cb.onError(err(r));
            });
        });
    }

    public void runSummary(long noteId, com.ai_photo.util.ResultCallback<?> cb) {
        BgExecutor.execute(() -> {
            Result<EnqueueResponse> r = repo.summary(noteId);
            runOnMain(() -> {
                if (r instanceof Result.Success) cb.onSuccess(((Result.Success<?>) r).data);
                else cb.onError(err(r));
            });
        });
    }

    private void runOnMain(Runnable r) {
        new android.os.Handler(android.os.Looper.getMainLooper()).post(r);
    }
}