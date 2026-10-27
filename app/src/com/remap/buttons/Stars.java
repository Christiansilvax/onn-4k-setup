package com.remap.buttons;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import java.util.Random;

/**
 * Fondo de partículas: de noche, estrellas que titilan y suben muy despacio sobre un azul profundo; de día, puntos
 * de luz suaves sobre el blanco frío. Todo se calcula al crearlo: cada cuadro solo dibuja círculos (unos 8 por segundo).
 */
final class Stars extends Drawable implements Animated {
    private static final int N = 70;

    private final float[] x = new float[N], y = new float[N], r = new float[N], rise = new float[N], blink = new float[N], phase = new float[N];
    private final Paint basePaint = new Paint(), dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int top, bottom;
    private final long t0 = SystemClock.uptimeMillis();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            invalidateSelf();
            main.postDelayed(this, 125); // 8 por segundo: las estrellas son chicas y suben despacio
        }
    };
    private boolean running;
    private int alpha = 255;

    Stars(boolean dark) {
        top = dark ? 0xFF060A1C : 0xFFEEF3FB;
        bottom = dark ? 0xFF1B2045 : 0xFFDCE6F7;
        dot.setColor(dark ? 0xFFFFFFFF : 0xFF7FA8E8);
        Random rnd = new Random(7); // siempre el mismo cielo
        for (int i = 0; i < N; i++) {
            x[i] = rnd.nextFloat();
            y[i] = rnd.nextFloat();
            boolean big = rnd.nextFloat() < 0.15f;
            r[i] = (dark ? 1.2f : 2.5f) + rnd.nextFloat() * (big ? 2.4f : 1.2f) * (dark ? 1f : 2f);
            rise[i] = 0.004f + rnd.nextFloat() * 0.01f;  // fracción de pantalla por segundo
            blink[i] = 0.6f + rnd.nextFloat() * 1.6f;     // radianes por segundo
            phase[i] = rnd.nextFloat() * 6.283f;
        }
    }

    @Override protected void onBoundsChange(Rect b) {
        basePaint.setShader(new LinearGradient(0, b.top, 0, b.bottom, top, bottom, Shader.TileMode.CLAMP));
    }

    @Override public void draw(Canvas c) {
        Rect b = getBounds();
        c.drawRect(b, basePaint);
        float s = (SystemClock.uptimeMillis() - t0) / 1000f;
        for (int i = 0; i < N; i++) {
            float yy = y[i] - s * rise[i];
            yy -= (float) Math.floor(yy); // al salir por arriba vuelve por abajo
            float glow = 0.3f + 0.7f * (0.5f + 0.5f * (float) Math.sin(s * blink[i] + phase[i]));
            dot.setAlpha(Math.round(glow * alpha));
            c.drawCircle(b.left + x[i] * b.width(), b.top + yy * b.height(), r[i], dot);
        }
    }

    @Override public void start() {
        if (running) return;
        running = true;
        main.post(tick);
    }

    @Override public void stop() {
        running = false;
        main.removeCallbacks(tick);
    }

    /** El fundido entre fases (TransitionDrawable) cambia la opacidad. */
    @Override public void setAlpha(int a) {
        alpha = a;
        basePaint.setAlpha(a);
        invalidateSelf();
    }

    @Override public void setColorFilter(ColorFilter cf) { }
    @Override public int getOpacity() { return alpha == 255 ? PixelFormat.OPAQUE : PixelFormat.TRANSLUCENT; }
}
