package com.ai_photo.ui.notes;

import android.net.Uri;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.ai_photo.R;
import com.ai_photo.data.local.FolderEntity;
import com.ai_photo.data.local.NoteEntity;
import com.ai_photo.data.model.note.*;
import com.ai_photo.data.repo.NoteRepo;
import com.ai_photo.util.BgExecutor;
import com.ai_photo.util.Result;
import java.util.ArrayList;
import java.util.List;

public class CaptureFragment extends Fragment {
    private NoteRepo repo;
    private Spinner folderSpinner;
    private EditText titleInput;
    private TextView pickHint;
    private final List<FolderEntity> folders = new ArrayList<>();
    private final List<Uri> selected = new ArrayList<>();

    private final ActivityResultLauncher<String> pickImages =
        registerForActivityResult(new ActivityResultContracts.GetMultipleContents(),
            (List<Uri> uris) -> {
                if (uris != null && !uris.isEmpty()) {
                    selected.clear();
                    selected.addAll(uris);
                    if (getView() != null) {
                        ((TextView) getView().findViewById(R.id.capture_pick_hint))
                            .setText(getString(R.string.capture_picked_fmt, selected.size()));
                    }
                }
            });

    private final ActivityResultLauncher<Uri> takePhoto =
        registerForActivityResult(new ActivityResultContracts.TakePicture(),
            (Boolean success) -> { /* no-op; Future: persist to file */ });

    @Override public void onCreate(@Nullable Bundle b) {
        super.onCreate(b);
        repo = new NoteRepo(requireContext());
    }

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup container, @Nullable Bundle b) {
        return inflater.inflate(R.layout.fragment_capture, container, false);
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle b) {
        super.onViewCreated(view, b);
        folderSpinner = view.findViewById(R.id.capture_folder_spinner);
        titleInput = view.findViewById(R.id.capture_title);
        pickHint = view.findViewById(R.id.capture_pick_hint);
        view.findViewById(R.id.capture_btn_pick).setOnClickListener(v -> pickImages.launch("image/*"));
        view.findViewById(R.id.capture_btn_camera).setOnClickListener(v -> {
            // simple "would take a photo" placeholder - needs FileProvider URI plumbing (Task 20)
            Toast.makeText(getContext(), R.string.capture_take_photo, Toast.LENGTH_SHORT).show();
        });
        view.findViewById(R.id.capture_btn_save).setOnClickListener(v -> doSave());
        view.findViewById(R.id.capture_btn_cancel).setOnClickListener(v ->
            requireActivity().onBackPressed());
        refreshFolders();
    }

    @SuppressWarnings("unchecked")
    private void refreshFolders() {
        BgExecutor.execute(() -> {
            Result<?> r = repo.listFolders();
            if (r instanceof Result.Success) {
                FolderListResponse data = (FolderListResponse) ((Result.Success<?>) r).data;
                folders.clear();
                if (data != null && data.list != null) {
                    for (FolderItem it : data.list) {
                        FolderEntity fe = new FolderEntity();
                        fe.folderId = it.folderId;
                        fe.name = it.name != null ? it.name : "";
                        fe.color = it.color != null ? it.color : "#4A90E2";
                        fe.sortIndex = it.sortIndex;
                        folders.add(fe);
                        repo.folderDao().upsert(fe);
                    }
                }
                final android.app.Activity a = getActivity();
                if (a != null && !a.isDestroyed()) {
                    a.runOnUiThread(() -> rebuildSpinner());
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

    @SuppressWarnings("unchecked")
    private void doSave() {
        String title = titleInput.getText().toString().trim();
        if (title.isEmpty()) {
            Toast.makeText(getContext(), R.string.capture_title_required, Toast.LENGTH_SHORT).show();
            return;
        }
        Long folderId = null;
        int pos = folderSpinner.getSelectedItemPosition();
        if (pos > 0 && pos - 1 < folders.size()) {
            folderId = folders.get(pos - 1).folderId;
        }
        final Long finalFolderId = folderId;
        BgExecutor.execute(() -> {
            // 1) create note
            Result<?> cr = repo.createNote(finalFolderId, title, "");
            if (!(cr instanceof Result.Success)) {
                String msg = cr instanceof Result.Error
                    ? ((Result.Error<?>) cr).message : getString(R.string.msg_network_err);
                final android.app.Activity a1 = getActivity();
                if (a1 != null && !a1.isDestroyed()) {
                    a1.runOnUiThread(() -> Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show());
                }
                return;
            }
            NoteCreateResponse nc = (NoteCreateResponse) ((Result.Success<?>) cr).data;
            long noteId = nc.noteId;
            // 2) upload files if any
            String uploadMsg;
            if (selected.isEmpty()) {
                uploadMsg = getString(R.string.msg_saved);
            } else {
                Result<?> ur = repo.uploadNoteFiles(getContext(), noteId, selected);
                if (ur instanceof Result.Success) {
                    uploadMsg = getString(R.string.msg_saved) + " + " + selected.size() + " imgs";
                } else {
                    uploadMsg = getString(R.string.msg_saved) + " (upload failed)";
                }
            }
            final long finalNoteId = noteId;
            final String finalMsg = uploadMsg;
            // 3) persist to room
            NoteEntity ne = new NoteEntity();
            ne.noteId = finalNoteId;
            ne.folderId = finalFolderId;
            ne.title = title;
            ne.textContent = "";
            ne.aiStatus = "pending";
            ne.updatedAt = System.currentTimeMillis();
            ne.createdAt = System.currentTimeMillis();
            ne.dirty = false;
            repo.noteDao().upsert(ne);
            final android.app.Activity a = getActivity();
            if (a != null && !a.isDestroyed()) {
                a.runOnUiThread(() -> {
                    Toast.makeText(getContext(), finalMsg, Toast.LENGTH_SHORT).show();
                    requireActivity().onBackPressed();
                });
            }
        });
    }
}
