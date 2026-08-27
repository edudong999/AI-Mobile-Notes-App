package com.ai_photo.ui.notes;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
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
        GlideUtil.loadThumb(h.thumb, n.thumbUrl);
        h.itemView.setOnClickListener(v -> onClick.onNote(n));
    }

    @Override public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        ImageView thumb;
        TextView title, summary, updatedAt;
        VH(View v) {
            super(v);
            thumb = v.findViewById(R.id.thumb);
            title = v.findViewById(R.id.title);
            summary = v.findViewById(R.id.summary);
            updatedAt = v.findViewById(R.id.updated_at);
        }
    }
}
