package com.ai_photo.data.api;

import com.ai_photo.AiPhotoApp;
import com.ai_photo.data.model.Envelope;
import com.ai_photo.util.Config;
import com.ai_photo.util.Result;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.concurrent.TimeUnit;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public final class RetrofitClient {

    private static final ApiService API = build();
    private static final Gson GSON = new Gson();

    private RetrofitClient() {}

    public static ApiService api() { return API; }

    public static <T> Result<T> exec(Call<Envelope<T>> call) {
        try {
            retrofit2.Response<Envelope<T>> resp = call.execute();
            if (!resp.isSuccessful()) {
                return Result.err(resp.code(), "HTTP " + resp.code());
            }
            Envelope<T> env = resp.body();
            if (env == null) return Result.err(-1, "Empty response");
            if (env.code == 200) return Result.ok(env.data);
            return Result.err(env.code, env.message != null ? env.message : "Biz error");
        } catch (IOException e) {
            return Result.net(e);
        }
    }

    public static Result<Object> execVoid(Call<Envelope<Object>> call) {
        return exec(call);
    }

    private static ApiService build() {
        HttpLoggingInterceptor log = new HttpLoggingInterceptor();
        log.setLevel(HttpLoggingInterceptor.Level.BASIC);

        OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(Config.CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS)
            .readTimeout(Config.READ_TIMEOUT_SEC, TimeUnit.SECONDS)
            .addInterceptor(new AuthInterceptor())
            .addInterceptor(log)
            .build();

        Retrofit retrofit = new Retrofit.Builder()
            .baseUrl(Config.BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build();

        return retrofit.create(ApiService.class);
    }

    private static class AuthInterceptor implements Interceptor {
        @Override public Response intercept(Chain chain) throws IOException {
            Request req = chain.request();
            String token = AiPhotoApp.get().session().token();
            if (token != null) {
                req = req.newBuilder()
                    .header("Authorization", "Bearer " + token)
                    .build();
            }
            return chain.proceed(req);
        }
    }
}
