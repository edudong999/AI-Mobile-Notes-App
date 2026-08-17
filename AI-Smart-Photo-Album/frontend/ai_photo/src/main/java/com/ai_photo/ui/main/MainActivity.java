package com.ai_photo.ui.main;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;
import com.ai_photo.AiPhotoApp;
import com.ai_photo.R;
import com.ai_photo.ui.login.LoginActivity;
import com.ai_photo.util.AppMode;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!AiPhotoApp.get().session().isLoggedIn()) {
            startActivity(new android.content.Intent(this, LoginActivity.class));
            finish();
            return;
        }
        setContentView(R.layout.activity_main);

        NavHostFragment host = (NavHostFragment) getSupportFragmentManager()
            .findFragmentById(R.id.nav_host);
        if (host == null) return;
        NavController nav = host.getNavController();

        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
        applyModeMenu(bottomNav, nav);

        bottomNav.setOnItemReselectedListener(item -> {
            // No-op: default behavior of popping to start is sufficient
        });
    }

    public void applyModeMenu(BottomNavigationView bottomNav, NavController nav) {
        AppMode mode = AppMode.current(this);
        bottomNav.getMenu().clear();
        if (mode == AppMode.NOTE) {
            bottomNav.inflateMenu(R.menu.bottom_nav_note);
        } else {
            bottomNav.inflateMenu(R.menu.bottom_nav_photo);
        }
        NavigationUI.setupWithNavController(bottomNav, nav);
    }
}