package com.ai_photo.ui.me;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ai_photo.R;
import com.ai_photo.data.model.category.CategoryItem;
import com.ai_photo.data.model.category.CategoryListResponse;
import com.ai_photo.data.model.category.CategoryCreateResponse;
import com.ai_photo.data.repo.CategoryRepo;
import com.ai_photo.util.BgExecutor;
import com.ai_photo.util.Result;

import java.util.ArrayList;
import java.util.List;

/** 分类管理：列表 + 新建 + 跳到按分类筛选的笔记页。 */
public class CategoriesFragment extends Fragment {

    private final List<CategoryItem> data = new ArrayList<>();
    private CategoryAdapter adapter;
    private CategoryRepo repo;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_categories, container, false);
    }

    @Override public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);
        repo = new CategoryRepo();
        RecyclerView rv = v.findViewById(R.id.categories_list);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new CategoryAdapter(data, this::onClick);
        rv.setAdapter(adapter);
        v.findViewById(R.id.categories_fab).setOnClickListener(x -> showCreateDialog());
    }

    @Override public void onResume() {
        super.onResume();
        // 从 NoteDetail 编辑分类后回到此页时刷新 noteCount。
        if (repo != null) load();
    }

    private void load() {
        BgExecutor.execute(() -> {
            Result<?> r = repo.list();
            if (!(r instanceof Result.Success)) return;
            CategoryListResponse resp = (CategoryListResponse) ((Result.Success<?>) r).data;
            if (resp == null || resp.list == null) return;
            final List<CategoryItem> list = new ArrayList<>(resp.list);
            final android.app.Activity a = getActivity();
            if (a == null || a.isDestroyed()) return;
            a.runOnUiThread(() -> {
                if (getView() == null) return;
                data.clear();
                data.addAll(list);
                adapter.notifyDataSetChanged();
            });
        });
    }

    private void showCreateDialog() {
        android.widget.EditText input = new android.widget.EditText(requireContext());
        input.setHint(R.string.category_name_hint);
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.category_create_title)
            .setView(input)
            .setPositiveButton(R.string.btn_save, (d, w) -> {
                String name = input.getText().toString().trim();
                if (name.isEmpty()) return;
                BgExecutor.execute(() -> {
                    Result<?> r = repo.create(name, null);
                    final android.app.Activity a = getActivity();
                    if (a != null && !a.isDestroyed()) {
                        a.runOnUiThread(() -> {
                            if (getView() == null) return;
                            if (r instanceof Result.Success) {
                                load();
                            }
                        });
                    }
                });
            })
            .setNegativeButton(R.string.btn_cancel, null)
            .show();
    }

    private void onClick(CategoryItem item) {
        android.os.Bundle b = new android.os.Bundle();
        b.putLong("categoryId", item.categoryId);
        NavHostFragment.findNavController(this)
            .navigate(R.id.notesFragment, b);
    }
}