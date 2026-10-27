package com.remap.buttons;

import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Lo que ves en tus apps de streaming (Netflix, Prime, Claro, Paramount+…) y lo parecido que hay en ellas.
 * Android no deja leer las recomendaciones de otras apps, así que se aprende de la sesión multimedia (MediaWatcher)
 * y el catálogo sale de TMDB, cuyos datos de "dónde verlo" son de JustWatch.
 *
 * Cada título es {clave, nombre, imagen, paquete}. La clave es "cat:v:…" (algo que viste: abre la app)
 * o "cat:r:<paquete>:<tipo>:<id>" (recomendado: lo busca en Google TV para ver la ficha).
 */
final class Catalog {
    static final String PREFIX = "cat:";
    private static final String API = "https://api.themoviedb.org/3";
    private static final String IMG = "https://image.tmdb.org/t/p/w780";
    private static final int MAX_SEEN = 20;
    private static final long TTL = 6 * 3600 * 1000L;
    /** Apps de música o del sistema: lo que suena ahí no es una película ni una serie. */
    private static final String[] NOT_VIDEO = {
        "com.spotify.tv.android", "com.google.android.youtube.tvmusic", "com.android.bluetooth", "com.amazon.mp3",
        "com.apple.android.music", "deezer.android.tv", "com.soundcloud.android", "com.remap.buttons"
    };
    /** Países que se pueden elegir en Ajustes ("" = automático). */
    static final String[] COUNTRIES = {"", "MX", "CO", "PE", "CL", "AR", "EC", "VE", "GT", "CR", "PA", "DO", "UY", "PY", "BO", "US", "ES"};

    private Catalog() { }

    static SharedPreferences sp(Context c) { return c.getSharedPreferences("catalogo", Context.MODE_PRIVATE); }

    static boolean enabled() { return !Keys.TMDB.isEmpty(); }

    // ---------- lo que viste ----------

    /** Apps de las que se aprende: cualquiera con imagen en el inicio, menos música y YouTube (eso va por Videos). */
    static boolean learnsFrom(Context c, String pkg) {
        for (String p : NOT_VIDEO) if (p.equals(pkg)) return false;
        for (String p : Videos.PLAYERS) if (p.equals(pkg)) return false;
        return Videos.installed(c, pkg);
    }

    /** {clave, nombre, imagen, paquete}, lo último primero. */
    static List<String[]> seen(Context c) { return parseItems(sp(c).getString("visto", "[]")); }

    /**
     * Anota lo que suena en una app. Hace red (busca el título en TMDB para la imagen y el nombre bueno):
     * llamar fuera del hilo principal. candidates: los textos que publica la app (título, álbum, subtítulo…).
     */
    static String remember(Context c, String pkg, List<String> candidates) {
        String[] m = null;
        if (enabled()) {
            for (String t : candidates) {
                try { m = match(c, t); } catch (Exception ignored) { }
                if (m != null) break;
            }
        }
        String name = m != null ? m[1] : candidates.get(0);
        String key = PREFIX + "v:" + pkg + (m != null ? ":" + m[0] : "::" + norm(name));
        String[] item = {key, name, m != null ? m[2] : "", pkg, String.valueOf(System.currentTimeMillis())};
        synchronized (Catalog.class) {
            List<String[]> list = seen(c);
            for (int i = list.size() - 1; i >= 0; i--) {
                if (list.get(i)[0].equals(key) || (list.get(i)[3].equals(pkg) && norm(list.get(i)[1]).equals(norm(name)))) list.remove(i);
            }
            list.add(0, item);
            while (list.size() > MAX_SEEN) list.remove(list.size() - 1);
            sp(c).edit().putString("visto", joinItems(list)).apply();
        }
        new Prefs(c).backup();
        return key;
    }

    /** Como remember, pero solo si algún texto es una película o serie que TMDB reconoce (textos leídos de la pantalla). */
    static void rememberIfKnown(Context c, String pkg, List<String> texts) {
        for (String t : texts) {
            String[] m = null;
            try { m = match(c, t); } catch (Exception ignored) { }
            if (m == null) continue;
            List<String> one = new ArrayList<>();
            one.add(m[1]);
            // el nombre tal como lo muestra la app, para buscarlo ahí al volver (AppSearch)
            sp(c).edit().putString("enApp_" + remember(c, pkg, one), t).putBoolean("pantalla_" + pkg, true).apply();
            return;
        }
    }

