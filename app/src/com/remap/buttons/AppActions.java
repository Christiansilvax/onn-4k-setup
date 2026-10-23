package com.remap.buttons;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;

/**
 * Menu de una app (mantener OK o tecla Menu): abrir, mover, info y desinstalar.
 * En modo mover las flechas cambian la app de lugar y OK o Atras terminan; el orden se guarda.
 * Cada mosaico de la fila es un LinearLayout con el icono (hijo 0) y el nombre (hijo 1), en el mismo orden que la lista.
 */
final class AppActions {
    private final Activity act;
    private final Prefs prefs;
    private final ViewGroup box;
    private final List<Prefs.AppEntry> apps;
    private View moving;

    AppActions(Activity a, Prefs p, ViewGroup box, List<Prefs.AppEntry> apps) {
        act = a;
        prefs = p;
        this.box = box;
        this.apps = apps;
    }

    static void open(Activity a, String pkg) {
        PackageManager pm = a.getPackageManager();
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (i == null) i = pm.getLaunchIntentForPackage(pkg);
        if (i != null) a.startActivity(i);
    }

    /** Paquetes en el orden mostrado, para saber si hay que redibujar. */
    String shown() { return Prefs.join(apps); }

    Prefs.AppEntry entryOf(View tile) {
        int i = box.indexOfChild(tile);
        return i >= 0 && i < apps.size() ? apps.get(i) : null;
    }

    /** Pone icono y nombre, y conecta abrir, mantener OK y tecla Menu. */
    void attach(final View tile) {
        bind(tile, entryOf(tile), false);
        tile.setOnClickListener(v -> { Prefs.AppEntry e = entryOf(v); if (e != null) open(act, e.pkg); });
        tile.setOnLongClickListener(v -> { menu(v); return true; });
        tile.setOnKeyListener((v, code, ev) -> {
            if (code != KeyEvent.KEYCODE_MENU) return false;
            if (ev.getAction() == KeyEvent.ACTION_UP) menu(v);
            return true;
        });
    }

    private void bind(View tile, Prefs.AppEntry e, boolean isMoving) {
        ViewGroup g = (ViewGroup) tile;
        ((ImageView) g.getChildAt(0)).setImageDrawable(e.icon);
        ((TextView) g.getChildAt(1)).setText(isMoving ? "◀ " + e.label + " ▶" : e.label);
    }

    private void menu(final View tile) {
        final Prefs.AppEntry e = entryOf(tile);
        if (e == null) return;
        final List<String> items = new ArrayList<>();
        items.add("Abrir");
        items.add("Mover");
        items.add("Información de la app");
        if (!isSystem(e.pkg) && !e.pkg.equals(act.getPackageName())) items.add("Desinstalar"); // no desinstalar el propio launcher
        new AlertDialog.Builder(act, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(e.label)
                .setItems(items.toArray(new String[0]), (d, w) -> {
                    switch (items.get(w)) {
                        case "Abrir": open(act, e.pkg); break;
                        case "Mover": startMove(tile); break;
                        case "Información de la app": info(e.pkg); break;
                        case "Desinstalar": uninstall(e.pkg); break;
                    }
                })
                .show();
    }

    private boolean isSystem(String pkg) {
        try {
            ApplicationInfo ai = act.getPackageManager().getApplicationInfo(pkg, 0);
            return (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
        } catch (PackageManager.NameNotFoundException ex) { return true; }
    }

    private void info(String pkg) {
        try {
            act.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg)));
        } catch (Exception ex) { Toast.makeText(act, "No se pudo abrir la información", Toast.LENGTH_SHORT).show(); }
    }

    private void uninstall(String pkg) {
        try {
            act.startActivity(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + pkg)));
        } catch (Exception ex) { Toast.makeText(act, "No se pudo abrir el desinstalador", Toast.LENGTH_SHORT).show(); }
    }

    private void startMove(View tile) {
        moving = tile;
        bind(tile, entryOf(tile), true);
        tile.requestFocus();
        Toast.makeText(act, "Muévela con las flechas. OK o Atrás para terminar.", Toast.LENGTH_LONG).show();
    }

    /** Termina el modo mover (al pulsar OK/Atras o si la pantalla se va). */
    void stop() {
        if (moving == null) return;
        Prefs.AppEntry e = entryOf(moving);
        if (e != null) bind(moving, e, false);
        moving = null;
        prefs.saveOrder(apps);
    }

    /** Llamar desde dispatchKeyEvent: en modo mover se queda con las flechas, OK y Atras. */
    boolean handleKey(KeyEvent ev) {
        if (moving == null) return false;
        int step;
        switch (ev.getKeyCode()) {
            case KeyEvent.KEYCODE_DPAD_LEFT: step = -1; break;
            case KeyEvent.KEYCODE_DPAD_RIGHT: step = 1; break;
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN: return true; // la fila solo se mueve a los lados
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_BACK:
                if (ev.getAction() == KeyEvent.ACTION_UP) stop();
                return true;
            default: return false;
        }
        if (ev.getAction() == KeyEvent.ACTION_DOWN) moveBy(step);
        return true;
    }

    /** Cambia la app de lugar redibujando los mosaicos; no se quitan vistas para no perder el foco. */
    private void moveBy(int step) {
        int from = box.indexOfChild(moving), to = from + step;
        if (from < 0 || to < 0 || to >= apps.size()) return;
        apps.add(to, apps.remove(from));
        for (int i = Math.min(from, to); i <= Math.max(from, to); i++) bind(box.getChildAt(i), apps.get(i), i == to);
        moving = box.getChildAt(to);
        moving.requestFocus();
    }
}
