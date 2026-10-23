package com.remap.buttons;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Launcher estilo Google TV: apps, "Seguir mirando" y recomendaciones que abren en TizenTube. */
public class HomeActivity extends Activity {
    /** Apps de streaming que el banner promociona si están instaladas (paquetes de Android TV). */
    private static final String[] STREAMING = {
        "com.netflix.ninja", "com.amazon.amazonvideo.livingroom", "com.disney.disneyplus", "com.wbd.stream",
        "com.hbo.hbonow", "com.cbs.ott", "com.cbs.ca", "com.crunchyroll.crunchyroid", "com.apple.atve.androidtv.appletv",
        "com.univision.prendetv", "tv.pluto.android", "com.google.android.youtube.tvmusic", "com.spotify.tv.android"
    };

    private final Handler main = new Handler(Looper.getMainLooper());
    private LinearLayout root, appsRow, recentRow, recoRow;
    private AppActions actions;
    private ImageView backdrop;
    private Bitmap shownBackdrop;
    private TextView clock, weatherView, recentTitle, recoTitle;
    private Prefs prefs;
    private FrameLayout frame;
    private GradientDrawable bg;
    private int phase = -1;
    private Weather weather;
    private long weatherAt;
    private boolean loadingWeather, loadingReco, focusedOnce;
    private NowPlaying nowPlaying;
    private Hero hero;
    private List<String[]> recos = new ArrayList<>();
    private Theme theme;
    private LauncherSettings settings;
    private long settingsStamp;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = new Prefs(this);
        prefs.restoreIfEmpty();
        settings = new LauncherSettings(this);
        settingsStamp = settings.stamp();
        theme = Theme.current(this, Weather.phase(weather));
        frame = new FrameLayout(this);
        applyPhase();
        // fondo: miniatura del video enfocado, desenfocada
        backdrop = new ImageView(this);
        backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);
        backdrop.setAlpha(0f);
        if (android.os.Build.VERSION.SDK_INT >= 31)
            backdrop.setRenderEffect(android.graphics.RenderEffect.createBlurEffect(60f, 60f, android.graphics.Shader.TileMode.CLAMP));
        frame.addView(backdrop, new FrameLayout.LayoutParams(-1, -1));
        View scrim = new View(this);
        scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{theme.scrimTop, theme.scrimMid, theme.scrimBottom}));
        frame.addView(scrim, new FrameLayout.LayoutParams(-1, -1));
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(40), dp(12), dp(40), dp(16));
        sv.addView(root);
        frame.addView(sv, new FrameLayout.LayoutParams(-1, -1));
        setContentView(frame);
        buildTop();
        nowPlaying = new NowPlaying(this, theme, new NowPlaying.Last() {
            @Override public String[] get() {
                List<String[]> h = Videos.history(HomeActivity.this);
                return h.isEmpty() ? null : h.get(0);
            }
            @Override public String[] next(String afterId) {
                // el siguiente de las recomendaciones; si no está en la lista, la primera distinta
                for (int i = 0; i < recos.size(); i++) {
                    if (recos.get(i)[0].equals(afterId)) return i + 1 < recos.size() ? recos.get(i + 1) : null;
                }
                for (String[] r : recos) if (!r[0].equals(afterId)) return r;
                return null;
            }
            @Override public void played(String id, String title) {
                Videos.remember(HomeActivity.this, id, title);
                refreshRecent();
            }
            @Override public void open(String id, String title) { openVideo(id, title); }
        });
        hero = new Hero(this, theme, new Hero.Opener() {
            @Override public void open(String id, String title) { openVideo(id, title); }
            @Override public void openApp(String pkg) { AppActions.open(HomeActivity.this, pkg); }
        });
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(-1, dp(200));
        hlp.setMargins(dp(10), dp(14), dp(10), 0);
        root.addView(hero, hlp);
        // la barra de reproducción va debajo del banner
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT);
        nlp.setMargins(dp(10), dp(14), dp(10), 0);
        root.addView(nowPlaying, nlp);
        buildApps();
        recentTitle = title("Seguir mirando");
        recentRow = row();
        recoTitle = title("Recomendaciones para ti");
        recoRow = row();
        // al instante desde la caché; onResume la renueva si hace falta
        showReco(settings.showReco() ? Videos.cachedReco(this) : new ArrayList<>());
    }

    @Override protected void onResume() {
        super.onResume();
        // se cambió algo en Ajustes: se reconstruye la pantalla con lo nuevo
        if (settings.stamp() != settingsStamp) {
            recreate();
            return;
        }
        refreshApps();
        refreshRecent();
        loadReco();
        if (settings.showBar()) nowPlaying.start();
        hero.start();
        tick();
    }

    @Override protected void onDestroy() {
        nowPlaying.destroy();
        super.onDestroy();
    }

    @Override protected void onPause() {
        super.onPause();
        main.removeCallbacksAndMessages(null);
        nowPlaying.stop();
        hero.stop();
        if (actions != null) actions.stop();
    }

    private void tick() {
        clock.setText(new SimpleDateFormat("h:mm a", Locale.getDefault()).format(new Date()));
        applyPhase();
        if (System.currentTimeMillis() - weatherAt > 20 * 60 * 1000L) loadWeather();
        main.postDelayed(this::tick, 20000);
    }

    private void loadWeather() {
        if (loadingWeather) return;
        loadingWeather = true;
        new Thread(() -> {
            Weather w = null;
            try { w = Weather.load(this); } catch (Exception ignored) { }
            final Weather got = w;
            runOnUiThread(() -> {
                loadingWeather = false;
                if (got == null) return;
                weather = got;
                weatherAt = System.currentTimeMillis();
                weatherView.setText(got.icon() + "  " + got.temp + "°   " + got.description() + (got.city.isEmpty() ? "" : "  ·  " + got.city));
                applyPhase();
            });
        }).start();
    }

    /** Fondo según el momento del día (amanecer, día, atardecer, noche), con fundido suave al cambiar. */
    private void applyPhase() {
        int p = Weather.phase(weather);
        if (p == phase) return;
        // de día a noche (o al revés) cambia el tema: se reconstruye la pantalla con los colores nuevos
        if (theme != null && Theme.current(this, p) != theme) {
            recreate();
            return;
        }
        GradientDrawable next = new GradientDrawable(GradientDrawable.Orientation.TL_BR, Theme.background(this, p));
        if (bg == null) frame.setBackground(next);
        else {
            android.graphics.drawable.TransitionDrawable td =
                    new android.graphics.drawable.TransitionDrawable(new android.graphics.drawable.Drawable[]{bg, next});
            td.setCrossFadeEnabled(true);
            frame.setBackground(td);
            td.startTransition(2500);
        }
        bg = next;
        phase = p;
    }

    /** El banner mezcla lo último que viste, recomendaciones y tus apps de streaming instaladas. */
    private void updateHero() {
        List<String[]> items = new ArrayList<>();
        if (!settings.showHero()) {
            hero.setItems(items); // vacío: el banner se oculta
            return;
        }
        List<String[]> h = Videos.history(this);
        for (int i = 0; i < h.size() && i < 2; i++) {
            long[] p = Videos.position(this, h.get(i)[0]);
            String label = "Seguir viendo";
            if (p != null && p[1] > 0) label += "  ·  quedan " + Math.max(1, (p[1] - p[0]) / 60000) + " min";
            items.add(new String[]{h.get(i)[0], h.get(i)[1], label});
        }
        for (int i = 0; i < recos.size() && items.size() < 7; i++) items.add(new String[]{recos.get(i)[0], recos.get(i)[1], "Recomendado para ti"});
        // una app cada dos videos
        List<String[]> apps = new ArrayList<>();
        for (String pkg : STREAMING) {
            if (!Videos.installed(this, pkg)) continue;
            try {
                CharSequence name = getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(pkg, 0));
                apps.add(new String[]{Hero.APP + pkg, String.valueOf(name), "En tu TV"});
            } catch (Exception ignored) { }
        }
        List<String[]> mixed = new ArrayList<>();
        int a = 0;
        for (int i = 0; i < items.size(); i++) {
            mixed.add(items.get(i));
            if (i % 2 == 1 && a < apps.size()) mixed.add(apps.get(a++));
        }
        while (a < apps.size()) mixed.add(apps.get(a++));
        hero.setItems(mixed);
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

    private GradientDrawable box(int color, int radius, int stroke, int strokeColor) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radius));
        if (stroke > 0) g.setStroke(dp(stroke), strokeColor);
        return g;
    }

    /** Cristal: translucido con borde fino; al enfocar se aclara y el borde brilla. */
    private StateListDrawable focusBg(int radius) {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_focused}, box(theme.focus, radius, 3, theme.focusStroke));
        s.addState(new int[]{}, box(theme.glass, radius, 1, theme.glassStroke));
        return s;
    }

    /** Agranda suavemente el elemento enfocado y, si hay miniatura, la pone de fondo. */
    private void glass(final View v, final ImageView thumb) {
        v.setOnFocusChangeListener((view, has) -> {
            view.animate().scaleX(has ? 1.05f : 1f).scaleY(has ? 1.05f : 1f).setDuration(150).start();
            view.setElevation(has ? 24f : 0f);
            if (has && settings.showBackdrop() && thumb != null && thumb.getDrawable() instanceof android.graphics.drawable.BitmapDrawable) {
                Bitmap bm = ((android.graphics.drawable.BitmapDrawable) thumb.getDrawable()).getBitmap();
                if (bm != shownBackdrop) {
                    shownBackdrop = bm;
                    backdrop.animate().alpha(0f).setDuration(120).withEndAction(() -> {
                        backdrop.setImageBitmap(bm);
                        backdrop.animate().alpha(0.45f).setDuration(350).start(); // tenue: no compite con el texto
                    }).start();
                }
            }
        });
    }

    private void buildTop() {
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        View sp = new View(this);
        top.addView(sp, new LinearLayout.LayoutParams(0, 1, 1f));
        weatherView = new TextView(this);
        weatherView.setTextColor(theme.textSoft);
        theme.style(weatherView);
        weatherView.setTextSize(17);
        weatherView.setPadding(0, 0, dp(28), 0);
        if (!settings.showWeather()) weatherView.setVisibility(View.GONE);
        top.addView(weatherView);
        clock = new TextView(this);
        clock.setTextColor(theme.text);
        theme.style(clock);
        clock.setTextSize(18);
        top.addView(clock);
        // ⚙ Ajustes del launcher
        ImageView gear = new ImageView(this);
        gear.setImageResource(R.drawable.ic_settings);
        gear.setColorFilter(theme.text);
        gear.setScaleType(ImageView.ScaleType.CENTER);
        gear.setFocusable(true);
        StateListDrawable gs = new StateListDrawable();
        GradientDrawable gOn = new GradientDrawable();
        gOn.setShape(GradientDrawable.OVAL);
        gOn.setColor(theme.focus);
        gOn.setStroke(dp(2), theme.focusStroke);
        GradientDrawable gOff = new GradientDrawable();
        gOff.setShape(GradientDrawable.OVAL);
        gOff.setColor(theme.chip);
        gs.addState(new int[]{android.R.attr.state_focused}, gOn);
        gs.addState(new int[]{}, gOff);
        gear.setBackground(gs);
        gear.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(dp(44), dp(44));
        glp.leftMargin = dp(20);
        top.addView(gear, glp);
        root.addView(top);
    }

    private TextView title(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(theme.text);
        theme.style(t);
        t.setTextSize(22);
        t.setPadding(dp(4), dp(14), 0, dp(4));
        root.addView(t);
        return t;
    }

    private LinearLayout row() {
        HorizontalScrollView h = new HorizontalScrollView(this);
        h.setHorizontalScrollBarEnabled(false);
        h.setClipChildren(false);
        h.setClipToPadding(false);
        LinearLayout l = new LinearLayout(this);
        l.setClipChildren(false);
        l.setPadding(dp(10), dp(14), dp(10), dp(14));
        h.addView(l);
        root.addView(h);
        return l;
    }

    private void buildApps() {
        title("Tus apps");
        appsRow = row();
    }

    /** Redibuja la fila si cambiaron las apps (instaladas, desinstaladas o movidas en otra pantalla). */
    private void refreshApps() {
        List<Prefs.AppEntry> apps = prefs.listApps(true);
        List<String> hidden = settings.hiddenApps();
        for (int i = apps.size() - 1; i >= 0; i--) if (hidden.contains(apps.get(i).pkg)) apps.remove(i);
        if (actions != null && actions.shown().equals(Prefs.join(apps))) return;
        int focused = appsRow.indexOfChild(appsRow.getFocusedChild());
        appsRow.removeAllViews();
        actions = new AppActions(this, prefs, appsRow, apps);
        for (int n = 0; n < apps.size(); n++) {
            LinearLayout c = new LinearLayout(this);
            c.setOrientation(LinearLayout.VERTICAL);
            c.setGravity(Gravity.CENTER_HORIZONTAL);
            c.setFocusable(true);
            glass(c, null);
            c.setPadding(dp(8), dp(8), dp(8), dp(6));
            // icono redondo sin tarjeta; el aro blanco aparece solo al enfocar
            ImageView iv = new ImageView(this);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setBackgroundColor(theme.chip);
            iv.setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override public void getOutline(View v, android.graphics.Outline o) { o.setOval(0, 0, v.getWidth(), v.getHeight()); }
            });
            iv.setClipToOutline(true);
            iv.setElevation(dp(6));
            iv.setDuplicateParentStateEnabled(true);
            GradientDrawable ringOn = new GradientDrawable();
            ringOn.setShape(GradientDrawable.OVAL);
            ringOn.setColor(0);
            ringOn.setStroke(dp(4), theme.focusStroke);
            StateListDrawable ring = new StateListDrawable();
            ring.addState(new int[]{android.R.attr.state_focused}, ringOn);
            ring.addState(new int[]{}, new android.graphics.drawable.ColorDrawable(0));
            iv.setForeground(ring);
            c.addView(iv, new LinearLayout.LayoutParams(dp(84), dp(84)));
            TextView t = new TextView(this);
            t.setTextColor(theme.textSoft);
            theme.style(t);
            t.setPadding(0, dp(8), 0, 0);
            t.setTextSize(12);
            t.setGravity(Gravity.CENTER);
            t.setMaxLines(1);
            t.setEllipsize(android.text.TextUtils.TruncateAt.END);
            c.addView(t, new LinearLayout.LayoutParams(dp(104), ViewGroup.LayoutParams.WRAP_CONTENT));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = dp(14);
            appsRow.addView(c, lp);
            actions.attach(c);
        }
        // si habia una app enfocada (p. ej. tras desinstalar), deja el foco cerca; al abrir, en la primera app
        if (focused >= 0 && apps.size() > 0) appsRow.getChildAt(Math.min(focused, apps.size() - 1)).requestFocus();
        else if (!focusedOnce && apps.size() > 0) appsRow.getChildAt(0).requestFocus();
        focusedOnce = true;
    }

    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        return (actions != null && actions.handleKey(e)) || super.dispatchKeyEvent(e);
    }

    private void refreshRecent() {
        recentRow.removeAllViews();
        List<String[]> h = Videos.history(this);
        recentTitle.setVisibility(h.isEmpty() ? View.GONE : View.VISIBLE);
        ((View) recentRow.getParent()).setVisibility(h.isEmpty() ? View.GONE : View.VISIBLE);
        for (String[] v : h) recentRow.addView(videoCard(v[0], v[1]), cardLp());
        updateHero();
    }

    private LinearLayout.LayoutParams cardLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(230), ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(14);
        return lp;
    }

    private View videoCard(final String id, final String name) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setFocusable(true);
        c.setBackground(focusBg(18));
        c.setPadding(dp(5), dp(5), dp(5), dp(5));
        final ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        iv.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override public void getOutline(View v, android.graphics.Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(14)); }
        });
        iv.setClipToOutline(true);
        // miniatura con la barra roja de lo ya visto, como en YouTube
        FrameLayout thumb = new FrameLayout(this);
        thumb.addView(iv, new FrameLayout.LayoutParams(-1, -1));
        long[] p = Videos.position(this, id);
        if (p != null && p[1] > 0) {
            View bar = new View(this);
            bar.setBackgroundColor(0xFFFF2D2D);
            FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(Math.round(dp(220) * Math.min(1f, p[0] / (float) p[1])), dp(4), Gravity.BOTTOM);
            blp.leftMargin = dp(2);
            blp.bottomMargin = dp(2);
            thumb.addView(bar, blp);
        }
        c.addView(thumb, new LinearLayout.LayoutParams(dp(220), dp(124)));
        TextView t = new TextView(this);
        t.setText(name);
        t.setTextColor(theme.text);
        theme.style(t);
        t.setTextSize(14);
        t.setMaxLines(2);
        t.setPadding(dp(6), dp(6), dp(6), dp(4));
        c.addView(t);
        c.setOnClickListener(v -> openVideo(id, name));
        glass(c, iv);
        fetchThumb(id, iv);
        return c;
    }

    /** Abre el video en la app donde se vio (TizenTube o YouTube), continuando donde quedó. */
    private void openVideo(String id, String name) {
        String app = Videos.player(this, id);
        if (app == null) return; // ni TizenTube ni YouTube instalados
        Videos.remember(this, id, name, app);
        String url = (Videos.TIZEN.equals(app) ? "https://www.youtube.com/tv?v=" : "https://www.youtube.com/watch?v=") + id;
        long[] p = Videos.position(this, id);
        if (p != null && p[0] > 15000 && (p[1] <= 0 || p[1] - p[0] > 30000)) url += "&t=" + (p[0] / 1000);
        Intent i = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url));
        i.setPackage(app);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { startActivity(i); } catch (Exception ignored) { }
    }

    private void fetchThumb(final String id, final ImageView iv) {
        new Thread(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL("https://i.ytimg.com/vi/" + id + "/mqdefault.jpg").openConnection();
                c.setConnectTimeout(8000);
                c.setReadTimeout(8000);
                InputStream in = c.getInputStream();
                final Bitmap bm = BitmapFactory.decodeStream(in);
                in.close();
                main.post(() -> iv.setImageBitmap(bm));
            } catch (Exception ignored) { }
        }).start();
    }

    /** Baja los últimos videos de cada canal por RSS (sin clave de API). */
    /** Recalcula en segundo plano si la caché es vieja o viste algo nuevo (ver Videos.recommend). */
    private void loadReco() {
        if (!settings.showReco() || loadingReco || !Videos.recoStale(this)) return;
        loadingReco = true;
        new Thread(() -> {
            final List<String[]> vids = Videos.recommend(this);
            runOnUiThread(() -> {
                loadingReco = false;
                showReco(vids);
            });
        }).start();
    }

    private void showReco(List<String[]> vids) {
        recos = vids;
        recoRow.removeAllViews();
        for (String[] v : vids) recoRow.addView(videoCard(v[0], v[1]), cardLp());
        recoTitle.setVisibility(vids.isEmpty() ? View.GONE : View.VISIBLE);
        ((View) recoRow.getParent()).setVisibility(vids.isEmpty() ? View.GONE : View.VISIBLE);
        updateHero();
    }
}
