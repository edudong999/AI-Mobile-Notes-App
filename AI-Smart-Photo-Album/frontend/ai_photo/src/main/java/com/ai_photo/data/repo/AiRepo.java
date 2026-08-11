package com.ai_photo.data.repo;

import com.ai_photo.data.api.RetrofitClient;
import com.ai_photo.data.model.ai.*;
import com.ai_photo.util.Result;

import java.util.List;

public class AiRepo {
    public Result<AiStatusResponse> status() {
        return RetrofitClient.exec(RetrofitClient.api().aiStatus());
    }

    public Result<ReanalyzeResponse> reanalyze(List<Long> photoIds) {
        ReanalyzeRequest req = new ReanalyzeRequest();
        req.photoIds = photoIds;
        return RetrofitClient.exec(RetrofitClient.api().reanalyze(req));
    }
}
