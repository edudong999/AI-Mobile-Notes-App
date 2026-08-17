package com.ai_photo.ui.profile;

import android.content.Intent;
import android.os.Bundle;
import android.view.*;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import com.ai_photo.R;
import com.ai_photo.data.api.RetrofitClient;
import com.ai_photo.data.model.user.UserMeResponse;
import com.ai_photo.data.repo.AuthRepo;
import com.ai_photo.ui.login.LoginActivity;
import com.ai_photo.util.BgExecutor;
import com.ai_photo.util.Result;

public class ProfileFragment extends Fragment {
    private AuthRepo authRepo = new AuthRepo();

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup container, @Nullable Bundle b) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle b) {
        super.onViewCreated(view, b);
        view.findViewById(R.id.btn_favorites).setOnClickListener(v ->
            NavHostFragment.findNavController(this).navigate(R.id.action_to_favorites));
        view.findViewById(R.id.btn_statistics).setOnClickListener(v ->
            NavHostFragment.findNavController(this).navigate(R.id.action_to_statistics));
        view.findViewById(R.id.btn_admin).setOnClickListener(v ->
            NavHostFragment.findNavController(this).navigate(R.id.action_to_admin));
        view.findViewById(R.id.btn_logout).setOnClickListener(v -> doLogout());

        BgExecutor.execute(() -> {
            Result<?> r = RetrofitClient.exec(RetrofitClient.api().me());
            final android.app.Activity a = getActivity();
            if (a == null || a.isDestroyed()) return;
            a.runOnUiThread(() -> {
                View v = getView();
                if (v == null) return;
                if (r instanceof Result.Success) {
                    UserMeResponse u = (UserMeResponse) ((Result.Success<?>) r).data;
                    if (u == null) return;
                    ((android.widget.TextView) v.findViewById(R.id.username)).setText(
                        u.username != null ? u.username : "");
                    ((android.widget.TextView) v.findViewById(R.id.email)).setText(
                        u.email != null ? u.email : "");
                    ((android.widget.TextView) v.findViewById(R.id.created_at)).setText(
                        u.createdAt != null ? u.createdAt : "");
                }
            });
        });
    }

    private void doLogout() {
        BgExecutor.execute(() -> {
            authRepo.logout();
            final android.app.Activity a = getActivity();
            if (a == null || a.isDestroyed()) return;
            a.runOnUiThread(() -> {
                if (a.isFinishing()) return;
                Toast.makeText(a, R.string.msg_logout_ok, Toast.LENGTH_SHORT).show();
                a.startActivity(new Intent(a, LoginActivity.class));
                a.finishAffinity();
            });
        });
    }
}
