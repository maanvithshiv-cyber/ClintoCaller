package com.clinto.caller;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/** Setup screen: give permissions, set the keyword, test the call. */
public class MainActivity extends Activity {

    private TextView status;

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        SharedPreferences p = CallHelper.prefs(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(36), dp(22), dp(36));
        root.setBackgroundColor(Color.parseColor("#F7F2E9"));

        root.addView(label("Clinto Caller", 28, "#1C1B19", true));
        root.addView(label("When a Clinto request mail arrives, your phone rings like a call. Lift it to hear the details.", 15, "#6E6A62", false));

        status = label("", 15, "#1C1B19", true);
        status.setPadding(dp(14), dp(12), dp(14), dp(12));
        GradientDrawable sb = new GradientDrawable();
        sb.setColor(Color.WHITE);
        sb.setCornerRadius(dp(12));
        status.setBackground(sb);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, -2);
        slp.setMargins(0, dp(16), 0, dp(8));
        root.addView(status, slp);

        root.addView(button("1. Allow notification access", "#1C1B19", v ->
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))));

        root.addView(button("2. Allow app notifications", "#1C1B19", v -> {
            if (Build.VERSION.SDK_INT >= 33) requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 7);
            else openAppSettings();
        }));

        root.addView(button("3. Allow full screen calls", "#1C1B19", v -> {
            if (Build.VERSION.SDK_INT >= 34) {
                try {
                    startActivity(new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                            Uri.parse("package:" + getPackageName())));
                    return;
                } catch (Exception ignored) { }
            }
            Toast.makeText(this, "Already allowed on this phone", Toast.LENGTH_SHORT).show();
        }));

        root.addView(button("4. Don't stop in background (battery)", "#1C1B19", v -> {
            try { startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)); }
            catch (Exception e) { openAppSettings(); }
        }));

        root.addView(label("Ring when a notification contains:", 14, "#6E6A62", true));
        EditText kw = new EditText(this);
        kw.setText(p.getString(CallHelper.KEY_KEYWORD, CallHelper.DEFAULT_KEYWORD));
        kw.setSingleLine(true);
        root.addView(kw);
        root.addView(button("Save keyword", "#55524B", v -> {
            p.edit().putString(CallHelper.KEY_KEYWORD, kw.getText().toString().trim()).apply();
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show();
        }));

        Switch on = new Switch(this);
        on.setText("Calls ON");
        on.setTextSize(16);
        on.setChecked(p.getBoolean(CallHelper.KEY_ENABLED, true));
        on.setOnCheckedChangeListener((b, c) -> p.edit().putBoolean(CallHelper.KEY_ENABLED, c).apply());
        LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(-1, -2);
        olp.setMargins(0, dp(14), 0, dp(6));
        root.addView(on, olp);

        root.addView(button("📞 Test call now", "#E8641B", v -> CallHelper.startCall(this,
                "New Clinto request\nType: New software\nName: Ravi Kumar\nBusiness: Sri Lakshmi Kirana\n"
                        + "Phone: 9876543210\nSoftware needed: Grocery Store\nDetails: 200 items, need billing")));

        ScrollView sv = new ScrollView(this);
        sv.addView(root);
        setContentView(sv);
    }

    @Override
    protected void onResume() {
        super.onResume();
        boolean listener = isListenerEnabled();
        boolean notif = getSystemService(NotificationManager.class).areNotificationsEnabled();
        boolean full = Build.VERSION.SDK_INT < 34 || getSystemService(NotificationManager.class).canUseFullScreenIntent();
        status.setText((listener ? "✅" : "❌") + " Notification access\n"
                + (notif ? "✅" : "❌") + " App notifications\n"
                + (full ? "✅" : "❌") + " Full screen calls\n"
                + (listener && notif && full ? "Ready! Clinto mails will ring like a call." : "Finish the steps below."));
    }

    private boolean isListenerEnabled() {
        String flat = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        return flat != null && flat.contains(getPackageName());
    }

    private void openAppSettings() {
        startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
    }

    private TextView label(String s, int sp, String color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(Color.parseColor(color));
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, dp(6), 0, dp(6));
        t.setGravity(Gravity.START);
        return t;
    }

    private Button button(String s, String color, android.view.View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(15);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor(color));
        bg.setCornerRadius(dp(12));
        b.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(52));
        lp.setMargins(0, dp(6), 0, dp(6));
        b.setLayoutParams(lp);
        b.setOnClickListener(l);
        return b;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
