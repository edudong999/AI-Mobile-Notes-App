package com.ai_photo.ui.search;

import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.ai_photo.R;
import com.ai_photo.data.model.photo.SearchItem;
import com.ai_photo.util.GlideUtil;

import java.util.ArrayList;
import java.util.List;

public class SearchResultAdapter extends RecyclerView.Adapter<SearchResultAdapter.VH> {
    private final List<SearchItem> items = new ArrayList<>();

    public void submit(List<SearchItem> data) {
        items.clear();
        if (data != null) items.addAll(data);
        notifyDataSetChanged();
    }

    @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup p, int t) {
        return new VH(LayoutInflater.from(p.getContext()).inflate(R.layout.item_search_result, p, false));
    }

    @Override public void onBindViewHolder(@NonNull VH h, int pos) {
        SearchItem it = items.get(pos);
        GlideUtil.loadThumb(h.thumb, it.thumbnailUrl);
        if (it.matchedTags != null) h.tags.setText(String.join(", ", it.matchedTags));
        h.score.setText(String.format(java.util.Locale.getDefault(), "%.2f", it.score));
    }

    @Override public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        ImageView thumb;
        TextView tags, score;
        VH(View v) { super(v);
            thumb = v.findViewById(R.id.thumb);
            tags = v.findViewById(R.id.tags);
            score = v.findViewById(R.id.score);
        }
    }
}
