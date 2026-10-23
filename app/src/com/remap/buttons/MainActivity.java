package com.remap.buttons;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.List;

/** Botones del control: una tarjeta por botón con la app que abre. Pensada para usarse solo con el control. */
public class MainActivity extends Activity {
    private static final int[] BG = {0xFF1B1038, 0xFF0B1B3F, 0xFF06283D}; // mismo degradado que el inicio
    private static final int MUTED = 0xFFAAB2C8;
    private static final int OK_GREEN = 0xFF4ADE80;
    private static final int WARN = 0xFFF59E0B;
    private static final int COLS = 4;
    private static final int PICK_COLS = 6;
    private static final int CAPTURE_S = 15;

    private final Handler main = new Handler(Looper.getMainLooper());
    private Prefs prefs;
    private LinearLayout status;
    private GridLayout grid;
    private TextView backup;
    private Dialog waiting;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = new Prefs(this);
        prefs.restoreIfEmpty();

        LinearLayout root = column();
        root.setPadding(dp(56), dp(36), dp(56), dp(28));
        root.setClipChildren(false);

        // encabezado: logo, título y estado del servicio
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.icon);
        rounded(logo, 18);
        head.addView(logo, new LinearLayout.LayoutParams(dp(64), dp(64)));
        LinearLayout titles = column();
        titles.setPadding(dp(20), 0, dp(20), 0);
        titles.addView(text("Botones del control", 30, Color.WHITE, true));
        titles.addView(text("Cada botón abre la app que elijas. Pulsa OK sobre una tarjeta para cambiarla.", 16, MUTED, false));
        head.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        status = new LinearLayout(this);
        head.addView(status);
        root.addView(head);

        grid = new GridLayout(this);
        grid.setColumnCount(COLS);
        grid.setClipChildren(false);
        grid.setPadding(0, dp(28), 0, 0);
        root.addView(grid);

        backup = text("", 14, MUTED, false);
        backup.setPadding(dp(10), dp(24), 0, 0);
        root.addView(backup);

        ScrollView sv = new ScrollView(this);
        sv.setClipChildren(false);
        sv.addView(root);
        FrameLayout frame = new FrameLayout(this);
        frame.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR, BG));
        frame.addView(sv, new FrameLayout.LayoutParams(-1, -1));
        setContentView(frame);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopCapture();
    }

    // ---------- pantalla principal ----------

    private boolean serviceOn() {
        String enabled = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return enabled != null && enabled.contains(getPackageName() + "/");
    }

    private void refresh() {
        status.removeAllViews();
        if (serviceOn()) {
            TextView on = pill("●  Activo", OK_GREEN, 0x2916A34A);
            status.addView(on);
        } else {
            TextView go = pill("Activar el servicio", Color.WHITE, 0);
            go.setBackground(focusBg(0xCCB45309, 999));
            go.setFocusable(true);
            go.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
            status.addView(go);
        }

        View focused = getCurrentFocus();
        int was = focused != null ? grid.indexOfChild(focused) : -1;
        grid.removeAllViews();
        for (int k : prefs.mappedKeys()) grid.addView(card(k));
        grid.addView(addCard());
        int n = grid.getChildCount();
        if (focused == null || was >= 0) grid.getChildAt(Math.max(0, Math.min(was, n - 1))).requestFocus();

        if (Prefs.canBackup()) {
            backup.setText("✔  Respaldo en " + Prefs.backupFile().getPath().replace("/storage/emulated/0/", "") + " (no se borra al desinstalar)");
            backup.setTextColor(MUTED);
        } else {
            backup.setText("⚠  Sin respaldo: falta el permiso de archivos. instalar.ps1 lo activa.");
            backup.setTextColor(WARN);
        }
    }

    /** Tarjeta de un botón: nombre del botón arriba, icono y nombre de la app que abre. */
    private View card(final int k) {
        LinearLayout c = tileBox();
        TextView chip = text(KeyNames.of(k), 14, Color.WHITE, true);
        chip.setSingleLine(true);
        chip.setEllipsize(TextUtils.TruncateAt.END);
        chip.setPadding(dp(14), dp(6), dp(14), dp(6));
        chip.setBackground(round(0x2EFFFFFF, 999));
        c.addView(chip);

        Drawable ic = prefs.iconOf(prefs.getPackage(k));
        ImageView iv = new ImageView(this);
        iv.setImageDrawable(ic != null ? ic : getDrawable(R.drawable.icon));
        if (ic == null) iv.setAlpha(0.35f);
        oval(iv);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(88), dp(88));
        ilp.topMargin = dp(20);
        c.addView(iv, ilp);

        TextView app = text(ic != null ? prefs.getLabel(k) : "App no instalada", 18, ic != null ? Color.WHITE : WARN, true);
        app.setSingleLine(true);
        app.setEllipsize(TextUtils.TruncateAt.END);
        app.setGravity(Gravity.CENTER);
        app.setPadding(0, dp(14), 0, 0);
        c.addView(app, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        c.setOnClickListener(v -> edit(k));
        return c;
    }

    private View addCard() {
        LinearLayout c = tileBox();
        c.setGravity(Gravity.CENTER);
        GradientDrawable dashed = round(0x10FFFFFF, 22);
        dashed.setStroke(dp(2), 0x66FFFFFF, dp(10), dp(7));
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_focused}, stroked(0x40FFFFFF, 22, 3, Color.WHITE));
        s.addState(new int[]{}, dashed);
        c.setBackground(s);
        TextView plus = text("＋", 48, Color.WHITE, false);
        plus.setGravity(Gravity.CENTER);
        c.addView(plus);
        TextView t = text("Agregar botón", 18, Color.WHITE, true);
        t.setPadding(0, dp(8), 0, 0);
        c.addView(t);
        c.setOnClickListener(v -> startCapture());
        return c;
    }

    /** Caja de cristal del tamaño de una tarjeta; crece un poco al enfocarla, como en el inicio. */
    private LinearLayout tileBox() {
        LinearLayout c = column();
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        c.setPadding(dp(16), dp(20), dp(16), dp(20));
        c.setBackground(focusBg(0x1FFFFFFF, 22));
        c.setFocusable(true);
        c.setClickable(true);
        c.setOnFocusChangeListener((v, has) -> {
            v.animate().scaleX(has ? 1.05f : 1f).scaleY(has ? 1.05f : 1f).setDuration(150).start();
            v.setElevation(has ? 24f : 0f);
        });
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.width = dp(208);
        lp.height = dp(232);
        lp.setMargins(dp(10), dp(10), dp(10), dp(10));
        c.setLayoutParams(lp);
        return c;
    }

    private void edit(final int k) {
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(KeyNames.of(k))
            .setItems(new String[]{"Elegir otra app", "Quitar (vuelve a su función normal)"}, (d, which) -> {
                if (which == 0) pickApp(k);
                else {
                    prefs.remove(k);
                    Toast.makeText(this, KeyNames.of(k) + " vuelve a funcionar como antes", Toast.LENGTH_LONG).show();
                    refresh();
                }
            })
            .setNegativeButton("Cancelar", null)
            .show();
    }

    // ---------- captura del botón ----------

    private void startCapture() {
        if (!serviceOn()) {
            Toast.makeText(this, "Primero activa el servicio (botón de arriba a la derecha).", Toast.LENGTH_LONG).show();
            return;
        }
        LinearLayout box = column();
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setPadding(dp(48), dp(36), dp(48), dp(32));
        box.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR, BG));
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.icon);
        rounded(logo, 18);
        box.addView(logo, new LinearLayout.LayoutParams(dp(72), dp(72)));
        TextView t = text("Pulsa el botón del control que quieres cambiar", 26, Color.WHITE, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, dp(20), 0, 0);
        box.addView(t);
        final TextView hint = text("", 17, MUTED, false);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, dp(14), 0, 0);
        box.addView(hint);

        Dialog d = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert).setView(box).create();
        d.setOnKeyListener((dlg, code, ev) -> {
            if (ev.getAction() == KeyEvent.ACTION_DOWN && code != KeyEvent.KEYCODE_BACK && Prefs.isProtected(code)) {
                hint.setText("Ese botón no se puede cambiar, para que no pierdas el control. Prueba con otro.");
                hint.setTextColor(WARN);
                return true;
            }
            return false;
        });
        d.setOnDismissListener(x -> { KeyService.capture = null; main.removeCallbacksAndMessages(null); });
        waiting = d;
        d.show();

        KeyService.capture = k -> {
            stopCapture();
            pickApp(k);
        };
        countdown(hint, CAPTURE_S);
    }

    private void countdown(final TextView hint, final int s) {
        if (waiting == null) return;
        if (s == 0) {
            stopCapture();
            Toast.makeText(this, "No pulsaste ningún botón. Intenta de nuevo.", Toast.LENGTH_LONG).show();
            return;
        }
        if (hint.getCurrentTextColor() != WARN) hint.setText("Quedan " + s + " s  ·  Atrás para cancelar");
        main.postDelayed(() -> countdown(hint, s - 1), 1000);
    }

    private void stopCapture() {
        KeyService.capture = null;
        main.removeCallbacksAndMessages(null);
        if (waiting != null) {
            Dialog d = waiting;
            waiting = null;
            d.dismiss();
        }
    }

    // ---------- elegir la app ----------

    /** Pantalla completa con todas las apps en cuadrícula; la que ya tiene el botón sale marcada y enfocada. */
    private void pickApp(final int keyCode) {
        final Dialog d = new Dialog(this, android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen);
        LinearLayout col = column();
        col.setPadding(dp(56), dp(36), dp(56), dp(28));
        col.setClipChildren(false);
        col.addView(text("¿Qué app abrirá el «" + KeyNames.of(keyCode) + "»?", 28, Color.WHITE, true));
        col.addView(text("Elige con OK  ·  Atrás para cancelar", 16, MUTED, false));

        GridLayout g = new GridLayout(this);
        g.setColumnCount(PICK_COLS);
        g.setClipChildren(false);
        g.setPadding(0, dp(20), 0, 0);
        String current = prefs.getPackage(keyCode);
        View focus = null;
        for (final Prefs.AppEntry e : prefs.listApps()) {
            boolean selected = e.pkg.equals(current);
            View tile = appTile(e, selected);
            tile.setOnClickListener(v -> {
                prefs.set(keyCode, e.pkg, e.label);
                d.dismiss();
                Toast.makeText(this, KeyNames.of(keyCode) + " ahora abre " + e.label, Toast.LENGTH_LONG).show();
                refresh();
            });
            g.addView(tile);
            if (selected || focus == null) focus = tile;
        }
        col.addView(g);

        ScrollView sv = new ScrollView(this);
        sv.setClipChildren(false);
        sv.addView(col);
        FrameLayout frame = new FrameLayout(this);
        frame.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR, BG));
        frame.addView(sv, new FrameLayout.LayoutParams(-1, -1));
        d.setContentView(frame);
        d.show();
        if (focus != null) focus.requestFocus();
    }

    private View appTile(Prefs.AppEntry e, boolean selected) {
        LinearLayout c = column();
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        c.setFocusable(true);
        c.setPadding(dp(8), dp(10), dp(8), dp(10));

        ImageView iv = new ImageView(this);
        iv.setImageDrawable(e.icon);
        oval(iv);
        iv.setDuplicateParentStateEnabled(true);
        // aro blanco al enfocar; verde fijo en la app que ya está elegida
        StateListDrawable ring = new StateListDrawable();
        ring.addState(new int[]{android.R.attr.state_focused}, ring(Color.WHITE));
        ring.addState(new int[]{}, selected ? ring(OK_GREEN) : new ColorDrawable(0));
        iv.setForeground(ring);
        c.addView(iv, new LinearLayout.LayoutParams(dp(80), dp(80)));

        TextView t = text(selected ? "✔ " + e.label : e.label, 13, selected ? OK_GREEN : 0xDDFFFFFF, selected);
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setPadding(0, dp(8), 0, 0);
        c.addView(t, new LinearLayout.LayoutParams(dp(110), ViewGroup.LayoutParams.WRAP_CONTENT));

        c.setOnFocusChangeListener((v, has) -> v.animate().scaleX(has ? 1.1f : 1f).scaleY(has ? 1.1f : 1f).setDuration(150).start());
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.setMargins(dp(6), dp(6), dp(6), dp(6));
        c.setLayoutParams(lp);
        return c;
    }

    // ---------- ayudas de diseño ----------

    private LinearLayout column() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(Typeface.create(bold ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        return t;
    }

    private TextView pill(String s, int textColor, int bg) {
        TextView t = text(s, 16, textColor, true);
        t.setPadding(dp(18), dp(8), dp(18), dp(8));
        if (bg != 0) t.setBackground(round(bg, 999));
        return t;
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radius));
        return g;
    }

    private GradientDrawable stroked(int color, int radius, int stroke, int strokeColor) {
        GradientDrawable g = round(color, radius);
        g.setStroke(dp(stroke), strokeColor);
        return g;
    }

    /** Cristal: translúcido con borde fino; al enfocar se aclara y el borde se pone blanco. */
    private Drawable focusBg(int normal, int radius) {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_focused}, stroked(0x40FFFFFF, radius, 3, Color.WHITE));
        s.addState(new int[]{}, stroked(normal, radius, 1, 0x33FFFFFF));
        return s;
    }

    private Drawable ring(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(0);
        g.setStroke(dp(4), color);
        return g;
    }

    private void oval(ImageView iv) {
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        iv.setBackgroundColor(0x33FFFFFF);
        iv.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) { o.setOval(0, 0, v.getWidth(), v.getHeight()); }
        });
        iv.setClipToOutline(true);
        iv.setElevation(dp(6));
    }

    private void rounded(ImageView iv, final int radius) {
        iv.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(radius)); }
        });
        iv.setClipToOutline(true);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
