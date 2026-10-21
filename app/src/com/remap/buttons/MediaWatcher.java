package com.remap.buttons;

import android.content.ComponentName;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.service.notification.NotificationListenerService;
import java.net.URLEncoder;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Lee lo que TizenTube reproduce y lo guarda en "Seguir mirando". */
public class MediaWatcher extends NotificationListenerService {
    private MediaSessionManager msm;
    private MediaController watched;
    private String lastTitle = "";

    private final MediaController.Callback cb = new MediaController.Callback() {
        @Override public void onMetadataChanged(MediaMetadata m) { check(); }
        @Override public void onPlaybackStateChanged(PlaybackState s) { check(); }
    };

    private final MediaSessionManager.OnActiveSessionsChangedListener sessions = this::attach;

    @Override public void onListenerConnected() {
        msm = (MediaSessionManager) getSystemService(MEDIA_SESSION_SERVICE);
        ComponentName me = new ComponentName(this, MediaWatcher.class);
        msm.addOnActiveSessionsChangedListener(sessions, me);
        attach(msm.getActiveSessions(me));
    }

    private void attach(List<MediaController> list) {
        if (watched != null) { watched.unregisterCallback(cb); watched = null; }
        if (list == null) return;
        for (MediaController c : list) {
            if (HomeActivity.TIZEN.equals(c.getPackageName())) {
                watched = c;
                c.registerCallback(cb);
                check();
                return;
            }
        }
    }

    private void check() {
        final MediaController c = watched;
        if (c == null) return;
        PlaybackState st = c.getPlaybackState();
        MediaMetadata md = c.getMetadata();
        if (st == null || md == null || st.getState() != PlaybackState.STATE_PLAYING) return;
        final String title = md.getString(MediaMetadata.METADATA_KEY_TITLE);
        if (title == null || title.isEmpty() || title.equals(lastTitle)) return;
        lastTitle = title;
        String mediaId = md.getString(MediaMetadata.METADATA_KEY_MEDIA_ID);
        if (mediaId != null && mediaId.matches("[\\w-]{11}")) {
            Videos.remember(this, mediaId, title);
            return;
        }
        new Thread(() -> {
            try {
                String html = Videos.get("https://www.youtube.com/results?search_query=" + URLEncoder.encode(title, "UTF-8"));
                Matcher m = Pattern.compile("\"videoId\":\"([\\w-]{11})\"").matcher(html);
                if (m.find()) Videos.remember(this, m.group(1), title);
            } catch (Exception ignored) { }
        }).start();
    }
}
