package com.ai_photo.ui.admin;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.ai_photo.R;
import com.ai_photo.data.model.admin.AdminCategoryItem;
import com.ai_photo.data.repo.AdminRepo;
import com.ai_photo.util.BgExecutor;
import com.ai_photo.util.Result;

public class AdminCategoryFragment extends Fragment {
    private AdminRepo repo = new AdminRepo();
    private AdminCategoryAdapter adapter;
    private String currentType = "tag";
    private static final String[] TYPES = {"scene", "emotion", "tag"};

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup container, @Nullable Bundle b) {
        return inflater.inflate(R.layout.fragment_admin_category, container, false);
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle b) {
        super.onViewCreated(view, b);
        Spinner typeSpinner = view.findViewById(R.id.type_spinner);
        typeSpinner.setAdapter(new ArrayAdapter<>(getContext(),
            android.R.layout.simple_spinner_item, TYPES));
        typeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                currentType = TYPES[pos];
                load();
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });

        adapter = new AdminCategoryAdapter(this::showEditDialog, this::confirmDelete);
        RecyclerView recycler = view.findViewById(R.id.recycler);
        recycler.setLayoutManager(new LinearLayoutManager(getContext()));
        recycler.setAdapter(adapter);

        view.findViewById(R.id.btn_create).setOnClickListener(v -> showCreateDialog());
        view.findViewById(R.id.btn_reset).setOnClickListener(v -> confirmReset());

        load();
    }

    @Override public void onDestroyView() {
        super.onDestroyView();
    }

    private void load() {
        BgExecutor.execute(() -> {
            Result<?> r = repo.list(currentType);
            final android.app.Activity a = getActivity();
            if (a == null || a.isDestroyed()) return;
            a.runOnUiThread(() -> {
                if (getView() == null) return;
                if (r instanceof Result.Success) {
                    com.ai_photo.data.model.admin.AdminCategoryListResponse data =
                        (com.ai_photo.data.model.admin.AdminCategoryListResponse) ((Result.Success<?>) r).data;
                    adapter.submit(data != null ? data.list : null);
                }
            });
        });
    }

    private void showCreateDialog() {
        final EditText input = new EditText(getContext());
        input.setHint(R.string.admin_dialog_create_hint);
        new AlertDialog.Builder(getContext())
            .setTitle(getString(R.string.admin_dialog_create_title, currentType))
            .setView(input)
            .setPositiveButton(R.string.admin_btn_create, (d, w) -> {
                String name = input.getText().toString().trim();
                if (name.isEmpty()) return;
                BgExecutor.execute(() -> {
                    Result<?> r = repo.create(currentType, name, null);
                    final android.app.Activity a = getActivity();
                    if (a == null || a.isDestroyed()) return;
                    a.runOnUiThread(() -> {
                        if (getView() == null) return;
                        if (r instanceof Result.Success) load();
                        else if (r instanceof Result.Error) toast(((Result.Error<?>) r).message);
                    });
                });
            })
            .setNegativeButton(R.string.admin_btn_cancel, null)
            .show();
    }

    private void showEditDialog(AdminCategoryItem item) {
        final EditText input = new EditText(getContext());
        input.setText(item.name);
        new AlertDialog.Builder(getContext())
            .setTitle(R.string.admin_dialog_rename_title)
            .setView(input)
            .setPositiveButton(R.string.admin_btn_save, (d, w) -> {
                String name = input.getText().toString().trim();
                if (name.isEmpty()) return;
                BgExecutor.execute(() -> {
                    Result<?> r = repo.update(item.categoryId, name, null);
                    final android.app.Activity a = getActivity();
                    if (a == null || a.isDestroyed()) return;
                    a.runOnUiThread(() -> {
                        if (getView() == null) return;
                        if (r instanceof Result.Success) load();
                        else if (r instanceof Result.Error) toast(((Result.Error<?>) r).message);
                    });
                });
            })
            .setNegativeButton(R.string.admin_btn_cancel, null)
            .show();
    }

    private void confirmDelete(AdminCategoryItem item) {
        new AlertDialog.Builder(getContext())
            .setTitle(getString(R.string.admin_dialog_delete_title, item.name))
            .setPositiveButton(R.string.admin_btn_delete, (d, w) -> BgExecutor.execute(() -> {
                Result<?> r = repo.delete(item.categoryId);
                final android.app.Activity a = getActivity();
                if (a == null || a.isDestroyed()) return;
                a.runOnUiThread(() -> {
                    if (getView() == null) return;
                    if (r instanceof Result.Success) load();
                    else if (r instanceof Result.Error) toast(((Result.Error<?>) r).message);
                });
            }))
            .setNegativeButton(R.string.admin_btn_cancel, null)
            .show();
    }

    private void confirmReset() {
        new AlertDialog.Builder(getContext())
            .setTitle(R.string.admin_dialog_reset_title)
            .setMessage(R.string.admin_dialog_reset_message)
            .setPositiveButton(R.string.admin_btn_reset, (d, w) -> BgExecutor.execute(() -> {
                Result<?> r = repo.reset();
                final android.app.Activity a = getActivity();
                if (a == null || a.isDestroyed()) return;
                a.runOnUiThread(() -> {
                    if (getView() == null) return;
                    if (r instanceof Result.Success) load();
                    else if (r instanceof Result.Error) toast(((Result.Error<?>) r).message);
                });
            }))
            .setNegativeButton(R.string.admin_btn_cancel, null)
            .show();
    }

    private void toast(String msg) {
        android.content.Context ctx = getContext();
        if (ctx != null && msg != null) Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show();
    }
}