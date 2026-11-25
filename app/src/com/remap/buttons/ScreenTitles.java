package com.remap.buttons;

import android.content.ComponentName;
import android.graphics.Rect;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.AudioPlaybackConfiguration;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.os.Handler;
import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Para apps de video que no avisan qué reproducen (sin sesión multimedia, como Xuper): mientras suena algo y esa app
 * está adelante, lee los textos grandes de su pantalla (y de la ficha que se abrió antes) y se queda con el que TMDB
 * reconoce como película o serie. Solo en la APK directa (Keys.SIDELOAD): Play Store no admite leer otras apps con
 * accesibilidad.
 */
final class ScreenTitles {
    private static final String TAG = "ScreenTitles";
    private static final long SETTLE_MS = 8000;     // espera a que la pantalla muestre el título

    private final KeyService svc;
    private final Handler main;
    private final List<String> recent = new ArrayList<>(); // textos de las últimas pantallas de la app de adelante
    private String recentPkg = "";
    private boolean playing;
    // por dónde va lo que suena: lo que marcaba el reproductor al anotarlo, más el tiempo que siguió sonando
    private String curKey;
    private long basePos, duration, playedMs, playSince;

    private final AudioManager.AudioPlaybackCallback audio = new AudioManager.AudioPlaybackCallback() {
        @Override public void onPlaybackConfigChanged(List<AudioPlaybackConfiguration> configs) {
            boolean was = playing;
            playing = false;
            for (AudioPlaybackConfiguration c : configs) {
                if (c.getAudioAttributes().getUsage() == AudioAttributes.USAGE_MEDIA) playing = true;
            }
            long now = System.currentTimeMillis();
            if (was && !playing && curKey != null) {
                playedMs += now - playSince;
                saveProgress();
            }
            if (!was && playing) playSince = now;
            if (playing) main.postDelayed(ScreenTitles.this::capture, 1500);
        }
    };

    private final Runnable learn = this::learn;

    ScreenTitles(KeyService s, Handler h) {
        svc = s;
        main = h;
    }

    static boolean available() { return Keys.SIDELOAD && Catalog.enabled(); }

    void start() {
        AudioManager am = (AudioManager) svc.getSystemService(KeyService.AUDIO_SERVICE);
        am.registerAudioPlaybackCallback(audio, main);
        audio.onPlaybackConfigChanged(am.getActivePlaybackConfigurations());
    }

    void stop() { ((AudioManager) svc.getSystemService(KeyService.AUDIO_SERVICE)).unregisterAudioPlaybackCallback(audio); }

    /** Cambió la pantalla: se guardan sus textos (la ficha que se abre antes de dar Play) y, si ya suena algo, se mira. */
    void windowChanged() { main.postDelayed(this::capture, 1500); }

    /** Lee la pantalla de adelante si es una app de video sin sesión; si además suena algo, programa anotarlo. */
    private void capture() {
        String pkg = addTexts();
        if (pkg == null || !playing) return;
        main.removeCallbacks(learn); // lo repetido no importa: Catalog.remember junta el mismo título
        main.postDelayed(learn, SETTLE_MS);
    }

    private void learn() {
        final String pkg = addTexts();
        if (pkg == null || !playing) return;
        final List<String> texts = new ArrayList<>(recent);
        final long[] times = playerTimes(); // {posición, duración} del reproductor, si se ven
        Log.d(TAG, pkg + " suena; textos: " + texts);
        new Thread(() -> {
            final String key = Catalog.rememberIfKnown(svc, pkg, texts);
            main.post(() -> {
                curKey = key;
                if (key == null || times == null) { curKey = null; return; }
                basePos = times[0];
                duration = times[1];
                playedMs = 0;
                playSince = System.currentTimeMillis();
                saveProgress();
            });
        }).start();
    }

    private void saveProgress() {
        long pos = basePos + playedMs + (playing ? System.currentTimeMillis() - playSince : 0);
        Catalog.saveProgress(svc, curKey, Math.min(pos, duration), duration);
    }

    /** Los tiempos que muestra el reproductor ("00:03" … "28:28"): el primero es la posición y el mayor la duración. */
    private long[] playerTimes() {
        AccessibilityNodeInfo root = svc.getRootInActiveWindow();
        if (root == null) return null;
        List<Long> t = new ArrayList<>();
        times(root, t, 0);
        if (t.size() < 2) return null;
        long max = 0;
        for (long x : t) max = Math.max(max, x);
        return t.get(0) < max ? new long[]{t.get(0), max} : null;
    }

    private static void times(AccessibilityNodeInfo n, List<Long> out, int depth) {
        if (n == null || depth > 40) return;
        CharSequence cs = n.getText();
        if (cs != null && cs.toString().trim().matches("\\d{1,2}:\\d{2}(:\\d{2})?")) {
            long ms = 0;
            for (String part : cs.toString().trim().split(":")) ms = ms * 60 + Long.parseLong(part);
            out.add(ms * 1000);
        }
        for (int i = 0; i < n.getChildCount(); i++) times(n.getChild(i), out, depth + 1);
    }

    private boolean watches(String pkg) {
        return new LauncherSettings(svc).learnApps() && Catalog.learnsFrom(svc, pkg) && !hasSession(pkg);
    }

    /** Las apps que sí publican sesión (Netflix, Paramount+…) ya las anota MediaWatcher. */
    private boolean hasSession(String pkg) {
        try {
            MediaSessionManager msm = (MediaSessionManager) svc.getSystemService(KeyService.MEDIA_SESSION_SERVICE);
            for (MediaController c : msm.getActiveSessions(new ComponentName(svc, MediaWatcher.class))) {
                if (pkg.equals(c.getPackageName())) return true;
            }
        } catch (Exception ignored) { } // sin acceso a notificaciones no se sabe: se lee igual
        return false;
    }

    /**
     * Suma los textos de la pantalla de adelante, de los más altos (títulos) a los más chicos, los nuevos primero.
     * Devuelve el paquete si es una app que se mira, o null.
     */
    private String addTexts() {
        AccessibilityNodeInfo root = svc.getRootInActiveWindow();
        if (root == null) return null;
        String pkg = String.valueOf(root.getPackageName());
        if (!watches(pkg)) return null;
        if (!pkg.equals(recentPkg)) recent.clear();
        recentPkg = pkg;
        List<Object[]> found = new ArrayList<>(); // {texto, alto}
        collect(root, found, 0);
        found.sort((a, b) -> (Integer) b[1] - (Integer) a[1]);
        Set<String> merged = new LinkedHashSet<>();
        for (int i = 0; i < found.size() && i < 6; i++) merged.add((String) found.get(i)[0]);
        merged.addAll(recent);
        recent.clear();
        for (String t : merged) if (recent.size() < 15) recent.add(t);
        return pkg;
    }

    private static void collect(AccessibilityNodeInfo n, List<Object[]> out, int depth) {
        if (n == null || depth > 40) return;
        CharSequence t = n.getText();
        if (t != null) {
            String s = t.toString().trim();
            if (s.length() >= 2 && s.length() <= 80 && !s.matches("[\\d\\s:./|-]+")) {
                Rect r = new Rect();
                n.getBoundsInScreen(r);
                out.add(new Object[]{s, r.height()});
            }
        }
        for (int i = 0; i < n.getChildCount(); i++) collect(n.getChild(i), out, depth + 1);
    }
}