    static void clear(Context c) {
        sp(c).edit().clear().commit();
        new Prefs(c).backup();
    }

    // ---------- filas del inicio ----------

    /** Filas guardadas {título, items}, para pintar al instante. */
    static List<Object[]> cachedRows(Context c) {
        List<Object[]> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(sp(c).getString("filas", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                out.add(new Object[]{o.getString("t"), parseItems(o.getJSONArray("i").toString())});
            }
        } catch (Exception ignored) { }
        return out;
    }

    /** Hay que recalcular si pasaron 6 h, si viste algo nuevo o si cambiaron los ajustes. */
    static boolean stale(Context c, LauncherSettings s) {
        SharedPreferences sp = sp(c);
        long age = System.currentTimeMillis() - sp.getLong("filasAt", 0);
        return age > TTL || !sp.getString("filasFor", "").equals(signature(c, s));
    }

    private static String signature(Context c, LauncherSettings s) {
        List<String[]> seen = seen(c);
        return "2|" + (seen.isEmpty() ? "" : seen.get(0)[0]) + "|" + s.country() + "|" + s.offServices() + "|" + s.showBecause();
    }

    /**
     * Hace red: llamar fuera del hilo principal. "Porque viste X" con lo parecido a tus 2 últimos títulos que esté
     * en tus apps, y "Popular en <app>" por cada app activada en Ajustes.
     */
    static List<Object[]> rows(Context c, LauncherSettings s) {
        List<Object[]> out = new ArrayList<>();
        if (!enabled()) return out;
        String country = country(c, s);
        Map<String, Integer> services = services(c, country); // paquete -> proveedor
        Map<Integer, String> byProvider = new HashMap<>();
        for (Map.Entry<String, Integer> e : services.entrySet()) byProvider.put(e.getValue(), e.getKey());
        Set<String> shown = new HashSet<>();
        for (String[] v : seen(c)) shown.add(tmdbPart(v[0]));
        if (s.showBecause()) {
            int done = 0;
            for (String[] v : seen(c)) {
                if (done >= 2) break;
                String t = tmdbPart(v[0]);
                if (t.isEmpty()) continue;
                done++;
                try {
                    // si la app donde lo viste no está en TMDB, lo parecido se abre en esa misma app
                    String own = services.containsKey(v[3]) ? null : v[3];
                    List<String[]> items = similar(c, t, country, byProvider, shown, own);
                    if (!items.isEmpty()) out.add(new Object[]{"Porque viste " + v[1], items});
                } catch (Exception ignored) { }
            }
        }
        for (Map.Entry<String, Integer> e : services.entrySet()) {
            if (s.isServiceOff(e.getKey())) continue;
            try {
                List<String[]> items = popular(c, e.getValue(), e.getKey(), country, shown);
                if (!items.isEmpty()) out.add(new Object[]{"Popular en " + label(c, e.getKey()), items});
            } catch (Exception ignored) { }
        }
        for (String pkg : screenApps(c)) {
            if (services.containsKey(pkg) || s.isServiceOff(pkg)) continue;
            try {
                List<String[]> items = trending(c, pkg, country, shown);
                if (!items.isEmpty()) out.add(new Object[]{"Popular en " + label(c, pkg), items});
            } catch (Exception ignored) { }
        }
        try {
            JSONArray a = new JSONArray();
            for (Object[] r : out) a.put(new JSONObject().put("t", r[0]).put("i", new JSONArray(joinItems(itemsOf(r)))));
            sp(c).edit().putString("filas", a.toString()).putLong("filasAt", System.currentTimeMillis())
                    .putString("filasFor", signature(c, s)).apply();
        } catch (Exception ignored) { }
        return out;
    }

