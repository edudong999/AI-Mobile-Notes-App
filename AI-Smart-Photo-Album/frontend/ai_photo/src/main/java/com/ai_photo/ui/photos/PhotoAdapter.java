package com.ai_photo.ui.photos;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.ai_photo.R;
import com.ai_photo.data.model.photo.PhotoListItem;
import com.ai_photo.util.GlideUtil;

import java.util.ArrayList;
import java.util.List;

public class PhotoAdapter extends RecyclerView.Adapter<PhotoAdapter.VH> {
    public interface OnClick { void onPhoto(PhotoListItem item); }
    public interface OnFavClick { void onFav(PhotoListItem item); }

    private final List<PhotoListItem> items = new ArrayList<>();
    private final OnClick onClick;
    private final OnFavClick onFavClick;

    public PhotoAdapter(OnClick onClick, OnFavClick onFavClick) {
        this.onClick = onClick;
        this.onFavClick = onFavClick;
    }

    public void submit(List<PhotoListItem> data) {
        items.clear();
        items.addAll(data);
        notifyDataSetChanged();
    }

    @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_photo_grid, parent, false);
        return new VH(v);
    }

    @Override public void onBindViewHolder(@NonNull VH h, int pos) {
        PhotoListItem it = items.get(pos);
        GlideUtil.loadThumb(h.thumb, it.thumbnailUrl);
        h.fav.setImageResource(it.isFavorite
            ? android.R.drawable.btn_star_big_on
            : android.R.drawable.btn_star_big_off);
        h.itemView.setOnClickListener(v -> onClick.onPhoto(it));
        h.fav.setOnClickListener(v -> onFavClick.onFav(it));
    }

    @Override public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        ImageView thumb, fav;
        VH(View v) { super(v); thumb = v.findViewById(R.id.thumb); fav = v.findViewById(R.id.fav); }
    }
}
