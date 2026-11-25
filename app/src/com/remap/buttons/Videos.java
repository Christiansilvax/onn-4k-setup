package com.remap.buttons;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Historial de videos, canales y recomendaciones de YouTube, aprendidos solo de lo que se ve en la caja. */
final class Videos {
    static final String TIZEN = "io.gh.reisxd.tizentube.cobalt";
    static final String YOUTUBE = "com.google.android.youtube.tv";
    /** Apps de YouTube de las que se aprende lo que ves. */
    static final String[] PLAYERS = {TIZEN, YOUTUBE};

    private static final int MAX_HISTORY = 12;
    private static final int MAX_RECO = 30;
    private static final long RECO_TTL = 30 * 60 * 1000L;

    static SharedPreferences sp(Context c) { return c.getSharedPreferences("videos", Context.MODE_PRIVATE); }

    static List<String[]> history(Context c) {
        SharedPreferences sp = sp(c);
        if (!sp.getBoolean("v2", false)) sp.edit().clear().putBoolean("v2", true).apply(); // borra pruebas viejas
        return parse(sp.getString("h", ""));
    }

    /** Como remember, anotando además en qué app se vio (para abrirlo ahí mismo). */
    static void remember(Context c, String id, String name, String app) {
        sp(c).edit().putString("src_" + id, app).apply();
        remember(c, id, name);
    }

    /** La app con la que abrir un video: donde se vio, si sigue instalada; si no, TizenTube o YouTube. */
    static String player(Context c, String id) {
        for (String p : new String[]{sp(c).getString("src_" + id, null), TIZEN, YOUTUBE}) {
            if (p != null && installed(c, p)) return p;
        }
        return null;
    }

    static boolean installed(Context c, String pkg) {
        PackageManager pm = c.getPackageManager();
        return pm.getLeanbackLaunchIntentForPackage(pkg) != null || pm.getLaunchIntentForPackage(pkg) != null;
    }

    /** Quita un video de "Seguir mirando". */
    static void forget(Context c, String id) {
        List<String[]> h = history(c);
        for (int i = h.size() - 1; i >= 0; i--) if (h.get(i)[0].equals(id)) h.remove(i);
        sp(c).edit().putString("h", join(h, MAX_HISTORY)).apply();
        new Prefs(c).backup();
    }

    static void remember(Context c, String id, String name) {
        List<String[]> h = history(c);
        for (int i = h.size() - 1; i >= 0; i--) if (h.get(i)[0].equals(id)) h.remove(i);
        h.add(0, new String[]{id, name});
        sp(c).edit().putString("h", join(h, MAX_HISTORY)).putLong("t_" + id, System.currentTimeMillis()).apply();
        new Prefs(c).backup();
        learnChannel(c, id);
    }

    /** Borra historial, canales, recomendaciones, posiciones y la app de cada video (Ajustes). */
    static void clear(Context c) {
        SharedPreferences.Editor ed = sp(c).edit();
        for (String k : sp(c).getAll().keySet()) if (!k.equals("v2")) ed.remove(k);
        ed.commit();
        new Prefs(c).backup();
    }

    /** Cuándo se vio por última vez (ms), o 0 si es de antes de guardarlo. */
    static long seenAt(Context c, String id) { return sp(c).getLong("t_" + id, 0); }

    /** Dónde quedó un video y cuánto dura (ms), para continuar ahí y dibujar la barra roja. */
    static void savePosition(Context c, String id, long pos, long dur) {
        sp(c).edit().putString("p_" + id, pos + "," + dur).apply();
    }

    /** {posición, duración} en ms, o null si no se sabe. */
    static long[] position(Context c, String id) {
        String v = sp(c).getString("p_" + id, null);
        if (v == null) return null;
        String[] p = v.split(",");
        try { return new long[]{Long.parseLong(p[0]), Long.parseLong(p[1])}; } catch (Exception e) { return null; }
    }

    /** Canales de los videos que ya viste. */
    static List<String> channels(Context c) {
        List<String> out = new ArrayList<>();
        for (String x : sp(c).getString("ch", "").split(",")) if (x.length() > 0) out.add(x);
        return out;
    }

