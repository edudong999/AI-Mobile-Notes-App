package com.ai_photo.util;

import android.content.Context;
import android.content.SharedPreferences;

/** Persisted server URL. Override Config.BASE_URL at runtime. */
public final class ServerPrefs {
    private static final String PREFS = "ai_photo_server";
    private static final String KEY_BASE_URL = "base_url";

    private ServerPrefs() {}

    public static String getBaseUrl(Context ctx) {
        SharedPreferences sp = ctx.getApplicationContext()
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return sp.getString(KEY_BASE_URL, Config.BASE_URL);
    }

    /** Returns the normalized URL (always ends with "/"). Null/blank → no change. */
    public static boolean setBaseUrl(Context ctx, String url) {
        if (url == null) return false;
        String trimmed = url.trim();
        if (trimmed.isEmpty()) return false;
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            trimmed = "http://" + trimmed;
        }
        if (!trimmed.endsWith("/")) {
            trimmed = trimmed + "/";
        }
        ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_BASE_URL, trimmed).apply();
        return true;
    }

    public static void resetBaseUrl(Context ctx) {
        ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(KEY_BASE_URL).apply();
    }
}
