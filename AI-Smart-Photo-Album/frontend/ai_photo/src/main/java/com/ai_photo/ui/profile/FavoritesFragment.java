package com.ai_photo.ui.profile;

import android.os.Bundle;
import android.view.*;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.ai_photo.R;
import com.ai_photo.data.api.RetrofitClient;
import com.ai_photo.data.model.photo.FavoriteResponse;
import com.ai_photo.data.model.photo.PhotoListItem;
import com.ai_photo.ui.photos.PhotoAdapter;
import com.ai_photo.util.Result;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class FavoritesFragment extends Fragment {
    private ExecutorService exec = Executors.newSingleThreadExecutor();

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup container, @Nullable Bundle b) {
        return inflater.inflate(R.layout.fragment_favorites, container, false);
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle b) {
        super.onViewCreated(view, b);
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
            Result<?> r = RetrofitClient.exec(RetrofitClient.api().favorites(1, 60));
            getActivity().runOnUiThread(() -> {
                if (r instanceof Result.Success) {
                    FavoriteResponse data = (FavoriteResponse) ((Result.Success<?>) r).data;
                    List<PhotoListItem> items = data.list.stream().map(f -> {
                        PhotoListItem it = new PhotoListItem();
                        it.photoId = f.photoId;
                        it.thumbnailUrl = f.thumbnailUrl;
                        it.isFavorite = true;
                        return it;
                    }).collect(Collectors.toList());
                    adapter.submit(items);
                } else if (r instanceof Result.Error) {
                    Toast.makeText(getContext(), ((Result.Error<?>) r).message, Toast.LENGTH_SHORT).show();
                }
            });
        });
    }
}
