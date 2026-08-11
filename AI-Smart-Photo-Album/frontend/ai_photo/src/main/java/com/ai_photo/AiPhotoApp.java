package com.ai_photo;

import android.app.Application;
import com.ai_photo.data.local.SessionStore;

public class AiPhotoApp extends Application {
    private static AiPhotoApp INSTANCE;
    private SessionStore session;

    @Override public void onCreate() {
        super.onCreate();
        INSTANCE = this;
        session = new SessionStore(this);
    }

    public static AiPhotoApp get() { return INSTANCE; }
    public SessionStore session() { return session; }
}