package com.ai_photo.util;

import android.widget.ImageView;
import com.ai_photo.R;
import com.bumptech.glide.Glide;

/** Helper for loading photo thumbnails and originals. */
public final class GlideUtil {
    private GlideUtil() {}

    /** Load thumbnail. URL is absolute (e.g., http://10.0.2.2:8000/static/thumb/123.webp). */
    public static void loadThumb(ImageView view, String url) {
        Glide.with(view.getContext())
            .load(url)
            .placeholder(R.color.category_tag)
            .into(view);
    }

    /** Load original full-size photo. */
    public static void loadOriginal(ImageView view, String url) {
        Glide.with(view.getContext())
            .load(url)
            .placeholder(R.color.status_pending)
            .into(view);
    }
}