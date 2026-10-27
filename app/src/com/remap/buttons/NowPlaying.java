package com.remap.buttons;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.List;

/**
 * Barra de reproducción del inicio, con tres modos:
 * - otra app suena (lector de notificaciones MediaWatcher): la controla con ⏮ ⏯ ⏭;
 * - reproduciendo aquí: el video de YouTube suena dentro de la barra (reproductor embed en el hueco de la miniatura);
 * - en reposo: el último video visto con su progreso; ▶ lo reproduce aquí mismo desde donde quedó.
 * ⛶ abre el video en TizenTube a pantalla completa, en el minuto actual. ✕ deja de reproducir y cierra la barra
 * (no vuelve hasta que se vea otro video); también se cierra sola tras un minuto sin reproducir.
 */
final class NowPlaying extends LinearLayout {
    /** Lo que el inicio sabe de los videos: el último visto, el siguiente recomendado, y abrir/anotar. */
    interface Last {
        String[] get();
        String[] next(String afterId);
        void played(String id, String title);
        void open(String id, String title);
    }

    // estados del reproductor de YouTube (IFrame API)
    private static final int YT_ENDED = 0, YT_PLAYING = 1, YT_BUFFERING = 3;

    private final Activity act;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final FrameLayout artBox;
    private final ImageView art;
    private final TextView source, title;
    private final View progress;
    private final FrameLayout track;
    private final ImageView prev, playPause, next, full, close;
    private final Runnable autoClose = this::close;
    private final Last last;
    private final Theme theme;
    private String[] lastShown; // último video visto, en reposo
    private String artFor;
    private MediaSessionManager msm;
    private MediaController ctl;

    // reproducción aquí mismo: el reproductor se precarga en pausa para que ▶ arranque al instante
    private WebView web;
    private String webId;      // video cargado en el reproductor
    private boolean webReady;
    private long webStartS;    // segundo donde debe arrancar
    private boolean seekChecked;
    private String localId, localTitle; // video que suena aquí (null si no)
    private double localPos, localDur;
    private int localState = -1;
    private final Runnable warm = this::warm;

    private final MediaController.Callback cb = new MediaController.Callback() {
        @Override public void onMetadataChanged(MediaMetadata m) { update(); }
        @Override public void onPlaybackStateChanged(PlaybackState s) { update(); }
        @Override public void onSessionDestroyed() { pick(null); }
    };
    private final MediaSessionManager.OnActiveSessionsChangedListener sessions = this::pick;
    private final Runnable tick = new Runnable() {
        @Override public void run() { updateProgress(); main.postDelayed(this, 1000); }
    };

