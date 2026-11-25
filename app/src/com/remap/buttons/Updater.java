package com.remap.buttons;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.Build;
import android.util.Log;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import org.json.JSONObject;

/**
 * Se actualiza solo desde el servidor (Keys.UPDATE_URL, de update.url al compilar): lee version.json
 * {code, apk, sha256}; si la versión es más nueva baja la APK, revisa su huella y la instala sin preguntar
 * (Android 12+ deja a una app actualizarse a sí misma con UPDATE_PACKAGES_WITHOUT_USER_ACTION). La revisa
 * KeyService al arrancar y cada hora. Sin update.url (o en la versión de Play Store) no hace nada.
 */
final class Updater {
    private static final String TAG = "Updater";
    static final long EVERY_MS = 3600 * 1000L;
    private static volatile boolean busy;

    private Updater() { }

    static boolean enabled() { return Keys.SIDELOAD && !Keys.UPDATE_URL.isEmpty(); }

    /** Hace red en su propio hilo. */
    static void check(Context ctx) {
        if (!enabled() || busy) return;
        busy = true;
        final Context c = ctx.getApplicationContext();
        new Thread(() -> {
            try { run(c); } catch (Exception e) { Log.w(TAG, "sin actualizar: " + e); }
            finally { busy = false; }
        }).start();
    }

    private static void run(Context c) throws Exception {
        JSONObject v = new JSONObject(Videos.get(Keys.UPDATE_URL + "version.json?t=" + System.currentTimeMillis()));
        long code = v.getLong("code");
        if (code <= installedCode(c)) return;
        File apk = new File(c.getCacheDir(), "update.apk");
        download(Keys.UPDATE_URL + v.optString("apk", "remap.apk"), apk);
        // una descarga cortada o cambiada no se instala (de todos modos Android exige la misma firma)
        if (!sha256(apk).equalsIgnoreCase(v.getString("sha256"))) {
            Log.w(TAG, "huella distinta, no se instala");
            apk.delete();
            return;
        }
        Log.i(TAG, "instalando versión " + code);
        install(c, apk);
    }

    @SuppressWarnings("deprecation")
    static long installedCode(Context c) throws Exception {
        android.content.pm.PackageInfo p = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
        return Build.VERSION.SDK_INT >= 28 ? p.getLongVersionCode() : p.versionCode;
    }

    private static void download(String url, File to) throws Exception {
        HttpURLConnection h = (HttpURLConnection) new URL(url).openConnection();
        h.setConnectTimeout(10000);
        h.setReadTimeout(30000);
        try (InputStream in = h.getInputStream(); OutputStream out = new FileOutputStream(to)) {
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
    }

    private static String sha256(File f) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new FileInputStream(f)) {
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : md.digest()) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private static void install(Context c, File apk) throws Exception {
        PackageInstaller pi = c.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams p = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        p.setAppPackageName(c.getPackageName());
        if (Build.VERSION.SDK_INT >= 31) p.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED);
        int id = pi.createSession(p);
        try (PackageInstaller.Session s = pi.openSession(id)) {
            try (InputStream in = new FileInputStream(apk); OutputStream out = s.openWrite("remap.apk", 0, apk.length())) {
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                s.fsync(out);
            }
            int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
            PendingIntent done = PendingIntent.getBroadcast(c, id, new Intent(c, Done.class), flags);
            s.commit(done.getIntentSender());
        }
    }

    /** Resultado de la instalación. Si Android pide confirmar (la primera vez, sin el permiso), muestra su aviso. */
    public static final class Done extends BroadcastReceiver {
        @Override public void onReceive(Context c, Intent i) {
            int st = i.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
            if (st == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                Intent ask = i.getParcelableExtra(Intent.EXTRA_INTENT);
                if (ask != null) {
                    ask.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    try { c.startActivity(ask); } catch (Exception ignored) { }
                }
                return;
            }
            Log.i(TAG, "estado " + st + ": " + i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE));
        }
    }
}
