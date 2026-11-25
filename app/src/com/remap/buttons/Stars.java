package com.remap.buttons;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import java.util.Random;

/**
 * Fondo fijo: de noche, estrellas sobre un azul profundo; de día, puntos de luz suaves sobre el blanco frío. Se pinta
 * una sola vez en un bitmap y la pantalla solo lo pega: sin animación ni temporizadores (la GPU de la caja es justa).
 */
final class Stars extends Drawable implements Animated {
    private static final int N = 70;

    private final float[] x = new float[N], y = new float[N], r = new float[N], rise = new float[N], blink = new float[N], phase = new float[N];
    private final Paint basePaint = new Paint(), dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int top, bottom;
    private final Paint out = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private Bitmap cache;
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
        if (b.isEmpty()) return;
        cache = Bitmap.createBitmap(b.width(), b.height(), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(cache);
        c.drawRect(0, 0, b.width(), b.height(), basePaint);
        float s = 12f; // un instante cualquiera: así las estrellas quedan repartidas y a distintos brillos
        for (int i = 0; i < N; i++) {
            float yy = y[i] - s * rise[i];
            yy -= (float) Math.floor(yy);
            float glow = 0.3f + 0.7f * (0.5f + 0.5f * (float) Math.sin(s * blink[i] + phase[i]));
            dot.setAlpha(Math.round(glow * 255));
            c.drawCircle(x[i] * b.width(), yy * b.height(), r[i], dot);
        }
    }

    @Override public void draw(Canvas c) {
        if (cache != null) c.drawBitmap(cache, null, getBounds(), out);
    }

    @Override public void start() { }

    @Override public void stop() { }

    /** El fundido entre fases (TransitionDrawable) cambia la opacidad. */
    @Override public void setAlpha(int a) {
        alpha = a;
        out.setAlpha(a);
        invalidateSelf();
    }

    @Override public void setColorFilter(ColorFilter cf) { }
    @Override public int getOpacity() { return alpha == 255 ? PixelFormat.OPAQUE : PixelFormat.TRANSLUCENT; }
}
