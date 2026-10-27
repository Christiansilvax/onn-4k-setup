package com.remap.buttons;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.View;
import android.view.animation.OvershootInterpolator;

/**
 * Cristal de los botones y tarjetas. De noche: translúcido con borde fino que brilla al enfocar (como siempre).
 * De día, "cristal líquido": blanco esmerilado en degradado, un brillo en la mitad de arriba y canto de luz;
 * al enfocar se vuelve casi opaco y se eleva con una sombra azulada, como el foco de tvOS.
 */
final class Glass {
    private Glass() { }

    /** Tarjeta o panel: en reposo es cristal. */
    static StateListDrawable panel(Context c, Theme t, float radiusDp) {
        StateListDrawable s = new StateListDrawable();
        if (t.light) {
            s.addState(FOCUSED, liquid(c, t, radiusDp, false, t.focus, Theme.alpha(t.focus, 0xE6), t.focusStroke, 1.5f));
            s.addState(new int[]{}, liquid(c, t, radiusDp, false, t.glass, t.glassEnd, t.glassStroke, 1f));
        } else {
            s.addState(FOCUSED, plain(c, radiusDp, false, t.focus, 3, t.focusStroke));
            s.addState(new int[]{}, plain(c, radiusDp, false, t.glass, 1, t.glassStroke));
        }
        return s;
    }

    /** Botón pequeño (redondo o píldora): en reposo es una ficha suave. */
    static StateListDrawable chip(Context c, Theme t, float radiusDp, boolean oval) {
        StateListDrawable s = new StateListDrawable();
        if (t.light) {
            s.addState(FOCUSED, liquid(c, t, radiusDp, oval, t.focus, Theme.alpha(t.focus, 0xE6), t.focusStroke, 1.5f));
            s.addState(new int[]{}, liquid(c, t, radiusDp, oval, t.glass, t.glassEnd, t.glassStroke, 1f));
        } else {
            s.addState(FOCUSED, plain(c, radiusDp, oval, t.focus, 2, t.focusStroke));
            s.addState(new int[]{}, plain(c, radiusDp, oval, t.chip, 0, 0));
        }
        return s;
    }

    /** Agranda el elemento enfocado y lo eleva; de día con rebote y sombra del color del tema. */
    static void lift(View v, Theme t, boolean has, float scale) {
        float k = has ? scale : 1f;
        if (t.light) {
            v.animate().scaleX(k).scaleY(k).setDuration(260).setInterpolator(new OvershootInterpolator(1.2f)).start();
            v.setElevation(has ? dp(v.getContext(), 18) : 0f);
            if (android.os.Build.VERSION.SDK_INT >= 28) {
                v.setOutlineSpotShadowColor(t.shadow);
                v.setOutlineAmbientShadowColor(t.shadow);
            }
        } else {
            v.animate().scaleX(k).scaleY(k).setDuration(150).start();
            v.setElevation(has ? 24f : 0f);
        }
    }

    private static final int[] FOCUSED = {android.R.attr.state_focused};

    private static Drawable liquid(Context c, Theme t, float radiusDp, boolean oval, int top, int bottom, int stroke, float strokeDp) {
        float r = dp(c, radiusDp);
        GradientDrawable fill = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{top, bottom});
        shape(fill, oval, r);
        fill.setStroke(Math.max(1, Math.round(dp(c, strokeDp))), stroke);
        // brillo especular: la parte de arriba se aclara y se desvanece antes de la mitad
        GradientDrawable sheen = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{t.rim, Theme.alpha(t.rim, 0x26), Theme.alpha(t.rim, 0), Theme.alpha(t.rim, 0)});
        shape(sheen, oval, r);
        LayerDrawable l = new LayerDrawable(new Drawable[]{fill, sheen});
        int in = Math.round(dp(c, strokeDp));
        l.setLayerInset(1, in, in, in, in);
        return l;
    }

    private static GradientDrawable plain(Context c, float radiusDp, boolean oval, int color, int strokeDp, int stroke) {
        GradientDrawable g = new GradientDrawable();
        shape(g, oval, (int) dp(c, radiusDp));
        g.setColor(color);
        if (strokeDp > 0) g.setStroke((int) dp(c, strokeDp), stroke);
        return g;
    }

    private static void shape(GradientDrawable g, boolean oval, float r) {
        if (oval) g.setShape(GradientDrawable.OVAL);
        else g.setCornerRadius(r);
    }

    private static float dp(Context c, float v) { return v * c.getResources().getDisplayMetrics().density; }
}
