package com.remap.buttons;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Historial de videos abiertos y lectura de feeds RSS de YouTube. */
final class Videos {
    private static final int MAX_HISTORY = 12;

    static List<String[]> history(Context c) {
        List<String[]> out = new ArrayList<>();
        SharedPreferences sp = c.getSharedPreferences("videos", Context.MODE_PRIVATE);
        if (!sp.getBoolean("v2", false)) sp.edit().clear().putBoolean("v2", true).apply(); // borra pruebas viejas
        String s = sp.getString("h", "");
        for (String line : s.split("\n")) {
            String[] p = line.split("\\|", 2);
            if (p.length == 2 && p[0].length() > 0) out.add(p);
        }
        return out;
    }

    static void remember(Context c, String id, String name) {
        List<String[]> h = history(c);
        for (int i = h.size() - 1; i >= 0; i--) if (h.get(i)[0].equals(id)) h.remove(i);
        h.add(0, new String[]{id, name.replace("|", " ").replace("\n", " ")});
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < h.size() && i < MAX_HISTORY; i++) sb.append(h.get(i)[0]).append('|').append(h.get(i)[1]).append('\n');
        c.getSharedPreferences("videos", Context.MODE_PRIVATE).edit().putString("h", sb.toString()).apply();
        learnChannel(c, id);
    }

    /** Canales de los videos que ya viste: de ahi salen las recomendaciones. */
    static List<String> channels(Context c) {
        List<String> out = new ArrayList<>();
        String s = c.getSharedPreferences("videos", Context.MODE_PRIVATE).getString("ch", "");
        for (String x : s.split(",")) if (x.length() > 0) out.add(x);
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
                c.getSharedPreferences("videos", Context.MODE_PRIVATE).edit().putString("ch", sb.toString()).apply();
            } catch (Exception ignored) { }
        }).start();
    }

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
}
