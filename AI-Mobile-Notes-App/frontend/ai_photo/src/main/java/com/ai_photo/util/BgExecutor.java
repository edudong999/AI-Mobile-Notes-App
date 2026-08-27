package com.ai_photo.util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Application-scoped single-thread executor. Safe to use across Fragment view recreation. */
public final class BgExecutor {
    private static final ExecutorService IO = Executors.newFixedThreadPool(2);
    private BgExecutor() {}

    public static void execute(Runnable r) {
        if (r == null) return;
        try {
            IO.execute(r);
        } catch (java.util.concurrent.RejectedExecutionException ignored) {}
    }
}