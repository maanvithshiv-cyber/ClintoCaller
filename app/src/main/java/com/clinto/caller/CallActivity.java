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

import android.speech.tts.Voice;
import android.speech.tts.UtteranceProgressListener;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.graphics.drawable.LayerDrawable;

import java.util.Calendar;
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
    private HudView hud;
    private TextView title;
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
                setAssistantVoice();
                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override public void onStart(String id) { runOnUiThread(() -> { if (hud != null) hud.setTalking(true); }); }
                    @Override public void onDone(String id) { if ("end".equals(id)) runOnUiThread(() -> { if (hud != null) hud.setTalking(false); status.setText("Done. Tap Repeat to hear again."); }); }
                    @Override public void onError(String id) { runOnUiThread(() -> { if (hud != null) hud.setTalking(false); }); }
                });
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
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.parseColor("#02060B"), Color.parseColor("#071A26"), Color.parseColor("#02060B")});
        root.setBackground(bg);
        root.setPadding(dp(20), dp(40), dp(20), dp(32));

        TextView top = text("CLINTO  ·  SECURE LINE", 12, "#29D3FF", true);
        top.setLetterSpacing(0.3f);

        hud = new HudView(this);
        hud.setLabel("CLINTO");
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(dp(280), dp(280));
        hlp.setMargins(0, dp(12), 0, dp(8));

        title = text("INCOMING REQUEST", 24, "#FFFFFF", true);
        title.setLetterSpacing(0.18f);
        status = text("New customer is calling…", 15, "#E8641B", true);

        ScrollView sv = new ScrollView(this);
        details = text("", 16, "#CFEFFF", false);
        details.setGravity(Gravity.START);
        details.setTypeface(Typeface.MONOSPACE);
        details.setLineSpacing(0, 1.25f);
        details.setPadding(dp(18), dp(14), dp(18), dp(14));
        GradientDrawable box = new GradientDrawable();
        box.setColor(Color.parseColor("#CC071621"));
        box.setCornerRadius(dp(18));
        box.setStroke(dp(1), Color.parseColor("#6629D3FF"));
        details.setBackground(box);
        sv.addView(details);
        LinearLayout.LayoutParams svlp = new LinearLayout.LayoutParams(-1, 0, 1f);
        svlp.setMargins(0, dp(14), 0, dp(20));

        ringButtons = row();
        ringButtons.addView(round("✕\nDecline", "#C0392B", v -> decline()));
        ringButtons.addView(space());
        Button liftBtn = round("✆\nLift", "#1FAF6A", v -> lift());
        ringButtons.addView(liftBtn);
        pulse(liftBtn);

        talkButtons = row();
        talkButtons.addView(round("↻\nRepeat", "#E8641B", v -> speak()));
        talkButtons.addView(space());
        talkButtons.addView(round("✓\nClose", "#2B3A45", v -> finish()));
        talkButtons.setVisibility(View.GONE);

        root.addView(top);
        root.addView(hud, hlp);
        root.addView(title);
        root.addView(status);
        root.addView(sv, svlp);
        root.addView(ringButtons);
        root.addView(talkButtons);
        setContentView(root);

        // entrance animation
        root.setAlpha(0f);
        root.setScaleX(0.94f);
        root.setScaleY(0.94f);
        root.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(450).start();
    }

    private void pulse(View v) {
        ObjectAnimator a = ObjectAnimator.ofPropertyValuesHolder(v,
                PropertyValuesHolder.ofFloat("scaleX", 1f, 1.12f),
                PropertyValuesHolder.ofFloat("scaleY", 1f, 1.12f));
        a.setDuration(650);
        a.setRepeatCount(ValueAnimator.INFINITE);
        a.setRepeatMode(ValueAnimator.REVERSE);
        a.start();
    }

    private void startRinging() {
        if (hud != null) hud.setRinging(true);
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
        if (hud != null) hud.setRinging(false);
        title.setText("CONNECTED");
        status.setText("Reading the request…");
        ringButtons.setVisibility(View.GONE);
        talkButtons.setVisibility(View.VISIBLE);
        if (ttsReady) speak();
    }

    /** Deep male "assistant" voice. Uses the voice chosen in the app if any. */
    private void setAssistantVoice() {
        applyVoice(tts, CallHelper.prefs(this));
    }

    static final String KEY_VOICE = "voice_name";
    static final String[] MALE = {"en-gb-x-rjs", "en-gb-x-gbd", "en-gb-x-gbb", "en-us-x-iom", "en-us-x-iol",
            "en-us-x-tpd", "en-in-x-end", "en-in-x-ene", "en-au-x-aud", "en-au-x-aub"};

    static boolean isMale(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        for (String m : MALE) if (n.startsWith(m)) return true;
        return (n.contains("male") && !n.contains("female")) || n.contains("smtm") || n.contains("#male");
    }

    /** English voices, male ones first, offline-installed preferred. */
    static java.util.List<Voice> englishVoices(TextToSpeech tts) {
        java.util.List<Voice> out = new java.util.ArrayList<>();
        try {
            for (Voice v : tts.getVoices()) {
                if (v == null || v.getLocale() == null || !"en".equals(v.getLocale().getLanguage())) continue;
                if (v.getFeatures() != null && v.getFeatures().contains("notInstalled")) continue;
                out.add(v);
            }
        } catch (Exception ignored) { }
        java.util.Collections.sort(out, (a, b) -> score(b) - score(a));
        return out;
    }

    private static int score(Voice v) {
        String n = v.getName().toLowerCase(Locale.ROOT);
        int s = 0;
        for (int i = 0; i < MALE.length; i++) if (n.startsWith(MALE[i])) { s = 100 - i * 3; break; }
        if (s == 0 && isMale(n)) s = 60;
        if ("GB".equals(v.getLocale().getCountry())) s += 5;
        if (!v.isNetworkConnectionRequired()) s += 3;
        return s;
    }

    static void applyVoice(TextToSpeech tts, android.content.SharedPreferences p) {
        String saved = p.getString(KEY_VOICE, "");
        Voice best = null;
        boolean chosen = false;
        java.util.List<Voice> list = englishVoices(tts);
        for (Voice v : list) if (v.getName().equals(saved)) { best = v; chosen = true; break; }
        if (best == null && !list.isEmpty()) best = list.get(0);
        if (best != null) tts.setVoice(best);
        else {
            int r = tts.setLanguage(Locale.UK);
            if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) tts.setLanguage(Locale.ENGLISH);
        }
        boolean male = best != null && isMale(best.getName());
        tts.setPitch(chosen || male ? 0.85f : 0.68f); // no male voice found -> make it much deeper
        tts.setSpeechRate(0.92f);
    }

    private String greeting() {
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (h < 12) return "Good morning, boss.";
        if (h < 17) return "Good afternoon, boss.";
        return "Good evening, boss.";
    }

    private void speak() {
        if (!ttsReady) return;
        tts.stop();
        setAssistantVoice();
        tts.speak(greeting() + " Kotha request vachindi.", TextToSpeech.QUEUE_FLUSH, null, "greet");
        tts.playSilentUtterance(600, TextToSpeech.QUEUE_ADD, "gap1");
        tts.speak(CallHelper.speakable(requestText), TextToSpeech.QUEUE_ADD, null, "details");
        tts.playSilentUtterance(500, TextToSpeech.QUEUE_ADD, "gap2");
        tts.speak("That's all for now, boss.", TextToSpeech.QUEUE_ADD, null, "end");
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
        b.setTextSize(14);
        b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        int c = Color.parseColor(color);
        GradientDrawable glow = new GradientDrawable();
        glow.setShape(GradientDrawable.OVAL);
        glow.setColor(Color.argb(60, Color.red(c), Color.green(c), Color.blue(c)));
        GradientDrawable inner = new GradientDrawable();
        inner.setShape(GradientDrawable.OVAL);
        inner.setColor(c);
        inner.setStroke(dp(2), Color.argb(200, 255, 255, 255));
        LayerDrawable ld = new LayerDrawable(new android.graphics.drawable.Drawable[]{glow, inner});
        ld.setLayerInset(1, dp(8), dp(8), dp(8), dp(8));
        b.setBackground(ld);
        b.setLayoutParams(new LinearLayout.LayoutParams(dp(100), dp(100)));
        b.setOnClickListener(l);
        return b;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
