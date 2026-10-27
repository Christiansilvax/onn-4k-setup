package com.remap.buttons;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;

public class KeyService extends AccessibilityService {
    interface CaptureListener { void onKey(int keyCode); }

    /** Cuando no es null, la proxima tecla se entrega aqui en vez de ejecutarse. */
    static volatile CaptureListener capture;

    private static final String GOOGLE_HOME = "com.google.android.apps.tv.launcherx";

    private final Handler main = new Handler(Looper.getMainLooper());
    private ScreenTitles titles;
    private AppSearch search;
    private static volatile KeyService running;

    /** Al abrirse la app, busca el título dentro de ella (ver AppSearch). No hace nada en la versión de Play Store. */
    static void searchIn(String pkg, String query, boolean history) {
        KeyService s = running;
        if (s == null || !Keys.SIDELOAD) return;
        s.main.post(() -> s.search.start(pkg, query, history));
    }

    @Override protected void onServiceConnected() {
        new Prefs(this).restoreIfEmpty();
        // al encender, el inicio de Google puede aparecer antes que este servicio: se revisa qué hay en pantalla
        main.postDelayed(this::replaceIfGoogleHome, 1500);
        search = new AppSearch(this, main);
        running = this;
        if (ScreenTitles.available()) {
            titles = new ScreenTitles(this, main);
            titles.start();
        }
    }

    @Override public void onDestroy() {
        running = null;
        if (titles != null) titles.stop();
        super.onDestroy();
    }

    private void replaceIfGoogleHome() {
        android.view.accessibility.AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;
        boolean google = GOOGLE_HOME.equals(String.valueOf(root.getPackageName()));
        root.recycle();
        if (google && new LauncherSettings(this).replaceGoogleHome()) openHome();
    }

    /**
     * Si aparece la pantalla de inicio de Google TV (al encender la caja o por otra vía), abre el inicio propio.
     * Solo esa pantalla: el resto de pantallas de Google (ajustes, cuenta…) no se tocan. Se apaga en Ajustes.
     */
    @Override public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        if (titles != null && e.getPackageName() != null) titles.windowChanged();
        if (!GOOGLE_HOME.equals(String.valueOf(e.getPackageName()))) return;
        CharSequence cls = e.getClassName();
        if (cls == null || !cls.toString().endsWith(".home.HomeActivity")) return;
        if (new LauncherSettings(this).replaceGoogleHome()) openHome();
    }

    private void openHome() {
        Intent h = new Intent(this, HomeActivity.class);
        h.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(h);
    }
    @Override public void onInterrupt() { }

    @Override
    protected boolean onKeyEvent(KeyEvent ev) {
        int k = ev.getKeyCode();

        CaptureListener c = capture;
        if (c != null && !Prefs.isProtected(k)) {
            if (ev.getAction() == KeyEvent.ACTION_DOWN && ev.getRepeatCount() == 0) {
                capture = null;
                main.post(() -> c.onKey(k));
            }
            return true;
        }

        // Home abre el launcher propio; el de Google TV se queda con el boton si el servicio se apaga.
        if (k == KeyEvent.KEYCODE_HOME) {
            if (ev.getAction() == KeyEvent.ACTION_DOWN && ev.getRepeatCount() == 0) openHome();
            return true;
        }

        Prefs prefs = new Prefs(this);
        if (!prefs.has(k)) return false; // sin configurar: comportamiento normal
        if (ev.getAction() == KeyEvent.ACTION_DOWN && ev.getRepeatCount() == 0) {
            Intent i = prefs.resolve(k);
            if (i != null) {
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
            } else {
                Toast.makeText(this, "No encuentro la app de este botón. Abre «Botones del control» y elige otra.", Toast.LENGTH_LONG).show();
            }
        }
        return true; // consume la tecla para que el sistema no haga su accion original
    }
}
