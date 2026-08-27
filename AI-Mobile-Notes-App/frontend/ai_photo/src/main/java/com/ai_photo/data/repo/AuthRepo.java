package com.ai_photo.data.repo;

import com.ai_photo.AiPhotoApp;
import com.ai_photo.data.api.RetrofitClient;
import com.ai_photo.data.model.auth.*;
import com.ai_photo.util.Result;

public class AuthRepo {
    public Result<AuthResponse> login(String username, String password) {
        LoginRequest req = new LoginRequest();
        req.username = username; req.password = password;
        Result<AuthResponse> r = RetrofitClient.exec(RetrofitClient.api().login(req));
        if (r instanceof Result.Success) {
            AuthResponse data = ((Result.Success<AuthResponse>) r).data;
            AiPhotoApp.get().session().save(data.token, data.userId, data.username);
        }
        return r;
    }

    public Result<AuthResponse> register(String username, String password, String email) {
        RegisterRequest req = new RegisterRequest();
        req.username = username; req.password = password; req.email = email;
        Result<AuthResponse> r = RetrofitClient.exec(RetrofitClient.api().register(req));
        if (r instanceof Result.Success) {
            AuthResponse data = ((Result.Success<AuthResponse>) r).data;
            AiPhotoApp.get().session().save(data.token, data.userId, data.username);
        }
        return r;
    }

    public Result<Object> logout() {
        Result<Object> r = RetrofitClient.execVoid(RetrofitClient.api().logout());
        AiPhotoApp.get().session().clear();
        return r;
    }
}
