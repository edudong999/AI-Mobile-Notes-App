package com.ai_photo.ui.folders;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.ai_photo.R;
import com.ai_photo.data.model.note.*;
import com.ai_photo.data.repo.NoteRepo;
import com.ai_photo.util.BgExecutor;
import com.ai_photo.util.Result;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import java.util.ArrayList;
import java.util.List;

public class FolderManageFragment extends Fragment {
    private NoteRepo repo;
    private RecyclerView recycler;
    private FolderAdapter adapter;
    private final List<FolderItem> folders = new ArrayList<>();

    @Override public void onCreate(@Nullable Bundle b) {
        super.onCreate(b);
        repo = new NoteRepo(requireContext());
    }

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup container, @Nullable Bundle b) {
        return inflater.inflate(R.layout.fragment_folder_manage, container, false);
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle b) {
        super.onViewCreated(view, b);
        recycler = view.findViewById(R.id.folder_recycler);
        FloatingActionButton fab = view.findViewById(R.id.folder_fab);
        adapter = new FolderAdapter(
            item -> showEditDialog(item, true),
            item -> confirmDelete(item));
        recycler.setLayoutManager(new LinearLayoutManager(getContext()));
        recycler.setAdapter(adapter);
        fab.setOnClickListener(v -> showEditDialog(null, false));
        refresh();
    }

    @Override public void onResume() { super.onResume(); refresh(); }

    @SuppressWarnings("unchecked")
    private void refresh() {
        BgExecutor.execute(() -> {
            Result<?> r = repo.listFolders();
            if (!(r instanceof Result.Success)) return;
            FolderListResponse data = (FolderListResponse) ((Result.Success<?>) r).data;
            folders.clear();
            if (data != null && data.list != null) folders.addAll(data.list);
            final android.app.Activity a = getActivity();
            if (a != null && !a.isDestroyed()) {
                a.runOnUiThread(() -> adapter.submit(folders));
            }
        });
    }

    private void showEditDialog(FolderItem existing, boolean isEdit) {
        View v = LayoutInflater.from(getContext()).inflate(R.layout.dialog_folder_edit, null, false);
        EditText nameInput = v.findViewById(R.id.folder_name);
        Spinner colorSpinner = v.findViewById(R.id.folder_color);
        final String[] colors = new String[]{"#4A90E2", "#E91E63", "#4CAF50", "#FF9800", "#9C27B0"};
        ArrayAdapter<String> ca = new ArrayAdapter<>(getContext(),
            android.R.layout.simple_spinner_item, colors);
        ca.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        colorSpinner.setAdapter(ca);
        if (isEdit && existing != null) {
            nameInput.setText(existing.name);
            for (int i = 0; i < colors.length; i++) {
                if (colors[i].equals(existing.color)) { colorSpinner.setSelection(i); break; }
            }
        }
        new AlertDialog.Builder(getContext())
            .setTitle(isEdit ? R.string.folder_edit_title : R.string.folder_create_title)
            .setView(v)
            .setPositiveButton(R.string.btn_save, (d, w) -> {
                String nm = nameInput.getText().toString().trim();
                if (TextUtils.isEmpty(nm)) return;
                String color = colors[colorSpinner.getSelectedItemPosition()];
                BgExecutor.execute(() -> {
                    if (isEdit && existing != null) {
                        repo.updateFolder(existing.folderId, nm, color, existing.sortIndex);
                    } else {
                        repo.createFolder(nm, color);
                    }
                    requireActivity().runOnUiThread(this::refresh);
                });
            })
            .setNegativeButton(R.string.btn_cancel, null)
            .show();
    }

    private void confirmDelete(FolderItem item) {
        new AlertDialog.Builder(getContext())
            .setTitle(R.string.folder_delete_title)
            .setMessage(getString(R.string.folder_delete_msg, item.name))
            .setPositiveButton(R.string.btn_delete, (d, w) -> {
                BgExecutor.execute(() -> {
                    repo.deleteFolder(item.folderId);
                    requireActivity().runOnUiThread(this::refresh);
                });
            })
            .setNegativeButton(R.string.btn_cancel, null)
            .show();
    }

    static class FolderAdapter extends RecyclerView.Adapter<FolderAdapter.VH> {
        interface OnEdit { void onRow(FolderItem item); }
        interface OnDelete { void onRow(FolderItem item); }
        private final List<FolderItem> items = new ArrayList<>();
        private final OnEdit onEdit; private final OnDelete onDelete;
        FolderAdapter(OnEdit e, OnDelete d) { this.onEdit = e; this.onDelete = d; }
        void submit(List<FolderItem> data) {
            items.clear(); if (data != null) items.addAll(data); notifyDataSetChanged();
        }
        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_folder, parent, false);
            return new VH(v);
        }
        @Override public void onBindViewHolder(@NonNull VH h, int pos) {
            FolderItem it = items.get(pos);
            h.name.setText(it.name != null ? it.name : "");
            h.color.setBackgroundColor(android.graphics.Color.parseColor(
                it.color != null ? it.color : "#4A90E2"));
            h.itemView.setOnClickListener(v -> onEdit.onRow(it));
            h.itemView.setOnLongClickListener(v -> { onDelete.onRow(it); return true; });
        }
        @Override public int getItemCount() { return items.size(); }
        static class VH extends RecyclerView.ViewHolder {
            View color; TextView name;
            VH(View v) { super(v); color = v.findViewById(R.id.folder_color_chip); name = v.findViewById(R.id.folder_name); }
        }
    }
}
