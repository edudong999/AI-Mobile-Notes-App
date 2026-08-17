package com.ai_photo.util;

import android.content.Context;
import android.content.SharedPreferences;

public enum AppMode { NOTE, PHOTO;

    private static final String KEY = "app_mode";
    private static final String PREFS = "ui_prefs";

    public static AppMode current(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String s = sp.getString(KEY, NOTE.name());
        try { return AppMode.valueOf(s); } catch (Exception e) { return NOTE; }
    }

    public static void set(Context ctx, AppMode m) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, m.name()).apply();
    }
}
