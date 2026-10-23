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
    static final String TIZEN = "io.gh.reisxd.tizentube.cobalt";
    private static final String[] CHANNELS = {"@TED", "@NatGeo", "@veritasium", "@LinusTechTips", "@Kurzgesagt"};
    private static final int BG = 0xFF0B1230;
    private static final int CARD = 0xFF1C2347;

    private final Handler main = new Handler(Looper.getMainLooper());
    private LinearLayout root, appsRow, recentRow, recoRow;
    private AppActions actions;
    private ImageView backdrop;
    private Bitmap shownBackdrop;
    private TextView clock, recentTitle;
    private Prefs prefs;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = new Prefs(this);
        prefs.restoreIfEmpty();
        FrameLayout frame = new FrameLayout(this);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF1B1038, 0xFF0B1B3F, 0xFF06283D});
        frame.setBackground(bg);
        // fondo: miniatura del video enfocado, desenfocada
        backdrop = new ImageView(this);
        backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);
        backdrop.setAlpha(0f);
        if (android.os.Build.VERSION.SDK_INT >= 31)
            backdrop.setRenderEffect(android.graphics.RenderEffect.createBlurEffect(60f, 60f, android.graphics.Shader.TileMode.CLAMP));
        frame.addView(backdrop, new FrameLayout.LayoutParams(-1, -1));
        View scrim = new View(this);
        scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x66000000, 0x99060A1E, 0xCC060A1E}));
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
        buildApps();
        recentTitle = title("Seguir mirando");
        recentRow = row();
        title("Recomendaciones para ti");
        recoRow = row();
        loadReco();
    }

    @Override protected void onResume() {
        super.onResume();
        refreshApps();
        refreshRecent();
        tick();
    }

    @Override protected void onPause() {
        super.onPause();
        main.removeCallbacksAndMessages(null);
        if (actions != null) actions.stop();
    }

    private void tick() {
        clock.setText(new SimpleDateFormat("h:mm a", Locale.getDefault()).format(new Date()));
        main.postDelayed(this::tick, 20000);
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
        s.addState(new int[]{android.R.attr.state_focused}, box(0x44FFFFFF, radius, 3, 0xFFFFFFFF));
        s.addState(new int[]{}, box(0x26FFFFFF, radius, 1, 0x33FFFFFF));
        return s;
    }

    /** Agranda suavemente el elemento enfocado y, si hay miniatura, la pone de fondo. */
    private void glass(final View v, final ImageView thumb) {
        v.setOnFocusChangeListener((view, has) -> {
            view.animate().scaleX(has ? 1.05f : 1f).scaleY(has ? 1.05f : 1f).setDuration(150).start();
            view.setElevation(has ? 24f : 0f);
            if (has && thumb != null && thumb.getDrawable() instanceof android.graphics.drawable.BitmapDrawable) {
                Bitmap bm = ((android.graphics.drawable.BitmapDrawable) thumb.getDrawable()).getBitmap();
                if (bm != shownBackdrop) {
                    shownBackdrop = bm;
                    backdrop.animate().alpha(0f).setDuration(120).withEndAction(() -> {
                        backdrop.setImageBitmap(bm);
                        backdrop.animate().alpha(0.85f).setDuration(350).start();
                    }).start();
                }
            }
        });
    }

    private void buildTop() {
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView t = new TextView(this);
        t.setText("Principal");
        t.setTextSize(18);
        t.setTextColor(Color.WHITE);
        t.setPadding(dp(20), dp(10), dp(20), dp(10));
        t.setBackground(box(0x33FFFFFF, 24, 0, 0));
        top.addView(t);
        View sp = new View(this);
        top.addView(sp, new LinearLayout.LayoutParams(0, 1, 1f));
        clock = new TextView(this);
        clock.setTextColor(Color.WHITE);
        clock.setTextSize(18);
        top.addView(clock);
        root.addView(top);
    }

    private TextView title(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(Color.WHITE);
        t.setTextSize(20);
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
            iv.setBackgroundColor(0x33FFFFFF);
            iv.setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override public void getOutline(View v, android.graphics.Outline o) { o.setOval(0, 0, v.getWidth(), v.getHeight()); }
            });
            iv.setClipToOutline(true);
            iv.setElevation(dp(6));
            iv.setDuplicateParentStateEnabled(true);
            GradientDrawable ringOn = new GradientDrawable();
            ringOn.setShape(GradientDrawable.OVAL);
            ringOn.setColor(0);
            ringOn.setStroke(dp(4), 0xFFFFFFFF);
            StateListDrawable ring = new StateListDrawable();
            ring.addState(new int[]{android.R.attr.state_focused}, ringOn);
            ring.addState(new int[]{}, new android.graphics.drawable.ColorDrawable(0));
            iv.setForeground(ring);
            c.addView(iv, new LinearLayout.LayoutParams(dp(84), dp(84)));
            TextView t = new TextView(this);
            t.setTextColor(0xDDFFFFFF);
            t.setShadowLayer(6f, 0f, 2f, 0xFF000000);
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
        // si habia una app enfocada (p. ej. tras desinstalar), deja el foco cerca
        if (focused >= 0 && apps.size() > 0) appsRow.getChildAt(Math.min(focused, apps.size() - 1)).requestFocus();
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
        c.addView(iv, new LinearLayout.LayoutParams(dp(220), dp(124)));
        TextView t = new TextView(this);
        t.setText(name);
        t.setTextColor(Color.WHITE);
        t.setTextSize(14);
        t.setMaxLines(2);
        t.setPadding(dp(6), dp(6), dp(6), dp(4));
        c.addView(t);
        c.setOnClickListener(v -> openVideo(id, name));
        glass(c, iv);
        fetchThumb(id, iv);
        return c;
    }

    private void openVideo(String id, String name) {
        Videos.remember(this, id, name);
        Intent i = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.youtube.com/tv?v=" + id));
        i.setPackage(TIZEN);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { startActivity(i); } catch (Exception e) { /* TizenTube no instalado */ }
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
    private void loadReco() {
        new Thread(() -> {
            final List<String[]> vids = new ArrayList<>();
            List<String> learned = Videos.channels(this);
            List<String> src = new ArrayList<>(learned);
            if (src.isEmpty()) for (String d : CHANNELS) src.add(d);
            for (String ch : src) {
                try {
                    String cid = Videos.channelId(ch);
                    if (cid == null) continue;
                    vids.addAll(Videos.feed(cid, 4));
                } catch (Exception ignored) { }
            }
            // intercala canales para variar la fila
            java.util.Collections.shuffle(vids);
            main.post(() -> {
                for (String[] v : vids) recoRow.addView(videoCard(v[0], v[1]), cardLp());
            });
        }).start();
    }
}
