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
import com.ai_photo.util.Result;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AdminCategoryFragment extends Fragment {
    private AdminRepo repo = new AdminRepo();
    private ExecutorService exec = Executors.newSingleThreadExecutor();
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
        exec.shutdown();
    }

    private void load() {
        exec.execute(() -> {
            Result<?> r = repo.list(currentType);
            final android.app.Activity a = getActivity();
            if (a == null) return;
            a.runOnUiThread(() -> {
                if (r instanceof Result.Success) {
                    adapter.submit(((com.ai_photo.data.model.admin.AdminCategoryListResponse)
                        ((Result.Success<?>) r).data).list);
                }
            });
        });
    }

    private void showCreateDialog() {
        final EditText input = new EditText(getContext());
        input.setHint("category name");
        new AlertDialog.Builder(getContext())
            .setTitle("Create " + currentType)
            .setView(input)
            .setPositiveButton("Create", (d, w) -> {
                String name = input.getText().toString().trim();
                if (name.isEmpty()) return;
                exec.execute(() -> {
                    Result<?> r = repo.create(currentType, name, null);
                    final android.app.Activity a = getActivity();
                    if (a == null) return;
                    a.runOnUiThread(() -> {
                        if (r instanceof Result.Success) load();
                        else if (r instanceof Result.Error) toast(((Result.Error<?>) r).message);
                    });
                });
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void showEditDialog(AdminCategoryItem item) {
        final EditText input = new EditText(getContext());
        input.setText(item.name);
        new AlertDialog.Builder(getContext())
            .setTitle("Rename")
            .setView(input)
            .setPositiveButton("Save", (d, w) -> {
                String name = input.getText().toString().trim();
                if (name.isEmpty()) return;
                exec.execute(() -> {
                    Result<?> r = repo.update(item.categoryId, name, null);
                    final android.app.Activity a = getActivity();
                    if (a == null) return;
                    a.runOnUiThread(() -> {
                        if (r instanceof Result.Success) load();
                        else if (r instanceof Result.Error) toast(((Result.Error<?>) r).message);
                    });
                });
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void confirmDelete(AdminCategoryItem item) {
        new AlertDialog.Builder(getContext())
            .setTitle("Delete " + item.name + "?")
            .setPositiveButton("Delete", (d, w) -> exec.execute(() -> {
                Result<?> r = repo.delete(item.categoryId);
                final android.app.Activity a = getActivity();
                if (a == null) return;
                a.runOnUiThread(() -> {
                    if (r instanceof Result.Success) load();
                    else if (r instanceof Result.Error) toast(((Result.Error<?>) r).message);
                });
            }))
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void confirmReset() {
        new AlertDialog.Builder(getContext())
            .setTitle("Reset all categories?")
            .setMessage("Removes all custom categories and re-seeds the 60 defaults.")
            .setPositiveButton("Reset", (d, w) -> exec.execute(() -> {
                Result<?> r = repo.reset();
                final android.app.Activity a = getActivity();
                if (a == null) return;
                a.runOnUiThread(() -> {
                    if (r instanceof Result.Success) load();
                    else if (r instanceof Result.Error) toast(((Result.Error<?>) r).message);
                });
            }))
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void toast(String msg) {
        Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
    }
}
