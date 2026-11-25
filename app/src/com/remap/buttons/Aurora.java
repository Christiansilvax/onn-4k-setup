package com.remap.buttons;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

/**
 * Fondo de día estilo iOS: manchas de color difusas sobre un blanco frío que derivan muy despacio.
 * start() y stop() encienden la animación (solo mientras el launcher está a la vista). Con dark, sobre un
 * azul noche y más tenue. La paleta se elige en Ajustes (Fondo animado).
 */
final class Aurora extends Drawable implements Animated {
    // {x, y, radio} relativos a la pantalla; cada mancha deriva en su propia órbita
    private static final float[][] SPOTS = {{0.12f, 0.18f, 0.62f}, {0.88f, 0.12f, 0.55f}, {0.78f, 0.92f, 0.60f}, {0.18f, 0.96f, 0.50f}};
    private static final int[] DAY = {0xFF8EC0FF, 0xFFC3B2FF, 0xFF9FE3D3, 0xFFFFCFAE};   // cielo, lavanda, menta, durazno
    private static final int[] DAWN = {0xFFFFC2A0, 0xFFFFB0CC, 0xFFCDBDFF, 0xFFFFDE96};  // durazno, rosa, lila, oro
    private static final int[] OCEAN = {0xFF5EC8F2, 0xFF4F7CFF, 0xFF6FE7D2, 0xFFA7C7FF};
    private static final int[] FOREST = {0xFF8FD694, 0xFF5FBF9A, 0xFFD8E7A0, 0xFFA3D9C9};
    private static final int[] NEON = {0xFFFF4FD8, 0xFF7A5CFF, 0xFF00E5FF, 0xFFFF9F45};

    private final int base;
    private final int[] colors;
    private final int core, mid; // opacidad del centro y del medio de cada mancha
    private final Paint basePaint = new Paint();
    private final Paint[] paints = new Paint[SPOTS.length];
    private final float[] radius = new float[SPOTS.length];
    private final Matrix m = new Matrix();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            t = (SystemClock.uptimeMillis() % 60000) / 60000f;
            render();
            invalidateSelf();
            main.postDelayed(this, 200);
        }
    };
    private boolean running, still;
    private float t;
    /**
     * Las manchas se pintan en un lienzo 6 veces más chico y se estiran a la pantalla: son tan difusas que se ve igual,
     * y la GPU de la caja pasa de pintar cinco pantallas enteras con degradados a pegar una sola imagen.
     */
    private static final int SCALE = 6;
    private Bitmap small;
    private Canvas smallCanvas;
    private final Paint out = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);

    /** La de fábrica: cielo de día, durazno al amanecer. */
    Aurora(boolean dawn) { this(dawn ? DAWN : DAY, dawn ? 0xFFFBF1EC : 0xFFEEF3FB, false); }

    /** Quieta: se pinta una vez y start() no hace nada (fondo sin animación). */
    Aurora still() {
        still = true;
        return this;
    }

    /** palette: cielo, atardecer, oceano, bosque o neon. */
    static Aurora of(String palette, boolean dark) {
        int[] p = "atardecer".equals(palette) ? DAWN : "oceano".equals(palette) ? OCEAN : "bosque".equals(palette) ? FOREST
                : "neon".equals(palette) ? NEON : DAY;
        return new Aurora(p, dark ? 0xFF0B1020 : 0xFFEEF3FB, dark);
    }

    private Aurora(int[] colors, int base, boolean dark) {
        this.base = base;
        this.colors = colors;
        core = dark ? 0x8C : 0xD9;
        mid = dark ? 0x38 : 0x66;
        basePaint.setColor(base);
        for (int i = 0; i < paints.length; i++) paints[i] = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    }

    @Override protected void onBoundsChange(Rect b) {
        int w = Math.max(1, b.width() / SCALE), h = Math.max(1, b.height() / SCALE);
        small = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        smallCanvas = new Canvas(small);
        float size = Math.max(w, h);
        for (int i = 0; i < SPOTS.length; i++) {
            radius[i] = Math.max(1f, size * SPOTS[i][2]);
            int c = colors[i];
            // centro en el origen: al dibujar solo se mueve con la matriz, sin crear degradados nuevos
            paints[i].setShader(new RadialGradient(0, 0, radius[i],
                    new int[]{Theme.alpha(c, core), Theme.alpha(c, mid), Theme.alpha(c, 0)},
                    new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        }
        render();
    }

    /** Pinta las manchas en el lienzo chico (en la CPU, unos pocos milisegundos). */
    private void render() {
        if (small == null) return;
        int w = small.getWidth(), h = small.getHeight();
        smallCanvas.drawColor(base);
        double a = t * 2 * Math.PI;
        for (int i = 0; i < SPOTS.length; i++) {
            float x = w * (SPOTS[i][0] + 0.07f * (float) Math.sin(a + i * 1.7));
            float y = h * (SPOTS[i][1] + 0.09f * (float) Math.cos(a * (i % 2 == 0 ? 1 : -1) + i));
            m.setTranslate(x, y);
            paints[i].getShader().setLocalMatrix(m);
            smallCanvas.drawCircle(x, y, radius[i], paints[i]);
        }
    }

    @Override public void draw(Canvas c) {
        if (small != null) c.drawBitmap(small, null, getBounds(), out);
    }

    /**
     * Un ciclo por minuto, redibujando 5 veces por segundo con un temporizador (no despierta la CPU en cada cuadro de
     * pantalla): se mueve tan lento que no se notan los saltos y la GPU de la caja queda libre.
     */
    @Override public void start() {
        if (running || still) return;
        running = true;
        main.post(tick);
    }

    @Override public void stop() {
        running = false;
        main.removeCallbacks(tick);
    }

    /** El fundido entre fases (TransitionDrawable) cambia la opacidad. */
    @Override public void setAlpha(int alpha) {
        out.setAlpha(alpha);
        invalidateSelf();
    }

    @Override public void setColorFilter(ColorFilter cf) { }
    @Override public int getOpacity() { return out.getAlpha() == 255 ? PixelFormat.OPAQUE : PixelFormat.TRANSLUCENT; }
}
