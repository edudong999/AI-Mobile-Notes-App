package com.ai_photo.ui.main;

import android.os.Bundle;
import android.view.MenuItem;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;
import com.ai_photo.AiPhotoApp;
import com.ai_photo.R;
import com.ai_photo.ui.login.LoginActivity;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Token check
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
        NavigationUI.setupWithNavController(bottomNav, nav);

        // Reselect on bottom nav -> pop to start of that tab's stack
        bottomNav.setOnItemReselectedListener(item -> {
            // No-op: default behavior of popping to start is sufficient
        });
    }
}