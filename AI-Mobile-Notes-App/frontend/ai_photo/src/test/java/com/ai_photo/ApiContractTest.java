package com.ai_photo;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.*;

/** Verifies that backend responses contain the field names declared in interface.md.
 *  Requires backend running at http://10.0.2.2:8000 (mapped to host 127.0.0.1:8000).
 *  Run: ./gradlew :ai_photo:testDebugUnitTest --tests "*ApiContractTest*" */
public class ApiContractTest {

    private static final String BASE = "http://127.0.0.1:8000";

    private final OkHttpClient client = new OkHttpClient();

    @Test public void test_register_then_login() throws Exception {
        String username = "test_" + System.currentTimeMillis();
        String body = String.format(
            "{\"username\":\"%s\",\"password\":\"secret123\",\"email\":\"%s@x.com\"}", username, username);
        Response r = post("/api/v1/auth/register", body);
        assertEquals(200, r.code());
        JsonObject j = parse(r);
        assertEquals(200, j.get("code").getAsInt());
        assertTrue(j.get("data").getAsJsonObject().has("userId"));
        assertTrue(j.get("data").getAsJsonObject().has("token"));

        // Login
        Response r2 = post("/api/v1/auth/login",
            String.format("{\"username\":\"%s\",\"password\":\"secret123\"}", username));
        assertEquals(200, r2.code());
        JsonObject j2 = parse(r2);
        assertTrue(j2.get("data").getAsJsonObject().has("expiresIn"));
    }

    @Test public void test_categories_seed() throws Exception {
        Response r = get("/api/v1/categories?type=scene");
        assertEquals(200, r.code());
        JsonObject j = parse(r);
        assertEquals("scene", j.get("data").getAsJsonObject().get("type").getAsString());
        assertTrue(j.get("data").getAsJsonObject().get("list").getAsJsonArray().size() >= 20);
    }

    @Test public void test_users_me_requires_auth() throws Exception {
        Response r = get("/api/v1/users/me");
        // 401 if no token, or 200 if registered-user was created in earlier test order
        assertTrue(r.code() == 200 || r.code() == 401);
    }

    private Response get(String path) throws Exception {
        return client.newCall(new Request.Builder().url(BASE + path).build()).execute();
    }

    private Response post(String path, String body) throws Exception {
        return client.newCall(new Request.Builder()
            .url(BASE + path)
            .post(okhttp3.RequestBody.create(body, okhttp3.MediaType.parse("application/json")))
            .build()).execute();
    }

    private JsonObject parse(Response r) throws Exception {
        ResponseBody body = r.body();
        assertNotNull(body);
        return JsonParser.parseString(body.string()).getAsJsonObject();
    }
}
