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
import com.ai_photo.data.model.note_ai.EnqueueResponse;
import com.ai_photo.data.repo.NoteAiRepo;
import com.ai_photo.data.repo.NoteRepo;
import com.ai_photo.util.BgExecutor;
import com.ai_photo.util.GlideUtil;
import com.ai_photo.util.Result;
import com.ai_photo.util.ServerPrefs;

import java.util.List;

public class NoteDetailFragment extends Fragment {
    private static final String ARG_NOTE_ID = "noteId";

    private long noteId;
    private NoteRepo repo;
    private NoteAiActions ai;
    private TextView titleView, metaView, summaryView, bodyView, questionsEntryText;
    private EditText editor;
    private LinearLayout questionsEntry;
    private RecyclerView filesStrip;
    private ProgressBar actionProgress;
    private View aiOverlay;
    private TextView aiOverlayLabel;
    private Button btnSave, btnDelete;
    private NoteEntity cached;
    private int questionsCount = 0;
    private volatile boolean isSaving = false;
    private volatile boolean isDeleting = false;
    private volatile boolean isLoading = false;
    private volatile boolean isAiRunning = false;
    private volatile int loadGeneration = 0;

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
        bodyView = view.findViewById(R.id.body_view);
        editor = view.findViewById(R.id.editor);
        questionsEntry = view.findViewById(R.id.questions_entry);
        questionsEntryText = view.findViewById(R.id.questions_entry_text);
        View mindMapEntry = view.findViewById(R.id.mind_map_entry);
        mindMapEntry.setOnClickListener(v ->
            androidx.navigation.fragment.NavHostFragment.findNavController(NoteDetailFragment.this)
                .navigate(R.id.action_to_mind_map,
                    makeNavArgs(noteId)));
        filesStrip = view.findViewById(R.id.files_strip);
        actionProgress = view.findViewById(R.id.action_progress);
        aiOverlay = view.findViewById(R.id.ai_loading_overlay);
        aiOverlayLabel = view.findViewById(R.id.ai_loading_label);
        btnSave = view.findViewById(R.id.btn_save);
        btnDelete = view.findViewById(R.id.btn_delete);

