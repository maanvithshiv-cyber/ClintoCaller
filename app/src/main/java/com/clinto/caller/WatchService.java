package com.clinto.caller;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.PowerManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;

/** Runs in the background and checks Netlify for new Clinto requests every 30 seconds. */
public class WatchService extends Service {

    public static final String FORM_ID = "6af337a9d8a8bf8cd01d8e6c"; // clinto-request form
    static final String API = "https://api.netlify.com/api/v1/forms/" + FORM_ID + "/submissions?per_page=10";
    static final String KEY_TOKEN = "netlify_token";
    static final String KEY_SEEN = "seen_ids";
    static final String KEY_LAST = "last_check";
    static final String CH_WATCH = "clinto_watch_v1";
    static final int NOTIF_WATCH = 4243;
    static final long EVERY_MS = 30_000;

    private HandlerThread thread;
    private Handler handler;

    public static void start(Context c) {
        Intent i = new Intent(c, WatchService.class);
        if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i); else c.startService(i);
    }

    public static void stop(Context c) {
        c.stopService(new Intent(c, WatchService.class));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm.getNotificationChannel(CH_WATCH) == null) {
            NotificationChannel ch = new NotificationChannel(CH_WATCH, "Watching for requests", NotificationManager.IMPORTANCE_MIN);
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
        }
        PendingIntent open = PendingIntent.getActivity(this, 3, new Intent(this, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(this, CH_WATCH)
                .setSmallIcon(android.R.drawable.sym_call_incoming)
                .setContentTitle("Clinto Caller is ON")
                .setContentText("Waiting for new Clinto requests")
                .setOngoing(true)
                .setContentIntent(open)
                .build();
        if (Build.VERSION.SDK_INT >= 34) startForeground(NOTIF_WATCH, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else startForeground(NOTIF_WATCH, n);

        thread = new HandlerThread("clinto-watch");
        thread.start();
        handler = new Handler(thread.getLooper());
        handler.post(loop);
    }

    private final Runnable loop = new Runnable() {
        @Override public void run() {
            checkOnce(WatchService.this);
            handler.postDelayed(this, EVERY_MS);
        }
    };

    /** Fetches latest submissions; rings for any we have not seen. Returns count or -1 on error, -2 bad token. */
    public static int checkOnce(Context c) {
        SharedPreferences p = CallHelper.prefs(c);
        String token = p.getString(KEY_TOKEN, "");
        if (token.isEmpty()) return -2;
        PowerManager.WakeLock wl = null;
        try {
            wl = c.getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "clinto:check");
            wl.acquire(20_000);
            JSONArray arr = fetch(token);
            if (arr == null) return -2;
            Set<String> seen = new HashSet<>(p.getStringSet(KEY_SEEN, new HashSet<>()));
            boolean first = seen.isEmpty() && !p.getBoolean("primed", false);
            String newest = null;
            int fresh = 0;
            for (int i = arr.length() - 1; i >= 0; i--) {
                JSONObject s = arr.getJSONObject(i);
                String id = s.optString("id");
                if (seen.contains(id)) continue;
                seen.add(id);
                fresh++;
                newest = format(s.optJSONObject("data"));
            }
            p.edit().putStringSet(KEY_SEEN, seen).putBoolean("primed", true)
                    .putLong(KEY_LAST, System.currentTimeMillis()).apply();
            // first run only remembers old requests, no ringing
            if (!first && newest != null && p.getBoolean(CallHelper.KEY_ENABLED, true)) CallHelper.startCall(c, newest);
            return fresh;
        } catch (Exception e) {
            return -1;
        } finally {
            if (wl != null && wl.isHeld()) wl.release();
        }
    }

    static JSONArray fetch(String token) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(API).openConnection();
        con.setRequestProperty("Authorization", "Bearer " + token);
        con.setRequestProperty("User-Agent", "ClintoCaller/1.1");
        con.setConnectTimeout(15_000);
        con.setReadTimeout(15_000);
        int code = con.getResponseCode();
        if (code == 401 || code == 403) return null;
        if (code != 200) throw new Exception("HTTP " + code);
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(con.getInputStream(), "UTF-8"))) {
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
        }
        return new JSONArray(sb.toString());
    }

    static String format(JSONObject d) {
        if (d == null) return "New Clinto request";
        StringBuilder sb = new StringBuilder();
        add(sb, "Type", d.optString("Request type"));
        add(sb, "Name", d.optString("Name"));
        add(sb, "Business", d.optString("Business"));
        add(sb, "Phone", d.optString("Phone"));
        String sw = d.optString("Software needed");
        if (sw.isEmpty()) sw = d.optString("Current software");
        add(sb, "Software", sw);
        add(sb, "Made by", d.optString("Made by"));
        add(sb, "Wants", d.optString("Wants"));
        String det = d.optString("Details");
        if (det.isEmpty()) det = d.optString("Upgrade details");
        add(sb, "Details", det);
        add(sb, "Email", d.optString("Email"));
        return sb.toString().trim();
    }

    private static void add(StringBuilder sb, String k, String v) {
        if (v != null && !v.trim().isEmpty() && !"null".equals(v)) sb.append(k).append(": ").append(v.trim()).append("\n");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (handler != null) handler.removeCallbacksAndMessages(null);
        if (thread != null) thread.quitSafely();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
