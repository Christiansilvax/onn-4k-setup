package com.remap.buttons;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Banner grande del inicio: rota entre lo último que viste, recomendaciones y tus apps de streaming.
 * OK reproduce el video o abre la app; ← → cambian.
 */
final class Hero extends FrameLayout {
    interface Opener {
        void open(String id, String title);
        void openApp(String pkg);
    }

    /** Prefijo del id para las diapositivas de apps ({APP + paquete, nombre, etiqueta}). */
    static final String APP = "app:";

    private static final long ROTATE_MS = 9000;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<String, Bitmap> thumbs = new HashMap<>();
    private final Opener opener;
    private final Theme theme;
    private final ImageView img, appArt;
    private final TextView label, title, play;
    private final LinearLayout dots;
    private List<String[]> items = new ArrayList<>(); // {id, título, etiqueta}
    private int idx;

    private final Runnable rotate = new Runnable() {
        @Override public void run() {
            if (!hasFocus() && items.size() > 1) show((idx + 1) % items.size());
            main.postDelayed(this, ROTATE_MS);
        }
    };

    Hero(Context c, Theme theme, Opener o) {
        super(c);
        opener = o;
        this.theme = theme;
        setFocusable(true);
        setClickable(true);
        setVisibility(GONE);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline out) { out.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(24)); }
        });
        setClipToOutline(true);
        setBackgroundColor(theme.chip);

        img = new ImageView(c);
        img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        img.setScaleX(1.12f); // recorta los bordes que traen algunas miniaturas (franjas negras o de marca)
        img.setScaleY(1.12f);
        addView(img, new LayoutParams(-1, -1));
        // imagen oficial de la app, nítida a la derecha (solo en diapositivas de apps)
        appArt = new ImageView(c);
        appArt.setScaleType(ImageView.ScaleType.FIT_CENTER);
        appArt.setVisibility(GONE);
        appArt.setElevation(dp(12));
        appArt.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline out) { out.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(16)); }
        });
        appArt.setClipToOutline(true);
        LayoutParams alp = new LayoutParams(dp(288), dp(162), Gravity.END | Gravity.CENTER_VERTICAL);
        alp.rightMargin = dp(56);
        // velo de izquierda a derecha y de abajo hacia arriba para que el texto se lea sobre cualquier imagen
        View side = new View(c);
        side.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Theme.alpha(theme.veil, 0xF2), Theme.alpha(theme.veil, 0xA6), Theme.alpha(theme.veil, 0)}));
        addView(side, new LayoutParams(-1, -1));
        View bottom = new View(c);
        bottom.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{Theme.alpha(theme.veil, 0xCC), Theme.alpha(theme.veil, 0)}));
        addView(bottom, new LayoutParams(-1, dp(120), Gravity.BOTTOM));
        addView(appArt, alp);

        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(36), 0, 0, dp(20));
        col.setDuplicateParentStateEnabled(true); // el foco del banner llega al botón Reproducir
        label = new TextView(c);
        label.setTextColor(theme.accent);
        label.setTextSize(13);
        label.setLetterSpacing(0.12f);
        theme.style(label);
        col.addView(label);
        title = new TextView(c);
        title.setTextColor(theme.text);
        title.setTextSize(26);
        theme.style(title);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setPadding(0, dp(6), 0, dp(14));
        col.addView(title);

        LinearLayout actions = new LinearLayout(c);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.setDuplicateParentStateEnabled(true);
        play = new TextView(c);
        play.setText("▶  Reproducir");
        play.setTextSize(16);
        play.setTypeface(Typeface.DEFAULT_BOLD);
        play.setPadding(dp(22), dp(9), dp(22), dp(9));
        play.setDuplicateParentStateEnabled(true);
        play.setTextColor(new android.content.res.ColorStateList(
                new int[][]{{android.R.attr.state_focused}, {}}, new int[]{theme.veil, theme.text}));
        StateListDrawable pb = new StateListDrawable();
        pb.addState(new int[]{android.R.attr.state_focused}, round(theme.text, 999, 0)); // botón invertido al enfocar
        pb.addState(new int[]{}, round(theme.chip, 999, 0));
        play.setBackground(pb);
        actions.addView(play);
        dots = new LinearLayout(c);
        dots.setPadding(dp(20), 0, 0, 0);
        actions.addView(dots);
        col.addView(actions);
        addView(col, new LayoutParams(Math.round(getResources().getDisplayMetrics().widthPixels * 0.55f), -2, Gravity.BOTTOM | Gravity.START));

        // aro blanco al enfocar
        StateListDrawable ring = new StateListDrawable();
        ring.addState(new int[]{android.R.attr.state_focused}, round(0, 24, 3));
        ring.addState(new int[]{}, new ColorDrawable(0));
        setForeground(ring);
        setOnClickListener(v -> {
            if (items.isEmpty()) return;
            String id = items.get(idx)[0];
            if (id.startsWith(APP)) opener.openApp(id.substring(APP.length()));
            else opener.open(id, items.get(idx)[1]);
        });
    }

    void setItems(List<String[]> list) {
        String now = items.isEmpty() ? null : items.get(idx)[0];
        items = list;
        setVisibility(list.isEmpty() ? GONE : VISIBLE);
        if (list.isEmpty()) return;
        int keep = 0;
        for (int i = 0; i < list.size(); i++) if (list.get(i)[0].equals(now)) keep = i;
        show(keep);
    }

    void start() { main.removeCallbacks(rotate); main.postDelayed(rotate, ROTATE_MS); }
    void stop() { main.removeCallbacks(rotate); }

    @Override public boolean onKeyDown(int code, KeyEvent ev) {
        if (items.size() > 1 && (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT)) {
            int step = code == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1;
            show((idx + step + items.size()) % items.size());
            return true;
        }
        return super.onKeyDown(code, ev);
    }

    private void show(int i) {
        idx = i;
        final String[] it = items.get(i);
        label.setText(it[2].toUpperCase());
        title.setText(it[1]);
        dots.removeAllViews();
        for (int d = 0; d < items.size(); d++) {
            View dot = new View(getContext());
            dot.setBackground(round(d == i ? theme.text : Theme.alpha(theme.text, 0x66), 999, 0));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(d == i ? dp(18) : dp(6), dp(6));
            lp.rightMargin = dp(6);
            dots.addView(dot, lp);
        }
        if (it[0].startsWith(APP)) {
            showApp(it[0].substring(APP.length()));
            return;
        }
        play.setText("▶  Reproducir");
        appArt.setVisibility(GONE);
        blur(false);
        Bitmap bm = thumbs.get(it[0]);
        if (bm != null) crossfade(bm);
        else fetch(it[0]);
    }

    /** Diapositiva de app: su banner de Android TV nítido a la derecha y desenfocado de fondo. */
    private void showApp(String pkg) {
        play.setText("Abrir");
        PackageManager pm = getContext().getPackageManager();
        Drawable banner = null;
        try {
            Intent launch = pm.getLeanbackLaunchIntentForPackage(pkg);
            if (launch != null) banner = pm.getActivityBanner(launch);
            if (banner == null) banner = pm.getApplicationBanner(pkg);
            if (banner == null) banner = pm.getApplicationIcon(pkg);
        } catch (Exception ignored) { }
        if (banner == null) return;
        Bitmap bm = Bitmap.createBitmap(640, 360, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bm);
        banner.setBounds(0, 0, 640, 360);
        banner.draw(cv);
        appArt.setImageDrawable(banner);
        appArt.setVisibility(VISIBLE);
        blur(true);
        crossfade(bm);
    }

    private void blur(boolean on) {
        if (Build.VERSION.SDK_INT >= 31) {
            img.setRenderEffect(on ? RenderEffect.createBlurEffect(40f, 40f, Shader.TileMode.CLAMP) : null);
        }
    }

    private void crossfade(final Bitmap bm) {
        img.animate().alpha(0f).setDuration(180).withEndAction(() -> {
            img.setImageBitmap(bm);
            img.animate().alpha(1f).setDuration(450).start();
        }).start();
    }

    /** Miniatura grande (maxres); si el video no la tiene, la de alta calidad. */
    private void fetch(final String id) {
        new Thread(() -> {
            Bitmap bm = download("https://i.ytimg.com/vi/" + id + "/maxresdefault.jpg");
            if (bm == null) bm = download("https://i.ytimg.com/vi/" + id + "/hqdefault.jpg");
            if (bm == null) return;
            final Bitmap got = bm;
            main.post(() -> {
                thumbs.put(id, got);
                if (!items.isEmpty() && items.get(idx)[0].equals(id)) crossfade(got);
            });
        }).start();
    }

    static Bitmap download(String url) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(8000);
            if (c.getResponseCode() != 200) return null;
            InputStream in = c.getInputStream();
            Bitmap bm = BitmapFactory.decodeStream(in);
            in.close();
            return bm;
        } catch (Exception e) { return null; }
    }

    private GradientDrawable round(int color, int radius, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radius));
        if (stroke > 0) g.setStroke(dp(stroke), theme.focusStroke);
        return g;
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }
}