    /** Busca el canal dueño del video y lo suma a la lista (en segundo plano). */
    static void learnChannel(final Context c, final String videoId) {
        new Thread(() -> {
            try {
                String html = get("https://www.youtube.com/watch?v=" + videoId);
                Matcher m = Pattern.compile("\"channelId\":\"(UC[\\w-]{22})\"").matcher(html);
                if (!m.find()) return;
                List<String> ch = channels(c);
                ch.remove(m.group(1));
                ch.add(0, m.group(1));
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < ch.size() && i < 15; i++) sb.append(ch.get(i)).append(',');
                sp(c).edit().putString("ch", sb.toString()).apply();
                new Prefs(c).backup();
            } catch (Exception ignored) { }
        }).start();
    }

    // ---------- recomendaciones ----------

    /** Las últimas recomendaciones calculadas, para pintar al instante. */
    static List<String[]> cachedReco(Context c) { return parse(sp(c).getString("reco", "")); }

    /** Hay que recalcular si pasó media hora o si viste algo nuevo desde la última vez. */
    static boolean recoStale(Context c) {
        SharedPreferences sp = sp(c);
        List<String[]> h = history(c);
        String first = h.isEmpty() ? "" : h.get(0)[0];
        return System.currentTimeMillis() - sp.getLong("recoAt", 0) > RECO_TTL || !first.equals(sp.getString("recoFor", ""));
    }

    /**
     * Hace red: llamar fuera del hilo principal. Intercala, sin repetir ni incluir lo ya visto:
     * sugerencias de YouTube de tus 3 últimos videos y lo nuevo de los canales que ves.
     * Nada genérico: si no hay de dónde sacar, devuelve la lista vacía.
     */
    static List<String[]> recommend(Context c) {
        List<String[]> hist = history(c);
        Set<String> seen = new HashSet<>();
        for (String[] v : hist) seen.add(v[0]);
        List<List<String[]>> sources = new ArrayList<>();
        for (int i = 0; i < hist.size() && i < 3; i++) {
            try { sources.add(related(hist.get(i)[0], 6)); } catch (Exception ignored) { }
        }
        List<String> ch = channels(c);
        for (int i = 0; i < ch.size() && i < 8; i++) {
            try { sources.add(feed(ch.get(i), 3)); } catch (Exception ignored) { }
        }
        List<String[]> out = new ArrayList<>();
        for (int round = 0; out.size() < MAX_RECO; round++) {
            boolean any = false;
            for (List<String[]> s : sources) {
                if (round >= s.size()) continue;
                any = true;
                if (seen.add(s.get(round)[0]) && out.size() < MAX_RECO) out.add(s.get(round));
            }
            if (!any) break;
        }
        sp(c).edit().putString("reco", join(out, MAX_RECO)).putLong("recoAt", System.currentTimeMillis())
                .putString("recoFor", hist.isEmpty() ? "" : hist.get(0)[0]).apply();
        new Prefs(c).backup();
        return out;
    }

    /** Sugerencias que YouTube muestra junto al video (no pide cuenta). En la página el título va antes del ID. */
    static List<String[]> related(String videoId, int max) throws Exception {
        String html = get("https://www.youtube.com/watch?v=" + videoId);
        List<Integer> titlePos = new ArrayList<>();
        List<String> titles = new ArrayList<>();
        Matcher t = Pattern.compile("\"lockupMetadataViewModel\":\\{\"title\":\\{\"content\":\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(html);
        while (t.find()) {
            titlePos.add(t.start());
            titles.add(unescape(t.group(1)));
        }
        List<String[]> out = new ArrayList<>();
        Matcher m = Pattern.compile("\"contentId\":\"([\\w-]{11})\",\"contentType\":\"LOCKUP_CONTENT_TYPE_VIDEO\"").matcher(html);
        while (m.find() && out.size() < max) {
            String title = null;
            for (int i = titlePos.size() - 1; i >= 0; i--) {
                if (titlePos.get(i) < m.start()) { title = titles.get(i); break; }
            }
            if (title != null && !m.group(1).equals(videoId)) out.add(new String[]{m.group(1), title});
        }
        return out;
    }

    // ---------- red y formatos ----------

    static String get(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(10000);
        c.setRequestProperty("User-Agent", "Mozilla/5.0");
        c.setRequestProperty("Accept-Language", "es");
        c.setRequestProperty("Cookie", "CONSENT=YES+1; SOCS=CAI");
        InputStream in = c.getInputStream();
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
        in.close();
        return bo.toString("UTF-8");
    }

    /** Acepta @usuario o un ID que empieza con UC. */
    static String channelId(String handle) throws Exception {
        if (handle.startsWith("UC")) return handle;
        String html = get("https://www.youtube.com/" + handle);
        Matcher m = Pattern.compile("\"channelId\":\"(UC[\\w-]{22})\"").matcher(html);
        if (m.find()) return m.group(1);
        m = Pattern.compile("channel_id=(UC[\\w-]{22})").matcher(html);
        return m.find() ? m.group(1) : null;
    }

    static List<String[]> feed(String channelId, int max) throws Exception {
        String xml = get("https://www.youtube.com/feeds/videos.xml?channel_id=" + channelId);
        List<String[]> out = new ArrayList<>();
        Matcher m = Pattern.compile("<yt:videoId>([\\w-]{11})</yt:videoId>.*?<title>(.*?)</title>", Pattern.DOTALL).matcher(xml);
        while (m.find() && out.size() < max) {
            String t = m.group(2).replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">");
            out.add(new String[]{m.group(1), t});
        }
        return out;
    }

    /** Lista guardada como líneas "id|título". */
    static List<String[]> parse(String s) {
        List<String[]> out = new ArrayList<>();
        for (String line : s.split("\n")) {
            String[] p = line.split("\\|", 2);
            if (p.length == 2 && p[0].length() > 0) out.add(p);
        }
        return out;
    }

    static String join(List<String[]> list, int max) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size() && i < max; i++) {
            sb.append(list.get(i)[0]).append('|').append(list.get(i)[1].replace("|", "·").replace("\n", " ")).append('\n');
        }
        return sb.toString();
    }

    /** Quita los escapes de un texto JSON (&, \", \\…). */
    private static String unescape(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch != '\\' || i + 1 >= s.length()) { sb.append(ch); continue; }
            char n = s.charAt(++i);
            if (n == 'u' && i + 4 < s.length()) {
                sb.append((char) Integer.parseInt(s.substring(i + 1, i + 5), 16));
                i += 4;
            } else if (n == 'n') sb.append(' ');
            else sb.append(n);
        }
        return sb.toString();
    }
}
