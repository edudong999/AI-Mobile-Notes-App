package com.ai_photo.ui.notes;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.ai_photo.R;
import com.ai_photo.data.local.CategoryEntity;
import com.ai_photo.data.local.NoteEntity;
import com.ai_photo.data.model.category.CategoryItem;
import com.ai_photo.data.model.category.CategoryListResponse;
import com.ai_photo.data.model.note.NoteListResponse;
import com.ai_photo.data.model.note.NoteListItem;
import com.ai_photo.data.repo.CategoryRepo;
import com.ai_photo.data.repo.NoteRepo;
import com.ai_photo.util.BgExecutor;
import com.ai_photo.util.Result;
import androidx.navigation.fragment.NavHostFragment;
import java.util.ArrayList;
import java.util.List;

public class NotesFragment extends Fragment {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private NoteRepo repo;
    private CategoryRepo catRepo;
    private Spinner categorySpinner;
    private RecyclerView recycler;
    private SwipeRefreshLayout swipe;
    private TextView empty;
    private NotesAdapter adapter;
    private final List<CategoryEntity> categories = new ArrayList<>();
    private Long selectedCategoryId = null;  // null = "全部"
    private Runnable poller;

    private final ActivityResultLauncher<String> pickNoteImage =
        registerForActivityResult(new ActivityResultContracts.GetMultipleContents(),
            (List<android.net.Uri> uris) -> {
                if (uris != null && !uris.isEmpty()) {
                    android.widget.Toast.makeText(getContext(),
                        R.string.notes_capture_queued, android.widget.Toast.LENGTH_SHORT).show();
                }
            });

