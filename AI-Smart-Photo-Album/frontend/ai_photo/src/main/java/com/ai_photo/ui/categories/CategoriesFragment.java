package com.ai_photo.ui.categories;

import android.os.Bundle;
import android.view.*;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.ai_photo.R;
import com.ai_photo.data.model.category.CategoryListResponse;
import com.ai_photo.data.repo.CategoryRepo;
import com.ai_photo.util.Result;
import com.google.android.material.tabs.TabLayout;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CategoriesFragment extends Fragment {
    private CategoryRepo repo = new CategoryRepo();
    private ExecutorService exec = Executors.newSingleThreadExecutor();
    private CategoryAdapter adapter;
    private static final String[] TYPES = {"scene", "emotion", "tag"};

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup container, @Nullable Bundle b) {
        return inflater.inflate(R.layout.fragment_categories, container, false);
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle b) {
        super.onViewCreated(view, b);
        RecyclerView recycler = view.findViewById(R.id.recycler);
        TabLayout tabs = view.findViewById(R.id.tabs);
        adapter = new CategoryAdapter(item -> {
            Bundle args = new Bundle();
            args.putLong("categoryId", item.categoryId);
            args.putString("categoryName", item.categoryName);
            NavHostFragment.findNavController(this).navigate(R.id.action_to_category_photos, args);
        });
        recycler.setLayoutManager(new LinearLayoutManager(getContext()));
        recycler.setAdapter(adapter);
        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) { load(tab.getPosition()); }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });
        load(0);
    }

    private void load(int idx) {
        String type = TYPES[idx];
        exec.execute(() -> {
            Result<?> r = repo.list(type);
            getActivity().runOnUiThread(() -> {
                if (r instanceof Result.Success) {
                    CategoryListResponse data = (CategoryListResponse) ((Result.Success<?>) r).data;
                    adapter.submit(data.list);
                } else if (r instanceof Result.Error) {
                    Toast.makeText(getContext(), ((Result.Error<?>) r).message, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(getContext(), R.string.msg_network_err, Toast.LENGTH_SHORT).show();
                }
            });
        });
    }
}