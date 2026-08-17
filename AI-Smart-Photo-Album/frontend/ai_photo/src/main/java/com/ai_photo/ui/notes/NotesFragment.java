package com.ai_photo.ui.notes;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.ai_photo.R;
import com.ai_photo.data.local.FolderEntity;
import com.ai_photo.data.local.NoteEntity;
import com.ai_photo.data.model.note.FolderListResponse;
import com.ai_photo.data.model.note.FolderItem;
import com.ai_photo.data.model.note.NoteListResponse;
import com.ai_photo.data.model.note.NoteListItem;
import com.ai_photo.data.repo.NoteRepo;
import com.ai_photo.util.BgExecutor;
import com.ai_photo.util.Result;
import java.util.ArrayList;
import java.util.List;

public class NotesFragment extends Fragment {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private NoteRepo repo;
    private Spinner folderSpinner;
    private RecyclerView recycler;
    private SwipeRefreshLayout swipe;
    private TextView empty;
    private NotesAdapter adapter;
    private final List<FolderEntity> folders = new ArrayList<>();
    private Long selectedFolderId = null;  // null = "全部"
    private Runnable poller;

    private final ActivityResultLauncher<String> pickNoteImage =
        registerForActivityResult(new ActivityResultContracts.GetMultipleContents(),
            (List<android.net.Uri> uris) -> {
                if (uris != null && !uris.isEmpty()) {
                    android.widget.Toast.makeText(getContext(),
                        R.string.notes_capture_queued, android.widget.Toast.LENGTH_SHORT).show();
                }
            });

    @Override public void onCreate(@Nullable Bundle b) {
        super.onCreate(b);
        repo = new NoteRepo(requireContext());
    }

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup container, @Nullable Bundle b) {
        return inflater.inflate(R.layout.fragment_notes, container, false);
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle b) {
        super.onViewCreated(view, b);
        folderSpinner = view.findViewById(R.id.folder_spinner);
        recycler = view.findViewById(R.id.recycler);
        swipe = view.findViewById(R.id.swipe);
        empty = view.findViewById(R.id.empty);
        com.google.android.material.floatingactionbutton.FloatingActionButton fab =
            view.findViewById(R.id.fab_new);

        adapter = new NotesAdapter(item ->
            android.widget.Toast.makeText(getContext(),
                getString(R.string.notes_open_fmt, item.noteId),
                android.widget.Toast.LENGTH_SHORT).show());
        recycler.setLayoutManager(new LinearLayoutManager(getContext()));
        recycler.setAdapter(adapter);

        folderSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                if (pos == 0) selectedFolderId = null;
                else if (pos - 1 < folders.size()) selectedFolderId = folders.get(pos - 1).folderId;
                refresh();
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });

        swipe.setOnRefreshListener(this::refresh);
        fab.setOnClickListener(v -> pickNoteImage.launch("image/*"));

        refresh();
    }

    @Override public void onResume() {
        super.onResume();
        pollFoldersFromServer();
        poller = new Runnable() {
            @Override public void run() {
                refresh();
                handler.postDelayed(this, 5000);
            }
        };
        handler.postDelayed(poller, 5000);
    }

    @Override public void onPause() {
        super.onPause();
        if (poller != null) handler.removeCallbacks(poller);
    }

    @Override public void onDestroyView() {
        super.onDestroyView();
        if (poller != null) handler.removeCallbacks(poller);
    }

    private void refresh() {
        BgExecutor.execute(() -> {
            List<NoteEntity> list = repo.noteDao().byFolder(selectedFolderId, 200);
            final android.app.Activity a = getActivity();
            if (a == null || a.isDestroyed()) return;
            a.runOnUiThread(() -> {
                if (getView() == null) return;
                swipe.setRefreshing(false);
                adapter.submit(list);
                empty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
            });
        });
    }

    @SuppressWarnings("unchecked")
    private void pollFoldersFromServer() {
        BgExecutor.execute(() -> {
            Result<?> r = repo.listFolders();
            if (r instanceof Result.Success) {
                FolderListResponse data = (FolderListResponse) ((Result.Success<?>) r).data;
                if (data != null) {
                    folders.clear();
                    for (FolderItem it : data.list) {
                        FolderEntity fe = new FolderEntity();
                        fe.folderId = it.folderId;
                        fe.name = it.name;
                        fe.color = it.color != null ? it.color : "#4A90E2";
                        fe.sortIndex = it.sortIndex;
                        folders.add(fe);
                    }
                    final android.app.Activity a = getActivity();
                    if (a != null && !a.isDestroyed()) {
                        a.runOnUiThread(() -> rebuildSpinner());
                    }
                }
            }
        });
        // also refresh notes list from network
        BgExecutor.execute(() -> {
            Result<?> nr = repo.listNotes(selectedFolderId, 1, 50);
            if (nr instanceof Result.Success) {
                NoteListResponse data = (NoteListResponse) ((Result.Success<?>) nr).data;
                if (data != null) {
                    for (NoteListItem it : data.list) {
                        NoteEntity ne = new NoteEntity();
                        ne.noteId = it.noteId;
                        ne.folderId = it.folderId;
                        ne.title = it.title != null ? it.title : "";
                        ne.summary = it.summary != null ? it.summary : "";
                        ne.aiStatus = it.aiStatus != null ? it.aiStatus : "pending";
                        ne.updatedAt = parseDate(it.updatedAt);
                        repo.noteDao().upsert(ne);
                    }
                }
            }
        });
    }

    private void rebuildSpinner() {
        List<String> labels = new ArrayList<>();
        labels.add(getString(R.string.notes_filter_all));
        for (FolderEntity f : folders) labels.add(f.name);
        ArrayAdapter<String> sa = new ArrayAdapter<>(getContext(),
            android.R.layout.simple_spinner_item, labels);
        sa.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        folderSpinner.setAdapter(sa);
    }

    private static long parseDate(String iso) {
        if (iso == null || iso.isEmpty()) return 0;
        try { return java.time.Instant.parse(iso).toEpochMilli(); }
        catch (Exception e) { return 0L; }
    }
}
