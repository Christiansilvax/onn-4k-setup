package com.remap.buttons;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Ajustes del launcher (pantalla SettingsActivity). Cada cambio sube "stamp" para que el inicio sepa que debe
 * reconstruirse, y se copia al respaldo JSON.
 */
final class LauncherSettings {
    static final String THEME_AUTO = "auto", THEME_LIGHT = "claro", THEME_DARK = "oscuro";

    private final Context ctx;
    private final SharedPreferences sp;

    LauncherSettings(Context c) {
        ctx = c;
        sp = sp(c);
    }

    static SharedPreferences sp(Context c) { return c.getSharedPreferences("launcher", Context.MODE_PRIVATE); }

    String theme() { return sp.getString("tema", THEME_AUTO); }
    boolean showHero() { return sp.getBoolean("banner", true); }
    boolean showWeather() { return sp.getBoolean("clima", true); }
    boolean showBackdrop() { return sp.getBoolean("fondo", true); }
    boolean showBar() { return sp.getBoolean("barra", true); }
    boolean showReco() { return sp.getBoolean("recomendaciones", true); }
    /** Si aparece el inicio de Google TV (p. ej. al encender), se cambia por este. */
    boolean replaceGoogleHome() { return sp.getBoolean("inicioPropio", true); }
    /** Segundos sin reproducir antes de que la barra se cierre sola; 0 = nunca. */
    int barCloseSeconds() { return sp.getInt("cierreBarra", 60); }

    List<String> hiddenApps() {
        List<String> out = new ArrayList<>();
        for (String p : sp.getString("ocultas", "").split(",")) if (!p.isEmpty()) out.add(p);
        return out;
    }

    boolean isHidden(String pkg) { return hiddenApps().contains(pkg); }

    void setHidden(String pkg, boolean hidden) {
        List<String> h = hiddenApps();
        h.remove(pkg);
        if (hidden) h.add(pkg);
        StringBuilder sb = new StringBuilder();
        for (String p : h) sb.append(sb.length() > 0 ? "," : "").append(p);
        save(sp.edit().putString("ocultas", sb.toString()));
    }

    void setTheme(String t) { save(sp.edit().putString("tema", t)); }
    void setBool(String key, boolean v) { save(sp.edit().putBoolean(key, v)); }
    void setBarCloseSeconds(int s) { save(sp.edit().putInt("cierreBarra", s)); }

    /** Cambia cada vez que se toca un ajuste. */
    long stamp() { return sp.getLong("stamp", 0); }

    private void save(SharedPreferences.Editor ed) {
        ed.putLong("stamp", stamp() + 1).commit(); // commit: el inicio lo lee al volver
        new Prefs(ctx).backup();
    }

    /** Claves que van al respaldo (sin "stamp", que es solo interno). */
    static final List<String> BACKUP_KEYS = Arrays.asList("tema", "banner", "clima", "fondo", "barra", "recomendaciones", "cierreBarra", "ocultas", "inicioPropio");
}
