package com.remap.buttons;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.widget.TextView;

/**
 * Colores del inicio: claro de día (amanecer y día) y oscuro al atardecer y de noche.
 * El claro es "cristal líquido" al estilo iOS: fondo Aurora, paneles esmerilados y foco por elevación.
 */
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
    /** cristal claro: parte de abajo del degradado, brillo del canto superior y color de la sombra al enfocar */
    final int glassEnd, rim, shadow;

    private Theme(boolean light, int text, int textSoft, int accent, int glass, int glassStroke, int focus, int focusStroke,
                  int chip, int scrimTop, int scrimMid, int scrimBottom, int veil, int glassEnd, int rim, int shadow) {
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
        this.glassEnd = glassEnd;
        this.rim = rim;
        this.shadow = shadow;
    }

    // velos del 75-90 %: la miniatura de fondo queda como un tinte y el texto siempre sobre un fondo parejo
    static final Theme DARK = new Theme(false, 0xFFFFFFFF, 0xFFFFFFFF, 0xFF8AB4FF,
            0x26FFFFFF, 0x33FFFFFF, 0x44FFFFFF, 0xFFFFFFFF,
            0x33FFFFFF, 0xBF060A1E, 0xD9060A1E, 0xE6060A1E, 0xFF0B1230,
            0x26FFFFFF, 0x33FFFFFF, 0xFF000000);

    // tinta casi negra, azul iOS y cristal blanco del 72 al 48 %; el velo solo aparece con la miniatura de fondo
    static final Theme LIGHT = new Theme(true, 0xFF1C1C1E, 0xFF55555F, 0xFF007AFF,
            0xB8FFFFFF, 0x99FFFFFF, 0xF7FFFFFF, 0xFFFFFFFF,
            0x1A1C1C1E, 0x40F4F7FC, 0x66F4F7FC, 0x8CF4F7FC, 0xFFF7F9FC,
            0x7AFFFFFF, 0xCCFFFFFF, 0xFF2F6FE0);

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

    /** Fondo de pantalla: Aurora animable de día, degradado de siempre al atardecer y de noche. */
    static Drawable backdrop(Context c, int phase) {
        Theme t = current(c, phase);
        if (t.light) return new Aurora(t == forPhase(phase) && phase == Weather.DAWN);
        return new GradientDrawable(GradientDrawable.Orientation.TL_BR, background(c, phase));
    }

    /**
     * Texto legible sobre cualquier fondo: semibold de día y negrilla con sombra oscura de noche.
     * Se llama después de fijar el color.
     */
    void style(TextView t) {
        if (light) { // semibold limpio, sin halo: el contraste lo dan la tinta y el cristal
            t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            t.setShadowLayer(0f, 0f, 0f, 0);
        } else {
            t.setTypeface(Typeface.DEFAULT_BOLD);
            t.setShadowLayer(10f, 0f, 2f, 0xCC000000);
        }
    }

    /** El mismo color con otra opacidad (0-255). */
    static int alpha(int color, int a) { return (color & 0x00FFFFFF) | (a << 24); }
}
