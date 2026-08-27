package com.ai_photo.ui.common;

import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.ai_photo.R;
import com.ai_photo.data.model.note_image_cleanup.ImageCleanupResponse;
import com.ai_photo.data.repo.ImageCleanupRepo;
import com.ai_photo.util.BgExecutor;
import com.ai_photo.util.GlideUtil;
import com.ai_photo.util.Result;
import com.ai_photo.util.ServerPrefs;

/** 全屏图片预览：支持 pinch-zoom + AI 图片清理（多模态）。 */
public class PhotoPreviewActivity extends AppCompatActivity {

    public static final String EXTRA_URL = "url";
    public static final String EXTRA_NOTE_ID = "noteId";
    public static final String EXTRA_FILE_ID = "fileId";

    private TouchImageView image;
    private ProgressBar progress;
    private View cleanupFab;
    private String originalUrl;
    private long noteId = -1L;
    private long fileId = -1L;

    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_photo_preview);
        image = findViewById(R.id.photo_preview_image);
        progress = findViewById(R.id.photo_preview_progress);
        cleanupFab = findViewById(R.id.photo_cleanup_fab);

        originalUrl = getIntent().getStringExtra(EXTRA_URL);
        noteId = getIntent().getLongExtra(EXTRA_NOTE_ID, -1L);
        fileId = getIntent().getLongExtra(EXTRA_FILE_ID, -1L);

        if (originalUrl == null || originalUrl.isEmpty()) {
            finish();
            return;
        }

        progress.setVisibility(View.VISIBLE);
        GlideUtil.loadOriginal(image, absUrl(originalUrl), new com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable>() {
            @Override public boolean onLoadFailed(@Nullable com.bumptech.glide.load.engine.GlideException e, Object model,
                                                  com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target,
                                                  boolean isFirstResource) {
                progress.setVisibility(View.GONE);
                return false;
            }
            @Override public boolean onResourceReady(android.graphics.drawable.Drawable resource, Object model,
                                                     com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target,
                                                     com.bumptech.glide.load.DataSource dataSource, boolean isFirstResource) {
                progress.setVisibility(View.GONE);
                image.post(() -> image.setImageMatrix(new android.graphics.Matrix()));
                return false;
            }
        });

        // 仅在知道 noteId + fileId 时才显示清理 FAB（来自 NoteDetail 才会传）
        if (noteId > 0 && fileId > 0) {
            cleanupFab.setVisibility(View.VISIBLE);
            cleanupFab.setOnClickListener(v -> runCleanup());
        } else {
            cleanupFab.setVisibility(View.GONE);
        }
    }

    private void runCleanup() {
        cleanupFab.setEnabled(false);
        Toast.makeText(this, R.string.photo_cleanup_running, Toast.LENGTH_SHORT).show();
        BgExecutor.execute(() -> {
            ImageCleanupRepo repo = new ImageCleanupRepo();
            Result<?> r = repo.cleanup(noteId, fileId, "commit_new");
            final android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
            h.post(() -> {
                cleanupFab.setEnabled(true);
                if (r instanceof Result.Success) {
                    ImageCleanupResponse data = (ImageCleanupResponse) ((Result.Success<?>) r).data;
                    if (data != null && data.cleanedUrl != null) {
                        showCompareDialog(data);
                    } else {
                        Toast.makeText(this,
                            getString(R.string.photo_cleanup_failed_fmt, "空响应"),
                            Toast.LENGTH_LONG).show();
                    }
                } else if (r instanceof Result.Error) {
                    String msg = ((Result.Error<?>) r).message;
                    Toast.makeText(this,
                        getString(R.string.photo_cleanup_failed_fmt,
                            msg != null ? msg : "HTTP " + ((Result.Error<?>) r).code),
                        Toast.LENGTH_LONG).show();
                } else if (r instanceof Result.Network) {
                    Throwable cause = ((Result.Network<?>) r).cause;
                    Toast.makeText(this,
                        getString(R.string.photo_cleanup_failed_fmt,
                            cause != null ? cause.getMessage() : "网络异常"),
                        Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    private void showCompareDialog(ImageCleanupResponse data) {
        android.view.View v = getLayoutInflater().inflate(R.layout.dialog_compare_image, null, false);
        android.widget.ImageView original = v.findViewById(R.id.compare_original);
        android.widget.ImageView cleaned = v.findViewById(R.id.compare_cleaned);
        GlideUtil.loadOriginal(original, absUrl(originalUrl), null);
        GlideUtil.loadOriginal(cleaned, absUrl(data.cleanedUrl), null);

        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.photo_cleanup_compare_title)
            .setView(v)
            .setPositiveButton(R.string.photo_cleanup_apply, (d, w) -> {
                // 应用：把清理结果作为主图（kind=cleaned, parent_file_id=original）
                if (data.cleanedUrl != null) {
                    originalUrl = data.cleanedUrl;
                    image.post(() -> {
                        progress.setVisibility(View.VISIBLE);
                        GlideUtil.loadOriginal(image, absUrl(originalUrl), null);
                        progress.setVisibility(View.GONE);
                    });
                }
                Toast.makeText(this, R.string.photo_cleanup_applied, Toast.LENGTH_SHORT).show();
                // 通知 NoteDetail 刷新（用 setResult + intent extras）
                android.content.Intent ret = new android.content.Intent();
                ret.putExtra("appliedFileId", data.cleanedFileId);
                ret.putExtra("appliedUrl", data.cleanedUrl);
                ret.putExtra("parentFileId", data.parentFileId == null ? fileId : data.parentFileId);
                setResult(RESULT_OK, ret);
            })
            .setNegativeButton(R.string.photo_cleanup_discard, (d, w) -> {
                // 放弃并删除清理结果
                BgExecutor.execute(() -> {
                    if (data.cleanedFileId > 0) {
                        new ImageCleanupRepo().deleteCleanedFile(data.cleanedFileId);
                    }
                });
                Toast.makeText(this, R.string.photo_cleanup_discarded, Toast.LENGTH_SHORT).show();
            })
            .show();
    }

    private String absUrl(String maybeRelative) {
        if (maybeRelative == null || maybeRelative.isEmpty()) return "";
        if (maybeRelative.startsWith("http://") || maybeRelative.startsWith("https://")) return maybeRelative;
        String base = ServerPrefs.getBaseUrl(this);
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        return maybeRelative.startsWith("/") ? base + maybeRelative : base + "/" + maybeRelative;
    }
}