    /**
     * Lo que se anotó sin imagen (sin clave de TMDB o sin red en ese momento) se vuelve a buscar, como mucho cada
     * 10 min. Hace red: llamar fuera del hilo principal. Devuelve true si algo cambió.
     */
    static boolean completeSeen(Context c) {
        boolean changed = false;
        if (!enabled() || System.currentTimeMillis() - sp(c).getLong("rematchAt", 0) < 10 * 60 * 1000L) return false;
        sp(c).edit().putLong("rematchAt", System.currentTimeMillis()).apply();
        synchronized (Catalog.class) {
            List<String[]> list = seen(c);
            for (int i = list.size() - 1; i >= 0; i--) {
                // lo anotado por error con la regla vieja (títulos en otro alfabeto)
                String nm = list.get(i)[1];
                if (norm(nm).length() < nm.replaceAll("\\s", "").length() * 0.6) {
                    list.remove(i);
                    changed = true;
                }
            }
            for (int i = 0; i < list.size(); i++) {
                String[] it = list.get(i);
                if (!tmdbPart(it[0]).isEmpty()) continue;
                String[] m = null;
                try { m = match(c, it[1]); } catch (Exception ignored) { }
                if (m == null) continue;
                list.set(i, new String[]{PREFIX + "v:" + it[3] + ":" + m[0], m[1], m[2], it[3], it.length > 4 ? it[4] : "0"});
                changed = true;
            }
            if (changed) sp(c).edit().putString("visto", joinItems(list)).apply();
        }
        if (changed) new Prefs(c).backup();
        return changed;
    }

    /** Id de la sección de una fila: "porque" o "pop:<paquete>" (para ordenarlas en Ajustes). */
    static String sectionOf(Object[] row) {
        if (((String) row[0]).startsWith("Porque")) return "porque";
        List<String[]> items = itemsOf(row);
        return "pop:" + (items.isEmpty() ? "" : items.get(0)[3]);
    }

    /** Las apps que se leen de la pantalla (Xuper) van arriba por defecto. */
    static boolean isScreenApp(Context c, String pkg) { return sp(c).getBoolean("pantalla_" + pkg, false); }

    @SuppressWarnings("unchecked")
    static List<String[]> itemsOf(Object[] row) { return (List<String[]>) row[1]; }

    /**
     * Títulos que TMDB recomienda a partir de uno, solo los que están en alguna de tus apps. Con fallback (la app
     * donde se vio, si TMDB no la conoce) también entran los demás, para abrirlos en esa app.
     */
    private static List<String[]> similar(Context c, String tmdb, String country, Map<Integer, String> mine, Set<String> shown,
                                          String fallback) throws Exception {
        List<String[]> out = new ArrayList<>();
        if (mine.isEmpty() && fallback == null) return out;
        String type = tmdb.split(":")[0];
        JSONArray res = api(c, "/" + tmdb.replace(':', '/') + "/recommendations?language=" + lang(country)).getJSONArray("results");
        for (int i = 0; i < res.length() && i < 16 && out.size() < 10; i++) {
            JSONObject o = res.getJSONObject(i);
            String t = type + ":" + o.getInt("id");
            if (shown.contains(t) || o.optString("backdrop_path").isEmpty()) continue;
            String pkg = mine.isEmpty() ? null : whereToWatch(c, t, country, mine);
            if (pkg == null) pkg = fallback;
            if (pkg == null) continue;
            shown.add(t);
            out.add(item(o, type, pkg));
        }
        return out;
    }

    /** La primera de tus apps donde se puede ver el título (incluido en la suscripción), o null. */
    private static String whereToWatch(Context c, String tmdb, String country, Map<Integer, String> mine) throws Exception {
        JSONObject r = api(c, "/" + tmdb.replace(':', '/') + "/watch/providers").getJSONObject("results").optJSONObject(country);
        JSONArray flat = r == null ? null : r.optJSONArray("flatrate");
        for (int i = 0; flat != null && i < flat.length(); i++) {
            String pkg = mine.get(flat.getJSONObject(i).getInt("provider_id"));
            if (pkg != null) return pkg;
        }
        return null;
    }

