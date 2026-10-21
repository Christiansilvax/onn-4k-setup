package com.remap.buttons;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Outline;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.List;

/** Todas las apps, con el mismo estilo del inicio: iconos redondos sobre degradado. */
public class AppsActivity extends Activity {
    private ImageView backdrop;
    private Object shown;

    private void setBackdrop(final android.graphics.drawable.Drawable d, final Object key) {
        if (key == shown) return;
        shown = key;
        final android.graphics.Bitmap bm = android.graphics.Bitmap.createBitmap(160, 160, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas cv = new android.graphics.Canvas(bm);
        d.setBounds(0, 0, 160, 160);
        d.draw(cv);
        backdrop.animate().alpha(0f).setDuration(120).withEndAction(() -> {
            backdrop.setImageBitmap(bm);
            backdrop.animate().alpha(0.9f).setDuration(350).start();
        }).start();
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        List<Prefs.AppEntry> apps = new Prefs(this).listApps();
        FrameLayout frame = new FrameLayout(this);
        frame.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF1B1038, 0xFF0B1B3F, 0xFF06283D}));

        // fondo: icono enfocado muy ampliado y desenfocado, con velo oscuro
        backdrop = new ImageView(this);
        backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);
        backdrop.setAlpha(0f);
        if (android.os.Build.VERSION.SDK_INT >= 31)
            backdrop.setRenderEffect(android.graphics.RenderEffect.createBlurEffect(80f, 80f, android.graphics.Shader.TileMode.CLAMP));
        frame.addView(backdrop, new FrameLayout.LayoutParams(-1, -1));
        View scrim = new View(this);
        scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x66000000, 0x99060A1E, 0xCC060A1E}));
        frame.addView(scrim, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(48), dp(24), dp(48), dp(24));
        TextView title = new TextView(this);
        title.setText("Apps");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(26);
        title.setPadding(dp(8), 0, 0, dp(12));
        col.addView(title);

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(6);
        grid.setClipChildren(false);
        for (Prefs.AppEntry a : apps) grid.addView(tile(a));
        col.addView(grid);

        ScrollView sv = new ScrollView(this);
        sv.setClipChildren(false);
        sv.addView(col);
        frame.addView(sv, new FrameLayout.LayoutParams(-1, -1));
        setContentView(frame);
    }

    private View tile(final Prefs.AppEntry a) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        c.setFocusable(true);
        c.setPadding(dp(8), dp(10), dp(8), dp(10));

        ImageView iv = new ImageView(this);
        iv.setImageDrawable(a.icon);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        iv.setBackgroundColor(0x33FFFFFF);
        iv.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) { o.setOval(0, 0, v.getWidth(), v.getHeight()); }
        });
        iv.setClipToOutline(true);
        iv.setElevation(dp(6));
        iv.setDuplicateParentStateEnabled(true);
        GradientDrawable on = new GradientDrawable();
        on.setShape(GradientDrawable.OVAL);
        on.setColor(0);
        on.setStroke(dp(4), 0xFFFFFFFF);
        StateListDrawable ring = new StateListDrawable();
        ring.addState(new int[]{android.R.attr.state_focused}, on);
        ring.addState(new int[]{}, new ColorDrawable(0));
        iv.setForeground(ring);
        c.addView(iv, new LinearLayout.LayoutParams(dp(84), dp(84)));

        TextView t = new TextView(this);
        t.setText(a.label);
        t.setTextColor(0xDDFFFFFF);
        t.setShadowLayer(6f, 0f, 2f, 0xFF000000);
        t.setTextSize(12);
        t.setGravity(Gravity.CENTER);
        t.setMaxLines(1);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setPadding(0, dp(8), 0, 0);
        c.addView(t, new LinearLayout.LayoutParams(dp(104), ViewGroup.LayoutParams.WRAP_CONTENT));

        c.setOnFocusChangeListener((v, has) -> {
            v.animate().scaleX(has ? 1.12f : 1f).scaleY(has ? 1.12f : 1f).setDuration(150).start();
            if (has) setBackdrop(a.icon, a.pkg);
        });
        c.setOnClickListener(v -> {
            Intent in = getPackageManager().getLeanbackLaunchIntentForPackage(a.pkg);
            if (in == null) in = getPackageManager().getLaunchIntentForPackage(a.pkg);
            if (in != null) startActivity(in);
        });
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.setMargins(dp(6), dp(6), dp(6), dp(6));
        c.setLayoutParams(lp);
        return c;
    }
}
