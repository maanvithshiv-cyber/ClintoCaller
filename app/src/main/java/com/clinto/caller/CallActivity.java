package com.clinto.caller;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Locale;

/** Full screen "incoming call". Lift = stop ringing and read the request aloud. */
public class CallActivity extends Activity {

    private MediaPlayer player;
    private Vibrator vibrator;
    private TextToSpeech tts;
    private boolean ttsReady = false, answered = false, teluguOk = false;
    private final Locale TELUGU = new Locale("te", "IN"), ENGLISH_IN = new Locale("en", "IN");
    private String requestText = "";
    private TextView status, details;
    private LinearLayout ringButtons, talkButtons;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        if (Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(true); setTurnScreenOn(true); }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);

        buildUi();
        tts = new TextToSpeech(this, st -> {
            if (st == TextToSpeech.SUCCESS) {
                teluguOk = tts.isLanguageAvailable(TELUGU) >= TextToSpeech.LANG_AVAILABLE;
                setEnglish();
                tts.setSpeechRate(0.9f);
                ttsReady = true;
                if (answered) speak();
            }
        });
        handle(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handle(intent);
    }

    private void handle(Intent i) {
        CallHelper.cancelCallNotification(this); // stop the notification ringing; this screen rings instead
        String t = i.getStringExtra(CallHelper.EXTRA_TEXT);
        if (t != null) requestText = t;
        details.setText(requestText);
        if (i.getBooleanExtra(CallHelper.EXTRA_ANSWER, false)) lift();
        else if (!answered) startRinging();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(Color.parseColor("#1C1B19"));
        root.setPadding(dp(24), dp(70), dp(24), dp(40));

        TextView icon = text("📞", 64, "#FFFFFF", false);
        TextView title = text("Clinto Request", 30, "#FFFFFF", true);
        status = text("Incoming request call…", 16, "#E8641B", true);

        ScrollView sv = new ScrollView(this);
        details = text("", 17, "#D9D3C7", false);
        details.setGravity(Gravity.START);
        details.setPadding(dp(18), dp(16), dp(18), dp(16));
        GradientDrawable box = new GradientDrawable();
        box.setColor(Color.parseColor("#2B2A27"));
        box.setCornerRadius(dp(16));
        details.setBackground(box);
        sv.addView(details);
        LinearLayout.LayoutParams svlp = new LinearLayout.LayoutParams(-1, 0, 1f);
        svlp.setMargins(0, dp(28), 0, dp(28));

        ringButtons = row();
        ringButtons.addView(round("Decline", "#C0392B", v -> decline()));
        ringButtons.addView(space());
        ringButtons.addView(round("Lift", "#2E8B57", v -> lift()));

        talkButtons = row();
        talkButtons.addView(round("Repeat", "#E8641B", v -> speak()));
        talkButtons.addView(space());
        talkButtons.addView(round("Close", "#55524B", v -> finish()));
        talkButtons.setVisibility(View.GONE);

        root.addView(icon);
        root.addView(title);
        root.addView(status);
        root.addView(sv, svlp);
        root.addView(ringButtons);
        root.addView(talkButtons);
        setContentView(root);
    }

    private void startRinging() {
        try {
            AudioManager am = getSystemService(AudioManager.class);
            am.setStreamVolume(AudioManager.STREAM_RING, am.getStreamMaxVolume(AudioManager.STREAM_RING), 0);
        } catch (Exception ignored) { }
        try {
            Uri uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
            if (uri == null) uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
            player.setDataSource(this, uri);
            player.setLooping(true);
            player.prepare();
            player.start();
        } catch (Exception ignored) { }
        try {
            vibrator = getSystemService(Vibrator.class);
            vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0, 900, 700}, 0));
        } catch (Exception ignored) { }
        handler.postDelayed(() -> { if (!answered) { stopRinging(); status.setText("Missed request — open your mail"); } }, 60_000);
    }

    private void stopRinging() {
        try { if (player != null) { player.stop(); player.release(); } } catch (Exception ignored) { }
        player = null;
        try { if (vibrator != null) vibrator.cancel(); } catch (Exception ignored) { }
    }

    private void lift() {
        answered = true;
        stopRinging();
        status.setText("Reading the request…");
        ringButtons.setVisibility(View.GONE);
        talkButtons.setVisibility(View.VISIBLE);
        if (ttsReady) speak();
    }

    private void setEnglish() {
        int r = tts.setLanguage(ENGLISH_IN);
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) tts.setLanguage(Locale.ENGLISH);
    }

    private void speak() {
        if (!ttsReady) return;
        tts.stop();
        // 1) greeting: Telugu voice if the phone has it, otherwise the same words in English voice
        if (teluguOk) {
            tts.setLanguage(TELUGU);
            tts.speak("హలో బాస్, కొత్త రిక్వెస్ట్ వచ్చింది.", TextToSpeech.QUEUE_FLUSH, null, "greet");
        } else {
            setEnglish();
            tts.speak("Hello boss, kotha request vachindi.", TextToSpeech.QUEUE_FLUSH, null, "greet");
        }
        // 2) details in English (names, numbers read clearly)
        setEnglish();
        tts.playSilentUtterance(500, TextToSpeech.QUEUE_ADD, "gap");
        tts.speak(CallHelper.speakable(requestText), TextToSpeech.QUEUE_ADD, null, "details");
    }

    private void decline() {
        stopRinging();
        finish();
    }

    @Override
    protected void onDestroy() {
        stopRinging();
        handler.removeCallbacksAndMessages(null);
        if (tts != null) { tts.stop(); tts.shutdown(); }
        super.onDestroy();
    }

    // ---- small UI helpers ----
    private TextView text(String s, int sp, String color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(Color.parseColor(color));
        t.setGravity(Gravity.CENTER);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, dp(4), 0, dp(4));
        return t;
    }

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER);
        return r;
    }

    private View space() {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(48), 1));
        return v;
    }

    private Button round(String label, String color, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(Color.WHITE);
        b.setTextSize(16);
        b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(Color.parseColor(color));
        b.setBackground(bg);
        b.setLayoutParams(new LinearLayout.LayoutParams(dp(92), dp(92)));
        b.setOnClickListener(l);
        return b;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
