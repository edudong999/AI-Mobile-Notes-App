package com.ai_photo.ui.photos;

import android.app.Activity;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.ai_photo.R;
import com.ai_photo.data.model.photo.*;
import com.ai_photo.data.repo.AiRepo;
import com.ai_photo.data.repo.PhotoRepo;
import com.ai_photo.util.BgExecutor;
import com.ai_photo.util.GlideUtil;
import com.ai_photo.util.Result;

import java.util.Collections;

public class PhotoDetailFragment extends Fragment {
    private PhotoRepo photoRepo = new PhotoRepo();
    private AiRepo aiRepo = new AiRepo();
    private long photoId;

    @Override public void onCreate(@Nullable Bundle b) {
        super.onCreate(b);
        photoId = getArguments() != null ? getArguments().getLong("photoId") : 0L;
    }

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup container, @Nullable Bundle b) {
        return inflater.inflate(R.layout.fragment_photo_detail, container, false);
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle b) {
        super.onViewCreated(view, b);
        view.findViewById(R.id.btn_reanalyze).setOnClickListener(v -> reanalyze());
        view.findViewById(R.id.btn_save_desc).setOnClickListener(v -> saveDesc(view));
        load();
    }

    @Override public void onDestroyView() {
        super.onDestroyView();
    }

    private void load() {
        BgExecutor.execute(() -> {
            Result<?> r = photoRepo.detail(photoId);
            final Activity a = getActivity();
            if (a == null || a.isDestroyed()) return;
            a.runOnUiThread(() -> {
                if (getView() == null) return;
                if (r instanceof Result.Success) {
                    bind((PhotoDetailResponse) ((Result.Success<?>) r).data);
                } else if (r instanceof Result.Error) {
                    toast(((Result.Error<?>) r).message);
                } else {
                    toast(R.string.msg_network_err);
                }
            });
        });
    }

    private void bind(PhotoDetailResponse d) {
        View v = getView();
        if (v == null || d == null) return;
        GlideUtil.loadOriginal(((ImageView) v.findViewById(R.id.photo)), d.originalUrl);
        if (d.metadata != null) {
            ((TextView) v.findViewById(R.id.file_name)).setText(
                d.metadata.fileName != null ? d.metadata.fileName : "");
            ((TextView) v.findViewById(R.id.meta)).setText(getString(
                R.string.detail_meta_format,
                d.metadata.width, d.metadata.height, d.metadata.size));
        } else {
            ((TextView) v.findViewById(R.id.file_name)).setText("");
            ((TextView) v.findViewById(R.id.meta)).setText(R.string.detail_meta_unknown);
        }
        if (d.aiAnalysis != null) {
            ((TextView) v.findViewById(R.id.description)).setText(
                d.aiAnalysis.description != null ? d.aiAnalysis.description : getString(R.string.detail_desc_none));
            ((TextView) v.findViewById(R.id.scene)).setText(getString(R.string.detail_field_scene,
                d.aiAnalysis.scene != null && d.aiAnalysis.scene.name != null
                    ? d.aiAnalysis.scene.name : getString(R.string.detail_field_unset)));
            ((TextView) v.findViewById(R.id.emotion)).setText(getString(R.string.detail_field_emotion,
                d.aiAnalysis.emotion != null && d.aiAnalysis.emotion.name != null
                    ? d.aiAnalysis.emotion.name : getString(R.string.detail_field_unset)));
            StringBuilder tagStr = new StringBuilder();
            if (d.aiAnalysis.tags != null) {
                for (AITagResult t : d.aiAnalysis.tags) {
                    if (t.name != null) tagStr.append(t.name).append(" ");
                }
            }
            ((TextView) v.findViewById(R.id.tags)).setText(getString(R.string.detail_field_tags, tagStr.toString().trim()));
            ((TextView) v.findViewById(R.id.analysis_status)).setText("");
        } else {
            ((TextView) v.findViewById(R.id.description)).setText("");
            ((TextView) v.findViewById(R.id.scene)).setText("");
            ((TextView) v.findViewById(R.id.emotion)).setText("");
            ((TextView) v.findViewById(R.id.tags)).setText("");
            ((TextView) v.findViewById(R.id.analysis_status)).setText(R.string.detail_analysis_pending);
        }
    }

    private void reanalyze() {
        BgExecutor.execute(() -> {
            Result<?> r = aiRepo.reanalyze(Collections.singletonList(photoId));
            final Activity a = getActivity();
            if (a == null || a.isDestroyed()) return;
            a.runOnUiThread(() -> {
                if (getView() == null) return;
                if (r instanceof Result.Success) {
                    toast(R.string.msg_queued);
                } else if (r instanceof Result.Error) {
                    toast(((Result.Error<?>) r).message);
                } else {
                    toast(R.string.msg_network_err);
                }
            });
        });
    }

    private void saveDesc(View v) {
        String desc = ((EditText) v.findViewById(R.id.desc_input)).getText().toString();
        BgExecutor.execute(() -> {
            Result<?> r = photoRepo.update(photoId, null, desc);
            final Activity a = getActivity();
            if (a == null || a.isDestroyed()) return;
            a.runOnUiThread(() -> {
                if (getView() == null) return;
                if (r instanceof Result.Success) {
                    toast(R.string.msg_saved);
                    load();
                } else if (r instanceof Result.Error) {
                    toast(((Result.Error<?>) r).message);
                } else {
                    toast(R.string.msg_network_err);
                }
            });
        });
    }

    private void toast(int resId) {
        android.content.Context ctx = getContext();
        if (ctx != null) Toast.makeText(ctx, resId, Toast.LENGTH_SHORT).show();
    }

    private void toast(String msg) {
        android.content.Context ctx = getContext();
        if (ctx != null && msg != null) Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show();
    }
}