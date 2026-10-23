package com.remap.buttons;

import android.content.ComponentName;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.SystemClock;
import android.service.notification.NotificationListenerService;
import java.net.URLEncoder;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lee lo que reproducen TizenTube y la app oficial de YouTube: lo guarda en "Seguir mirando" (con la app donde se vio)
 * y anota por dónde va para continuar ahí.
 */
public class MediaWatcher extends NotificationListenerService {
    private MediaSessionManager msm;
    private MediaController watched;
    private String lastTitle = "";
    private volatile String lastId; // ID del video que suena (se resuelve en segundo plano)
    private PlaybackState lastState;
    private MediaMetadata lastMeta;
    private String lastApp;

    private final MediaController.Callback cb = new MediaController.Callback() {
        @Override public void onMetadataChanged(MediaMetadata m) { check(); }
        @Override public void onPlaybackStateChanged(PlaybackState s) { check(); }
        @Override public void onSessionDestroyed() { savePosition(); } // TizenTube cierra la sesión al salir
    };

    private final MediaSessionManager.OnActiveSessionsChangedListener sessions = this::attach;

    @Override public void onListenerConnected() {
        msm = (MediaSessionManager) getSystemService(MEDIA_SESSION_SERVICE);
        ComponentName me = new ComponentName(this, MediaWatcher.class);
        msm.addOnActiveSessionsChangedListener(sessions, me);
        attach(msm.getActiveSessions(me));
    }

    private void attach(List<MediaController> list) {
        if (watched != null) { savePosition(); watched.unregisterCallback(cb); watched = null; }
        if (list == null) return;
        // la que esté sonando primero; si ninguna suena, la primera de YouTube que haya
        MediaController pick = null;
        for (MediaController c : list) {
            if (!java.util.Arrays.asList(Videos.PLAYERS).contains(c.getPackageName())) continue;
            PlaybackState st = c.getPlaybackState();
            if (st != null && st.getState() == PlaybackState.STATE_PLAYING) { pick = c; break; }
            if (pick == null) pick = c;
        }
        if (pick != null) {
            watched = pick;
            pick.registerCallback(cb);
            check();
        }
    }

    private void check() {
        final MediaController c = watched;
        if (c == null) return;
        PlaybackState st = c.getPlaybackState();
        MediaMetadata md = c.getMetadata();
        if (st == null || md == null) return;
        final String title = md.getString(MediaMetadata.METADATA_KEY_TITLE);
        if (title == null || title.isEmpty()) return;
        if (title.equals(lastTitle)) {
            lastState = st;
            lastMeta = md;
            savePosition();
            return;
        }
        if (st.getState() != PlaybackState.STATE_PLAYING) return;
        savePosition(); // el video anterior, antes de cambiar
        lastTitle = title;
        lastApp = c.getPackageName();
        lastId = null;
        lastState = st;
        lastMeta = md;
        String mediaId = md.getString(MediaMetadata.METADATA_KEY_MEDIA_ID);
        if (mediaId != null && mediaId.matches("[\\w-]{11}")) {
            lastId = mediaId;
            Videos.remember(this, mediaId, title, lastApp);
            return;
        }
        // TizenTube no publica el ID: se busca por el título
        final String app = lastApp;
        new Thread(() -> {
            try {
                String html = Videos.get("https://www.youtube.com/results?search_query=" + URLEncoder.encode(title, "UTF-8"));
                Matcher m = Pattern.compile("\"videoId\":\"([\\w-]{11})\"").matcher(html);
                if (m.find() && title.equals(lastTitle)) {
                    lastId = m.group(1);
                    Videos.remember(this, lastId, title, app);
                }
            } catch (Exception ignored) { }
        }).start();
    }

    /** Guarda por dónde va el video actual (estimado al momento si sigue sonando). */
    private void savePosition() {
        PlaybackState st = lastState;
        MediaMetadata md = lastMeta;
        String id = lastId;
        if (id == null || st == null || md == null) return;
        long pos = st.getPosition();
        if (st.getState() == PlaybackState.STATE_PLAYING) {
            pos += (long) ((SystemClock.elapsedRealtime() - st.getLastPositionUpdateTime()) * st.getPlaybackSpeed());
        }
        long dur = md.getLong(MediaMetadata.METADATA_KEY_DURATION);
        if (pos > 0) Videos.savePosition(this, id, dur > 0 ? Math.min(pos, dur) : pos, dur);
    }
}
