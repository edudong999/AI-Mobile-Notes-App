package com.ai_photo.data.repo;

import com.ai_photo.data.api.RetrofitClient;
import com.ai_photo.data.model.note_search.*;
import com.ai_photo.util.Result;

public class NoteSearchRepo {
    public Result<SearchResponse> search(String query, String mode) {
        SearchRequest req = new SearchRequest();
        req.query = query; req.mode = mode;
        return RetrofitClient.exec(RetrofitClient.api().noteSearch(req));
    }
}
