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
import android.os.PowerManager;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DateFormat;
import java.util.Date;

/** Setup screen: connect to Netlify, give permissions, test the call. */
public class MainActivity extends Activity {

    private TextView status;
    private SharedPreferences p;

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        p = CallHelper.prefs(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(36), dp(22), dp(36));
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.parseColor("#02060B"), Color.parseColor("#071A26"), Color.parseColor("#02060B")}));

        HudView hud = new HudView(this);
        hud.setLabel("CLINTO");
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(dp(190), dp(190));
        hlp.gravity = Gravity.CENTER_HORIZONTAL;
        root.addView(hud, hlp);
        TextView name = label("CLINTO CALLER", 26, "#FFFFFF", true);
        name.setGravity(Gravity.CENTER);
        name.setLetterSpacing(0.2f);
        root.addView(name);
        root.addView(label("When a customer sends a request on clinto.online, your phone rings like a call. Lift it to hear the details.", 15, "#7FA3B5", false));

        status = label("", 15, "#CFEFFF", true);
        status.setTypeface(Typeface.MONOSPACE);
        status.setPadding(dp(14), dp(12), dp(14), dp(12));
        GradientDrawable sb = new GradientDrawable();
        sb.setColor(Color.parseColor("#0B1C26"));
        sb.setStroke(dp(1), Color.parseColor("#6629D3FF"));
        sb.setCornerRadius(dp(12));
        status.setBackground(sb);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, -2);
        slp.setMargins(0, dp(16), 0, dp(8));
        root.addView(status, slp);

        // --- Step 1: connect Netlify ---
        root.addView(label("1. Connect to Netlify (one time)", 16, "#E6F7FF", true));
        root.addView(label("app.netlify.com → profile (top right) → User settings → Applications → Personal access tokens → New access token → copy and paste here.", 13, "#7FA3B5", false));
        EditText tok = new EditText(this);
        tok.setHint("Paste Netlify token");
        tok.setSingleLine(true);
        tok.setTextColor(Color.WHITE);
        tok.setHintTextColor(Color.parseColor("#5F8597"));
        tok.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        if (!p.getString(WatchService.KEY_TOKEN, "").isEmpty()) tok.setHint("Connected ✓ (paste a new token to change)");
        root.addView(tok);
        root.addView(button("Connect & start", "#E8641B", v -> {
            String t = tok.getText().toString().trim();
            if (t.isEmpty() && p.getString(WatchService.KEY_TOKEN, "").isEmpty()) {
                Toast.makeText(this, "Paste the token first", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!t.isEmpty()) p.edit().putString(WatchService.KEY_TOKEN, t).remove(WatchService.KEY_SEEN).putBoolean("primed", false).apply();
            status.setText("Connecting…");
            new Thread(() -> {
                int r = WatchService.checkOnce(this);
                runOnUiThread(() -> {
                    if (r == -2) {
                        p.edit().remove(WatchService.KEY_TOKEN).apply();
                        Toast.makeText(this, "Token did not work. Copy it again.", Toast.LENGTH_LONG).show();
                    } else if (r == -1) {
                        Toast.makeText(this, "No internet. Try again.", Toast.LENGTH_LONG).show();
                    } else {
                        tok.setText("");
                        tok.setHint("Connected ✓ (paste a new token to change)");
                        p.edit().putBoolean(CallHelper.KEY_ENABLED, true).apply();
                        WatchService.start(this);
                        Toast.makeText(this, "Connected! Watching for requests.", Toast.LENGTH_LONG).show();
                    }
                    refresh();
                });
            }).start();
        }));

        // --- Step 2: permissions ---
        root.addView(label("2. Permissions", 16, "#E6F7FF", true));
        root.addView(button("Allow app notifications", "#0F2633", v -> {
            if (Build.VERSION.SDK_INT >= 33) requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 7);
            else openAppSettings();
        }));
        root.addView(button("Allow full screen calls", "#0F2633", v -> {
            if (Build.VERSION.SDK_INT >= 34) {
                try {
                    startActivity(new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                            Uri.parse("package:" + getPackageName())));
                    return;
                } catch (Exception ignored) { }
            }
            Toast.makeText(this, "Already allowed on this phone", Toast.LENGTH_SHORT).show();
        }));
        root.addView(button("Keep running in background (battery)", "#0F2633", v -> {
            try {
                startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:" + getPackageName())));
            } catch (Exception e) {
                try { startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)); }
                catch (Exception e2) { openAppSettings(); }
            }
        }));

        // --- Step 3: on/off + test ---
        root.addView(label("3. Test", 16, "#E6F7FF", true));
        Switch on = new Switch(this);
        on.setText("Calls ON");
        on.setTextSize(16);
        on.setTextColor(Color.WHITE);
        on.setChecked(p.getBoolean(CallHelper.KEY_ENABLED, true));
        on.setOnCheckedChangeListener((b, c) -> {
            p.edit().putBoolean(CallHelper.KEY_ENABLED, c).apply();
            if (c && !p.getString(WatchService.KEY_TOKEN, "").isEmpty()) WatchService.start(this);
            if (!c) WatchService.stop(this);
            refresh();
        });
        LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(-1, -2);
        olp.setMargins(0, dp(8), 0, dp(6));
        root.addView(on, olp);

        root.addView(button("📞 Test call now", "#E8641B", v -> CallHelper.startCall(this,
                "Type: New software\nName: Ravi Kumar\nBusiness: Sri Lakshmi Kirana\n"
                        + "Phone: 9876543210\nSoftware: Grocery Store\nDetails: 200 items, need billing")));

        ScrollView sv = new ScrollView(this);
        sv.addView(root);
        setContentView(sv);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!p.getString(WatchService.KEY_TOKEN, "").isEmpty() && p.getBoolean(CallHelper.KEY_ENABLED, true)) {
            try { WatchService.start(this); } catch (Exception ignored) { }
        }
        refresh();
    }

    private void refresh() {
        boolean connected = !p.getString(WatchService.KEY_TOKEN, "").isEmpty();
        boolean notif = getSystemService(NotificationManager.class).areNotificationsEnabled();
        boolean full = Build.VERSION.SDK_INT < 34 || getSystemService(NotificationManager.class).canUseFullScreenIntent();
        boolean battery = getSystemService(PowerManager.class).isIgnoringBatteryOptimizations(getPackageName());
        boolean on = p.getBoolean(CallHelper.KEY_ENABLED, true);
        long last = p.getLong(WatchService.KEY_LAST, 0);
        status.setText((connected ? "✅" : "❌") + " Connected to Netlify\n"
                + (notif ? "✅" : "❌") + " App notifications\n"
                + (full ? "✅" : "❌") + " Full screen calls\n"
                + (battery ? "✅" : "⚠️") + " Background (battery)\n"
                + (on ? "🟢 Calls ON" : "⚪ Calls OFF")
                + (last > 0 ? "\nLast checked: " + DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(last)) : "")
                + (connected && notif && full && on ? "\nReady! New requests will ring like a call." : ""));
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
        bg.setCornerRadius(dp(14));
        bg.setStroke(dp(1), Color.parseColor("#8829D3FF"));
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
