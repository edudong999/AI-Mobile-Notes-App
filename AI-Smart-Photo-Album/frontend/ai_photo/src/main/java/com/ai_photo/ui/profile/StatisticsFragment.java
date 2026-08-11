package com.ai_photo.ui.profile;

import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.ai_photo.R;
import com.ai_photo.data.api.RetrofitClient;
import com.ai_photo.data.model.user.*;
import com.ai_photo.util.Result;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class StatisticsFragment extends Fragment {
    private ExecutorService exec = Executors.newSingleThreadExecutor();

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup container, @Nullable Bundle b) {
        return inflater.inflate(R.layout.fragment_statistics, container, false);
    }

    @Override public void onDestroyView() {
        super.onDestroyView();
        exec.shutdown();
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle b) {
        super.onViewCreated(view, b);
        exec.execute(() -> {
            Result<?> r = RetrofitClient.exec(RetrofitClient.api().statistics());
            final android.app.Activity a = getActivity();
            if (a == null) return;
            a.runOnUiThread(() -> {
                if (r instanceof Result.Success) {
                    bind(getView(), (StatisticsResponse) ((Result.Success<?>) r).data);
                }
            });
        });
    }

    private void bind(View v, StatisticsResponse s) {
        if (v == null) return;
        ((TextView) v.findViewById(R.id.total_photos)).setText("Total: " + s.totalPhotos);
        ((TextView) v.findViewById(R.id.analyzed_photos)).setText("Analyzed: " + s.analyzedPhotos);
        ((TextView) v.findViewById(R.id.favorite_count)).setText("Favorites: " + s.favoriteCount);
        if (s.categoryDistribution == null) return;
        fillDist((LinearLayout) v.findViewById(R.id.scene_dist), s.categoryDistribution.scene);
        fillDist((LinearLayout) v.findViewById(R.id.emotion_dist), s.categoryDistribution.emotion);
        fillDist((LinearLayout) v.findViewById(R.id.tag_dist), s.categoryDistribution.tag);
    }

    private void fillDist(LinearLayout container, java.util.List<DistributionItem> items) {
        container.removeAllViews();
        if (items == null) return;
        for (DistributionItem it : items) {
            TextView row = new TextView(getContext());
            row.setText(String.format("%s : %d (%.1f%%)", it.name, it.count, it.percentage * 100));
            container.addView(row);
        }
    }
}
