package com.ai_photo.ui.notes;

import android.net.Uri;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import com.ai_photo.R;
import com.ai_photo.data.local.NoteEntity;
import com.ai_photo.data.model.category.CategoryItem;
import com.ai_photo.data.model.category.CategoryListResponse;
import com.ai_photo.data.model.note.NoteCreateResponse;
import com.ai_photo.data.repo.CategoryRepo;
import com.ai_photo.data.repo.NoteRepo;
import com.ai_photo.util.BgExecutor;
import com.ai_photo.util.Result;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class CaptureFragment extends Fragment {
    private static final String CAMERA_DIR = "capture";

    private NoteRepo repo;
    private CategoryRepo catRepo;
    private Spinner categorySpinner;
    private EditText titleInput;
    private TextView pickHint;
    private final List<CategoryItem> categories = new ArrayList<>();
    private final List<Uri> selected = new ArrayList<>();
    private Uri pendingCameraUri;

    private final ActivityResultLauncher<String> pickImages =
        registerForActivityResult(new ActivityResultContracts.GetMultipleContents(),
            (List<Uri> uris) -> {
                if (uris != null && !uris.isEmpty()) {
                    selected.clear();
                    selected.addAll(uris);
                    updatePickHint();
                }
            });

    private final ActivityResultLauncher<Uri> takePhoto =
        registerForActivityResult(new ActivityResultContracts.TakePicture(),
            (Boolean success) -> {
                if (Boolean.TRUE.equals(success) && pendingCameraUri != null
                        && !selected.contains(pendingCameraUri)) {
                    selected.add(pendingCameraUri);
                    updatePickHint();
                }
                pendingCameraUri = null;
            });

    @Override public void onCreate(@Nullable Bundle b) {
        super.onCreate(b);
        repo = new NoteRepo(requireContext());
        catRepo = new CategoryRepo();
    }

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup container, @Nullable Bundle b) {
        return inflater.inflate(R.layout.fragment_capture, container, false);
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle b) {
        super.onViewCreated(view, b);
        categorySpinner = view.findViewById(R.id.capture_category_spinner);
        titleInput = view.findViewById(R.id.capture_title);
        pickHint = view.findViewById(R.id.capture_pick_hint);
        view.findViewById(R.id.capture_btn_pick).setOnClickListener(v -> pickImages.launch("image/*"));
        view.findViewById(R.id.capture_btn_camera).setOnClickListener(v -> launchCamera());
        view.findViewById(R.id.capture_btn_save).setOnClickListener(v -> doSave());
        view.findViewById(R.id.capture_btn_cancel).setOnClickListener(v ->
            requireActivity().getOnBackPressedDispatcher().onBackPressed());
        refreshCategories();
    }

    private void updatePickHint() {
        if (pickHint != null) {
            pickHint.setText(getString(R.string.capture_picked_fmt, selected.size()));
        }
    }

    private void launchCamera() {
        try {
            File dir = new File(requireContext().getCacheDir(), CAMERA_DIR);
            if (!dir.exists() && !dir.mkdirs()) {
                throw new IOException("无法创建拍照目录");
            }
            File out = new File(dir, "cap_" + System.currentTimeMillis() + ".jpg");
            pendingCameraUri = FileProvider.getUriForFile(
                requireContext(),
                requireContext().getPackageName() + ".fileprovider",
                out);
            takePhoto.launch(pendingCameraUri);
        } catch (Exception e) {
            pendingCameraUri = null;
            Toast.makeText(getContext(),
                getString(R.string.capture_camera_fail, e.getMessage()),
                Toast.LENGTH_SHORT).show();
        }
    }

    @SuppressWarnings("unchecked")
    private void refreshCategories() {
        BgExecutor.execute(() -> {
            Result<?> r = catRepo.list();
            if (r instanceof Result.Success) {
                CategoryListResponse data = (CategoryListResponse) ((Result.Success<?>) r).data;
                categories.clear();
                if (data != null && data.list != null) {
                    categories.addAll(data.list);
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
        for (CategoryItem c : categories) labels.add(c.name);
        ArrayAdapter<String> sa = new ArrayAdapter<>(getContext(),
            android.R.layout.simple_spinner_item, labels);
        sa.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        categorySpinner.setAdapter(sa);
    }

    @SuppressWarnings("unchecked")
    private void doSave() {
        String title = titleInput.getText().toString().trim();
        if (title.isEmpty()) {
            Toast.makeText(getContext(), R.string.capture_title_required, Toast.LENGTH_SHORT).show();
            return;
        }
        final List<Integer> selectedIds = new ArrayList<>();
        int pos = categorySpinner.getSelectedItemPosition();
        if (pos > 0 && pos - 1 < categories.size()) {
            selectedIds.add((int) categories.get(pos - 1).categoryId);
        }
        BgExecutor.execute(() -> {
            Result<?> cr = repo.createNote(title, "", selectedIds);
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
            NoteEntity ne = new NoteEntity();
            ne.noteId = finalNoteId;
            ne.categoriesCsv = joinIds(selectedIds);
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
                    requireActivity().getOnBackPressedDispatcher().onBackPressed();
                });
            }
        });
    }

    private static String joinIds(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(ids.get(i));
        }
        return sb.toString();
    }
}