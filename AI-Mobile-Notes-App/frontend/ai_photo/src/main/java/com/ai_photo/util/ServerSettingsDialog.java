package com.ai_photo.util;

import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import com.ai_photo.R;
import com.ai_photo.data.api.RetrofitClient;

/** 抽出服务器 URL 设置对话框：登录页和详情页共用。 */
public final class ServerSettingsDialog {

    private ServerSettingsDialog() {}

    public interface OnSaved { void onSaved(); }

    public static void show(Context ctx, OnSaved onSaved) {
        View v = LayoutInflater.from(ctx).inflate(R.layout.dialog_server_settings, null, false);
        com.google.android.material.textfield.TextInputEditText input =
            v.findViewById(R.id.server_url_input);
        TextView status = v.findViewById(R.id.server_test_status);
        String current = ServerPrefs.getBaseUrl(ctx);
        input.setText(current);
        input.setSelection(input.getText().length());

        final AlertDialog[] holder = new AlertDialog[1];
        holder[0] = new AlertDialog.Builder(ctx)
            .setTitle(R.string.server_settings_title)
            .setView(v)
            .setNeutralButton(R.string.server_settings_reset, (d, w) -> {
                ServerPrefs.resetBaseUrl(ctx);
                RetrofitClient.invalidate();
                android.widget.Toast.makeText(ctx,
                    "已恢复默认：" + ServerPrefs.getBaseUrl(ctx),
                    android.widget.Toast.LENGTH_SHORT).show();
                if (onSaved != null) onSaved.onSaved();
            })
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.server_settings_save, null)
            .create();
        holder[0].setOnShowListener(d -> {
            Button btnSave = holder[0].getButton(AlertDialog.BUTTON_POSITIVE);
            Button btnTest = holder[0].getButton(AlertDialog.BUTTON_NEUTRAL);
            btnTest.setText(R.string.server_settings_test);
            btnTest.setOnClickListener(view -> {
                String url = input.getText() != null ? input.getText().toString().trim() : "";
                if (url.isEmpty()) { status.setText(R.string.server_settings_invalid); return; }
                status.setText(R.string.server_settings_testing);
                testConnection(ctx, url, status);
            });
            btnSave.setOnClickListener(view -> {
                String url = input.getText() != null ? input.getText().toString().trim() : "";
                if (url.isEmpty()) { status.setText(R.string.server_settings_invalid); return; }
                boolean ok = ServerPrefs.setBaseUrl(ctx, url);
                if (!ok) { status.setText(R.string.server_settings_invalid); return; }
                RetrofitClient.invalidate();
                holder[0].dismiss();
                android.widget.Toast.makeText(ctx,
                    "已保存：" + ServerPrefs.getBaseUrl(ctx),
                    android.widget.Toast.LENGTH_SHORT).show();
                if (onSaved != null) onSaved.onSaved();
            });
        });
        holder[0].show();
    }

    private static void testConnection(Context ctx, String url, TextView statusView) {
        String normalized = url.trim();
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            normalized = "http://" + normalized;
        }
        if (!normalized.endsWith("/")) normalized = normalized + "/";
        final String probe = normalized + "health";
        final long t0 = System.currentTimeMillis();
        BgExecutor.execute(() -> {
            final String[] resultHolder = new String[1];
            try {
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection)
                    new java.net.URL(probe).openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setRequestMethod("GET");
                int code = conn.getResponseCode();
                long t1 = System.currentTimeMillis();
                int elapsed = (int) (t1 - t0);
                conn.disconnect();
                resultHolder[0] = code >= 200 && code < 500
                    ? ctx.getString(R.string.server_settings_test_ok, elapsed)
                    : ctx.getString(R.string.server_settings_test_fail, "HTTP " + code);
            } catch (Exception e) {
                long t1 = System.currentTimeMillis();
                int elapsed = (int) (t1 - t0);
                resultHolder[0] = ctx.getString(R.string.server_settings_test_fail,
                    e.getClass().getSimpleName() + ": " + (e.getMessage() != null ? e.getMessage() : ""));
            }
            final String fResult = resultHolder[0];
            android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
            h.post(() -> { if (statusView != null) statusView.setText(fResult); });
        });
    }
}