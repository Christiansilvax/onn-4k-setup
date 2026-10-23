package com.remap.buttons;

import android.content.Context;
import android.graphics.Typeface;
import android.widget.TextView;

/** Colores del inicio: claro de día (amanecer y día) y oscuro al atardecer y de noche. */
final class Theme {
    final boolean light;
    /** texto principal, texto secundario y acento (etiquetas en mayúsculas) */
    final int text, textSoft, accent;
    /** tarjetas de cristal: relleno y borde normales, relleno y borde al enfocar */
    final int glass, glassStroke, focus, focusStroke;
    /** relleno suave para fichas, pistas de progreso y botones en reposo */
    final int chip;
    /** velo sobre el fondo (arriba, medio, abajo) y color base de los velos del banner */
    final int scrimTop, scrimMid, scrimBottom, veil;

    private Theme(boolean light, int text, int textSoft, int accent, int glass, int glassStroke, int focus, int focusStroke,
                  int chip, int scrimTop, int scrimMid, int scrimBottom, int veil) {
        this.light = light;
        this.text = text;
        this.textSoft = textSoft;
        this.accent = accent;
        this.glass = glass;
        this.glassStroke = glassStroke;
        this.focus = focus;
        this.focusStroke = focusStroke;
        this.chip = chip;
        this.scrimTop = scrimTop;
        this.scrimMid = scrimMid;
        this.scrimBottom = scrimBottom;
        this.veil = veil;
    }

    // velos del 75-90 %: la miniatura de fondo queda como un tinte y el texto siempre sobre un fondo parejo
    static final Theme DARK = new Theme(false, 0xFFFFFFFF, 0xFFFFFFFF, 0xFF8AB4FF,
            0x26FFFFFF, 0x33FFFFFF, 0x44FFFFFF, 0xFFFFFFFF,
            0x33FFFFFF, 0xBF060A1E, 0xD9060A1E, 0xE6060A1E, 0xFF0B1230);

    static final Theme LIGHT = new Theme(true, 0xFF000000, 0xFF000000, 0xFF1D3FA8,
            0x99FFFFFF, 0x1F000000, 0xFFFFFFFF, 0xFF000000,
            0x1A000000, 0xBFF2F7FF, 0xD9F2F7FF, 0xE6F2F7FF, 0xFFF2F7FF);

    static Theme forPhase(int phase) {
        return phase == Weather.DAWN || phase == Weather.DAYTIME ? LIGHT : DARK;
    }

    /** El tema según el ajuste: automático (por la hora del sol), siempre claro o siempre oscuro. */
    static Theme current(Context c, int phase) {
        String mode = new LauncherSettings(c).theme();
        if (LauncherSettings.THEME_LIGHT.equals(mode)) return LIGHT;
        if (LauncherSettings.THEME_DARK.equals(mode)) return DARK;
        return forPhase(phase);
    }

    /** Degradado de fondo: el del momento del día, salvo que el tema forzado no coincida con él. */
    static int[] background(Context c, int phase) {
        Theme t = current(c, phase);
        if (t != forPhase(phase)) return Weather.colors(t.light ? Weather.DAYTIME : Weather.NIGHT);
        return Weather.colors(phase);
    }

    /**
     * Texto legible sobre cualquier fondo: negrilla y un halo (brillo blanco de día, sombra oscura de noche).
     * Se llama después de fijar el color.
     */
    void style(TextView t) {
        t.setTypeface(Typeface.DEFAULT_BOLD);
        if (light) t.setShadowLayer(8f, 0f, 0f, 0xE6FFFFFF);
        else t.setShadowLayer(10f, 0f, 2f, 0xCC000000);
    }

    /** El mismo color con otra opacidad (0-255). */
    static int alpha(int color, int a) { return (color & 0x00FFFFFF) | (a << 24); }
}
