package com.ai_photo.data.local;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;
import com.ai_photo.util.Result;

/** Stores JWT in EncryptedSharedPreferences. Falls back to plain prefs if keystore unavailable. */
public final class SessionStore {
    private static final String FILE = "ai_photo_session";
    private static final String KEY_TOKEN = "jwt_token";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_USERNAME = "username";

    private final SharedPreferences prefs;

    public SessionStore(Context ctx) {
        SharedPreferences p;
        try {
            MasterKey key = new MasterKey.Builder(ctx)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build();
            p = EncryptedSharedPreferences.create(
                ctx, FILE, key,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
        } catch (Exception e) {
            // Fallback to plain prefs (less secure but functional)
            p = ctx.getSharedPreferences(FILE + "_plain", Context.MODE_PRIVATE);
        }
        this.prefs = p;
    }

    public void save(String token, long userId, String username) {
        prefs.edit()
            .putString(KEY_TOKEN, token)
            .putLong(KEY_USER_ID, userId)
            .putString(KEY_USERNAME, username)
            .apply();
    }

    public String token() { return prefs.getString(KEY_TOKEN, null); }
    public long userId() { return prefs.getLong(KEY_USER_ID, 0L); }
    public String username() { return prefs.getString(KEY_USERNAME, null); }
    public boolean isLoggedIn() { return token() != null; }

    public void clear() { prefs.edit().clear().apply(); }
}