    /** Apps de las que se leyó algo en pantalla (sin catálogo en TMDB, como Xuper) y siguen instaladas. */
    private static List<String> screenApps(Context c) {
        List<String> out = new ArrayList<>();
        for (String k : sp(c).getAll().keySet()) {
            if (!k.startsWith("pantalla_")) continue;
            String pkg = k.substring("pantalla_".length());
            if (Videos.installed(c, pkg)) out.add(pkg);
        }
        return out;
    }

    /** Lo más visto de la semana (películas y series), para abrir en una app que tiene de todo pero no está en TMDB. */
    private static List<String[]> trending(Context c, String pkg, String country, Set<String> shown) throws Exception {
        List<String[]> out = new ArrayList<>();
        JSONArray res = api(c, "/trending/all/week?language=" + lang(country)).getJSONArray("results");
        for (int i = 0; i < res.length() && out.size() < 16; i++) {
            JSONObject o = res.getJSONObject(i);
            String type = o.optString("media_type");
            if (!type.equals("tv") && !type.equals("movie")) continue;
            if (o.optString("backdrop_path").isEmpty() || shown.contains(type + ":" + o.getInt("id"))) continue;
            out.add(item(o, type, pkg));
        }
        return out;
    }

    /** Lo más visto de una app en el país: películas y series intercaladas. */
    private static List<String[]> popular(Context c, int provider, String pkg, String country, Set<String> shown) throws Exception {
        List<List<String[]>> both = new ArrayList<>();
        for (String type : new String[]{"tv", "movie"}) {
            List<String[]> l = new ArrayList<>();
            JSONArray res = api(c, "/discover/" + type + "?watch_region=" + country + "&with_watch_providers=" + provider
                    + "&with_watch_monetization_types=flatrate&sort_by=popularity.desc&language=" + lang(country)).getJSONArray("results");
            for (int i = 0; i < res.length(); i++) {
                JSONObject o = res.getJSONObject(i);
                if (o.optString("backdrop_path").isEmpty() || shown.contains(type + ":" + o.getInt("id"))) continue;
                l.add(item(o, type, pkg));
            }
            both.add(l);
        }
        List<String[]> out = new ArrayList<>();
        for (int i = 0; out.size() < 16 && (i < both.get(0).size() || i < both.get(1).size()); i++) {
            for (List<String[]> l : both) if (i < l.size() && out.size() < 16) out.add(l.get(i));
        }
        return out;
    }

    private static String[] item(JSONObject o, String type, String pkg) {
        String name = o.optString(type.equals("tv") ? "name" : "title");
        return new String[]{PREFIX + "r:" + pkg + ":" + type + ":" + o.optInt("id"), name, IMG + o.optString("backdrop_path"), pkg};
    }

    /** {tipo:id, nombre, imagen} si el texto es el nombre de una película o serie (o empieza por él), o null. */
    static String[] match(Context c, String text) throws Exception {
        String q = norm(text);
        if (q.length() < 4) return null; // "TV", "HD"… son etiquetas de la app, no títulos
        // TMDB no encuentra nada si van guiones largos o dos puntos ("Misión: Imposible – Sentencia mortal")
        String clean = text.replaceAll("[\\u2010-\\u2015:|\\-]+", " ").replaceAll("\\s+", " ").trim();
        JSONArray res = api(c, "/search/multi?query=" + URLEncoder.encode(clean, "UTF-8") + "&language=" + lang(country(c, null))).getJSONArray("results");
        for (int i = 0; i < res.length() && i < 8; i++) {
            JSONObject o = res.getJSONObject(i);
            String type = o.optString("media_type");
            if (!type.equals("tv") && !type.equals("movie")) continue;
            String name = o.optString(type.equals("tv") ? "name" : "title");
            String orig = o.optString(type.equals("tv") ? "original_name" : "original_title");
            for (String full : new String[]{name, orig}) {
                String n = norm(full);
                // casi todo en otro alfabeto ("TV 동물농장" queda en "tv"): no se compara
                if (n.length() < 4 || n.length() < full.replaceAll("\\s", "").length() * 0.6) continue;
                // "Stranger Things: Capítulo 1" también es Stranger Things
                if ((q.equals(n) || (q.startsWith(n) && n.length() >= 4))) {
                    String bd = o.optString("backdrop_path");
                    return new String[]{type + ":" + o.getInt("id"), name, bd.isEmpty() ? "" : IMG + bd};
                }
            }
        }
        return null;
    }

