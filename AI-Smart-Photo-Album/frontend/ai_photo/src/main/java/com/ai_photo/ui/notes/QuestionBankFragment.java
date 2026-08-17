package com.ai_photo.ui.notes;

import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.ai_photo.R;
import com.ai_photo.data.model.note.*;
import com.ai_photo.data.model.note_ai.QuestionsResponse;
import com.ai_photo.data.repo.NoteAiRepo;
import com.ai_photo.data.repo.NoteRepo;
import com.ai_photo.util.BgExecutor;
import com.ai_photo.util.Result;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class QuestionBankFragment extends Fragment {
    private static final String ARG_NOTE_ID = "noteId";
    private long noteId;
    private NoteRepo repo;
    private NoteAiRepo aiRepo;
    private RecyclerView recycler;
    private TextView empty;
    private QuestionAdapter adapter;
    private final List<QuestionItem> questions = new ArrayList<>();

    public static QuestionBankFragment newInstance(long noteId) {
        QuestionBankFragment f = new QuestionBankFragment();
        Bundle b = new Bundle(); b.putLong(ARG_NOTE_ID, noteId);
        f.setArguments(b); return f;
    }

    @Override public void onCreate(@Nullable Bundle b) {
        super.onCreate(b);
        if (getArguments() != null) noteId = getArguments().getLong(ARG_NOTE_ID);
        repo = new NoteRepo(requireContext());
        aiRepo = new NoteAiRepo();
    }

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup container, @Nullable Bundle b) {
        return inflater.inflate(R.layout.fragment_question_bank, container, false);
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle b) {
        super.onViewCreated(view, b);
        recycler = view.findViewById(R.id.question_recycler);
        empty = view.findViewById(R.id.question_empty);
        view.findViewById(R.id.btn_generate_questions).setOnClickListener(v -> generate());
        adapter = new QuestionAdapter();
        recycler.setLayoutManager(new LinearLayoutManager(getContext()));
        recycler.setAdapter(adapter);
        load();
    }

    @SuppressWarnings("unchecked")
    private void load() {
        BgExecutor.execute(() -> {
            Result<?> r = repo.getNote(noteId);
            if (r instanceof Result.Success) {
                NoteDetailResponse d = (NoteDetailResponse) ((Result.Success<?>) r).data;
                questions.clear();
                if (d != null && d.questions != null) questions.addAll(d.questions);
                final android.app.Activity a = getActivity();
                if (a != null && !a.isDestroyed()) {
                    a.runOnUiThread(() -> {
                        adapter.submit(questions);
                        empty.setVisibility(questions.isEmpty() ? View.VISIBLE : View.GONE);
                    });
                }
            }
        });
    }

    private void generate() {
        BgExecutor.execute(() -> {
            Result<QuestionsResponse> r = aiRepo.generateQuestions(noteId, 5,
                Arrays.asList("choice", "truefalse", "short"));
            if (r instanceof Result.Success) {
                QuestionsResponse data = (QuestionsResponse) ((Result.Success<?>) r).data;
                questions.clear();
                if (data != null && data.questions != null) questions.addAll(data.questions);
                final android.app.Activity a = getActivity();
                if (a != null && !a.isDestroyed()) {
                    a.runOnUiThread(() -> {
                        adapter.submit(questions);
                        empty.setVisibility(questions.isEmpty() ? View.VISIBLE : View.GONE);
                        Toast.makeText(getContext(),
                            getString(R.string.note_ai_questions_queued, questions.size()),
                            Toast.LENGTH_SHORT).show();
                    });
                }
            } else {
                final android.app.Activity a = getActivity();
                if (a != null && !a.isDestroyed()) {
                    String err = r instanceof Result.Error ? ((Result.Error<?>) r).message
                        : getString(R.string.msg_network_err);
                    a.runOnUiThread(() -> Toast.makeText(getContext(), err, Toast.LENGTH_SHORT).show());
                }
            }
        });
    }

    static class QuestionAdapter extends RecyclerView.Adapter<QuestionAdapter.VH> {
        private final List<QuestionItem> items = new ArrayList<>();
        void submit(List<QuestionItem> data) {
            items.clear(); if (data != null) items.addAll(data); notifyDataSetChanged();
        }
        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_question, parent, false);
            return new VH(v);
        }
        @Override public void onBindViewHolder(@NonNull VH h, int pos) {
            QuestionItem q = items.get(pos);
            h.type.setText(q.questionType != null ? q.questionType : "");
            h.stem.setText(q.stem != null ? q.stem : "");
            h.answer.setText("答案：" + (q.answer != null ? q.answer : ""));
            h.explanation.setText(q.explanation != null ? q.explanation : "");
            boolean showAnswer = h.itemView.getTag() != null && (Boolean) h.itemView.getTag();
            h.answer.setVisibility(showAnswer ? View.VISIBLE : View.GONE);
            h.explanation.setVisibility(showAnswer ? View.VISIBLE : View.GONE);
            h.itemView.setTag(!showAnswer);
            h.itemView.setOnClickListener(v -> {
                boolean newState = h.itemView.getTag() != null && (Boolean) h.itemView.getTag();
                h.itemView.setTag(newState);
                h.answer.setVisibility(newState ? View.VISIBLE : View.GONE);
                h.explanation.setVisibility(newState ? View.VISIBLE : View.GONE);
            });
        }
        @Override public int getItemCount() { return items.size(); }
        static class VH extends RecyclerView.ViewHolder {
            TextView type, stem, answer, explanation;
            VH(View v) {
                super(v);
                type = v.findViewById(R.id.question_type);
                stem = v.findViewById(R.id.question_stem);
                answer = v.findViewById(R.id.question_answer);
                explanation = v.findViewById(R.id.question_explanation);
            }
        }
    }
}
