package com.ai_photo.ui.photos;

import android.net.Uri;
import android.os.Bundle;
import android.view.*;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.ai_photo.R;
import com.ai_photo.data.model.photo.PhotoListItem;
import com.ai_photo.data.model.photo.PhotoListResponse;
import com.ai_photo.data.repo.PhotoRepo;
import com.ai_photo.util.BgExecutor;
import com.ai_photo.util.Result;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.List;

public class PhotoListFragment extends Fragment {
    private PhotoRepo repo = new PhotoRepo();
    private RecyclerView recycler;
    private SwipeRefreshLayout swipe;
    private PhotoAdapter adapter;

    private final ActivityResultLauncher<String> pickImage =
        registerForActivityResult(new ActivityResultContracts.GetMultipleContents(),
            (List<Uri> uris) -> {
                if (uris != null && !uris.isEmpty()) doUpload(uris);
            });

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup container, @Nullable Bundle b) {
        return inflater.inflate(R.layout.fragment_photo_list, container, false);
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle b) {
        super.onViewCreated(view, b);
        recycler = view.findViewById(R.id.recycler);
        swipe = view.findViewById(R.id.swipe);
        FloatingActionButton fab = view.findViewById(R.id.fab_upload);

        adapter = new PhotoAdapter(
            item -> {
                Bundle args = new Bundle();
                args.putLong("photoId", item.photoId);
                NavHostFragment.findNavController(this)
                    .navigate(R.id.action_to_detail, args);
            },
            item -> BgExecutor.execute(() -> {
                Result<?> r = item.isFavorite
                    ? repo.unfavorite(item.photoId)
                    : repo.favorite(item.photoId);
                if (!(r instanceof Result.Success)) return;
                final android.app.Activity a = getActivity();
                if (a == null || a.isDestroyed()) return;
                a.runOnUiThread(() -> {
                    if (getView() == null) return;
                    refresh();
                });
            })
        );
        recycler.setLayoutManager(new GridLayoutManager(getContext(), 3));
        recycler.setAdapter(adapter);

        swipe.setOnRefreshListener(this::refresh);
        fab.setOnClickListener(v -> pickImage.launch("image/*"));

        refresh();
    }

    @SuppressWarnings("unchecked")
    private void refresh() {
        swipe.setRefreshing(true);
        BgExecutor.execute(() -> {
            Result<?> r = repo.list(1, 60);
            final android.app.Activity a = getActivity();
            if (a == null || a.isDestroyed()) return;
            a.runOnUiThread(() -> {
                if (getView() == null) return;
                swipe.setRefreshing(false);
                if (r instanceof Result.Success) {
                    Result.Success<PhotoListResponse> ok = (Result.Success<PhotoListResponse>) r;
                    adapter.submit(ok.data.list);
                } else if (r instanceof Result.Error) {
                    Toast.makeText(getContext(),
                        ((Result.Error<?>) r).message, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(getContext(), R.string.msg_network_err, Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void doUpload(List<Uri> uris) {
        swipe.setRefreshing(true);
        BgExecutor.execute(() -> {
            Result<?> r = repo.uploadFromUris(getContext(), uris);
            final android.app.Activity a = getActivity();
            if (a == null || a.isDestroyed()) return;
            a.runOnUiThread(() -> {
                if (getView() == null) return;
                swipe.setRefreshing(false);
                if (r instanceof Result.Success) {
                    Toast.makeText(getContext(), R.string.msg_upload_ok, Toast.LENGTH_SHORT).show();
                    refresh();
                } else if (r instanceof Result.Error) {
                    Toast.makeText(getContext(), ((Result.Error<?>) r).message, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(getContext(), R.string.msg_network_err, Toast.LENGTH_SHORT).show();
                }
            });
        });
    }
}
