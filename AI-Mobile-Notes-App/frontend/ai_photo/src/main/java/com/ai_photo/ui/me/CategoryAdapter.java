package com.ai_photo.ui.me;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.ai_photo.R;
import com.ai_photo.data.model.category.CategoryItem;

import java.util.List;

public class CategoryAdapter extends RecyclerView.Adapter<CategoryAdapter.VH> {
    public interface OnClick { void onClick(CategoryItem item); }

    private final List<CategoryItem> items;
    private final OnClick onClick;

    public CategoryAdapter(List<CategoryItem> items, OnClick onClick) {
        this.items = items;
        this.onClick = onClick;
    }

    @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_category, parent, false);
        return new VH(v);
    }

    @Override public void onBindViewHolder(@NonNull VH h, int pos) {
        CategoryItem c = items.get(pos);
        h.name.setText(c.name);
        h.count.setText(c.noteCount + " 篇");
        h.itemView.setOnClickListener(x -> onClick.onClick(c));
    }

    @Override public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView name, count;
        VH(View v) {
            super(v);
            name = v.findViewById(R.id.category_name);
            count = v.findViewById(R.id.category_count);
        }
    }
}