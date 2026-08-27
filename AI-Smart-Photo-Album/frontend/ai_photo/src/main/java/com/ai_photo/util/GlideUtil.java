package com.ai_photo.util;

import android.graphics.drawable.Drawable;
import android.widget.ImageView;
import com.ai_photo.R;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestListener;

/** Helper for loading photo thumbnails and originals. */
public final class GlideUtil {
    private GlideUtil() {}

    /** Load thumbnail. Accepts absolute URL or server-relative path (/static/...). */
    public static void loadThumb(ImageView view, String url) {
        load(view, url, R.color.category_tag, null);
    }

    /** Load original full-size photo. */
    public static void loadOriginal(ImageView view, String url) {
        load(view, url, R.color.status_pending, null);
    }

    /** Load original full-size photo with a load-complete listener. */
    public static void loadOriginal(ImageView view, String url, RequestListener listener) {
        load(view, url, R.color.status_pending, listener);
    }

    private static void load(ImageView view, String url, int placeholderRes, RequestListener listener) {
        if (view == null || url == null || url.isEmpty()) {
            if (view != null) view.setImageResource(placeholderRes);
            return;
        }
        String base = ServerPrefs.getBaseUrl(view.getContext()).replaceAll("/$", "");
        String absolute = url.startsWith("http://") || url.startsWith("https://")
            ? url
            : base + (url.startsWith("/") ? url : "/" + url);
        var req = Glide.with(view.getContext().getApplicationContext()).load(absolute).placeholder(placeholderRes);
        if (listener != null) req = req.listener((RequestListener<Drawable>) listener);
        req.into(view);
    }
}