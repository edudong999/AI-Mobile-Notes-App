package com.ai_photo.data.api;

import com.ai_photo.AiPhotoApp;
import com.ai_photo.data.model.Envelope;
import com.ai_photo.util.Config;
import com.ai_photo.util.Result;
import com.ai_photo.util.ServerPrefs;
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

    private static volatile ApiService API = null;
    private static volatile String CURRENT_BASE = null;
    private static final Gson GSON = new Gson();

    private RetrofitClient() {}

    public static ApiService api() {
        String want = ServerPrefs.getBaseUrl(AiPhotoApp.get());
        ApiService local = API;
        if (local != null && want.equals(CURRENT_BASE)) {
            return local;
        }
        synchronized (RetrofitClient.class) {
            if (API == null || !want.equals(CURRENT_BASE)) {
                API = build(want);
                CURRENT_BASE = want;
            }
            return API;
        }
    }

    /** Force-rebuild on next api() call (after the user changes the server URL). */
    public static void invalidate() {
        synchronized (RetrofitClient.class) {
            CURRENT_BASE = null;
            // keep API alive for in-flight requests; will be replaced lazily
        }
    }

    public static <T> Result<T> exec(Call<Envelope<T>> call) {
        try {
            retrofit2.Response<Envelope<T>> resp = call.execute();
            // 即使 HTTP 4xx/5xx 也尝试解 envelope：后端 BizException 把业务码塞进 HTTP 状态，
            // 但 body 里 {"code":..., "message":...} 仍是我们想要的真正错误信息。
            if (resp.code() >= 200 && resp.code() < 300) {
                Envelope<T> env = resp.body();
                if (env == null) return Result.err(-1, "Empty response");
                if (env.code == 200) return Result.ok(env.data);
                return Result.err(env.code, env.message != null ? env.message : "Biz error");
            }
            // 非 2xx：尝试从 body 拿业务错误信息
            Envelope<T> env = null;
            try { env = resp.body(); } catch (Exception ignore) {}
            if (env != null && env.message != null && !env.message.isEmpty()) {
                return Result.err(env.code != 0 ? env.code : resp.code(), env.message);
            }
            okhttp3.ResponseBody errorBody = resp.errorBody();
            if (errorBody != null) {
                String raw = errorBody.string();
                if (raw != null && !raw.isEmpty()) {
                    try {
                        Envelope<?> parsed = GSON.fromJson(raw, Envelope.class);
                        if (parsed != null && parsed.message != null && !parsed.message.isEmpty()) {
                            return Result.err(parsed.code != 0 ? parsed.code : resp.code(), parsed.message);
                        }
                    } catch (Exception ignore) {}
                    return Result.err(resp.code(), raw.length() > 200 ? raw.substring(0, 200) : raw);
                }
            }
            return Result.err(resp.code(), "HTTP " + resp.code());
        } catch (IOException | com.google.gson.JsonSyntaxException e) {
            return Result.net(e);
        } catch (RuntimeException e) {
            return Result.net(e);
        }
    }

    public static Result<Object> execVoid(Call<Envelope<Object>> call) {
        return exec(call);
    }

    private static ApiService build(String baseUrl) {
        HttpLoggingInterceptor log = new HttpLoggingInterceptor();
        log.setLevel(HttpLoggingInterceptor.Level.BASIC);

        OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(Config.CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS)
            .readTimeout(Config.READ_TIMEOUT_SEC, TimeUnit.SECONDS)
            .addInterceptor(new AuthInterceptor())
            .addInterceptor(new UnauthorizedInterceptor())
            .addInterceptor(log)
            .build();

        Retrofit retrofit = new Retrofit.Builder()
            .baseUrl(baseUrl)
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

    private static class UnauthorizedInterceptor implements Interceptor {
        @Override public Response intercept(Chain chain) throws IOException {
            Response resp = chain.proceed(chain.request());
            if (resp.code() == 401) {
                AiPhotoApp.get().session().clear();
            }
            return resp;
        }
    }
}
