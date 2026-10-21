package com.remap.buttons;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.List;

/** Pantalla sencilla pensada para usarse solo con el control del TV. */
public class MainActivity extends Activity {
    private static final int BG = 0xFF14161B;
    private static final int CARD = 0xFF242833;
    private static final int FOCUS = 0xFF2F6FED;
    private static final int OK_GREEN = 0xFF2E9E5B;
    private static final int WARN = 0xFFC77B16;
    private static final long CAPTURE_MS = 15000;

    private final Handler main = new Handler(Looper.getMainLooper());
    private LinearLayout status;
    private LinearLayout list;
    private Dialog waiting;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = column();
        root.setBackgroundColor(BG);
        root.setPadding(dp(56), dp(32), dp(56), dp(32));

        root.addView(text("Botones del control", 34, Color.WHITE, true));
        root.addView(text("Elige un botón de tu control y dile qué app debe abrir.", 20, 0xFFB8BECC, false));

        status = column();
        status.setPadding(0, dp(20), 0, 0);
        root.addView(status);

        View add = bigButton("＋  Cambiar un botón", FOCUS);
        add.setOnClickListener(v -> startCapture());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(20);
        root.addView(add, lp);

        TextView sub = text("Botones ya cambiados", 22, Color.WHITE, true);
        sub.setPadding(0, dp(28), 0, dp(8));
        root.addView(sub);

        list = column();
        root.addView(list);

        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.setBackgroundColor(BG);
        sv.addView(root);
        setContentView(sv);
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
        if (!serviceOn()) {
            LinearLayout box = column();
            box.setBackground(round(WARN, dp(14)));
            box.setPadding(dp(24), dp(18), dp(24), dp(18));
            box.addView(text("Falta un paso: activar esta app", 24, Color.WHITE, true));
            box.addView(text("Pulsa el botón de abajo, busca «Botones del control» en la lista y ponla en «Activado».", 18, Color.WHITE, false));
            View go = bigButton("Ir a activarla", 0xFF8A5410);
            go.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = dp(12);
            box.addView(go, lp);
            status.addView(box);
        } else {
            TextView ok = text("✔ La app está activada y lista", 20, 0xFF6FD79A, false);
            status.addView(ok);
        }

