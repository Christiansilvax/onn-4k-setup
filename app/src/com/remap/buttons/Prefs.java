package com.remap.buttons;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Environment;
import android.view.KeyEvent;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Guarda por cada tecla el paquete y el nombre visible de la app elegida, y el orden del launcher.
 * Cada cambio se copia a Documents/RemapBotones/botones.json, que sobrevive a desinstalar la app.
 */
final class Prefs {
    private final SharedPreferences sp;
    private final PackageManager pm;
    private final Context ctx;

    Prefs(Context c) {
        ctx = c;
        sp = c.getSharedPreferences("remap", Context.MODE_PRIVATE);
        pm = c.getPackageManager();
    }

    /** Teclas que no se pueden remapear, para no dejar el TV sin control. */
    static boolean isProtected(int k) {
        switch (k) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_HOME:
            case KeyEvent.KEYCODE_POWER:
                return true;
            default:
                return false;
        }
    }

    boolean has(int keyCode) { return sp.contains("k" + keyCode); }

    String getPackage(int keyCode) { return part(keyCode, 0); }
    String getLabel(int keyCode) { return part(keyCode, 1); }

    private String part(int keyCode, int i) {
        String v = sp.getString("k" + keyCode, null);
        if (v == null) return null;
        String[] p = v.split("\\|", 2);
        return p.length > i ? p[i] : null;
    }

    void set(int keyCode, String pkg, String label) {
        sp.edit().putString("k" + keyCode, pkg + "|" + label).apply();
        backup();
    }

    void remove(int keyCode) {
        sp.edit().remove("k" + keyCode).apply();
        backup();
    }

    // ---------- respaldo en JSON ----------

    static File backupFile() {
        return new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "RemapBotones/botones.json");
    }

    /** Escribir fuera de la app necesita "acceso a todos los archivos" (instalar.ps1 lo da por adb). */
    static boolean canBackup() {
        return Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager();
    }

    private void backup() {
        if (!canBackup()) return;
        try {
            JSONArray keys = new JSONArray();
            for (int k : mappedKeys()) {
                keys.put(new JSONObject().put("keyCode", k).put("boton", KeyNames.of(k))
                        .put("paquete", getPackage(k)).put("app", getLabel(k)));
            }
            JSONArray order = new JSONArray();
            for (String p : sp.getString("order", "").split(",")) if (!p.isEmpty()) order.put(p);
            JSONObject root = new JSONObject().put("version", 1).put("botones", keys).put("orden", order);
            File f = backupFile();
            f.getParentFile().mkdirs();
            Files.write(f.toPath(), root.toString(2).getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) { }
    }

    /** Tras instalar de cero (sin datos) recupera botones y orden del respaldo; si hay datos y no hay respaldo, lo crea. */
    void restoreIfEmpty() {
        if (!canBackup()) return;
        if (!sp.getAll().isEmpty()) {
            if (!backupFile().exists()) backup();
            return;
        }
        if (!backupFile().exists()) return;
        try {
            JSONObject root = new JSONObject(new String(Files.readAllBytes(backupFile().toPath()), StandardCharsets.UTF_8));
            SharedPreferences.Editor ed = sp.edit();
            JSONArray keys = root.optJSONArray("botones");
            for (int i = 0; keys != null && i < keys.length(); i++) {
                JSONObject o = keys.getJSONObject(i);
                ed.putString("k" + o.getInt("keyCode"), o.getString("paquete") + "|" + o.getString("app"));
            }
            JSONArray order = root.optJSONArray("orden");
            StringBuilder sb = new StringBuilder();
            for (int i = 0; order != null && i < order.length(); i++) sb.append(i > 0 ? "," : "").append(order.getString(i));
            if (sb.length() > 0) ed.putString("order", sb.toString());
            ed.apply();
        } catch (Exception ignored) { }
    }

    /** Teclas configuradas. */
    List<Integer> mappedKeys() {
        List<Integer> out = new ArrayList<>();
        for (Map.Entry<String, ?> e : sp.getAll().entrySet()) {
            if (e.getKey().startsWith("k")) out.add(Integer.parseInt(e.getKey().substring(1)));
        }
        out.sort(Integer::compare);
        return out;
    }

    /** Intent de lanzamiento. Si el paquete ya no existe, busca otra app con el mismo nombre. */
    Intent resolve(int keyCode) {
        String pkg = getPackage(keyCode);
        String label = getLabel(keyCode);
        if (pkg == null) return null;
        Intent i = launchIntent(pkg);
        if (i != null) return i;
        if (label != null) {
            for (AppEntry e : listApps()) {
                if (e.label.equalsIgnoreCase(label)) {
                    set(keyCode, e.pkg, e.label); // actualiza al nuevo nombre de paquete
                    return launchIntent(e.pkg);
                }
            }
        }
        return null;
    }

    private Intent launchIntent(String pkg) {
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (i == null) i = pm.getLaunchIntentForPackage(pkg);
        return i;
    }

    static final class AppEntry {
        final String pkg, label;
        final Drawable icon;
        AppEntry(String p, String l, Drawable i) { pkg = p; label = l; icon = i; }
    }

    /** Icono de la app, o null si ya no está instalada. */
    Drawable iconOf(String pkg) {
        try { return pm.getApplicationIcon(pkg); } catch (PackageManager.NameNotFoundException e) { return null; }
    }

    List<AppEntry> listApps() { return listApps(false); }

    /** withSelf: incluye "Botones" (la pantalla de esta app) para mostrarla en el inicio. */
    List<AppEntry> listApps(boolean withSelf) {
        List<ResolveInfo> all = new ArrayList<>();
        all.addAll(pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0));
        all.addAll(pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0));
        List<AppEntry> out = new ArrayList<>();
        List<String> seen = new ArrayList<>();
        for (ResolveInfo r : all) {
            ApplicationInfo ai = r.activityInfo.applicationInfo;
            if (seen.contains(ai.packageName) || (!withSelf && ai.packageName.equals(ctx.getPackageName()))) continue;
            seen.add(ai.packageName);
            out.add(new AppEntry(ai.packageName, shortName(ai.packageName, String.valueOf(r.loadLabel(pm))), r.loadIcon(pm)));
        }
        // primero el orden que el usuario eligio (las apps nuevas van al final), luego FIRST y alfabetico
        final List<String> order = Arrays.asList(sp.getString("order", "").split(","));
        out.sort((a, b) -> {
            int oa = pos(order, a.pkg), ob = pos(order, b.pkg);
            if (oa != ob) return Integer.compare(oa, ob);
            int ra = rank(a.pkg), rb = rank(b.pkg);
            return ra != rb ? ra - rb : a.label.compareToIgnoreCase(b.label);
        });
        return out;
    }

    private static int pos(List<String> order, String pkg) {
        int i = order.indexOf(pkg);
        return i < 0 ? Integer.MAX_VALUE : i;
    }

    static String join(List<AppEntry> apps) {
        StringBuilder sb = new StringBuilder();
        for (AppEntry a : apps) sb.append(sb.length() > 0 ? "," : "").append(a.pkg);
        return sb.toString();
    }

    /** Guarda el orden de las apps tras moverlas. */
    void saveOrder(List<AppEntry> apps) {
        sp.edit().putString("order", join(apps)).apply();
        backup();
    }

    /** Orden inicial de las primeras apps del launcher (hasta que el usuario las mueva); el resto va alfabetico. */
    private static final String[] FIRST = {
        "com.xuper.netxxus", "io.gh.reisxd.tizentube.cobalt", "com.magmaplayer",
        "com.cbs.ca", "com.cxinventor.file.explorer", "com.android.tv.settings", "com.remap.buttons"
    };

    /** Nombres cortos para que todos los iconos queden alineados. */
    private static String shortName(String pkg, String label) {
        switch (pkg) {
            case "io.gh.reisxd.tizentube.cobalt": return "TizenTube";
            case "com.cxinventor.file.explorer": return "Archivos";
            case "com.xuper.netxxus": return "Xuper";
            case "com.spocky.projengmenu": return "Projectivy";
            case "com.remap.buttons": return "Botones";
            default: return label;
        }
    }

    private static int rank(String pkg) {
        for (int i = 0; i < FIRST.length; i++) if (FIRST[i].equals(pkg)) return i;
        return FIRST.length;
    }
}