    NowPlaying(Activity a, Theme theme, Last last) {
        super(a);
        act = a;
        this.theme = theme;
        this.last = last;
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setPadding(dp(12), dp(9), dp(12), dp(9));
        setBackground(glass(18));
        setVisibility(GONE);
        // la barra no toma el foco: solo sus botones, así OK nunca saca del inicio por accidente
        setFocusable(false);

        artBox = new FrameLayout(a);
        artBox.setBackgroundColor(theme.chip);
        artBox.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(12)); }
        });
        artBox.setClipToOutline(true);
        art = new ImageView(a);
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        artBox.addView(art, new FrameLayout.LayoutParams(-1, -1));
        addView(artBox, new LayoutParams(dp(112), dp(63)));

        LinearLayout col = new LinearLayout(a);
        col.setOrientation(VERTICAL);
        col.setPadding(dp(16), 0, dp(16), 0);
        source = new TextView(a);
        source.setTextColor(theme.accent);
        theme.style(source);
        source.setTextSize(12);
        source.setLetterSpacing(0.08f);
        col.addView(source);
        title = new TextView(a);
        title.setTextColor(theme.text);
        theme.style(title);
        title.setTextSize(17);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        col.addView(title);
        track = new FrameLayout(a);
        track.setBackground(pill(theme.chip));
        progress = new View(a);
        progress.setBackground(pill(theme.text));
        track.addView(progress, new FrameLayout.LayoutParams(0, -1));
        LayoutParams tlp = new LayoutParams(-1, dp(4));
        tlp.topMargin = dp(8);
        col.addView(track, tlp);
        addView(col, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

        prev = button(R.drawable.ic_prev, v -> { if (ctl != null) ctl.getTransportControls().skipToPrevious(); });
        playPause = button(R.drawable.ic_play, v -> togglePlay());
        next = button(R.drawable.ic_next, v -> skipNext());
        full = button(R.drawable.ic_full, v -> openFull());
        close = button(R.drawable.ic_close, v -> close());
        addView(prev);
        addView(playPause);
        addView(next);
        addView(full);
        addView(close);
    }

    /** Llamar en onResume de la pantalla. */
    void start() {
        try {
            msm = (MediaSessionManager) act.getSystemService(Activity.MEDIA_SESSION_SERVICE);
            ComponentName me = new ComponentName(act, MediaWatcher.class);
            msm.addOnActiveSessionsChangedListener(sessions, me);
            pick(msm.getActiveSessions(me));
        } catch (SecurityException e) {
            update(); // sin lector de notificaciones: solo lo local y el último video visto
        }
        main.post(tick);
        main.postDelayed(warm, 800); // apenas se pinta el inicio
    }

    /**
     * Llamar en onPause: guarda el minuto y vacía el reproductor para dejar la memoria a la otra app.
     * El motor web se conserva: volver a crearlo es lo más lento en esta caja.
     */
    void stop() {
        main.removeCallbacks(tick);
        main.removeCallbacks(warm);
        main.removeCallbacks(autoClose);
        if (msm != null) msm.removeOnActiveSessionsChangedListener(sessions);
        if (ctl != null) ctl.unregisterCallback(cb);
        ctl = null;
        saveLocal();
        localId = null;
        unload();
    }

    /** Vacía la página del reproductor (libera el video) sin destruir el motor web. */
    private void unload() {
        if (web != null) {
            web.loadUrl("about:blank");
            web.setVisibility(INVISIBLE);
        }
        webId = null;
        webReady = false;
    }

    void destroy() {
        if (web != null) {
            artBox.removeView(web);
            web.destroy();
            web = null;
        }
        webId = null;
        webReady = false;
    }

    /** Deja cargado en pausa el último video visto, en su minuto. */
    private void warm() {
        if (localId != null || remoteShown() || lastShown == null || lastShown[0].equals(webId)) return;
        long[] p = Videos.position(act, lastShown[0]);
        loadPlayer(lastShown[0], p != null ? p[0] : 0, false);
    }

    // ---------- modos ----------

    /** Elige la sesión de otra app que está sonando; si ninguna suena, la primera en pausa. */
    private void pick(List<MediaController> list) {
        if (ctl != null) ctl.unregisterCallback(cb);
        ctl = null;
        MediaController best = null;
        if (list != null) {
            for (MediaController c : list) {
                if (c.getPackageName().equals(act.getPackageName())) continue;
                if (c.getMetadata() == null || c.getPlaybackState() == null) continue;
                int s = c.getPlaybackState().getState();
                if (s == PlaybackState.STATE_PLAYING || s == PlaybackState.STATE_BUFFERING) { best = c; break; }
                if (s == PlaybackState.STATE_PAUSED && best == null) best = c;
            }
        }
        ctl = best;
        if (ctl != null) ctl.registerCallback(cb, main);
        update();
    }

    private boolean remoteShown() {
        MediaMetadata md = ctl != null ? ctl.getMetadata() : null;
        PlaybackState st = ctl != null ? ctl.getPlaybackState() : null;
        return localId == null && md != null && st != null && st.getState() != PlaybackState.STATE_STOPPED
                && st.getState() != PlaybackState.STATE_NONE && !TextUtils.isEmpty(md.getString(MediaMetadata.METADATA_KEY_TITLE));
    }

    private boolean remotePlaying() {
        PlaybackState s = ctl != null ? ctl.getPlaybackState() : null;
        return s != null && (s.getState() == PlaybackState.STATE_PLAYING || s.getState() == PlaybackState.STATE_BUFFERING);
    }

    private void update() {
        if (localId != null) { showLocal(); return; }
        if (!remoteShown()) { showLast(); return; }
        if (remoteKey().equals(closedKey())) { hide(); return; }
        MediaMetadata md = ctl.getMetadata();
        setVisibility(VISIBLE);
        showWeb(false);
        artFor = null;
        prev.setVisibility(VISIBLE);
        next.setVisibility(VISIBLE);
        source.setText((remotePlaying() ? "REPRODUCIENDO" : "EN PAUSA") + "  ·  " + appName(ctl.getPackageName()).toUpperCase());
        String artist = md.getString(MediaMetadata.METADATA_KEY_ARTIST);
        String t = md.getString(MediaMetadata.METADATA_KEY_TITLE);
        title.setText(TextUtils.isEmpty(artist) ? t : t + "  —  " + artist);
        Bitmap bm = md.getBitmap(MediaMetadata.METADATA_KEY_ART);
        if (bm == null) bm = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        if (bm == null) bm = md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
        if (bm != null) art.setImageBitmap(bm);
        else {
            try { art.setImageDrawable(act.getPackageManager().getApplicationIcon(ctl.getPackageName())); }
            catch (PackageManager.NameNotFoundException e) { art.setImageDrawable(null); }
        }
        playPause.setImageResource(remotePlaying() ? R.drawable.ic_pause : R.drawable.ic_play);
        updateProgress();
        scheduleAutoClose();
    }

    private void showLocal() {
        setVisibility(VISIBLE);
        showWeb(true);
        prev.setVisibility(GONE);
        next.setVisibility(VISIBLE);
        boolean on = localState == YT_PLAYING || localState == YT_BUFFERING;
        source.setText((localState == YT_PLAYING ? "REPRODUCIENDO AQUÍ" : localState == YT_BUFFERING || localState < 0 ? "CARGANDO…" : "EN PAUSA") + "  ·  YOUTUBE");
        title.setText(localTitle);
        playPause.setImageResource(on ? R.drawable.ic_pause : R.drawable.ic_play);
        updateProgress();
        scheduleAutoClose();
    }

    /** En reposo: el último video visto, con su progreso; ▶ lo reproduce aquí. Si no hay, se oculta. */
    private void showLast() {
        lastShown = last.get();
        if (lastShown == null || lastShown[0].equals(closedKey())) {
            hide();
            return;
        }
        setVisibility(VISIBLE);
        showWeb(false);
        prev.setVisibility(GONE);
        next.setVisibility(VISIBLE);
        playPause.setImageResource(R.drawable.ic_play);
        source.setText("CONTINUAR VIENDO  ·  YOUTUBE");
        title.setText(lastShown[1]);
        loadArt(lastShown[0]);
        updateProgress();
        scheduleAutoClose();
    }

    // ---------- cerrar ----------

    /** ✕ o un minuto sin reproducir: para lo que suene, oculta la barra y recuerda qué se cerró. */
    private void close() {
        String key;
        if (localId != null) {
            key = localId;
            saveLocal();
            localId = null;
            unload();
        } else if (remoteShown()) {
            key = remoteKey();
            if (remotePlaying()) ctl.getTransportControls().pause();
        } else {
            key = lastShown != null ? lastShown[0] : null;
        }
        if (key != null) Videos.sp(act).edit().putString("barClosed", key).apply();
        hide();
    }

    private void hide() {
        main.removeCallbacks(autoClose);
        View down = hasFocus() ? focusSearch(FOCUS_DOWN) : null;
        if (down != null) down.requestFocus();
        setVisibility(GONE);
    }

    /** Lo último que se cerró: no se vuelve a mostrar hasta que suene o se vea otra cosa. */
    private String closedKey() { return Videos.sp(act).getString("barClosed", ""); }

    private String remoteKey() {
        MediaMetadata md = ctl.getMetadata();
        return ctl.getPackageName() + ":" + (md != null ? md.getString(MediaMetadata.METADATA_KEY_TITLE) : "");
    }

    /** Si la barra está a la vista sin reproducir nada, se cierra sola (1 min por defecto; se cambia en Ajustes). */
    private void scheduleAutoClose() {
        main.removeCallbacks(autoClose);
        boolean active = localId != null
                ? localState < 0 || localState == YT_PLAYING || localState == YT_BUFFERING
                : remoteShown() && remotePlaying();
        long ms = new LauncherSettings(act).barCloseSeconds() * 1000L; // 0 = nunca (Ajustes)
        if (getVisibility() == VISIBLE && !active && ms > 0) main.postDelayed(autoClose, ms);
    }

    private void loadArt(final String id) {
        if (id.equals(artFor)) return;
        artFor = id;
        art.setImageDrawable(null);
        new Thread(() -> {
            Bitmap bm = Hero.download("https://i.ytimg.com/vi/" + id + "/mqdefault.jpg");
            if (bm != null) main.post(() -> { if (id.equals(artFor)) art.setImageBitmap(bm); });
        }).start();
    }

    // ---------- botones ----------

    private void togglePlay() {
        if (localId != null) {
            js(localState == YT_PLAYING || localState == YT_BUFFERING ? "player.pauseVideo()" : "player.playVideo()");
        } else if (remoteShown()) {
            if (remotePlaying()) ctl.getTransportControls().pause();
            else ctl.getTransportControls().play();
        } else if (lastShown != null) {
            long[] p = Videos.position(act, lastShown[0]);
            playLocal(lastShown[0], lastShown[1], p != null ? p[0] : 0);
        }
    }

    private void skipNext() {
        if (localId == null && remoteShown()) { ctl.getTransportControls().skipToNext(); return; }
        String current = localId != null ? localId : lastShown != null ? lastShown[0] : null;
        if (localId != null) saveLocal();
        String[] n = last.next(current);
        if (n != null) playLocal(n[0], n[1], 0);
    }

    /** ⛶: el video en TizenTube a pantalla completa, en el minuto actual. */
    private void openFull() {
        if (localId != null) {
            js("player.pauseVideo()");
            saveLocal();
            String id = localId, t = localTitle;
            stopLocal();
            last.open(id, t);
        } else if (remoteShown()) {
            PendingIntent pi = ctl.getSessionActivity();
            try {
                if (pi != null) { pi.send(); return; }
            } catch (PendingIntent.CanceledException ignored) { }
            AppActions.open(act, ctl.getPackageName());
        } else if (lastShown != null) {
            last.open(lastShown[0], lastShown[1]);
        }
    }

    // ---------- reproductor embed de YouTube ----------

    /** Reproduce aquí; si el video ya está precargado solo lo arranca (y lo lleva a su minuto). */
    private void playLocal(String id, String t, long startMs) {
        localId = id;
        localTitle = t;
        localPos = startMs / 1000.0;
        localDur = 0;
        localState = -1;
        seekChecked = false;
        last.played(id, t);
        if (id.equals(webId)) {
            webStartS = startMs / 1000;
            if (webReady) js("go(" + webStartS + ")"); // si aún carga, Bridge.ready() lo arranca
        } else {
            loadPlayer(id, startMs, true);
        }
        update();
    }

    /**
     * Carga el reproductor embed de YouTube. Es pequeño, así que YouTube elige solo una calidad baja (ligera
     * para la caja); a pantalla completa (⛶) el video sigue en TizenTube con su propia calidad.
     */
    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    private void loadPlayer(String id, long startMs, boolean autoplay) {
        if (web == null) {
            web = new WebView(act);
            web.setFocusable(false);
            web.setBackgroundColor(Color.BLACK);
            web.getSettings().setJavaScriptEnabled(true);
            web.getSettings().setMediaPlaybackRequiresUserGesture(false);
            web.getSettings().setDomStorageEnabled(true);
            web.addJavascriptInterface(new Bridge(), "Android");
            web.setVisibility(INVISIBLE);
            artBox.addView(web, new FrameLayout.LayoutParams(-1, -1));
        }
        webId = id;
        webReady = false;
        webStartS = startMs / 1000;
        String html = "<!doctype html><html><head><meta name=viewport content='width=device-width'>"
                + "<style>html,body,#p{margin:0;width:100%;height:100%;background:#000;overflow:hidden}</style></head><body><div id=p></div>"
                + "<script src='https://www.youtube.com/iframe_api'></script><script>"
                + "var player;function go(t){if(t>0)player.seekTo(t,true);player.playVideo();}"
                + "function onYouTubeIframeAPIReady(){player=new YT.Player('p',{width:'100%',height:'100%',videoId:'" + id + "',"
                + "playerVars:{autoplay:" + (autoplay ? 1 : 0) + ",start:" + webStartS + ",controls:0,playsinline:1,rel:0,fs:0,iv_load_policy:3,origin:'https://remap.local'},"
                + "events:{onReady:function(e){Android.ready();" + (autoplay ? "e.target.playVideo();" : "") + "},onError:function(e){Android.error(e.data);}}});}"
                + "setInterval(function(){if(player&&player.getPlayerState){Android.tick(player.getCurrentTime(),player.getDuration(),player.getPlayerState());}},1000);"
                + "</script></body></html>";
        web.loadDataWithBaseURL("https://remap.local/", html, "text/html", "utf-8", null);
    }

    private void stopLocal() {
        unload();
        localId = null;
        localState = -1;
        update();
    }

    private void saveLocal() {
        if (localId != null && localPos > 0) {
            Videos.savePosition(act, localId, (long) (localPos * 1000), (long) (localDur * 1000));
        }
    }

    private void js(String code) {
        if (web != null) web.evaluateJavascript("try{" + code + "}catch(e){}", null); // el reproductor puede no estar listo
    }

    private void showWeb(boolean on) {
        if (web != null) web.setVisibility(on ? VISIBLE : INVISIBLE); // invisible sigue cargando en segundo plano
        art.setVisibility(on ? GONE : VISIBLE);
    }

    private final class Bridge {
        @JavascriptInterface public void ready() {
            main.post(() -> {
                webReady = true;
                if (localId != null && localId.equals(webId)) js("go(" + webStartS + ")");
            });
        }

        @JavascriptInterface public void tick(double pos, double dur, int state) {
            main.post(() -> {
                if (localId == null || !localId.equals(webId)) return;
                // si arrancó lejos del minuto guardado, se lleva ahí una vez
                if (state == YT_PLAYING && !seekChecked) {
                    seekChecked = true;
                    if (webStartS > 10 && pos < webStartS - 5) js("player.seekTo(" + webStartS + ",true)");
                }
                localPos = pos;
                localDur = dur;
                boolean changed = state != localState;
                localState = state;
                if (state == YT_ENDED) { skipNext(); return; }
                if (state == YT_PLAYING) saveLocal(); // cada segundo, para retomar justo donde quedó
                if (changed) showLocal();
            });
        }

        /** El dueño no permite verlo fuera de YouTube (o no existe): se abre en TizenTube. */
        @JavascriptInterface public void error(int code) {
            main.post(() -> {
                if (localId == null) { unload(); return; } // falló la precarga: se intenta de nuevo al pulsar ▶
                String id = localId, t = localTitle;
                stopLocal();
                last.open(id, t);
            });
        }
    }

    // ---------- progreso ----------

    private void updateProgress() {
        if (getVisibility() != VISIBLE) return;
        float f;
        if (localId != null) {
            f = localDur > 0 ? (float) (localPos / localDur) : 0f;
            track.setVisibility(localDur > 0 ? VISIBLE : INVISIBLE);
        } else if (remoteShown()) {
            PlaybackState st = ctl.getPlaybackState();
            long dur = ctl.getMetadata().getLong(MediaMetadata.METADATA_KEY_DURATION);
            track.setVisibility(dur > 0 ? VISIBLE : INVISIBLE);
            if (dur <= 0) return;
            long pos = st.getPosition();
            if (st.getState() == PlaybackState.STATE_PLAYING) {
                pos += (long) ((SystemClock.elapsedRealtime() - st.getLastPositionUpdateTime()) * st.getPlaybackSpeed());
            }
            f = pos / (float) dur;
        } else {
            long[] p = lastShown != null ? Videos.position(act, lastShown[0]) : null;
            track.setVisibility(p != null && p[1] > 0 ? VISIBLE : INVISIBLE);
            if (p == null || p[1] <= 0) return;
            f = p[0] / (float) p[1];
        }
        if (track.getWidth() == 0) return;
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) progress.getLayoutParams();
        lp.width = Math.round(track.getWidth() * Math.max(0f, Math.min(1f, f)));
        progress.setLayoutParams(lp);
    }

    // ---------- ayudas ----------

    private String appName(String pkg) {
        try {
            PackageManager pm = act.getPackageManager();
            return String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)));
        } catch (PackageManager.NameNotFoundException e) { return pkg; }
    }

    private ImageView button(int icon, View.OnClickListener l) {
        ImageView b = new ImageView(act);
        b.setImageResource(icon);
        b.setColorFilter(theme.text); // los íconos son blancos: de día se tiñen oscuros
        b.setScaleType(ImageView.ScaleType.CENTER);
        b.setFocusable(true);
        b.setClickable(true);
        b.setBackground(Glass.chip(act, theme, 24, true));
        b.setOnClickListener(l);
        b.setOnFocusChangeListener((v, has) -> {
            if (theme.light) Glass.lift(v, theme, has, 1.12f);
            else v.animate().scaleX(has ? 1.12f : 1f).scaleY(has ? 1.12f : 1f).setDuration(120).start();
        });
        LayoutParams lp = new LayoutParams(dp(48), dp(48));
        lp.leftMargin = dp(10);
        b.setLayoutParams(lp);
        return b;
    }

    private GradientDrawable pill(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(4));
        return g;
    }

    private StateListDrawable glass(int radius) { return Glass.panel(act, theme, radius); }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }
}
