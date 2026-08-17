package com.ai_photo.ui.notes;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.ai_photo.R;
import com.ai_photo.data.local.NoteEntity;
import com.ai_photo.data.local.NoteFileEntity;
import com.ai_photo.data.model.note.NoteDetailResponse;
import com.ai_photo.data.repo.NoteAiRepo;
import com.ai_photo.data.repo.NoteRepo;
import com.ai_photo.util.BgExecutor;
import com.ai_photo.util.GlideUtil;
import com.ai_photo.util.Result;

import java.util.List;

public class NoteDetailFragment extends Fragment {
    private static final String ARG_NOTE_ID = "noteId";

    private long noteId;
    private NoteRepo repo;
    private NoteAiActions ai;
    private TextView titleView, metaView, summaryView;
    private EditText editor;
    private RecyclerView filesStrip;
    private NoteEntity cached;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable poller;

    public static NoteDetailFragment newInstance(long noteId) {
        NoteDetailFragment f = new NoteDetailFragment();
        Bundle b = new Bundle();
        b.putLong(ARG_NOTE_ID, noteId);
        f.setArguments(b);
        return f;
    }

    @Override public void onCreate(@Nullable Bundle b) {
        super.onCreate(b);
        if (getArguments() != null) noteId = getArguments().getLong(ARG_NOTE_ID);
        repo = new NoteRepo(requireContext());
        ai = new NoteAiActions();
    }

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup container, @Nullable Bundle b) {
        return inflater.inflate(R.layout.fragment_note_detail, container, false);
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle b) {
        super.onViewCreated(view, b);
        titleView = view.findViewById(R.id.title);
        metaView = view.findViewById(R.id.meta);
        summaryView = view.findViewById(R.id.summary);
        editor = view.findViewById(R.id.editor);
        filesStrip = view.findViewById(R.id.files_strip);

        filesStrip.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));

        view.findViewById(R.id.btn_polish).setOnClickListener(v ->
            runPolish("polish"));
        view.findViewById(R.id.btn_translate).setOnClickListener(v ->
            pickTargetLangAndTranslate());
        view.findViewById(R.id.btn_questions).setOnClickListener(v ->
            ai.generateQuestions(noteId, new NoteAiActions.OnQuestionsResult() {
                @Override public void onResult(List<com.ai_photo.data.model.note.QuestionItem> items) {
                    Toast.makeText(getContext(),
                        getString(R.string.note_ai_questions_queued, items.size()),
                        Toast.LENGTH_SHORT).show();
                }
                @Override public void onError(String err) {
                    Toast.makeText(getContext(), err, Toast.LENGTH_SHORT).show();
                }
            }));
        view.findViewById(R.id.btn_ocr).setOnClickListener(v ->
            ai.runOcr(noteId, new com.ai_photo.util.ResultCallback<Object>() {
                @Override public void onSuccess(Object data) {
                    Toast.makeText(getContext(), R.string.note_ai_ocr_queued, Toast.LENGTH_SHORT).show();
                }
                @Override public void onError(String err) { Toast.makeText(getContext(), err, Toast.LENGTH_SHORT).show(); }
            }));
        view.findViewById(R.id.btn_summary).setOnClickListener(v ->
            ai.runSummary(noteId, new com.ai_photo.util.ResultCallback<Object>() {
                @Override public void onSuccess(Object data) {
                    Toast.makeText(getContext(), R.string.note_ai_summary_queued, Toast.LENGTH_SHORT).show();
                }
                @Override public void onError(String err) { Toast.makeText(getContext(), err, Toast.LENGTH_SHORT).show(); }
            }));

        loadDetail();
    }

    @Override public void onResume() {
        super.onResume();
        poller = new Runnable() {
            @Override public void run() {
                refreshStatus();
                handler.postDelayed(this, 4000);
            }
        };
        handler.postDelayed(poller, 4000);
    }

    @Override public void onPause() {
        super.onPause();
        if (poller != null) handler.removeCallbacks(poller);
    }

    @Override public void onDestroyView() {
        super.onDestroyView();
        if (poller != null) handler.removeCallbacks(poller);
    }

    @SuppressWarnings("unchecked")
    private void loadDetail() {
        BgExecutor.execute(() -> {
            // prefer local Room cache
            NoteEntity local = repo.noteDao().byId(noteId);
            final android.app.Activity a = getActivity();
            if (a == null || a.isDestroyed()) return;
            if (local != null) {
                cached = local;
                a.runOnUiThread(() -> renderFromLocal(local));
            }
            // then network refresh
            Result<?> r = repo.getNote(noteId);
            if (r instanceof Result.Success) {
                NoteDetailResponse d = (NoteDetailResponse) ((Result.Success<?>) r).data;
                if (d != null) {
                    NoteEntity ne = local != null ? local : new NoteEntity();
                    ne.noteId = d.noteId;
                    ne.folderId = d.folderId;
                    ne.title = d.title != null ? d.title : "";
                    ne.textContent = d.textContent != null ? d.textContent : "";
                    ne.summary = d.summary != null ? d.summary : "";
                    ne.aiStatus = d.aiStatus != null ? d.aiStatus : "pending";
                    ne.ocrEngine = d.ocrEngine;
                    ne.isArchived = d.isArchived;
                    ne.updatedAt = parseDate(d.updatedAt);
                    ne.dirty = false;
                    repo.noteDao().upsert(ne);
                    cached = ne;
                    if (a != null && !a.isDestroyed()) {
                        a.runOnUiThread(() -> {
                            if (getView() == null) return;
                            renderFromLocal(ne);
                            renderFiles(d);
                        });
                    }
                }
            }
        });
    }

    private void renderFromLocal(NoteEntity n) {
        titleView.setText(n.title != null && !n.title.isEmpty() ? n.title : "(无标题)");
        metaView.setText("更新于 " + (n.updatedAt > 0 ? new java.text.SimpleDateFormat(
                "yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(new java.util.Date(n.updatedAt)) : "—"));
        summaryView.setText(n.summary != null && !n.summary.isEmpty() ? n.summary : "(尚无摘要)");
        if (TextUtils.isEmpty(editor.getText())) {
            editor.setText(n.textContent != null ? n.textContent : "");
        }
        List<NoteFileEntity> files = repo.noteFileDao().byNote(n.noteId);
        if (files != null && !files.isEmpty()) {
            filesStrip.setAdapter(new FilesStripAdapter(files));
        } else {
            filesStrip.setAdapter(null);
        }
    }

    private void renderFiles(NoteDetailResponse d) {
        if (d.files == null) return;
        java.util.ArrayList<NoteFileEntity> list = new java.util.ArrayList<>();
        for (com.ai_photo.data.model.note.NoteFileItem it : d.files) {
            NoteFileEntity e = new NoteFileEntity();
            e.fileId = it.fileId;
            e.noteId = d.noteId;
            e.remoteUrl = it.url;
            e.thumbUrl = it.thumbUrl;
            e.width = it.width;
            e.height = it.height;
            e.sortIndex = it.sortIndex;
            list.add(e);
            repo.noteFileDao().upsertAll(java.util.Collections.singletonList(e));
        }
        filesStrip.setAdapter(new FilesStripAdapter(list));
    }

    private void refreshStatus() {
        BgExecutor.execute(() -> {
            Result<?> r = new NoteAiRepo().status();
            final android.app.Activity a = getActivity();
            if (a == null || a.isDestroyed()) return;
            if (r instanceof Result.Success) {
                a.runOnUiThread(() -> {
                    if (getView() == null || cached == null) return;
                    // Lightweight: just trigger a re-render — the actual status field would normally come
                    // from a per-note endpoint. For now we leave the badge unchanged.
                });
            }
        });
    }

    private void runPolish(String action) {
        String text = editor.getText().toString();
        if (TextUtils.isEmpty(text)) {
            Toast.makeText(getContext(), R.string.note_detail_empty, Toast.LENGTH_SHORT).show();
            return;
        }
        ai.polish(noteId, action, text, new NoteAiActions.OnTextResult() {
            @Override public void onResult(String s) {
                showDiffDialog(action, text, s);
            }
            @Override public void onError(String err) {
                Toast.makeText(getContext(), err, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void pickTargetLangAndTranslate() {
        final String[] langs = new String[]{"en", "zh"};
        final String[] labels = new String[]{"English", "中文"};
        new android.app.AlertDialog.Builder(getContext())
            .setTitle(R.string.note_ai_translate)
            .setItems(labels, (d, i) -> runTranslate(langs[i]))
            .show();
    }

    private void runTranslate(String lang) {
        String text = editor.getText().toString();
        if (TextUtils.isEmpty(text)) {
            Toast.makeText(getContext(), R.string.note_detail_empty, Toast.LENGTH_SHORT).show();
            return;
        }
        ai.translate(noteId, lang, text, new NoteAiActions.OnTextResult() {
            @Override public void onResult(String s) { showDiffDialog("translate", text, s); }
            @Override public void onError(String err) { Toast.makeText(getContext(), err, Toast.LENGTH_SHORT).show(); }
        });
    }

    private void showDiffDialog(String action, String original, String aiText) {
        View v = LayoutInflater.from(getContext()).inflate(R.layout.dialog_note_diff, null, false);
        TextView orig = v.findViewById(R.id.diff_original);
        TextView aiOut = v.findViewById(R.id.diff_ai);
        orig.setText(original);
        aiOut.setText(aiText);
        new android.app.AlertDialog.Builder(getContext())
            .setTitle(getString(R.string.note_ai_diff_title, action))
            .setView(v)
            .setPositiveButton(R.string.note_ai_diff_accept, (d, w) -> {
                editor.setText(aiText);
                saveToServer(aiText);
            })
            .setNegativeButton(R.string.note_ai_diff_reject, null)
            .show();
    }

    private void saveToServer(String text) {
        String title = cached != null && cached.title != null ? cached.title : "";
        Long folderId = cached != null ? cached.folderId : null;
        BgExecutor.execute(() -> {
            repo.updateNote(noteId, folderId, title, text, cached != null && cached.isArchived);
            final android.app.Activity a = getActivity();
            if (a == null || a.isDestroyed()) return;
            a.runOnUiThread(() -> {
                NoteEntity ne = repo.noteDao().byId(noteId);
                if (ne != null) {
                    ne.textContent = text;
                    repo.noteDao().upsert(ne);
                    cached = ne;
                }
                Toast.makeText(getContext(), R.string.note_detail_saved, Toast.LENGTH_SHORT).show();
            });
        });
    }

    private static long parseDate(String iso) {
        if (iso == null || iso.isEmpty()) return 0;
        try { return java.time.Instant.parse(iso).toEpochMilli(); }
        catch (Exception e) { return 0L; }
    }

    /** Tiny horizontal strip adapter — ImageView per row, 160dp, square. */
    static class FilesStripAdapter extends RecyclerView.Adapter<FilesStripAdapter.VH> {
        private final List<NoteFileEntity> items;
        FilesStripAdapter(List<NoteFileEntity> items) { this.items = items; }
        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ImageView iv = new ImageView(parent.getContext());
            android.view.ViewGroup.LayoutParams lp = new android.view.ViewGroup.LayoutParams(
                (int) (160 * parent.getResources().getDisplayMetrics().density),
                (int) (160 * parent.getResources().getDisplayMetrics().density));
            iv.setLayoutParams(lp);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            int pad = (int) (4 * parent.getResources().getDisplayMetrics().density);
            iv.setPadding(pad, pad, pad, pad);
            return new VH(iv);
        }
        @Override public void onBindViewHolder(@NonNull VH h, int pos) {
            NoteFileEntity e = items.get(pos);
            String url = e.thumbUrl != null && !e.thumbUrl.isEmpty() ? e.thumbUrl : e.remoteUrl;
            GlideUtil.loadThumb((ImageView) h.itemView, url);
        }
        @Override public int getItemCount() { return items.size(); }
        static class VH extends RecyclerView.ViewHolder {
            VH(View v) { super(v); }
        }
    }
}