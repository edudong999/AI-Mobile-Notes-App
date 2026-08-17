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
import com.ai_photo.util.BgExecutor;
import com.ai_photo.util.Result;

public class StatisticsFragment extends Fragment {

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup container, @Nullable Bundle b) {
        return inflater.inflate(R.layout.fragment_statistics, container, false);
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle b) {
        super.onViewCreated(view, b);
        BgExecutor.execute(() -> {
            Result<?> r = RetrofitClient.exec(RetrofitClient.api().statistics());
            final android.app.Activity a = getActivity();
            if (a == null || a.isDestroyed()) return;
            a.runOnUiThread(() -> {
                if (getView() == null) return;
                if (r instanceof Result.Success) {
                    bind(getView(), (StatisticsResponse) ((Result.Success<?>) r).data);
                }
            });
        });
    }

    private void bind(View v, StatisticsResponse s) {
        if (v == null || s == null) return;
        ((TextView) v.findViewById(R.id.total_photos)).setText(getString(
            R.string.stats_total_format, s.totalPhotos != null ? s.totalPhotos : 0));
        ((TextView) v.findViewById(R.id.analyzed_photos)).setText(getString(
            R.string.stats_analyzed_format, s.analyzedPhotos != null ? s.analyzedPhotos : 0));
        ((TextView) v.findViewById(R.id.favorite_count)).setText(getString(
            R.string.stats_favorites_format, s.favoriteCount != null ? s.favoriteCount : 0));
        if (s.categoryDistribution == null) return;
        fillDist((LinearLayout) v.findViewById(R.id.scene_dist), s.categoryDistribution.scene);
        fillDist((LinearLayout) v.findViewById(R.id.emotion_dist), s.categoryDistribution.emotion);
        fillDist((LinearLayout) v.findViewById(R.id.tag_dist), s.categoryDistribution.tag);
    }

    private void fillDist(LinearLayout container, java.util.List<DistributionItem> items) {
        container.removeAllViews();
        if (items == null || getContext() == null) return;
        for (DistributionItem it : items) {
            TextView row = new TextView(getContext());
            double pct = it.percentage != null ? it.percentage : 0.0;
            row.setText(getString(R.string.stats_distribution_format,
                it.name != null ? it.name : "",
                it.count != null ? it.count : 0,
                pct * 100));
            container.addView(row);
        }
    }
}
