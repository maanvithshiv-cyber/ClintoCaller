package com.clinto.caller;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;

/** Shared helpers: settings + starting the "incoming call" for a new request. */
public class CallHelper {

    public static final String PREFS = "clinto_prefs";
    public static final String KEY_KEYWORD = "keyword";
    public static final String KEY_ENABLED = "enabled";
    public static final String DEFAULT_KEYWORD = "clinto request";

    public static final String EXTRA_TEXT = "request_text";
    public static final String EXTRA_ANSWER = "auto_answer";

    static final String CHANNEL_ID = "clinto_calls_v1";
    static final int NOTIF_ID = 4242;

    public static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static void ensureChannel(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return;
        NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "Clinto request calls",
                NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("Rings like a phone call when a new Clinto request arrives");
        Uri ring = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
        AudioAttributes aa = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        ch.setSound(ring, aa);
        ch.enableVibration(true);
        ch.setVibrationPattern(new long[]{0, 800, 600, 800, 600, 800});
        ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        ch.setBypassDnd(true);
        nm.createNotificationChannel(ch);
    }

    /** Shows a ringing call notification + full screen call screen. */
    public static void startCall(Context c, String requestText) {
        ensureChannel(c);

        Intent open = new Intent(c, CallActivity.class)
                .putExtra(EXTRA_TEXT, requestText)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent fullScreen = PendingIntent.getActivity(c, 1, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent lift = new Intent(c, CallActivity.class)
                .putExtra(EXTRA_TEXT, requestText)
                .putExtra(EXTRA_ANSWER, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent liftPi = PendingIntent.getActivity(c, 2, lift,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder b = new Notification.Builder(c, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.sym_call_incoming)
                .setContentTitle("📞 New Clinto request")
                .setContentText("Tap Lift to hear the details")
                .setCategory(Notification.CATEGORY_CALL)
                .setOngoing(true)
                .setAutoCancel(true)
                .setTimeoutAfter(60_000)
                .setContentIntent(fullScreen)
                .setFullScreenIntent(fullScreen, true)
                .addAction(new Notification.Action.Builder(null, "Lift", liftPi).build());

        Notification n = b.build();
        n.flags |= Notification.FLAG_INSISTENT; // keep ringing until handled
        c.getSystemService(NotificationManager.class).notify(NOTIF_ID, n);

        // When the phone is idle/locked Android opens the call screen from the full-screen intent.
        // Also try opening it directly (works when this app is allowed to).
        try { c.startActivity(open); } catch (Exception ignored) { }
    }

    public static void cancelCallNotification(Context c) {
        c.getSystemService(NotificationManager.class).cancel(NOTIF_ID);
    }

    /** Makes the text nice to listen to: reads phone numbers digit by digit, drops links. */
    public static String speakable(String raw) {
        if (raw == null) return "";
        String s = raw.replaceAll("https?://\\S+", " ")
                .replaceAll("[\\w.+-]+@[\\w.-]+", " email ")
                .replaceAll("[\\r\\n]+", ". ")
                .replaceAll("\\s+", " ");
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\+?\\d[\\d ]{8,14}\\d").matcher(s);
        StringBuffer out = new StringBuffer();
        while (m.find()) {
            String digits = m.group().replaceAll("\\D", "");
            StringBuilder spaced = new StringBuilder();
            for (char ch : digits.toCharArray()) spaced.append(ch).append(' ');
            m.appendReplacement(out, spaced.toString().trim());
        }
        m.appendTail(out);
        return out.toString().trim();
    }
}
