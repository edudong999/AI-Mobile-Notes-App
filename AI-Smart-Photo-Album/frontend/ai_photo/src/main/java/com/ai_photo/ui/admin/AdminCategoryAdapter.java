package com.ai_photo.ui.admin;

import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.ai_photo.R;
import com.ai_photo.data.model.admin.AdminCategoryItem;

import java.util.ArrayList;
import java.util.List;

public class AdminCategoryAdapter extends RecyclerView.Adapter<AdminCategoryAdapter.VH> {
    public interface OnEdit { void onEdit(AdminCategoryItem item); }
    public interface OnDelete { void onDelete(AdminCategoryItem item); }

    private final List<AdminCategoryItem> items = new ArrayList<>();
    private final OnEdit onEdit;
    private final OnDelete onDelete;

    public AdminCategoryAdapter(OnEdit onEdit, OnDelete onDelete) {
        this.onEdit = onEdit;
        this.onDelete = onDelete;
    }

    public void submit(List<AdminCategoryItem> data) {
        items.clear();
        items.addAll(data);
        notifyDataSetChanged();
    }

    @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup p, int t) {
        return new VH(LayoutInflater.from(p.getContext())
            .inflate(R.layout.item_admin_category, p, false));
    }

    @Override public void onBindViewHolder(@NonNull VH h, int pos) {
        AdminCategoryItem it = items.get(pos);
        h.name.setText(it.name);
        h.btnEdit.setOnClickListener(v -> onEdit.onEdit(it));
        h.btnDelete.setOnClickListener(v -> onDelete.onDelete(it));
    }

    @Override public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView name;
        Button btnEdit, btnDelete;
        VH(View v) { super(v);
            name = v.findViewById(R.id.name);
            btnEdit = v.findViewById(R.id.btn_edit);
            btnDelete = v.findViewById(R.id.btn_delete);
        }
    }
}