        filesStrip.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));

        // 进入即给用户即时反馈：标题/正文显示占位文本，不再是空白屏
        titleView.setText(R.string.note_detail_title_placeholder);
        metaView.setText(R.string.note_detail_meta_loading);
        summaryView.setText(R.string.note_detail_summary_empty);
        bodyView.setText(R.string.note_detail_loading);
        actionProgress.setVisibility(View.VISIBLE);
        isLoading = true;

        // 默认只读：body_view 显示，editor 隐藏
        bodyView.setVisibility(View.VISIBLE);
        editor.setVisibility(View.GONE);

        // 点正文任意位置 → 切到编辑态
        bodyView.setOnClickListener(v -> enterEditMode());

        // 长按标题 → 打开服务器设置（IP 变了的时候用）
        titleView.setOnLongClickListener(v -> {
            showServerSettingsDialog();
            return true;
        });

        // 题目入口（懒渲染：有题目再显示）点击 → 进题库
        questionsEntry.setOnClickListener(v ->
            androidx.navigation.fragment.NavHostFragment.findNavController(NoteDetailFragment.this)
                .navigate(R.id.action_to_question_bank,
                    makeNavArgs(noteId)));

        view.findViewById(R.id.btn_polish).setOnClickListener(v ->
            pickPolishAction());
        view.findViewById(R.id.btn_translate).setOnClickListener(v ->
            pickTargetLangAndTranslate());
        view.findViewById(R.id.btn_questions).setOnClickListener(v -> {
            if (isAiRunning) {
                Toast.makeText(getContext(), R.string.ai_loading_in_progress, Toast.LENGTH_SHORT).show();
                return;
            }
            showAiOverlay(R.string.ai_loading_questions);
            ai.generateQuestions(noteId, new NoteAiActions.OnQuestionsResult() {
                @Override public void onResult(List<com.ai_photo.data.model.note.QuestionItem> items) {
                    hideAiOverlay();
                    if (getView() == null) return;
                    androidx.navigation.fragment.NavHostFragment.findNavController(NoteDetailFragment.this)
                        .navigate(R.id.action_to_question_bank, makeNavArgs(noteId));
                }
                @Override public void onError(String err) {
                    hideAiOverlay();
                    if (getView() == null) return;
                    Toast.makeText(getContext(), err, Toast.LENGTH_SHORT).show();
                }
            });
        });
        view.findViewById(R.id.btn_ocr).setOnClickListener(v ->
            ai.runOcr(noteId, new com.ai_photo.util.ResultCallback<EnqueueResponse>() {
                @Override public void onSuccess(EnqueueResponse data) {
                    Toast.makeText(getContext(), R.string.note_ai_ocr_queued, Toast.LENGTH_SHORT).show();
                }
                @Override public void onError(String err) { Toast.makeText(getContext(), err, Toast.LENGTH_SHORT).show(); }
            }));
        view.findViewById(R.id.btn_summary).setOnClickListener(v ->
            ai.runSummary(noteId, new com.ai_photo.util.ResultCallback<EnqueueResponse>() {
                @Override public void onSuccess(EnqueueResponse data) {
                    Toast.makeText(getContext(), R.string.note_ai_summary_queued, Toast.LENGTH_SHORT).show();
                }
                @Override public void onError(String err) { Toast.makeText(getContext(), err, Toast.LENGTH_SHORT).show(); }
            }));

        view.findViewById(R.id.btn_save).setOnClickListener(v -> {
            String text = readCurrentText();
            saveToServer(text);
        });

        view.findViewById(R.id.btn_export).setOnClickListener(v -> pickExportFormat());

        view.findViewById(R.id.btn_delete).setOnClickListener(v -> confirmDelete());

        loadDetail();
    }

    private void setProgress(boolean loading, boolean saving, boolean deleting) {
        isLoading = loading;
        isSaving = saving;
        isDeleting = deleting;
        boolean show = loading || saving || deleting;
        if (actionProgress != null) {
            actionProgress.setVisibility(show ? View.VISIBLE : View.GONE);
        }
        if (btnSave != null) {
            // 保存按钮：保存中或加载中或删除中禁用
            btnSave.setEnabled(!saving && !deleting);
        }
        if (btnDelete != null) {
            btnDelete.setEnabled(!saving && !deleting);
        }
    }

    private void showAiOverlay(int labelRes) {
        if (aiOverlay == null) return;
        isAiRunning = true;
        aiOverlayLabel.setText(labelRes);
        aiOverlay.setVisibility(View.VISIBLE);
    }

    private void hideAiOverlay() {
        if (aiOverlay == null) return;
        isAiRunning = false;
        aiOverlay.setVisibility(View.GONE);
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
        // 让进行中的后台任务发现 view 已销毁，自动放弃 runOnUiThread
        loadGeneration++;
    }

    @SuppressWarnings("unchecked")
    private void loadDetail() {
        final int gen = ++loadGeneration;
        // 加载态：先关掉旧进度，立刻显示新的；标题/正文占位已由 onViewCreated 设好
        setProgress(true, isSaving, isDeleting);

        BgExecutor.execute(() -> {
            // 1) 先渲染本地缓存（快，不阻塞 UI）
            NoteEntity local = repo.noteDao().byId(noteId);
            List<NoteFileEntity> localFiles = repo.noteFileDao().byNote(noteId);
            final android.app.Activity a = getActivity();
            if (gen != loadGeneration) return; // stale

            if (local != null && a != null && !a.isDestroyed()) {
                final NoteEntity forRender = local;
                final List<NoteFileEntity> filesForRender = localFiles;
                cached = local;
                a.runOnUiThread(() -> {
                    if (gen != loadGeneration || getView() == null) return;
                    renderFromLocal(forRender, filesForRender);
                    if (filesForRender != null && !filesForRender.isEmpty()) {
                        filesStrip.setAdapter(new FilesStripAdapter(filesForRender));
                    }
                });
            }

            // 2) 网络刷新（带错误回报 + stale 检查）
            Result<?> r = repo.getNote(noteId);
            if (gen != loadGeneration) return;

            final android.app.Activity a2 = getActivity();
            if (a2 == null || a2.isDestroyed()) return;

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
                    java.util.ArrayList<NoteFileEntity> builtFiles = new java.util.ArrayList<>();
                    if (d.files != null) {
                        for (com.ai_photo.data.model.note.NoteFileItem it : d.files) {
                            NoteFileEntity e = new NoteFileEntity();
                            e.fileId = it.fileId;
                            e.noteId = d.noteId;
                            e.remoteUrl = it.url;
                            e.thumbUrl = it.thumbUrl;
                            e.width = it.width;
                            e.height = it.height;
                            e.sortIndex = it.sortIndex;
                            builtFiles.add(e);
                        }
                        if (!builtFiles.isEmpty()) {
                            repo.noteFileDao().upsertAll(builtFiles);
                        }
                    }
                    cached = ne;
                    final int qc = d.questions != null ? d.questions.size() : 0;
                    final java.util.ArrayList<NoteFileEntity> finalFiles = builtFiles;
                    a2.runOnUiThread(() -> {
                        if (gen != loadGeneration || getView() == null) return;
                        questionsCount = qc;
                        // 合并：本地文件 + 网络新增/更新
                        java.util.ArrayList<NoteFileEntity> merged = new java.util.ArrayList<>();
                        if (localFiles != null) merged.addAll(localFiles);
                        for (NoteFileEntity e : finalFiles) {
                            int idx = -1;
                            for (int i = 0; i < merged.size(); i++) {
                                if (merged.get(i).fileId == e.fileId) { idx = i; break; }
                            }
                            if (idx >= 0) merged.set(idx, e); else merged.add(e);
                        }
                        renderFromLocal(ne, merged);
                        if (!merged.isEmpty()) {
                            filesStrip.setAdapter(new FilesStripAdapter(merged));
                        } else {
                            filesStrip.setAdapter(null);
                        }
                        setProgress(false, isSaving, isDeleting);
                    });
                }
            } else {
                // 网络失败：报告用户，保留本地缓存
                final String errMsg;
                final int errCode;
                if (r instanceof Result.Error) {
                    errCode = ((Result.Error<?>) r).code;
                    errMsg = ((Result.Error<?>) r).message != null
                            ? ((Result.Error<?>) r).message : "HTTP " + errCode;
                } else if (r instanceof Result.Network) {
                    errCode = -1;
                    Throwable cause = ((Result.Network<?>) r).cause;
                    errMsg = "网络异常：" + (cause != null ? cause.getMessage() : "未知");
                } else {
                    errCode = -1;
                    errMsg = "未知错误";
                }
                a2.runOnUiThread(() -> {
                    if (gen != loadGeneration || getView() == null) return;
                    setProgress(false, isSaving, isDeleting);
                    // 404：本地也没数据，提示用户返回
                    if (errCode == 404 && local == null) {
                        Toast.makeText(getContext(),
                            getString(R.string.note_detail_load_failed, "笔记不存在或已删除"),
                            Toast.LENGTH_LONG).show();
                        if (btnDelete != null) btnDelete.setEnabled(false);
                        if (btnSave != null) btnSave.setEnabled(false);
                        if (editor.getVisibility() == View.VISIBLE) exitEditMode();
                    } else {
                        // 其它错误（含本地有缓存）：不打扰，只 toast
                        Toast.makeText(getContext(),
                            getString(R.string.note_detail_load_failed, errMsg),
                            Toast.LENGTH_SHORT).show();
                        // 网络层错误时主动提示「服务器地址可能变了」
                        if (r instanceof Result.Network) {
                            new android.app.AlertDialog.Builder(getContext())
                                .setTitle(R.string.server_settings_title)
                                .setMessage(getString(R.string.note_detail_load_failed, errMsg)
                                    + "\n\n" + getString(R.string.server_settings_help))
                                .setPositiveButton(R.string.server_settings_open,
                                    (d, w) -> showServerSettingsDialog())
                                .setNegativeButton(android.R.string.cancel, null)
                                .show();
                        }
                    }
                });
            }
        });
    }

    private void renderFromLocal(NoteEntity n, List<NoteFileEntity> files) {
        titleView.setText(n.title != null && !n.title.isEmpty() ? n.title : "(无标题)");
        metaView.setText("更新于 " + (n.updatedAt > 0 ? new java.text.SimpleDateFormat(
                "yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(new java.util.Date(n.updatedAt)) : "—"));
        summaryView.setText(n.summary != null && !n.summary.isEmpty()
                ? n.summary
                : getString(R.string.note_detail_summary_empty));
        String body = n.textContent != null ? n.textContent : "";
        if (editor.getVisibility() == View.GONE) {
            bodyView.setText(body.isEmpty() ? getString(R.string.note_detail_body_empty) : body);
        } else {
            // 编辑态时也同步进编辑器（防止来回切换丢字）
            if (TextUtils.isEmpty(editor.getText())) {
                editor.setText(body);
            }
        }
        if (files != null && !files.isEmpty()) {
            filesStrip.setAdapter(new FilesStripAdapter(files));
        } else {
            filesStrip.setAdapter(null);
        }
        // 题目入口可见性：有题目才显示
        if (questionsCount > 0) {
            questionsEntry.setVisibility(View.VISIBLE);
            questionsEntryText.setText(getString(R.string.note_detail_questions_entry_fmt, questionsCount));
        } else {
            questionsEntry.setVisibility(View.GONE);
        }
    }

    private void renderFiles(NoteDetailResponse d) {
        if (d.files == null) return;
        java.util.ArrayList<NoteFileEntity> list = new java.util.ArrayList<>();
        java.util.ArrayList<NoteFileEntity> toUpsert = new java.util.ArrayList<>();
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
            toUpsert.add(e);
        }
        if (!toUpsert.isEmpty()) {
            repo.noteFileDao().upsertAll(toUpsert);
        }
        filesStrip.setAdapter(new FilesStripAdapter(list));
    }

    private void refreshStatus() {
        // 同时刷新当前 note 的正文/摘要（OCR / 摘要完成后让用户能看到真实 AI 输出），
        // 不再只 ping 全局 status。
        final int gen = loadGeneration;
        BgExecutor.execute(() -> {
            Result<?> r = repo.getNote(noteId);
            final android.app.Activity a = getActivity();
            if (a == null || a.isDestroyed()) return;
            if (r instanceof Result.Success) {
                NoteDetailResponse d = (NoteDetailResponse) ((Result.Success<?>) r).data;
                if (d == null) return;
                NoteEntity ne = repo.noteDao().byId(noteId);
                if (ne == null) return;
                ne.title = d.title != null ? d.title : "";
                ne.textContent = d.textContent != null ? d.textContent : "";
                ne.summary = d.summary != null ? d.summary : "";
                ne.aiStatus = d.aiStatus != null ? d.aiStatus : ne.aiStatus;
                ne.isArchived = d.isArchived;
                ne.updatedAt = parseDate(d.updatedAt);
                repo.noteDao().upsert(ne);
                cached = ne;
                final int qc = d.questions != null ? d.questions.size() : 0;
                final List<NoteFileEntity> files = repo.noteFileDao().byNote(noteId);
                a.runOnUiThread(() -> {
                    if (getView() == null || gen != loadGeneration) return;
                    // 用户正在编辑时不要覆盖正文
                    if (editor.getVisibility() != View.VISIBLE) {
                        questionsCount = qc;
                        renderFromLocal(ne, files);
                    }
                });
            }
        });
    }

    private void pickPolishAction() {
        final String[] actions = new String[]{"polish", "expand", "shorten"};
        final String[] labels = new String[]{
            getString(R.string.note_ai_polish_action_polish),
            getString(R.string.note_ai_polish_action_expand),
            getString(R.string.note_ai_polish_action_shorten)
        };
        new android.app.AlertDialog.Builder(getContext())
            .setTitle(R.string.note_ai_polish)
            .setItems(labels, (d, i) -> runPolish(actions[i]))
            .show();
    }

    private void runPolish(String action) {
        if (isAiRunning) {
            Toast.makeText(getContext(), R.string.ai_loading_in_progress, Toast.LENGTH_SHORT).show();
            return;
        }
        String text = readCurrentText();
        if (TextUtils.isEmpty(text) || text.equals(getString(R.string.note_detail_body_empty))) {
            Toast.makeText(getContext(), R.string.note_detail_empty, Toast.LENGTH_SHORT).show();
            return;
        }
        showAiOverlay(R.string.ai_loading_polish);
        ai.polish(noteId, action, text, new NoteAiActions.OnTextResult() {
            @Override public void onResult(String s) {
                hideAiOverlay();
                if (getView() == null) return;
                showDiffDialog(action, text, s);
            }
            @Override public void onError(String err) {
                hideAiOverlay();
                if (getView() == null) return;
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
        if (isAiRunning) {
            Toast.makeText(getContext(), R.string.ai_loading_in_progress, Toast.LENGTH_SHORT).show();
            return;
        }
        String text = readCurrentText();
        if (TextUtils.isEmpty(text) || text.equals(getString(R.string.note_detail_body_empty))) {
            Toast.makeText(getContext(), R.string.note_detail_empty, Toast.LENGTH_SHORT).show();
            return;
        }
        showAiOverlay(R.string.ai_loading_translate);
        ai.translate(noteId, lang, text, new NoteAiActions.OnTextResult() {
            @Override public void onResult(String s) {
                hideAiOverlay();
                if (getView() == null) return;
                showDiffDialog("translate", text, s);
            }
            @Override public void onError(String err) {
                hideAiOverlay();
                if (getView() == null) return;
                Toast.makeText(getContext(), err, Toast.LENGTH_SHORT).show();
            }
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
                // 接受 AI 结果：写回只读视图并保存
                bodyView.setText(aiText);
                exitEditMode();
                saveToServer(aiText);
            })
            .setNegativeButton(R.string.note_ai_diff_reject, null)
            .show();
    }

    private void saveToServer(String text) {
        if (isSaving) {
            Toast.makeText(getContext(), R.string.note_detail_save_in_progress, Toast.LENGTH_SHORT).show();
            return;
        }
        // 增量更新：cached 未就绪时只更新 textContent，避免把 title 清空
        final Long folderId   = cached != null ? cached.folderId : null;
        final String title    = cached != null && cached.title != null ? cached.title : null;
        final Boolean archived = cached != null ? cached.isArchived : null;
        final String toSave   = text != null ? text : "";

        // 占位文本不允许直接保存 —— 否则会把「（正文为空…）」当真实内容存进去
        if (toSave.equals(getString(R.string.note_detail_body_empty))) {
            if (cached != null && (cached.textContent == null || cached.textContent.isEmpty())) {
                Toast.makeText(getContext(), R.string.note_detail_empty, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getContext(), "请先点正文进入编辑再保存", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        isSaving = true;
        setProgress(isLoading, true, isDeleting);

        BgExecutor.execute(() -> {
            Result<?> r = repo.updateNote(noteId, folderId, title, toSave, archived);
            final boolean ok = r instanceof Result.Success;
            final String errMsg;
            if (ok) {
                NoteEntity ne = repo.noteDao().byId(noteId);
                if (ne != null) {
                    ne.textContent = toSave;
                    repo.noteDao().upsert(ne);
                    cached = ne;
                }
                errMsg = null;
            } else if (r instanceof Result.Error) {
                errMsg = ((Result.Error<?>) r).message != null
                        ? ((Result.Error<?>) r).message : "HTTP " + ((Result.Error<?>) r).code;
            } else if (r instanceof Result.Network) {
                Throwable cause = ((Result.Network<?>) r).cause;
                errMsg = "网络异常：" + (cause != null ? cause.getMessage() : "未知");
            } else {
                errMsg = "未知错误";
            }

            final android.app.Activity a = getActivity();
            if (a == null || a.isDestroyed()) {
                isSaving = false;
                return;
            }
            a.runOnUiThread(() -> {
                isSaving = false;
                setProgress(isLoading, false, isDeleting);
                if (getView() == null) return;
                if (ok) {
                    bodyView.setText(toSave);
                    exitEditMode();
                    Toast.makeText(getContext(), R.string.note_detail_saved, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(getContext(),
                        getString(R.string.note_detail_save_failed, errMsg),
                        Toast.LENGTH_LONG).show();
                    // 网络错误时主动弹服务器设置入口
                    if (r instanceof Result.Network) {
                        new android.app.AlertDialog.Builder(getContext())
                            .setTitle(R.string.server_settings_title)
                            .setMessage(getString(R.string.note_detail_save_failed, errMsg)
                                + "\n\n" + getString(R.string.server_settings_help))
                            .setPositiveButton(R.string.server_settings_open,
                                (d, w) -> showServerSettingsDialog())
                            .setNegativeButton(android.R.string.cancel, null)
                            .show();
                    }
                }
            });
        });
    }

    private void pickExportFormat() {
        final String[] fmts = new String[]{"zip", "md"};
        final String[] labels = new String[]{
            getString(R.string.note_detail_export_fmt_zip),
            getString(R.string.note_detail_export_fmt_md)
        };
        new android.app.AlertDialog.Builder(getContext())
            .setTitle(R.string.note_detail_export_title)
            .setItems(labels, (d, i) -> startExport(fmts[i]))
            .show();
    }

    private void startExport(String format) {
        setProgress(isLoading, isSaving, isDeleting);
        final android.app.Activity a = getActivity();
        BgExecutor.execute(() -> {
            Result<?> r = repo.exportNote(noteId, format);
            if (a == null || a.isDestroyed()) return;
            a.runOnUiThread(() -> {
                setProgress(false, isSaving, isDeleting);
                if (getView() == null) return;
                if (r instanceof Result.Success) {
                    com.ai_photo.data.model.note.ExportResponse er =
                        (com.ai_photo.data.model.note.ExportResponse) ((Result.Success<?>) r).data;
                    if (er != null && er.downloadUrl != null) {
                        triggerDownload(er.downloadUrl, format);
                    } else {
                        Toast.makeText(getContext(),
                            getString(R.string.note_detail_export_failed, "空链接"),
                            Toast.LENGTH_SHORT).show();
                    }
                } else {
                    String msg;
                    if (r instanceof Result.Error) {
                        msg = ((Result.Error<?>) r).message != null
                            ? ((Result.Error<?>) r).message
                            : "HTTP " + ((Result.Error<?>) r).code;
                    } else if (r instanceof Result.Network) {
                        Throwable cause = ((Result.Network<?>) r).cause;
                        msg = "网络异常：" + (cause != null ? cause.getMessage() : "未知");
                    } else {
                        msg = "未知错误";
                    }
                    Toast.makeText(getContext(),
                        getString(R.string.note_detail_export_failed, msg),
                        Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    private void triggerDownload(String relativeUrl, String format) {
        try {
            String base = ServerPrefs.getBaseUrl(requireContext());
            if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
            String url = relativeUrl.startsWith("/") ? base + relativeUrl : base + "/" + relativeUrl;
            String filename = "note_" + noteId + "." + format;
            android.app.DownloadManager dm =
                (android.app.DownloadManager) requireContext().getSystemService(
                    android.content.Context.DOWNLOAD_SERVICE);
            if (dm == null) {
                // 权限不全的回退：直接打开浏览器
                android.content.Intent i = new android.content.Intent(android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse(url));
                startActivity(i);
                return;
            }
            android.app.DownloadManager.Request req = new android.app.DownloadManager.Request(android.net.Uri.parse(url));
            req.setTitle(filename);
            req.setDescription(getString(R.string.note_detail_export_title));
            req.setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            req.setMimeType(format.equals("zip") ? "application/zip" : "text/markdown");
            req.setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, filename);
            dm.enqueue(req);
            Toast.makeText(getContext(), R.string.note_detail_export_queued, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(getContext(),
                getString(R.string.note_detail_export_failed, e.getMessage()),
                Toast.LENGTH_SHORT).show();
        }
    }

    private void confirmDelete() {
        new android.app.AlertDialog.Builder(getContext())
            .setTitle(R.string.note_detail_delete_title)
            .setMessage(R.string.note_detail_delete_msg)
            .setPositiveButton(R.string.note_detail_delete, (dlg, w) -> doDelete())
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void doDelete() {
        if (isDeleting) return;
        isDeleting = true;
        setProgress(isLoading, isSaving, true);
        BgExecutor.execute(() -> {
            Result<?> r = repo.deleteNote(noteId);
            final android.app.Activity a = getActivity();
            if (a == null || a.isDestroyed()) {
                isDeleting = false;
                return;
            }
            if (r instanceof Result.Success) {
                repo.noteFileDao().deleteByNote(noteId);
                repo.noteDao().deleteById(noteId);
                repo.aiJobDao().deleteByNote(noteId);
                repo.embeddingMetaDao().deleteByNote(noteId);
                a.runOnUiThread(() -> {
                    if (getView() == null) return;
                    Toast.makeText(getContext(), R.string.note_detail_deleted, Toast.LENGTH_SHORT).show();
                    androidx.navigation.fragment.NavHostFragment.findNavController(this)
                        .popBackStack();
                });
            } else {
                final String err;
                if (r instanceof Result.Error) {
                    String m = ((Result.Error<?>) r).message;
                    err = m != null ? m : "HTTP " + ((Result.Error<?>) r).code;
                } else if (r instanceof Result.Network) {
                    Throwable cause = ((Result.Network<?>) r).cause;
                    err = "网络异常：" + (cause != null ? cause.getMessage() : "未知");
                } else {
                    err = "未知错误";
                }
                a.runOnUiThread(() -> {
                    isDeleting = false;
                    setProgress(isLoading, isSaving, false);
                    if (getView() == null) return;
                    Toast.makeText(getContext(),
                        getString(R.string.note_detail_delete_failed, err),
                        Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void enterEditMode() {
        // 把当前正文灌进编辑器并聚焦
        String current = bodyView.getText().toString();
        if (current.equals(getString(R.string.note_detail_body_empty))) {
            current = "";
        }
        editor.setText(current);
        editor.setSelection(editor.getText().length());
        bodyView.setVisibility(View.GONE);
        editor.setVisibility(View.VISIBLE);
        editor.requestFocus();
        android.view.inputmethod.InputMethodManager imm =
            (android.view.inputmethod.InputMethodManager) requireContext()
                .getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(editor, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
    }

    private void exitEditMode() {
        editor.setVisibility(View.GONE);
        bodyView.setVisibility(View.VISIBLE);
        // 隐藏键盘
        android.view.inputmethod.InputMethodManager imm =
            (android.view.inputmethod.InputMethodManager) requireContext()
                .getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(editor.getWindowToken(), 0);
    }

    /** 服务器设置对话框：改 URL + 测试连通性 + 重试当前操作。 */
    private void showServerSettingsDialog() {
        View v = LayoutInflater.from(getContext()).inflate(R.layout.dialog_server_settings, null, false);
        com.google.android.material.textfield.TextInputEditText input =
            v.findViewById(R.id.server_url_input);
        android.widget.TextView status = v.findViewById(R.id.server_test_status);
        String current = ServerPrefs.getBaseUrl(requireContext());
        input.setText(current);
        input.setSelection(input.getText().length());

        final android.app.AlertDialog[] holder = new android.app.AlertDialog[1];
        holder[0] = new android.app.AlertDialog.Builder(getContext())
            .setTitle(R.string.server_settings_title)
            .setView(v)
            .setNeutralButton(R.string.server_settings_reset, (d, w) -> {
                ServerPrefs.resetBaseUrl(requireContext());
                com.ai_photo.data.api.RetrofitClient.invalidate();
                Toast.makeText(getContext(),
                    "已恢复默认：" + ServerPrefs.getBaseUrl(requireContext()),
                    Toast.LENGTH_SHORT).show();
                retryLastOp();
            })
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.server_settings_save, null)
            .create();
        // 自定义 Positive 按钮：先校验 + 可选测试，再保存
        holder[0].setOnShowListener(d -> {
            android.widget.Button btnSave = holder[0].getButton(android.app.AlertDialog.BUTTON_POSITIVE);
            android.widget.Button btnTest = holder[0].getButton(android.app.AlertDialog.BUTTON_NEUTRAL);
            // 测试按钮复用 Neutral（中性按钮的左侧位）
            // 重新映射：把 Neutral 当作「测试连接」
            btnTest.setText(R.string.server_settings_test);
            btnTest.setOnClickListener(view -> {
                String url = input.getText() != null ? input.getText().toString().trim() : "";
                if (url.isEmpty()) {
                    status.setText(R.string.server_settings_invalid);
                    return;
                }
                status.setText(R.string.server_settings_testing);
                testConnection(url, status);
            });
            btnSave.setOnClickListener(view -> {
                String url = input.getText() != null ? input.getText().toString().trim() : "";
                if (url.isEmpty()) {
                    status.setText(R.string.server_settings_invalid);
                    return;
                }
                boolean ok = ServerPrefs.setBaseUrl(requireContext(), url);
                if (!ok) {
                    status.setText(R.string.server_settings_invalid);
                    return;
                }
                com.ai_photo.data.api.RetrofitClient.invalidate();
                holder[0].dismiss();
                Toast.makeText(getContext(),
                    "已保存：" + ServerPrefs.getBaseUrl(requireContext()),
                    Toast.LENGTH_SHORT).show();
                retryLastOp();
            });
        });
        holder[0].show();
    }

    private void testConnection(String url, android.widget.TextView statusView) {
        String normalized = url.trim();
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            normalized = "http://" + normalized;
        }
        if (!normalized.endsWith("/")) normalized = normalized + "/";
        final String probe = normalized + "health";
        final long t0 = System.currentTimeMillis();
        BgExecutor.execute(() -> {
            final String[] resultHolder = new String[1];
            final int[] elapsedHolder = new int[1];
            try {
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection)
                    new java.net.URL(probe).openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setRequestMethod("GET");
                int code = conn.getResponseCode();
                long t1 = System.currentTimeMillis();
                elapsedHolder[0] = (int) (t1 - t0);
                conn.disconnect();
                resultHolder[0] = code >= 200 && code < 500
                        ? getString(R.string.server_settings_test_ok, elapsedHolder[0])
                        : getString(R.string.server_settings_test_fail, "HTTP " + code);
            } catch (Exception e) {
                long t1 = System.currentTimeMillis();
                elapsedHolder[0] = (int) (t1 - t0);
                resultHolder[0] = getString(R.string.server_settings_test_fail,
                    e.getClass().getSimpleName() + ": " + (e.getMessage() != null ? e.getMessage() : ""));
            }
            final String fResult = resultHolder[0];
            android.app.Activity a = getActivity();
            if (a == null || a.isDestroyed()) return;
            a.runOnUiThread(() -> {
                if (statusView != null) statusView.setText(fResult);
            });
        });
    }

    /** URL 改完后，重新触发当前需要做的操作。 */
    private void retryLastOp() {
        // 重新拉详情
        loadDetail();
    }

    private String readCurrentText() {
        return editor.getVisibility() == View.VISIBLE
                ? editor.getText().toString()
                : bodyView.getText().toString();
    }

    private static Bundle makeNavArgs(long noteId) {
        Bundle b = new Bundle();
        b.putLong("noteId", noteId);
        return b;
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
            // 点缩略图 → 全屏预览（原图优先，没有再降级缩略图）
            String previewUrl = e.remoteUrl != null && !e.remoteUrl.isEmpty()
                ? e.remoteUrl : url;
            h.itemView.setOnClickListener(v -> {
                android.content.Context ctx = v.getContext();
                android.content.Intent intent = new android.content.Intent(ctx,
                    com.ai_photo.ui.common.PhotoPreviewActivity.class);
                intent.putExtra(com.ai_photo.ui.common.PhotoPreviewActivity.EXTRA_URL, previewUrl);
                ctx.startActivity(intent);
            });
        }
        @Override public int getItemCount() { return items.size(); }
        static class VH extends RecyclerView.ViewHolder {
            VH(View v) { super(v); }
        }
    }
}