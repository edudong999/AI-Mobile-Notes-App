package com.ai_photo.ui.login;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.ai_photo.AiPhotoApp;
import com.ai_photo.R;
import com.ai_photo.data.repo.AuthRepo;
import com.ai_photo.ui.main.MainActivity;
import com.ai_photo.util.Result;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LoginActivity extends AppCompatActivity {
    private AuthRepo repo = new AuthRepo();
    private ExecutorService exec = Executors.newSingleThreadExecutor();
    private boolean registerMode = false;

    private EditText username, password, email;
    private Button submit;
    private TextView toggle, title;
    private ProgressBar loading;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Already logged in -> skip to Main
        if (AiPhotoApp.get().session().isLoggedIn()) {
            goToMain();
            return;
        }
        setContentView(R.layout.activity_login);
        title = findViewById(R.id.title);
        username = findViewById(R.id.username);
        password = findViewById(R.id.password);
        email = findViewById(R.id.email);
        submit = findViewById(R.id.submit);
        toggle = findViewById(R.id.toggle);
        loading = findViewById(R.id.loading);

        toggle.setOnClickListener(v -> {
            registerMode = !registerMode;
            applyMode();
        });
        submit.setOnClickListener(v -> doSubmit());
        applyMode();
    }

    private void applyMode() {
        title.setText(registerMode ? R.string.title_register : R.string.title_login);
        submit.setText(registerMode ? R.string.btn_register : R.string.btn_login);
        toggle.setText(registerMode ? "返回登录" : "立即注册");
        email.setVisibility(registerMode ? View.VISIBLE : View.GONE);
    }

    private void doSubmit() {
        String u = username.getText().toString().trim();
        String p = password.getText().toString();
        if (u.isEmpty() || p.isEmpty()) {
            toast("用户名和密码必填");
            return;
        }
        loading.setVisibility(View.VISIBLE);
        submit.setEnabled(false);
        exec.execute(() -> {
            Result<?> r = registerMode
                ? repo.register(u, p, email.getText().toString())
                : repo.login(u, p);
            runOnUiThread(() -> {
                loading.setVisibility(View.GONE);
                submit.setEnabled(true);
                handleResult(r);
            });
        });
    }

    private void handleResult(Result<?> r) {
        if (r instanceof Result.Success) {
            toast(registerMode ? R.string.msg_register_ok : R.string.msg_login_ok);
            goToMain();
        } else if (r instanceof Result.Error) {
            toast(((Result.Error<?>) r).message);
        } else {
            toast(R.string.msg_network_err);
        }
    }

    private void goToMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    private void toast(int resId) { Toast.makeText(this, resId, Toast.LENGTH_SHORT).show(); }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
}
