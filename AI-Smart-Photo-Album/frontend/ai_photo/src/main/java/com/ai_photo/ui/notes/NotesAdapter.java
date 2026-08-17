package com.ai_photo.ui.notes;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.ai_photo.R;
import com.ai_photo.data.local.NoteEntity;
import com.ai_photo.util.GlideUtil;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class NotesAdapter extends RecyclerView.Adapter<NotesAdapter.VH> {
    public interface OnClick { void onNote(NoteEntity item); }

    private final List<NoteEntity> items = new ArrayList<>();
    private final OnClick onClick;
    private static final SimpleDateFormat FMT =
            new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());

    public NotesAdapter(OnClick onClick) {
        this.onClick = onClick;
    }

    public void submit(List<NoteEntity> data) {
        items.clear();
        if (data != null) items.addAll(data);
        notifyDataSetChanged();
    }

    @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_note, parent, false);
        return new VH(v);
    }

    @Override public void onBindViewHolder(@NonNull VH h, int pos) {
        NoteEntity n = items.get(pos);
        h.title.setText(n.title != null && !n.title.isEmpty() ? n.title : "(无标题)");
        h.summary.setText(n.summary != null ? n.summary : "");
        h.summary.setVisibility(n.summary != null && !n.summary.isEmpty() ? View.VISIBLE : View.GONE);
        h.updatedAt.setText(n.updatedAt > 0 ? FMT.format(new Date(n.updatedAt)) : "");
        bindStatusBadge(h.statusBadge, n.aiStatus);
        GlideUtil.loadThumb(h.thumb, null); // server-side thumbnail not yet exposed; show placeholder
        h.itemView.setOnClickListener(v -> onClick.onNote(n));
    }

    private void bindStatusBadge(TextView badge, String status) {
        if (status == null) status = "pending";
        int colorRes;
        int labelRes;
        switch (status) {
            case "done":       colorRes = R.color.status_done;       labelRes = R.string.ai_queue_badge_done; break;
            case "processing": colorRes = R.color.status_processing; labelRes = R.string.ai_queue_badge_processing; break;
            case "failed":     colorRes = R.color.status_failed;     labelRes = R.string.ai_queue_badge_failed; break;
            default:           colorRes = R.color.status_pending;    labelRes = R.string.ai_queue_badge_pending;
        }
        badge.setBackgroundColor(ContextCompat.getColor(badge.getContext(), colorRes));
        badge.setText(labelRes);
        badge.setVisibility(View.VISIBLE);
    }

    @Override public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        ImageView thumb;
        TextView title, summary, statusBadge, updatedAt;
        VH(View v) {
            super(v);
            thumb = v.findViewById(R.id.thumb);
            title = v.findViewById(R.id.title);
            summary = v.findViewById(R.id.summary);
            statusBadge = v.findViewById(R.id.status_badge);
            updatedAt = v.findViewById(R.id.updated_at);
        }
    }
}
