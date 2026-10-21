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

    private final Handler main = new Handler(Looper.getMainLooper());

    @Override public void onAccessibilityEvent(AccessibilityEvent e) { }
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
            if (ev.getAction() == KeyEvent.ACTION_DOWN && ev.getRepeatCount() == 0) {
                Intent h = new Intent(this, HomeActivity.class);
                h.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(h);
            }
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
