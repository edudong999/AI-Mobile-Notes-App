package com.ai_photo.ui.me;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.ai_photo.R;
import com.ai_photo.data.local.SessionStore;
import com.ai_photo.util.ServerPrefs;

/** "我的" 主屏：账号、菜单、关于 三张卡。 */
public class MeFragment extends Fragment {

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_me, container, false);
    }

    @Override public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);
        View account = v.findViewById(R.id.card_account);
        View settings = v.findViewById(R.id.row_settings);
        View categories = v.findViewById(R.id.row_categories);
        View about = v.findViewById(R.id.row_about);

        // 账号卡：填充用户名 + 当前服务器地址
        TextView username = account.findViewById(R.id.me_username);
        TextView server = account.findViewById(R.id.me_server);
        SessionStore session = new SessionStore(requireContext());
        username.setText(session.username() != null ? session.username() : "未登录");
        server.setText(ServerPrefs.getBaseUrl(requireContext()));

        settings.setOnClickListener(x ->
            NavHostFragment.findNavController(MeFragment.this)
                .navigate(R.id.settingsFragment));
        categories.setOnClickListener(x ->
            NavHostFragment.findNavController(MeFragment.this)
                .navigate(R.id.categoriesFragment));
        about.setOnClickListener(x ->
            NavHostFragment.findNavController(MeFragment.this)
                .navigate(R.id.aboutFragment));
    }
}