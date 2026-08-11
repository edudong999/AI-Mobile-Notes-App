package com.ai_photo.ui.ai;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.ai_photo.R;
import com.ai_photo.data.api.RetrofitClient;
import com.ai_photo.data.model.ai.AiStatusResponse;
import com.ai_photo.util.Result;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AiQueueFragment extends Fragment {
    private ExecutorService exec = Executors.newSingleThreadExecutor();
    private Handler handler = new Handler(Looper.getMainLooper());
    private TextView statusSummary;
    private ProgressBar progressBar;
    private Runnable poller;

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup container, @Nullable Bundle b) {
        return inflater.inflate(R.layout.fragment_ai_queue, container, false);
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle b) {
        super.onViewCreated(view, b);
        statusSummary = view.findViewById(R.id.status_summary);
        progressBar = view.findViewById(R.id.progress_bar);
        view.findViewById(R.id.btn_refresh).setOnClickListener(v -> fetchStatus());
    }

    @Override public void onResume() {
        super.onResume();
        fetchStatus();
        poller = new Runnable() {
            @Override public void run() {
                fetchStatus();
                handler.postDelayed(this, 3000);
            }
        };
        handler.postDelayed(poller, 3000);
    }

    @Override public void onPause() {
        super.onPause();
        handler.removeCallbacks(poller);
    }

    private void fetchStatus() {
        exec.execute(() -> {
            Result<?> r = RetrofitClient.exec(RetrofitClient.api().aiStatus());
            getActivity().runOnUiThread(() -> {
                if (r instanceof Result.Success) {
                    AiStatusResponse s = (AiStatusResponse) ((Result.Success<?>) r).data;
                    statusSummary.setText(String.format(
                        "%d done / %d pending (total %d)", s.done, s.pending, s.total));
                    progressBar.setProgress((int)(s.progress * 100));
                }
            });
        });
    }
}
