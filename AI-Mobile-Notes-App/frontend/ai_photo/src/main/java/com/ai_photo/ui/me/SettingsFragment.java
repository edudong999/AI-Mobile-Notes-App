package com.ai_photo.ui.me;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.ai_photo.R;
import com.ai_photo.data.api.RetrofitClient;
import com.ai_photo.data.local.SessionStore;
import com.ai_photo.ui.login.LoginActivity;
import com.ai_photo.util.ServerPrefs;

/** 设置：服务器 URL + 登出。 */
public class SettingsFragment extends Fragment {

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);
        EditText url = v.findViewById(R.id.settings_server_url);
        url.setText(ServerPrefs.getBaseUrl(requireContext()));
        v.findViewById(R.id.settings_save).setOnClickListener(x -> {
            String next = url.getText().toString().trim();
            ServerPrefs.setBaseUrl(requireContext(), next);
            RetrofitClient.invalidate();
            Toast.makeText(requireContext(), "已保存", Toast.LENGTH_SHORT).show();
            requireActivity().recreate();
        });
        v.findViewById(R.id.settings_logout).setOnClickListener(x -> {
            new SessionStore(requireContext()).clear();
            android.content.Intent i = new android.content.Intent(requireContext(), LoginActivity.class);
            i.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
                    | android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            requireActivity().finish();
        });
    }
}