    // ---------- tus apps y sus proveedores ----------

    /**
     * Tus apps instaladas que TMDB conoce en el país: paquete -> id de proveedor. Se empareja por nombre
     * ("Prime Video" ~ "Amazon Prime Video", "Paramount+" ~ "Paramount Plus"), así sirve para cualquier app.
     */
    static Map<String, Integer> services(Context c, String country) {
        SharedPreferences sp = sp(c);
        if (sp.contains("serv_" + country) && System.currentTimeMillis() - sp.getLong("servAt_" + country, 0) < 7 * 24 * 3600 * 1000L) {
            return cachedServices(c, country);
        }
        Map<String, Integer> out = new LinkedHashMap<>();
        try {
            Map<String, Integer> providers = new HashMap<>(); // nombre normalizado -> id
            for (String type : new String[]{"movie", "tv"}) {
                JSONArray res = api(c, "/watch/providers/" + type + "?watch_region=" + country).getJSONArray("results");
                for (int i = 0; i < res.length(); i++) {
                    JSONObject o = res.getJSONObject(i);
                    providers.put(norm(o.getString("provider_name")), o.getInt("provider_id"));
                }
            }
            StringBuilder sb = new StringBuilder();
            for (ResolveInfo ri : launchable(c)) {
                String pkg = ri.activityInfo.packageName;
                if (!learnsFrom(c, pkg) || out.containsKey(pkg)) continue;
                String app = norm(label(c, pkg)).replace("tv", "");
                if (app.length() < 3) continue;
                // el proveedor de nombre más corto que contenga el de la app ("netflix", no "netflixbasicwithads")
                String best = null;
                for (String p : providers.keySet()) {
                    if (p.contains(app) && (best == null || p.length() < best.length())) best = p;
                }
                if (best == null) continue;
                out.put(pkg, providers.get(best));
                sb.append(sb.length() > 0 ? "," : "").append(pkg).append('=').append(providers.get(best));
            }
            sp.edit().putString("serv_" + country, sb.toString()).putLong("servAt_" + country, System.currentTimeMillis()).apply();
        } catch (Exception ignored) { }
        return out;
    }

    /** Las apps ya emparejadas, sin red (para Ajustes). country null = el que se usó la última vez. */
    static Map<String, Integer> cachedServices(Context c, String country) {
        SharedPreferences sp = sp(c);
        if (country == null) {
            country = new LauncherSettings(c).country();
            if (country.isEmpty()) country = sp.getString("paisAuto", "MX");
        }
        Map<String, Integer> out = new LinkedHashMap<>();
        for (String p : sp.getString("serv_" + country, "").split(",")) {
            String[] kv = p.split("=");
            if (kv.length == 2 && Videos.installed(c, kv[0])) out.put(kv[0], Integer.parseInt(kv[1]));
        }
        return out;
    }

    /** País detectado por la conexión la última vez, o "". */
    static String autoCountry(Context c) { return sp(c).getString("paisAuto", ""); }

    private static List<ResolveInfo> launchable(Context c) {
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        return c.getPackageManager().queryIntentActivities(i, 0);
    }

    static String label(Context c, String pkg) {
        try {
            PackageManager pm = c.getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            return String.valueOf(pm.getApplicationLabel(ai));
        } catch (Exception e) { return pkg; }
    }

    /** País del catálogo: el elegido en Ajustes o el de la conexión (se guarda una semana). */
    static String country(Context c, LauncherSettings s) {
        String chosen = s != null ? s.country() : new LauncherSettings(c).country();
        if (!chosen.isEmpty()) return chosen;
        SharedPreferences sp = sp(c);
        String cc = sp.getString("paisAuto", "");
        if (cc.isEmpty() || System.currentTimeMillis() - sp.getLong("paisAt", 0) > 7 * 24 * 3600 * 1000L) {
            try {
                String got = new JSONObject(Videos.get("https://ipwho.is/?fields=success,country_code")).optString("country_code");
                if (got.length() == 2) {
                    cc = got;
                    sp.edit().putString("paisAuto", cc).putLong("paisAt", System.currentTimeMillis()).apply();
                }
            } catch (Exception ignored) { }
        }
        return cc.isEmpty() ? "MX" : cc;
    }

