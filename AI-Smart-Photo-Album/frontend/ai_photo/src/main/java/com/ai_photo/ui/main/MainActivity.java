package com.ai_photo.ui.main;

import android.os.Bundle;
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

        bottomNav.setOnItemReselectedListener(item -> {
            // Reselecting the active tab pops back to the start destination of the
            // associated graph (e.g. note detail → notes list).
            nav.popBackStack(item.getItemId(), false);
        });
    }

    @Override protected void onResume() {
        super.onResume();
        if (!AiPhotoApp.get().session().isLoggedIn()) {
            startActivity(new android.content.Intent(this, LoginActivity.class));
            finish();
        }
    }
}