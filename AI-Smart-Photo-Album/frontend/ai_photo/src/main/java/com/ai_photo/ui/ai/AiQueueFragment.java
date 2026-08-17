package com.ai_photo.ui.ai;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.ai_photo.R;
import com.ai_photo.data.model.ai.*;
import com.ai_photo.data.repo.AiRepo;
import com.ai_photo.util.BgExecutor;
import com.ai_photo.util.Result;

import java.util.ArrayList;
import java.util.List;

public class AiQueueFragment extends Fragment {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final AiRepo repo = new AiRepo();

    private TextView totalSummary, badgePending, badgeProcessing, badgeDone, badgeFailed, empty;
    private ProgressBar progressBar;
    private RecyclerView recycler;
    private AiQueueAdapter adapter;
    private Runnable poller;

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup container, @Nullable Bundle b) {
        return inflater.inflate(R.layout.fragment_ai_queue, container, false);
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle b) {
        super.onViewCreated(view, b);
        totalSummary = view.findViewById(R.id.queue_total_summary);
        badgePending = view.findViewById(R.id.badge_pending);
        badgeProcessing = view.findViewById(R.id.badge_processing);
        badgeDone = view.findViewById(R.id.badge_done);
        badgeFailed = view.findViewById(R.id.badge_failed);
        progressBar = view.findViewById(R.id.queue_progress_bar);
        recycler = view.findViewById(R.id.queue_recycler);
        empty = view.findViewById(R.id.queue_empty);

        adapter = new AiQueueAdapter(item -> doRetry(item));
        recycler.setLayoutManager(new LinearLayoutManager(getContext()));
        recycler.setAdapter(adapter);
    }

    @Override public void onResume() {
        super.onResume();
        fetchAll();
        poller = new Runnable() {
            @Override public void run() {
                fetchAll();
                handler.postDelayed(this, 3000);
            }
        };
        handler.postDelayed(poller, 3000);
    }

    @Override public void onPause() {
        super.onPause();
        if (poller != null) handler.removeCallbacks(poller);
    }

    @Override public void onDestroyView() {
        super.onDestroyView();
        if (poller != null) handler.removeCallbacks(poller);
    }

    private void fetchAll() {
        BgExecutor.execute(() -> {
            Result<?> sr = repo.status();
            Result<?> qr = repo.queue();
            final android.app.Activity a = getActivity();
            if (a == null || a.isDestroyed()) return;
            a.runOnUiThread(() -> {
                if (getView() == null) return;
                render(sr, qr);
            });
        });
    }

    private void render(Result<?> sr, Result<?> qr) {
        int total = 0, done = 0, pending = 0, processing = 0, failed = 0;
        if (sr instanceof Result.Success) {
            AiStatusResponse s = (AiStatusResponse) ((Result.Success<?>) sr).data;
            if (s != null) {
                total      = nz(s.total);
                done       = nz(s.done);
                pending    = nz(s.pending);
                processing = nz(s.processing);
                failed     = nz(s.failed);
            }
        }
        totalSummary.setText(getString(R.string.ai_queue_summary, total, done));
        progressBar.setProgress(total == 0 ? 0 : (int) Math.round(done * 100.0 / total));
        badgePending.setText(getString(R.string.ai_queue_badge_pending) + " " + pending);
        badgeProcessing.setText(getString(R.string.ai_queue_badge_processing) + " " + processing);
        badgeDone.setText(getString(R.string.ai_queue_badge_done) + " " + done);
        badgeFailed.setText(getString(R.string.ai_queue_badge_failed) + " " + failed);

        List<AiQueueAdapter.Row> rows = new ArrayList<>();
        if (qr instanceof Result.Success) {
            AiQueueResponse q = (AiQueueResponse) ((Result.Success<?>) qr).data;
            if (q != null) {
                appendSection(rows, q.processing, R.string.ai_queue_section_processing);
                appendSection(rows, q.pending,    R.string.ai_queue_section_pending);
                appendSection(rows, q.failed,     R.string.ai_queue_section_failed);
                appendSection(rows, q.done,       R.string.ai_queue_section_done);
            }
        }
        adapter.submit(rows);

        boolean hasAny = !rows.isEmpty();
        empty.setVisibility(hasAny ? View.GONE : View.VISIBLE);
        if (total == 0) {
            empty.setText(R.string.ai_queue_empty);
        } else if (!hasAny) {
            empty.setText(R.string.msg_network_err);
        }
    }

    private void appendSection(List<AiQueueAdapter.Row> sink, List<AiQueueItem> items, int labelRes) {
        if (items == null || items.isEmpty()) return;
        sink.add(AiQueueAdapter.Row.header(getString(labelRes, items.size())));
        for (AiQueueItem it : items) sink.add(AiQueueAdapter.Row.row(it));
    }

    private void doRetry(AiQueueItem item) {
        BgExecutor.execute(() -> {
            java.util.ArrayList<Long> ids = new java.util.ArrayList<>();
            ids.add(item.photoId);
            Result<?> r = repo.reanalyze(ids);
            final android.app.Activity a = getActivity();
            if (a == null || a.isDestroyed()) return;
            a.runOnUiThread(() -> {
                if (getView() == null) return;
                if (r instanceof Result.Success) {
                    Toast.makeText(getContext(),
                        getString(R.string.ai_queue_retry_queued, item.photoId),
                        Toast.LENGTH_SHORT).show();
                } else if (r instanceof Result.Error) {
                    Toast.makeText(getContext(),
                        ((Result.Error<?>) r).message, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(getContext(), R.string.msg_network_err, Toast.LENGTH_SHORT).show();
                }
                fetchAll();
            });
        });
    }

    private static int nz(Integer v) { return v == null ? 0 : v; }
}