    private static String lang(String country) { return "ES".equals(country) ? "es-ES" : "es-MX"; }

    // ---------- abrir ----------

    /**
     * Lo que viste se abre en su app (ahí sigue donde quedó). Lo recomendado se busca dentro de la app si acepta
     * búsquedas (Paramount+ sí, Netflix no); si no, se abre la app. La ficha de Google TV no carga sin su inicio.
     */
    static void open(Context c, String key, String name) {
        String[] p = key.split(":");
        if (p.length < 3) return;
        String pkg = p[2];
        Intent search = new Intent(Intent.ACTION_SEARCH).setPackage(pkg).putExtra(SearchManager.QUERY, name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        boolean searchable = search.resolveActivity(c.getPackageManager()) != null;
        if ("r".equals(p[1]) && searchable) {
            try {
                c.startActivity(search);
                return;
            } catch (Exception ignored) { }
        }
        // apps sin enlaces ni búsqueda de afuera (Xuper): se busca dentro con accesibilidad (solo APK directa)
        String inApp = sp(c).getString("enApp_" + key, name);
        if (!searchable && (sp(c).contains("enApp_" + key) || sp(c).getBoolean("pantalla_" + pkg, false))) KeyService.searchIn(pkg, inApp, "v".equals(p[1]));
        PackageManager pm = c.getPackageManager();
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (i == null) i = pm.getLaunchIntentForPackage(pkg);
        if (i == null) return;
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { c.startActivity(i); } catch (Exception ignored) { }
    }

    static String pkgOf(String key) {
        String[] p = key.split(":");
        return p.length > 2 ? p[2] : "";
    }

    // ---------- red y formatos ----------

    /** "tv:123" de una clave, o "" si no se encontró en TMDB. */
    private static String tmdbPart(String key) {
        String[] p = key.split(":");
        return p.length >= 5 && !p[3].isEmpty() ? p[3] + ":" + p[4] : "";
    }

    /** La clave de la API v3 va en la URL; el token largo (v4) va en la cabecera. */
    private static JSONObject api(Context c, String path) throws Exception {
        String key = Keys.TMDB;
        boolean bearer = key.length() > 40;
        String url = API + path + (bearer ? "" : (path.contains("?") ? "&" : "?") + "api_key=" + key);
        HttpURLConnection h = (HttpURLConnection) new URL(url).openConnection();
        h.setConnectTimeout(10000);
        h.setReadTimeout(10000);
        h.setRequestProperty("Accept", "application/json");
        if (bearer) h.setRequestProperty("Authorization", "Bearer " + key);
        InputStream in = h.getInputStream();
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
        in.close();
        return new JSONObject(bo.toString("UTF-8"));
    }

    /** Minúsculas, sin acentos ni signos; "+" cuenta como "plus" (Disney+ = Disney Plus). */
    static String norm(String s) {
        if (s == null) return "";
        String t = Normalizer.normalize(s.toLowerCase(Locale.ROOT).replace("+", "plus"), Normalizer.Form.NFD);
        return t.replaceAll("[^a-z0-9]", "");
    }

    static List<String[]> parseItems(String json) {
        List<String[]> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(json);
            for (int i = 0; i < a.length(); i++) {
                JSONArray x = a.getJSONArray(i);
                out.add(x.length() > 4 ? new String[]{x.getString(0), x.getString(1), x.getString(2), x.getString(3), x.getString(4)}
                        : new String[]{x.getString(0), x.getString(1), x.getString(2), x.getString(3)});
            }
        } catch (Exception ignored) { }
        return out;
    }

    static String joinItems(List<String[]> list) {
        JSONArray a = new JSONArray();
        for (String[] it : list) {
            JSONArray x = new JSONArray();
            for (String s : it) x.put(s);
            a.put(x);
        }
        return a.toString();
    }
}
