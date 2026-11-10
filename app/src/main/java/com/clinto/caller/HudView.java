package com.clinto.caller;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.graphics.Typeface;
import android.view.View;
import android.view.animation.LinearInterpolator;

/** Animated futuristic HUD: rotating rings, radar sweep, pulsing core and voice waves. */
public class HudView extends View {

    public static final int CYAN = Color.parseColor("#29D3FF");
    public static final int ORANGE = Color.parseColor("#E8641B");

    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();
    private final ValueAnimator ticker = ValueAnimator.ofFloat(0f, 1f);

    private float t = 0f;          // running time in seconds
    private long lastNs = 0;
    private float speed = 1f;      // ring speed multiplier
    private float targetSpeed = 1f;
    private boolean talking = false;
    private float level = 0f;      // voice wave strength 0..1
    private String label = "CLINTO";

    public HudView(Context c) {
        super(c);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        text.setColor(Color.WHITE);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        text.setLetterSpacing(0.25f);
        ticker.setDuration(1000);
        ticker.setRepeatCount(ValueAnimator.INFINITE);
        ticker.setInterpolator(new LinearInterpolator());
        ticker.addUpdateListener(a -> {
            long now = System.nanoTime();
            float dt = lastNs == 0 ? 0 : Math.min(0.05f, (now - lastNs) / 1e9f);
            lastNs = now;
            speed += (targetSpeed - speed) * Math.min(1f, dt * 3f);
            t += dt * speed;
            float goal = talking ? 1f : 0f;
            level += (goal - level) * Math.min(1f, dt * 4f);
            invalidate();
        });
    }

    /** Ringing = fast, energetic rings. */
    public void setRinging(boolean ringing) { targetSpeed = ringing ? 2.4f : 1f; }

    /** Talking = voice waves around the core. */
    public void setTalking(boolean on) { talking = on; }

    public void setLabel(String s) { label = s; invalidate(); }

    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); lastNs = 0; ticker.start(); }

    @Override protected void onDetachedFromWindow() { ticker.cancel(); super.onDetachedFromWindow(); }

    private float dp(float v) { return v * getResources().getDisplayMetrics().density; }

    private int alpha(int color, int a) { return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color)); }

    private void arc(Canvas c, float cx, float cy, float r, float start, float sweep, float width, int color) {
        oval.set(cx - r, cy - r, cx + r, cy + r);
        stroke.setStrokeWidth(width);
        stroke.setColor(color);
        stroke.setShader(null);
        c.drawArc(oval, start, sweep, false, stroke);
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        float cx = w / 2f, cy = h / 2f;
        float R = Math.min(w, h) / 2f * 0.94f;
        if (R <= 0) return;

        // soft background glow
        fill.setShader(new RadialGradient(cx, cy, R, new int[]{alpha(CYAN, 60), alpha(CYAN, 18), Color.TRANSPARENT},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, R, fill);
        fill.setShader(null);

        // 1) outer tick ring (slow clockwise)
        c.save();
        c.rotate(t * 8f, cx, cy);
        stroke.setShader(null);
        for (int i = 0; i < 90; i++) {
            boolean big = i % 6 == 0;
            stroke.setStrokeWidth(big ? dp(2.2f) : dp(1.2f));
            stroke.setColor(alpha(CYAN, big ? 220 : 110));
            float r1 = R * (big ? 0.90f : 0.93f);
            c.drawLine(cx, cy - R, cx, cy - r1, stroke);
            c.rotate(4f, cx, cy);
        }
        c.restore();

        // 2) three long cyan arcs (clockwise)
        float a1 = t * 45f;
        for (int i = 0; i < 3; i++) arc(c, cx, cy, R * 0.84f, a1 + i * 120f, 72f, dp(5), alpha(CYAN, 230));

        // 3) dashed orange ring (counter-clockwise)
        float a2 = -t * 70f;
        for (int i = 0; i < 24; i++) arc(c, cx, cy, R * 0.74f, a2 + i * 15f, 8f, dp(4), alpha(ORANGE, 210));

        // 4) thin full ring + fast runner arc
        arc(c, cx, cy, R * 0.64f, 0, 360, dp(1.2f), alpha(CYAN, 120));
        arc(c, cx, cy, R * 0.64f, t * 160f, 40f, dp(3.5f), alpha(Color.WHITE, 230));
        arc(c, cx, cy, R * 0.64f, t * 160f + 180f, 18f, dp(3.5f), alpha(ORANGE, 230));

        // 5) radar sweep
        c.save();
        c.rotate(t * 120f, cx, cy);
        fill.setShader(new SweepGradient(cx, cy, new int[]{Color.TRANSPARENT, Color.TRANSPARENT, alpha(CYAN, 120)},
                new float[]{0f, 0.75f, 1f}));
        c.drawCircle(cx, cy, R * 0.58f, fill);
        fill.setShader(null);
        c.restore();

        // 6) voice waves around the core
        if (level > 0.02f) {
            int bars = 64;
            float base = R * 0.40f;
            for (int i = 0; i < bars; i++) {
                double ang = Math.toRadians(i * 360.0 / bars - 90);
                float n = (float) (Math.abs(Math.sin(t * 9 + i * 0.7)) * 0.6 + Math.abs(Math.sin(t * 5.3 + i * 1.9)) * 0.4);
                float len = R * 0.16f * n * level;
                float x1 = cx + (float) Math.cos(ang) * base, y1 = cy + (float) Math.sin(ang) * base;
                float x2 = cx + (float) Math.cos(ang) * (base + len), y2 = cy + (float) Math.sin(ang) * (base + len);
                stroke.setStrokeWidth(dp(3));
                stroke.setColor(alpha(i % 2 == 0 ? CYAN : ORANGE, 230));
                c.drawLine(x1, y1, x2, y2, stroke);
            }
        }

        // 7) pulsing core
        float pulse = 1f + 0.06f * (float) Math.sin(t * 4.0);
        float core = R * 0.34f * pulse;
        fill.setShader(new RadialGradient(cx, cy, core, new int[]{alpha(Color.WHITE, 255), alpha(CYAN, 230), alpha(CYAN, 40)},
                new float[]{0f, 0.35f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, core, fill);
        fill.setShader(null);
        fill.setColor(Color.parseColor("#071018"));
        c.drawCircle(cx, cy, core * 0.72f, fill);
        arc(c, cx, cy, core * 0.72f, 0, 360, dp(2), alpha(CYAN, 255));
        arc(c, cx, cy, core * 0.86f, -t * 200f, 120f, dp(2), alpha(ORANGE, 255));

        // label
        text.setTextSize(Math.max(dp(10), core * 0.30f));
        Paint.FontMetrics fm = text.getFontMetrics();
        c.drawText(label, cx, cy - (fm.ascent + fm.descent) / 2f, text);
    }
}
