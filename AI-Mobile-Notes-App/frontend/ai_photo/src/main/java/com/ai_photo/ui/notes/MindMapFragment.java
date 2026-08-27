package com.ai_photo.ui.notes;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.ai_photo.R;
import com.ai_photo.data.local.NoteEntity;
import com.ai_photo.data.model.note_ai.MindmapNode;
import com.ai_photo.data.model.note_ai.MindmapResponse;
import com.ai_photo.data.repo.NoteAiRepo;
import com.ai_photo.data.repo.NoteRepo;
import com.ai_photo.util.BgExecutor;
import com.ai_photo.util.Result;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.List;

/** Renders the AI-generated mind map tree as indented bullet rows. */
public class MindMapFragment extends Fragment {
    private static final String ARG_NOTE_ID = "noteId";

    private long noteId;
    private NoteRepo noteRepo;
    private NoteAiRepo aiRepo;
    private View titleView, regenBtn, progress, emptyView;
    private LinearLayout treeContainer;
    private final Gson gson = new Gson();

    public static MindMapFragment newInstance(long noteId) {
        MindMapFragment f = new MindMapFragment();
        Bundle b = new Bundle();
        b.putLong(ARG_NOTE_ID, noteId);
        f.setArguments(b);
        return f;
    }

    @Override public void onCreate(@Nullable Bundle b) {
        super.onCreate(b);
        if (getArguments() != null) noteId = getArguments().getLong(ARG_NOTE_ID);
        noteRepo = new NoteRepo(requireContext());
        aiRepo = new NoteAiRepo();
    }

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup container, @Nullable Bundle b) {
        return inflater.inflate(R.layout.fragment_mind_map, container, false);
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle b) {
        super.onViewCreated(view, b);
        titleView = view.findViewById(R.id.mind_map_title);
        regenBtn = view.findViewById(R.id.mind_map_regen);
        progress = view.findViewById(R.id.mind_map_progress);
        emptyView = view.findViewById(R.id.mind_map_empty);
        treeContainer = view.findViewById(R.id.mind_map_tree);

        regenBtn.setOnClickListener(v -> generate(true));

        loadFromCache();
        if (treeContainer.getChildCount() == 0) generate(false);
    }

    /** Read the cached mindmap_json (if any) and render it instantly. */
    private void loadFromCache() {
        BgExecutor.execute(() -> {
            NoteEntity n = noteRepo.noteDao().byId(noteId);
            final String cached = n == null ? null : n.mindmapJson;
            final String title = n == null ? "" : (n.title == null ? "" : n.title);
            if (getActivity() == null || getActivity().isDestroyed()) return;
            getActivity().runOnUiThread(() -> {
                if (!TextUtils.isEmpty(title)) {
                    ((TextView) titleView).setText(title);
                }
                if (!TextUtils.isEmpty(cached)) {
                    MindmapNode root = parseTree(cached);
                    if (root != null) renderTree(root);
                }
            });
        });
    }

    private void generate(boolean force) {
        progress.setVisibility(View.VISIBLE);
        emptyView.setVisibility(View.GONE);
        BgExecutor.execute(() -> {
            Result<MindmapResponse> r = aiRepo.mindmap(noteId, 3);
            final MindmapNode tree;
            final String errMsg;
            if (r instanceof Result.Success) {
                MindmapResponse d = (MindmapResponse) ((Result.Success<?>) r).data;
                tree = d != null ? d.tree : null;
                if (tree != null) {
                    NoteEntity n = noteRepo.noteDao().byId(noteId);
                    if (n != null) {
                        n.mindmapJson = gson.toJson(tree);
                        noteRepo.noteDao().upsert(n);
                    }
                }
                errMsg = null;
            } else {
                tree = null;
                if (r instanceof Result.Error) errMsg = ((Result.Error<?>) r).message;
                else if (r instanceof Result.Network) errMsg = "网络错误";
                else errMsg = "未知错误";
            }
            if (getActivity() == null || getActivity().isDestroyed()) return;
            getActivity().runOnUiThread(() -> {
                progress.setVisibility(View.GONE);
                if (tree != null) {
                    renderTree(tree);
                } else if (force) {
                    Toast.makeText(getContext(), errMsg, Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    private MindmapNode parseTree(String json) {
        try {
            Type t = new TypeToken<MindmapNode>() {}.getType();
            return gson.fromJson(json, t);
        } catch (Exception e) {
            return null;
        }
    }

    private void renderTree(MindmapNode root) {
        treeContainer.removeAllViews();
        if (root == null) {
            emptyView.setVisibility(View.VISIBLE);
            return;
        }
        addNode(root, 0);
    }

    /** Recursive render: each child becomes a TextView indented by depth. */
    private void addNode(MindmapNode node, int depth) {
        if (node == null || TextUtils.isEmpty(node.label)) return;
        TextView tv = new TextView(getContext());
        int indentDp = depth * 16;
        tv.setPadding(indentDp, (depth == 0 ? 8 : 6), 8, 6);
        if (depth == 0) {
            tv.setTextSize(18f);
            tv.setTypeface(tv.getTypeface(), android.graphics.Typeface.BOLD);
            tv.setTextColor(0xFF1F2937);
        } else if (depth == 1) {
            tv.setTextSize(15f);
            tv.setTypeface(tv.getTypeface(), android.graphics.Typeface.BOLD);
            tv.setTextColor(0xFF2563EB);
        } else {
            tv.setTextSize(13f);
            tv.setTextColor(0xFF374151);
        }
        tv.setText((depth == 0 ? "" : "• ") + node.label);
        treeContainer.addView(tv);

        if (node.children != null) {
            for (MindmapNode child : node.children) addNode(child, depth + 1);
        }
    }
}