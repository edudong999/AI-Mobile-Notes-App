package com.ai_photo.ui.photos;

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
import com.ai_photo.util.GlideUtil;
import com.ai_photo.util.Result;

import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PhotoDetailFragment extends Fragment {
    private PhotoRepo photoRepo = new PhotoRepo();
    private AiRepo aiRepo = new AiRepo();
    private ExecutorService exec = Executors.newSingleThreadExecutor();
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

    private void load() {
        exec.execute(() -> {
            Result<?> r = photoRepo.detail(photoId);
            getActivity().runOnUiThread(() -> {
                if (r instanceof Result.Success) {
                    bind((PhotoDetailResponse) ((Result.Success<?>) r).data);
                } else if (r instanceof Result.Error) {
                    Toast.makeText(getContext(),
                        ((Result.Error<?>) r).message, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(getContext(), R.string.msg_network_err, Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void bind(PhotoDetailResponse d) {
        View v = getView();
        if (v == null) return;
        GlideUtil.loadOriginal(((ImageView) v.findViewById(R.id.photo)), d.originalUrl);
        ((TextView) v.findViewById(R.id.file_name)).setText(d.metadata.fileName);
        ((TextView) v.findViewById(R.id.meta)).setText(
            d.metadata.width + "x" + d.metadata.height + " | " + d.metadata.size + " bytes");
        if (d.aiAnalysis != null) {
            ((TextView) v.findViewById(R.id.description)).setText(
                d.aiAnalysis.description != null ? d.aiAnalysis.description : "(none)");
            ((TextView) v.findViewById(R.id.scene)).setText(
                "scene: " + (d.aiAnalysis.scene != null ? d.aiAnalysis.scene.name : "(none)"));
            ((TextView) v.findViewById(R.id.emotion)).setText(
                "emotion: " + (d.aiAnalysis.emotion != null ? d.aiAnalysis.emotion.name : "(none)"));
            StringBuilder tagStr = new StringBuilder("tags: ");
            if (d.aiAnalysis.tags != null) {
                for (AITagResult t : d.aiAnalysis.tags) tagStr.append(t.name).append(" ");
            }
            ((TextView) v.findViewById(R.id.tags)).setText(tagStr.toString());
        } else {
            ((TextView) v.findViewById(R.id.analysis_status)).setText("AI analysis not ready");
        }
    }

    private void reanalyze() {
        exec.execute(() -> {
            Result<?> r = aiRepo.reanalyze(Collections.singletonList(photoId));
            getActivity().runOnUiThread(() -> {
                if (r instanceof Result.Success) {
                    Toast.makeText(getContext(), "queued", Toast.LENGTH_SHORT).show();
                } else if (r instanceof Result.Error) {
                    Toast.makeText(getContext(), ((Result.Error<?>) r).message, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(getContext(), R.string.msg_network_err, Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void saveDesc(View v) {
        String desc = ((EditText) v.findViewById(R.id.desc_input)).getText().toString();
        exec.execute(() -> {
            Result<?> r = photoRepo.update(photoId, null, desc);
            getActivity().runOnUiThread(() -> {
                if (r instanceof Result.Success) {
                    Toast.makeText(getContext(), "saved", Toast.LENGTH_SHORT).show();
                    load();
                } else if (r instanceof Result.Error) {
                    Toast.makeText(getContext(), ((Result.Error<?>) r).message, Toast.LENGTH_SHORT).show();
                }
            });
        });
    }
}
