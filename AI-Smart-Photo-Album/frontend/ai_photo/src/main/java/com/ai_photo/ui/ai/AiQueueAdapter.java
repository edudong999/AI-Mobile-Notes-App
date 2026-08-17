package com.ai_photo.ui.ai;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.ai_photo.R;
import com.ai_photo.data.model.ai.AiQueueItem;
import com.ai_photo.util.GlideUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter for AI queue rows. Each section (pending/processing/failed/done) starts with a header,
 * followed by the rows belonging to that bucket.
 */
public class AiQueueAdapter extends RecyclerView.Adapter<AiQueueAdapter.VH> {

    public interface OnRetry { void onRetry(AiQueueItem item); }

    /** A "row" is either a section header (no photo, just a label) or an actual photo row. */
    public static class Row {
        public final boolean isHeader;
        public final AiQueueItem item;
        public final String headerLabel;
        private Row(boolean isHeader, AiQueueItem it, String lbl) {
            this.isHeader = isHeader; this.item = it; this.headerLabel = lbl;
        }
        public static Row header(String label) { return new Row(true, null, label); }
        public static Row row(AiQueueItem it) { return new Row(false, it, null); }
    }

    private final List<Row> rows = new ArrayList<>();
    private final OnRetry onRetry;

    public AiQueueAdapter(OnRetry onRetry) { this.onRetry = onRetry; }

    public void submit(List<Row> data) {
        rows.clear();
        if (data != null) rows.addAll(data);
        notifyDataSetChanged();
    }

    @Override public int getItemViewType(int position) {
        return rows.get(position).isHeader ? 1 : 0;
    }

    @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layout = viewType == 1 ? R.layout.item_ai_queue_header : R.layout.item_ai_queue_row;
        View v = LayoutInflater.from(parent.getContext()).inflate(layout, parent, false);
        return new VH(v, viewType == 1);
    }

    @Override public void onBindViewHolder(@NonNull VH h, int pos) {
        Row r = rows.get(pos);
        if (r.isHeader) {
            h.header.setText(r.headerLabel);
            return;
        }
        AiQueueItem it = r.item;
        GlideUtil.loadThumb(h.thumb, it.thumbnailUrl);
        h.filename.setText(it.fileName != null ? it.fileName : ("#" + it.photoId));

        // 状态徽章：背景 / 文字 / 描述
        int bgRes, colorRes, badgeTextRes;
        int descRes, descCountRes;
        boolean showRetry = false;
        boolean showError = false;
        String status = it.status == null ? "" : it.status;
        switch (status) {
            case "pending":
                bgRes = R.drawable.ai_bg_status_orange; colorRes = R.color.ai_status_pending_text;
                badgeTextRes = R.string.ai_queue_badge_pending;
                descRes = it.retryCount > 0 ? R.string.ai_queue_desc_pending_retry_n : R.string.ai_queue_desc_pending;
                descCountRes = it.retryCount > 0 ? R.integer.ai_queue_retry_count : 0;
                showError = false;
                break;
            case "processing":
                bgRes = R.drawable.ai_bg_status_blue; colorRes = R.color.ai_status_processing_text;
                badgeTextRes = R.string.ai_queue_badge_processing;
                descRes = it.retryCount > 0 ? R.string.ai_queue_desc_processing_retry_n : R.string.ai_queue_desc_processing;
                descCountRes = it.retryCount > 0 ? R.integer.ai_queue_retry_count : 0;
                showError = it.errorMessage != null && !it.errorMessage.isEmpty();
                break;
            case "done":
                bgRes = R.drawable.ai_bg_status_green; colorRes = R.color.ai_status_done_text;
                badgeTextRes = R.string.ai_queue_badge_done;
                descRes = R.string.ai_queue_desc_done;
                descCountRes = 0;
                showError = false;
                break;
            case "failed":
            default:
                bgRes = R.drawable.ai_bg_status_red; colorRes = R.color.ai_status_failed_text;
                badgeTextRes = R.string.ai_queue_badge_failed;
                descRes = it.retryCount > 0 ? R.string.ai_queue_desc_failed_n : R.string.ai_queue_desc_failed;
                descCountRes = it.retryCount > 0 ? R.integer.ai_queue_retry_count : 0;
                showError = it.errorMessage != null && !it.errorMessage.isEmpty();
                showRetry = onRetry != null;
                break;
        }

        h.badge.setText(h.itemView.getContext().getString(badgeTextRes));
        h.badge.setBackgroundResource(bgRes);
        h.badge.setTextColor(h.itemView.getContext().getColor(colorRes));

        if (descCountRes != 0) {
            h.desc.setText(h.itemView.getContext().getString(descRes, it.retryCount));
        } else {
            h.desc.setText(h.itemView.getContext().getString(descRes));
        }

        if (showError) {
            h.error.setVisibility(View.VISIBLE);
            h.error.setText(it.errorMessage);
        } else {
            h.error.setVisibility(View.GONE);
        }

        if (showRetry) {
            h.retry.setVisibility(View.VISIBLE);
            h.retry.setOnClickListener(vv -> onRetry.onRetry(it));
        } else {
            h.retry.setVisibility(View.GONE);
            h.retry.setOnClickListener(null);
        }
    }

    @Override public int getItemCount() { return rows.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView header;
        ImageView thumb;
        TextView filename, desc, error, badge, retry;
        VH(View v, boolean isHeader) {
            super(v);
            if (isHeader) {
                header = v.findViewById(R.id.queue_header);
            } else {
                thumb = v.findViewById(R.id.queue_thumb);
                filename = v.findViewById(R.id.queue_filename);
                desc = v.findViewById(R.id.queue_status_desc);
                error = v.findViewById(R.id.queue_error);
                badge = v.findViewById(R.id.queue_badge);
                retry = v.findViewById(R.id.queue_retry_btn);
            }
        }
    }

    /** Marker to silence the unused-import warning on some setups (kept for IDE happiness). */
    @SuppressWarnings("unused")
    private static void touch(ColorStateList ignored) {}
}
