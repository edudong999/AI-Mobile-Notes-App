package com.ai_photo.util;

import android.widget.ImageView;
import com.ai_photo.R;
import com.bumptech.glide.Glide;

/** Helper for loading photo thumbnails and originals. */
public final class GlideUtil {
    private GlideUtil() {}

    /** Load thumbnail. Accepts absolute URL or server-relative path (/static/...). */
    public static void loadThumb(ImageView view, String url) {
        load(view, url, R.color.category_tag);
    }

    /** Load original full-size photo. */
    public static void loadOriginal(ImageView view, String url) {
        load(view, url, R.color.status_pending);
    }

    private static void load(ImageView view, String url, int placeholderRes) {
        if (view == null || url == null || url.isEmpty()) {
            if (view != null) view.setImageResource(placeholderRes);
            return;
        }
        String absolute = url.startsWith("http://") || url.startsWith("https://")
            ? url
            : Config.BASE_URL.replaceAll("/$", "") + (url.startsWith("/") ? url : "/" + url);
        Glide.with(view.getContext().getApplicationContext())
            .load(absolute)
            .placeholder(placeholderRes)
            .into(view);
    }
}