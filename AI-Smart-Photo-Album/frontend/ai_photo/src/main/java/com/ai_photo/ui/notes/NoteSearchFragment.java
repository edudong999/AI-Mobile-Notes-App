package com.ai_photo.ui.notes;

import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.ai_photo.R;
import com.ai_photo.data.model.note_search.*;
import com.ai_photo.data.repo.NoteSearchRepo;
import com.ai_photo.util.BgExecutor;
import com.ai_photo.util.Result;
import java.util.ArrayList;
import java.util.List;

public class NoteSearchFragment extends Fragment {
    private EditText queryInput;
    private RadioGroup modeGroup;
    private RecyclerView recycler;
    private TextView empty;
    private SearchAdapter adapter;
    private final NoteSearchRepo repo = new NoteSearchRepo();
    private static final String MODE_KEYWORD = "keyword";
    private static final String MODE_SEMANTIC = "semantic";
    private static final String MODE_HYBRID = "hybrid";

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup container, @Nullable Bundle b) {
        return inflater.inflate(R.layout.fragment_note_search, container, false);
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle b) {
        super.onViewCreated(view, b);
        queryInput = view.findViewById(R.id.note_search_input);
        modeGroup = view.findViewById(R.id.note_search_mode);
        recycler = view.findViewById(R.id.note_search_recycler);
        empty = view.findViewById(R.id.note_search_empty);
        adapter = new SearchAdapter();
        recycler.setLayoutManager(new LinearLayoutManager(getContext()));
        recycler.setAdapter(adapter);
        view.findViewById(R.id.note_search_btn).setOnClickListener(v -> doSearch());
        queryInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                doSearch();
                return true;
            }
            return false;
        });
        // set initial mode = hybrid
        modeGroup.check(R.id.note_search_mode_hybrid);
    }

    @SuppressWarnings("unchecked")
    private void doSearch() {
        String q = queryInput.getText().toString().trim();
        if (q.isEmpty()) {
            Toast.makeText(getContext(), R.string.search_hint_required, Toast.LENGTH_SHORT).show();
            return;
        }
        String mode;
        int checkedId = modeGroup.getCheckedRadioButtonId();
        if (checkedId == R.id.note_search_mode_keyword) mode = MODE_KEYWORD;
        else if (checkedId == R.id.note_search_mode_semantic) mode = MODE_SEMANTIC;
        else mode = MODE_HYBRID;

        BgExecutor.execute(() -> {
            Result<SearchResponse> r = repo.search(q, mode);
            final android.app.Activity a = getActivity();
            if (a == null || a.isDestroyed()) return;
            a.runOnUiThread(() -> {
                if (getView() == null) return;
                if (r instanceof Result.Success) {
                    SearchResponse data = (SearchResponse) ((Result.Success<?>) r).data;
                    List<SearchHit> hits = data != null && data.hits != null ? data.hits : java.util.Collections.emptyList();
                    adapter.submit(hits);
                    empty.setVisibility(hits.isEmpty() ? View.VISIBLE : View.GONE);
                    empty.setText(getString(R.string.search_results_fmt, data == null ? 0 : data.total));
                } else {
                    String err = r instanceof Result.Error ? ((Result.Error<?>) r).message : getString(R.string.msg_network_err);
                    Toast.makeText(getContext(), err, Toast.LENGTH_SHORT).show();
                    adapter.submit(new ArrayList<>());
                    empty.setText(R.string.search_results_empty);
                    empty.setVisibility(View.VISIBLE);
                }
            });
        });
    }

    static class SearchAdapter extends RecyclerView.Adapter<SearchAdapter.VH> {
        private final List<SearchHit> items = new ArrayList<>();
        void submit(List<SearchHit> data) {
            items.clear(); if (data != null) items.addAll(data); notifyDataSetChanged();
        }
        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_note_search_hit, parent, false);
            return new VH(v);
        }
        @Override public void onBindViewHolder(@NonNull VH h, int pos) {
            SearchHit hit = items.get(pos);
            h.title.setText(hit.title != null ? hit.title : "(无标题)");
            h.snippet.setText(hit.snippet != null ? hit.snippet : "");
            h.score.setText(String.format(java.util.Locale.getDefault(), "%.2f", hit.score));
        }
        @Override public int getItemCount() { return items.size(); }
        static class VH extends RecyclerView.ViewHolder {
            TextView title, snippet, score;
            VH(View v) {
                super(v);
                title = v.findViewById(R.id.search_hit_title);
                snippet = v.findViewById(R.id.search_hit_snippet);
                score = v.findViewById(R.id.search_hit_score);
            }
        }
    }
}