    @Override public void onCreate(@Nullable Bundle b) {
        super.onCreate(b);
        repo = new NoteRepo(requireContext());
        catRepo = new CategoryRepo();
        // 可选：从 nav-arg 读取 categoryId（由 CategoriesFragment 跳转过来）
        if (getArguments() != null && getArguments().containsKey("categoryId")) {
            long cid = getArguments().getLong("categoryId", -1L);
            if (cid > 0) selectedCategoryId = cid;
        }
    }

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup container, @Nullable Bundle b) {
        return inflater.inflate(R.layout.fragment_notes, container, false);
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle b) {
        super.onViewCreated(view, b);
        categorySpinner = view.findViewById(R.id.category_spinner);
        recycler = view.findViewById(R.id.recycler);
        swipe = view.findViewById(R.id.swipe);
        empty = view.findViewById(R.id.empty);
        com.google.android.material.floatingactionbutton.FloatingActionButton fab =
            view.findViewById(R.id.fab_new);

        adapter = new NotesAdapter(item -> {
            Bundle args = new Bundle();
            args.putLong("noteId", item.noteId);
            NavHostFragment.findNavController(this)
                .navigate(R.id.action_to_note_detail, args);
        });
        recycler.setLayoutManager(new LinearLayoutManager(getContext()));
        recycler.setAdapter(adapter);

        categorySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                Long prev = selectedCategoryId;
                if (pos == 0) selectedCategoryId = null;
                else if (pos - 1 < categories.size()) selectedCategoryId = categories.get(pos - 1).categoryId;
                // 选择变化时立即拉一次远端（带 categoryId 过滤），再 refresh 渲染
                if (prev == null ? selectedCategoryId != null : !prev.equals(selectedCategoryId)) {
                    pollNotesFromServer();
                }
                refresh();
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });

        swipe.setOnRefreshListener(this::refresh);
        fab.setOnClickListener(v ->
            NavHostFragment.findNavController(this).navigate(R.id.action_to_capture));

        refresh();
    }

    @Override public void onResume() {
        super.onResume();
        pollCategoriesFromServer();
        poller = new Runnable() {
            @Override public void run() {
                refresh();
                handler.postDelayed(this, 5000);
            }
        };
        handler.postDelayed(poller, 5000);
    }

    @Override public void onPause() {
        super.onPause();
        if (poller != null) handler.removeCallbacks(poller);
    }

    @Override public void onDestroyView() {
        super.onDestroyView();
        if (poller != null) handler.removeCallbacks(poller);
    }

    private void refresh() {
        BgExecutor.execute(() -> {
            List<NoteEntity> all = repo.noteDao().recent(200);
            List<NoteEntity> list = filterByCategory(all, selectedCategoryId);
            final android.app.Activity a = getActivity();
            if (a == null || a.isDestroyed()) return;
            a.runOnUiThread(() -> {
                if (getView() == null) return;
                swipe.setRefreshing(false);
                adapter.submit(list);
                empty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
            });
        });
    }

    /** 按分类筛选：null=全部；否则匹配 categoriesCsv 中的 id。 */
    private static List<NoteEntity> filterByCategory(List<NoteEntity> all, Long categoryId) {
        if (categoryId == null) return all;
        List<NoteEntity> out = new ArrayList<>();
        String target = "," + categoryId + ",";
        for (NoteEntity n : all) {
            String csv = n.categoriesCsv;
            if (csv == null || csv.isEmpty()) continue;
            String wrapped = "," + csv + ",";
            if (wrapped.contains(target)) out.add(n);
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private void pollCategoriesFromServer() {
        BgExecutor.execute(() -> {
            Result<?> cr = catRepo.list();
            if (cr instanceof Result.Success) {
                CategoryListResponse data = (CategoryListResponse) ((Result.Success<?>) cr).data;
                if (data != null) {
                    categories.clear();
                    for (CategoryItem it : data.list) {
                        CategoryEntity ce = new CategoryEntity();
                        ce.categoryId = it.categoryId;
                        ce.name = it.name != null ? it.name : "";
                        ce.color = it.color != null ? it.color : "#4A90E2";
                        ce.sortIndex = it.sortIndex;
                        ce.noteCount = it.noteCount;
                        categories.add(ce);
                    }
                    final android.app.Activity a = getActivity();
                    if (a != null && !a.isDestroyed()) {
                        a.runOnUiThread(() -> rebuildSpinner());
                    }
                }
            }
        });
        // also refresh notes list from network (server-side filter by categoryId)
        pollNotesFromServer();
    }

    private void pollNotesFromServer() {
        BgExecutor.execute(() -> {
            Result<?> nr = repo.listNotes(selectedCategoryId, 1, 50);
            if (nr instanceof Result.Success) {
                NoteListResponse data = (NoteListResponse) ((Result.Success<?>) nr).data;
                if (data != null) {
                    for (NoteListItem it : data.list) {
                        NoteEntity ne = new NoteEntity();
                        ne.noteId = it.noteId;
                        ne.categoriesCsv = it.categories == null ? "" : joinIds(it.categories);
                        ne.title = it.title != null ? it.title : "";
                        ne.summary = it.summary != null ? it.summary : "";
                        ne.aiStatus = it.aiStatus != null ? it.aiStatus : "pending";
                        ne.thumbUrl = it.thumbUrl;
                        ne.updatedAt = parseDate(it.updatedAt);
                        repo.noteDao().upsert(ne);
                    }
                }
            }
        });
    }

    private void rebuildSpinner() {
        List<String> labels = new ArrayList<>();
        labels.add(getString(R.string.notes_filter_all));
        for (CategoryEntity c : categories) labels.add(c.name);
        ArrayAdapter<String> sa = new ArrayAdapter<>(getContext(),
            android.R.layout.simple_spinner_item, labels);
        sa.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        categorySpinner.setAdapter(sa);
        // 若已有选定分类（来自 nav-arg），尝试同步下拉位置
        if (selectedCategoryId != null) {
            for (int i = 0; i < categories.size(); i++) {
                if (categories.get(i).categoryId == selectedCategoryId) {
                    categorySpinner.setSelection(i + 1);
                    break;
                }
            }
        }
    }

    private static String joinIds(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(ids.get(i));
        }
        return sb.toString();
    }

    private static long parseDate(String iso) {
        if (iso == null || iso.isEmpty()) return 0;
        try { return java.time.Instant.parse(iso).toEpochMilli(); }
        catch (Exception e) { return 0L; }
    }
}