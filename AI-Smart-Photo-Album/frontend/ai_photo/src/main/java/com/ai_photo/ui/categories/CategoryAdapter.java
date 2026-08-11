package com.ai_photo.ui.categories;

import android.view.*;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.ai_photo.R;
import com.ai_photo.data.model.category.CategoryListItem;

import java.util.ArrayList;
import java.util.List;

public class CategoryAdapter extends RecyclerView.Adapter<CategoryAdapter.VH> {
    public interface OnClick { void onCategory(CategoryListItem item); }
    private final List<CategoryListItem> items = new ArrayList<>();
    private final OnClick onClick;

    public CategoryAdapter(OnClick onClick) { this.onClick = onClick; }

    public void submit(List<CategoryListItem> data) {
        items.clear();
        items.addAll(data);
        notifyDataSetChanged();
    }

    @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup p, int t) {
        return new VH(LayoutInflater.from(p.getContext()).inflate(R.layout.item_category, p, false));
    }

    @Override public void onBindViewHolder(@NonNull VH h, int pos) {
        CategoryListItem it = items.get(pos);
        h.name.setText(it.categoryName);
        h.count.setText(it.photoCount + " photos");
        h.itemView.setOnClickListener(v -> onClick.onCategory(it));
    }

    @Override public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView name, count;
        VH(View v) { super(v);
            name = v.findViewById(R.id.name);
            count = v.findViewById(R.id.count);
        }
    }
}