        list.removeAllViews();
        Prefs p = new Prefs(this);
        List<Integer> keys = p.mappedKeys();
        if (keys.isEmpty()) {
            list.addView(text("Todavía no has cambiado ningún botón.", 18, 0xFF8B93A5, false));
        }
        for (int k : keys) list.addView(card(p, k));
    }

    private View card(Prefs p, int k) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(20), dp(14), dp(20), dp(14));
        row.setBackground(focusBg(CARD));
        row.setFocusable(true);
        row.setClickable(true);

        row.addView(text(KeyNames.of(k), 22, Color.WHITE, true), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(text("→", 26, 0xFF8B93A5, false));

        Drawable ic = p.iconOf(p.getPackage(k));
        if (ic != null) {
            ImageView iv = new ImageView(this);
            iv.setImageDrawable(ic);
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(48), dp(48));
            ilp.leftMargin = dp(16);
            row.addView(iv, ilp);
        }
        TextView app = text(p.getLabel(k), 22, Color.WHITE, false);
        app.setPadding(dp(14), 0, 0, 0);
        row.addView(app);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(10);
        row.setLayoutParams(lp);
        row.setOnClickListener(v -> edit(k));
        return row;
    }

    private void edit(int k) {
        new AlertDialog.Builder(this)
            .setTitle(KeyNames.of(k))
            .setItems(new String[]{"Elegir otra app", "Dejar el botón como estaba", "Cancelar"}, (d, which) -> {
                if (which == 0) pickApp(k);
                else if (which == 1) {
                    new Prefs(this).remove(k);
                    Toast.makeText(this, KeyNames.of(k) + " vuelve a funcionar como antes", Toast.LENGTH_LONG).show();
                    refresh();
                }
            })
            .show();
    }

    // ---------- captura del botón ----------

    private void startCapture() {
        if (!serviceOn()) {
            Toast.makeText(this, "Primero activa la app (aviso naranja arriba).", Toast.LENGTH_LONG).show();
            return;
        }
        LinearLayout box = column();
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setPadding(dp(40), dp(32), dp(40), dp(32));
        box.setBackgroundColor(BG);
        TextView t = text("Pulsa ahora el botón del control que quieres cambiar", 28, Color.WHITE, true);
        t.setGravity(Gravity.CENTER);
        box.addView(t);
        TextView hint = text("Tienes 15 segundos. Para cancelar pulsa Atrás.", 18, 0xFFB8BECC, false);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, dp(16), 0, 0);
        box.addView(hint);

        Dialog d = new AlertDialog.Builder(this).setView(box).create();
        d.setOnKeyListener((dlg, code, ev) -> {
            if (ev.getAction() == KeyEvent.ACTION_DOWN && code != KeyEvent.KEYCODE_BACK && Prefs.isProtected(code)) {
                hint.setText("Ese botón no se puede cambiar, para que no pierdas el control. Prueba con otro.");
                hint.setTextColor(0xFFF0B060);
                return true;
            }
            return false;
        });
        d.setOnDismissListener(x -> { KeyService.capture = null; main.removeCallbacksAndMessages(null); });
        waiting = d;
        d.show();

        KeyService.capture = k -> {
            stopCapture();
            Toast.makeText(this, "Detecté: " + KeyNames.of(k), Toast.LENGTH_SHORT).show();
            pickApp(k);
        };
        main.postDelayed(() -> {
            if (waiting != null) {
                stopCapture();
                Toast.makeText(this, "No pulsaste ningún botón. Intenta de nuevo.", Toast.LENGTH_LONG).show();
            }
        }, CAPTURE_MS);
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

    private void pickApp(int keyCode) {
        Prefs p = new Prefs(this);
        List<Prefs.AppEntry> apps = p.listApps();
        ListView lv = new ListView(this);
        lv.setAdapter(new AppAdapter(this, apps));
        lv.setDividerHeight(0);
        lv.setPadding(dp(12), dp(8), dp(12), dp(8));
        lv.setBackgroundColor(BG);

        AlertDialog dlg = new AlertDialog.Builder(this)
            .setTitle("¿Qué app abrirá el «" + KeyNames.of(keyCode) + "»?")
            .setView(lv)
            .setNegativeButton("Cancelar", null)
            .create();
        lv.setOnItemClickListener((parent, v, pos, id) -> {
            Prefs.AppEntry e = apps.get(pos);
            p.set(keyCode, e.pkg, e.label);
            dlg.dismiss();
            Toast.makeText(this, "Listo: " + KeyNames.of(keyCode) + " ahora abre " + e.label, Toast.LENGTH_LONG).show();
            refresh();
        });
        dlg.show();
    }

    private final class AppAdapter extends BaseAdapter {
        private final Context c;
        private final List<Prefs.AppEntry> apps;
        AppAdapter(Context c, List<Prefs.AppEntry> apps) { this.c = c; this.apps = apps; }
        @Override public int getCount() { return apps.size(); }
        @Override public Object getItem(int i) { return apps.get(i); }
        @Override public long getItemId(int i) { return i; }
        @Override public View getView(int i, View old, ViewGroup parent) {
            Prefs.AppEntry e = apps.get(i);
            LinearLayout row = new LinearLayout(c);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(16), dp(10), dp(16), dp(10));
            row.setBackground(focusBg(CARD));
            ImageView iv = new ImageView(c);
            iv.setImageDrawable(e.icon);
            row.addView(iv, new LinearLayout.LayoutParams(dp(48), dp(48)));
            TextView tv = text(e.label, 22, Color.WHITE, false);
            tv.setPadding(dp(16), 0, 0, 0);
            row.addView(tv);
            return row;
        }
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
        if (bold) t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        return t;
    }

    private TextView bigButton(String s, int color) {
        TextView t = text(s, 24, Color.WHITE, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(24), dp(18), dp(24), dp(18));
        t.setBackground(focusBg(color));
        t.setFocusable(true);
        t.setClickable(true);
        return t;
    }

    private Drawable round(int color, int radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        return g;
    }

    /** Fondo que se ve claramente resaltado cuando el control se posa encima. */
    private Drawable focusBg(int normal) {
        GradientDrawable focused = (GradientDrawable) round(normal, dp(14));
        focused.setStroke(dp(4), Color.WHITE);
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_focused}, focused);
        s.addState(new int[]{}, round(normal, dp(14)));
        return s;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
