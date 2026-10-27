package com.remap.buttons;

import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.animation.LinearInterpolator;

/**
 * Fondo de día estilo iOS: manchas de color difusas sobre un blanco frío que derivan muy despacio.
 * start() y stop() encienden la animación (solo mientras el launcher está a la vista).
 */
final class Aurora extends Drawable {
    // {x, y, radio} relativos a la pantalla; cada mancha deriva en su propia órbita
    private static final float[][] SPOTS = {{0.12f, 0.18f, 0.62f}, {0.88f, 0.12f, 0.55f}, {0.78f, 0.92f, 0.60f}, {0.18f, 0.96f, 0.50f}};
    private static final int[] DAY = {0xFF8EC0FF, 0xFFC3B2FF, 0xFF9FE3D3, 0xFFFFCFAE};   // cielo, lavanda, menta, durazno
    private static final int[] DAWN = {0xFFFFC2A0, 0xFFFFB0CC, 0xFFCDBDFF, 0xFFFFDE96};  // durazno, rosa, lila, oro

    private final int base;
    private final int[] colors;
    private final Paint basePaint = new Paint();
    private final Paint[] paints = new Paint[SPOTS.length];
    private final float[] radius = new float[SPOTS.length];
    private final Matrix m = new Matrix();
    private ValueAnimator anim;
    private float t;
    private long drawnAt;

    Aurora(boolean dawn) {
        base = dawn ? 0xFFFBF1EC : 0xFFEEF3FB;
        colors = dawn ? DAWN : DAY;
        basePaint.setColor(base);
        for (int i = 0; i < paints.length; i++) paints[i] = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    }

    @Override protected void onBoundsChange(Rect b) {
        float size = Math.max(b.width(), b.height());
        for (int i = 0; i < SPOTS.length; i++) {
            radius[i] = Math.max(1f, size * SPOTS[i][2]);
            int c = colors[i];
            // centro en el origen: al dibujar solo se mueve con la matriz, sin crear degradados nuevos
            paints[i].setShader(new RadialGradient(0, 0, radius[i],
                    new int[]{Theme.alpha(c, 0xD9), Theme.alpha(c, 0x66), Theme.alpha(c, 0)},
                    new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        }
    }

    @Override public void draw(Canvas c) {
        Rect b = getBounds();
        c.drawRect(b, basePaint);
        double a = t * 2 * Math.PI;
        for (int i = 0; i < SPOTS.length; i++) {
            float x = b.left + b.width() * (SPOTS[i][0] + 0.07f * (float) Math.sin(a + i * 1.7));
            float y = b.top + b.height() * (SPOTS[i][1] + 0.09f * (float) Math.cos(a * (i % 2 == 0 ? 1 : -1) + i));
            m.setTranslate(x, y);
            paints[i].getShader().setLocalMatrix(m);
            c.drawCircle(x, y, radius[i], paints[i]);
        }
    }

    /** Un ciclo por minuto, redibujando a unos 20 cuadros por segundo: el movimiento es lentísimo. */
    void start() {
        if (anim != null) return;
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(60000);
        anim.setRepeatCount(ValueAnimator.INFINITE);
        anim.setInterpolator(new LinearInterpolator());
        anim.addUpdateListener(v -> {
            long now = android.os.SystemClock.uptimeMillis();
            if (now - drawnAt < 50) return;
            drawnAt = now;
            t = (float) v.getAnimatedValue();
            invalidateSelf();
        });
        anim.start();
    }

    void stop() {
        if (anim == null) return;
        anim.cancel();
        anim = null;
    }

    /** El fundido entre fases (TransitionDrawable) cambia la opacidad. */
    @Override public void setAlpha(int alpha) {
        basePaint.setAlpha(alpha);
        for (Paint p : paints) p.setAlpha(alpha);
        invalidateSelf();
    }

    @Override public void setColorFilter(ColorFilter cf) { }
    @Override public int getOpacity() { return basePaint.getAlpha() == 255 ? PixelFormat.OPAQUE : PixelFormat.TRANSLUCENT; }
}
