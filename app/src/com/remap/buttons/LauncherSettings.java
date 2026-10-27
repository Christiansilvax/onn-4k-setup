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
    boolean showWifi() { return sp.getBoolean("wifi", true); }
    boolean showBackdrop() { return sp.getBoolean("fondo", true); }
    boolean showBar() { return sp.getBoolean("barra", true); }
    boolean showReco() { return sp.getBoolean("recomendaciones", true); }
    /** Anotar lo que se ve en Netflix, Prime, Claro… (ver Catalog). */
    boolean learnApps() { return sp.getBoolean("aprender", true); }
    boolean showBecause() { return sp.getBoolean("porqueViste", true); }
    /** País del catálogo de TMDB; "" = el de la conexión. */
    String country() { return sp.getString("pais", ""); }
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

    /** Apps sin fila "Popular en…" (separadas por coma). */
    String offServices() { return sp.getString("sinCatalogo", ""); }

    boolean isServiceOff(String pkg) { return Arrays.asList(offServices().split(",")).contains(pkg); }

    void setServiceOff(String pkg, boolean off) {
        List<String> l = new ArrayList<>(Arrays.asList(offServices().split(",")));
        l.remove("");
        l.remove(pkg);
        if (off) l.add(pkg);
        save(sp.edit().putString("sinCatalogo", android.text.TextUtils.join(",", l)));
    }

    void setCountry(String c) { save(sp.edit().putString("pais", c)); }

    /**
     * Ids de secciones ("apps", "seguir", "youtube", "porque", "pop:<paquete>") en el orden del inicio: primero las
     * que el usuario ordenó y luego las nuevas en el orden de fábrica (Seguir mirando, Popular en Xuper…).
     */
    List<String> sectionOrder(List<String> ids) {
        List<String> saved = new ArrayList<>(Arrays.asList(sp.getString("ordenInicio", "").split(",")));
        List<String> out = new ArrayList<>();
        for (String id : saved) if (ids.contains(id)) out.add(id);
        List<String> rest = new ArrayList<>();
        for (String id : ids) if (!out.contains(id)) rest.add(id);
        rest.sort((a, b) -> defaultRank(a) - defaultRank(b));
        if (saved.size() <= 1) return rest; // sin orden guardado: el de fábrica
        for (String id : rest) {
            // una fila nueva de "Seguir mirando en <app>" va junto a la de YouTube, no al final
            int at = id.startsWith("seguir:") ? out.indexOf("seguir") : -1;
            if (at >= 0) out.add(at, id);
            else out.add(id);
        }
        return out;
    }

    private int defaultRank(String id) {
        // lo que ibas viendo en Xuper primero; luego lo de YouTube y lo de las demás apps
        if (id.startsWith("seguir:")) return Catalog.isScreenApp(ctx, id.substring(7)) ? 0 : 1;
        if (id.equals("seguir")) return 1;
        if (id.startsWith("pop:") && Catalog.isScreenApp(ctx, id.substring(4))) return 2;
        if (id.equals("apps")) return 3;
        if (id.equals("youtube")) return 4;
        if (id.equals("porque")) return 5;
        return 6;
    }

    /** Guarda el orden de las filas (las visibles primero; las ocultas detrás, en su orden). */
    void setSectionOrder(List<String> order) { save(sp.edit().putString("ordenInicio", android.text.TextUtils.join(",", order))); }

    /**
     * Fila quitada del inicio. Las que ya tenían su ajuste lo reutilizan (así tampoco se piden a internet):
     * YouTube = recomendaciones, Porque viste = porqueViste, Popular en <app> = sinCatalogo.
     */
    boolean isRowHidden(String id) {
        if (id.equals("youtube")) return !showReco();
        if (id.equals("porque")) return !showBecause();
        if (id.startsWith("pop:")) return isServiceOff(id.substring(4));
        return Arrays.asList(sp.getString("filasOcultas", "").split(",")).contains(id);
    }

    void setRowHidden(String id, boolean hidden) {
        if (id.equals("youtube")) { setBool("recomendaciones", !hidden); return; }
        if (id.equals("porque")) { setBool("porqueViste", !hidden); return; }
        if (id.startsWith("pop:")) { setServiceOff(id.substring(4), hidden); return; }
        List<String> l = new ArrayList<>(Arrays.asList(sp.getString("filasOcultas", "").split(",")));
        l.remove("");
        l.remove(id);
        if (hidden) l.add(id);
        save(sp.edit().putString("filasOcultas", android.text.TextUtils.join(",", l)));
    }

    /** Fondo animado: "auto" (sin animación: Aurora quieta de día, degradado de noche), "aurora:<paleta>" o "estrellas". */
    static final String[] BACKGROUNDS = {"auto", "aurora:cielo", "aurora:atardecer", "aurora:oceano", "aurora:bosque", "aurora:neon", "estrellas"};
    String background() {
        // las primeras versiones dejaban la Aurora animada; desde que es opcional todas las cajas arrancan quietas
        if (!sp.getBoolean("fondoQuieto", false)) sp.edit().putBoolean("fondoQuieto", true).putString("fondoAnimado", "auto").commit();
        return sp.getString("fondoAnimado", "auto");
    }
    void setBackground(String b) { save(sp.edit().putString("fondoAnimado", b)); }

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
    static final List<String> BACKUP_KEYS = Arrays.asList("tema", "banner", "clima", "wifi", "fondo", "barra", "recomendaciones", "cierreBarra", "ocultas", "inicioPropio",
            "aprender", "porqueViste", "pais", "sinCatalogo", "ordenInicio", "filasOcultas", "fondoAnimado");
}
