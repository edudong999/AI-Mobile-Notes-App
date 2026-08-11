package com.ai_photo.ui.categories;

import android.os.Bundle;
import android.view.*;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.ai_photo.R;
import com.ai_photo.data.model.category.CategoryPhotosResponse;
import com.ai_photo.data.model.photo.PhotoListItem;
import com.ai_photo.data.repo.CategoryRepo;
import com.ai_photo.ui.photos.PhotoAdapter;
import com.ai_photo.util.Result;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class CategoryPhotosFragment extends Fragment {
    private CategoryRepo repo = new CategoryRepo();
    private ExecutorService exec = Executors.newSingleThreadExecutor();
    private long categoryId;
    private String categoryName;

    @Override public void onCreate(@Nullable Bundle b) {
        super.onCreate(b);
        if (getArguments() != null) {
            categoryId = getArguments().getLong("categoryId");
            categoryName = getArguments().getString("categoryName", "");
        }
    }

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup container, @Nullable Bundle b) {
        return inflater.inflate(R.layout.fragment_category_photos, container, false);
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle b) {
        super.onViewCreated(view, b);
        requireActivity().setTitle(categoryName);
        RecyclerView recycler = view.findViewById(R.id.recycler);
        PhotoAdapter adapter = new PhotoAdapter(
            item -> {
                Bundle args = new Bundle();
                args.putLong("photoId", item.photoId);
                androidx.navigation.fragment.NavHostFragment.findNavController(this)
                    .navigate(R.id.action_to_detail, args);
            },
            item -> {}
        );
        recycler.setLayoutManager(new GridLayoutManager(getContext(), 3));
        recycler.setAdapter(adapter);

        exec.execute(() -> {
            Result<?> r = repo.photos(categoryId, 1, 60);
            final android.app.Activity a = getActivity();
            if (a == null) return;
            a.runOnUiThread(() -> {
                if (r instanceof Result.Success) {
                    CategoryPhotosResponse data = (CategoryPhotosResponse) ((Result.Success<?>) r).data;
                    List<PhotoListItem> items = data.list.stream().map(p -> {
                        PhotoListItem it = new PhotoListItem();
                        it.photoId = p.photoId;
                        it.thumbnailUrl = p.thumbnailUrl;
                        it.createdAt = p.createdAt;
                        return it;
                    }).collect(Collectors.toList());
                    adapter.submit(items);
                } else if (r instanceof Result.Error) {
                    Toast.makeText(getContext(), ((Result.Error<?>) r).message, Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    @Override public void onDestroyView() {
        super.onDestroyView();
        exec.shutdown();
    }
}