package com.ai_photo.ui.search;

import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.ai_photo.R;
import com.ai_photo.data.model.category.*;
import com.ai_photo.data.model.photo.SearchItem;
import com.ai_photo.data.repo.CategoryRepo;
import com.ai_photo.data.repo.PhotoRepo;
import com.ai_photo.util.Result;
import com.google.android.material.tabs.TabLayout;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class SearchFragment extends Fragment {
    private PhotoRepo photoRepo = new PhotoRepo();
    private CategoryRepo categoryRepo = new CategoryRepo();
    private ExecutorService exec = Executors.newSingleThreadExecutor();

    private SearchResultAdapter adapter;
    private RecyclerView recycler;
    private View searchPanel, filterPanel;
    private EditText query;
    private Spinner sceneSpinner, emotionSpinner, tagSpinner;

    private Map<String, Long> sceneMap = new LinkedHashMap<>();
    private Map<String, Long> emotionMap = new LinkedHashMap<>();
    private Map<String, Long> tagMap = new LinkedHashMap<>();

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup container, @Nullable Bundle b) {
        return inflater.inflate(R.layout.fragment_search, container, false);
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle b) {
        super.onViewCreated(view, b);
        TabLayout tabs = view.findViewById(R.id.tabs);
        searchPanel = view.findViewById(R.id.search_panel);
        filterPanel = view.findViewById(R.id.filter_panel);
        query = view.findViewById(R.id.query);
        recycler = view.findViewById(R.id.recycler);
        sceneSpinner = view.findViewById(R.id.scene_spinner);
        emotionSpinner = view.findViewById(R.id.emotion_spinner);
        tagSpinner = view.findViewById(R.id.tag_spinner);

        adapter = new SearchResultAdapter();
        recycler.setLayoutManager(new LinearLayoutManager(getContext()));
        recycler.setAdapter(adapter);

        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) {
                boolean searchMode = tab.getPosition() == 0;
                searchPanel.setVisibility(searchMode ? View.VISIBLE : View.GONE);
                filterPanel.setVisibility(searchMode ? View.GONE : View.VISIBLE);
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });

        view.findViewById(R.id.btn_go).setOnClickListener(v -> doSearch());
        view.findViewById(R.id.btn_filter).setOnClickListener(v -> doFilter());

        loadCategorySpinners();
    }

    private void loadCategorySpinners() {
        exec.execute(() -> {
            // Load all three types
            Map<String, Long> s = loadOne("scene");
            Map<String, Long> e = loadOne("emotion");
            Map<String, Long> t = loadOne("tag");
            getActivity().runOnUiThread(() -> {
                sceneMap = s; emotionMap = e; tagMap = t;
                sceneSpinner.setAdapter(spinnerAdapter(sceneMap.keySet()));
                emotionSpinner.setAdapter(spinnerAdapter(emotionMap.keySet()));
                tagSpinner.setAdapter(spinnerAdapter(tagMap.keySet()));
            });
        });
    }

    private Map<String, Long> loadOne(String type) {
        Result<?> r = categoryRepo.list(type);
        if (r instanceof Result.Success) {
            CategoryListResponse data = (CategoryListResponse) ((Result.Success<?>) r).data;
            return data.list.stream().collect(Collectors.toMap(
                c -> c.categoryName, c -> c.categoryId, (a, b) -> a, LinkedHashMap::new));
        }
        return new LinkedHashMap<>();
    }

    private ArrayAdapter<String> spinnerAdapter(java.util.Set<String> items) {
        List<String> list = new ArrayList<>();
        list.add("(none)");
        list.addAll(items);
        return new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_item, list);
    }

    private void doSearch() {
        String q = query.getText().toString().trim();
        if (q.isEmpty()) { Toast.makeText(getContext(), "query empty", Toast.LENGTH_SHORT).show(); return; }
        exec.execute(() -> {
            Result<?> r = photoRepo.search(q, 1, 30);
            getActivity().runOnUiThread(() -> showResults(r));
        });
    }

    private void doFilter() {
        Long sId = pickId(sceneSpinner, sceneMap);
        Long eId = pickId(emotionSpinner, emotionMap);
        Long tId = pickId(tagSpinner, tagMap);
        if (sId == null && eId == null && tId == null) {
            Toast.makeText(getContext(), "select at least one", Toast.LENGTH_SHORT).show();
            return;
        }
        exec.execute(() -> {
            Result<?> r = photoRepo.filter(sId, eId, tId, 1, 30);
            getActivity().runOnUiThread(() -> showResults(r));
        });
    }

    private Long pickId(Spinner spinner, Map<String, Long> map) {
        String name = (String) spinner.getSelectedItem();
        if (name == null || "(none)".equals(name)) return null;
        return map.get(name);
    }

    private void showResults(Result<?> r) {
        if (r instanceof Result.Success) {
            List<SearchItem> items = (List<SearchItem>) ((Result.Success<?>) r).data.getClass()
                .equals(com.ai_photo.data.model.photo.SearchResponse.class)
                ? ((com.ai_photo.data.model.photo.SearchResponse) ((Result.Success<?>) r).data).list
                : new ArrayList<>();
            adapter.submit(items);
        } else if (r instanceof Result.Error) {
            Toast.makeText(getContext(), ((Result.Error<?>) r).message, Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(getContext(), R.string.msg_network_err, Toast.LENGTH_SHORT).show();
        }
    }
}
