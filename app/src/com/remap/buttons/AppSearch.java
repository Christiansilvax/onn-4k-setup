package com.remap.buttons;

import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.ArrayList;
import java.util.List;

/**
 * Lleva a un título dentro de una app que no acepta enlaces ni búsquedas de afuera (como Xuper): con accesibilidad
 * abre su historial y el título ahí (sigue en el punto exacto); si no está, toca su lupa, escribe el nombre y abre el
 * resultado igual. Luego acepta "seguir" y lo pone en pantalla completa. Cada 700 ms mira la
 * pantalla y da el paso que toque; se rinde a los 45 s o si aparece otra app. Solo en la APK directa (Keys.SIDELOAD).
 */
final class AppSearch {
    private static final String TAG = "AppSearch";
    private static final long STEP_MS = 700, GIVE_UP_MS = 45000;
    private static final String[] FULLSCREEN = {"pantalla completa", "full screen", "fullscreen", "reproducir", "play"};
    private static final String[] CONTINUE = {"seguir", "continuar", "continue", "reanudar"};
    private static final String[] HISTORY = {"historia", "historial", "history", "mi lista"};

    private final KeyService svc;
    private final Handler main;
    private String pkg, query;
    private long until;
    private boolean typed, opened, inHistory, noHistory, historyTab;
    private long historyAt;
    private long detailsAt, tappedAt; // cuándo apareció la ficha y cuándo se tocó "pantalla completa"
    private int taps, tabTaps, cardTries;

    private final Runnable step = this::step;

    AppSearch(KeyService s, Handler h) {
        svc = s;
        main = h;
    }

    /** history: buscar primero en el historial de la app (lo que ya viste); si no, directo al buscador. */
    void start(String pkg, String query, boolean history) {
        this.pkg = pkg;
        this.query = query;
        typed = opened = inHistory = historyTab = false;
        noHistory = !history;
        historyAt = 0;
        detailsAt = tappedAt = 0;
        taps = tabTaps = cardTries = 0;
        until = System.currentTimeMillis() + GIVE_UP_MS;
        main.removeCallbacks(step);
        main.postDelayed(step, 1500);
    }

    private void step() {
        if (pkg == null || System.currentTimeMillis() > until) { pkg = null; return; }
        AccessibilityNodeInfo root = svc.getRootInActiveWindow();
        if (root != null && pkg.equals(String.valueOf(root.getPackageName())) && act(root)) {
            Log.d(TAG, "listo: " + query);
            pkg = null;
            return;
        }
        main.postDelayed(step, STEP_MS);
    }

    /** Un paso según la pantalla. true = terminado. */
    private boolean act(AccessibilityNodeInfo root) {
        long now = System.currentTimeMillis();
        // aviso "Comenzar a partir de 01:18:22": seguir donde iba
        AccessibilityNodeInfo cont = find(root, n -> n.isVisibleToUser() && matchesAny(n, CONTINUE));
        if (cont != null) {
            click(cont);
            opened = true;
            return false;
        }
        AccessibilityNodeInfo input = find(root, AppSearch::isEditText);
        // en la ficha del título: pantalla completa (solo el botón que se ve: a pantalla completa la ficha sigue debajo)
        AccessibilityNodeInfo fs = find(root, n -> n.isVisibleToUser() && matchesAny(n, FULLSCREEN));
        if (fs != null && input == null && (opened || hasText(root, query))) {
            if (detailsAt == 0) detailsAt = now;
            // el reproductor de la ficha tarda en cargar: se espera y se reintenta cada 3 s hasta que la ficha se tape
            if (now - detailsAt > 3000 && now - tappedAt > 3000 && taps < 3) {
                tappedAt = now;
                taps++;
                click(fs);
            }
            return false;
        }
        // ya no se ve la ficha tras tocar "pantalla completa": está reproduciendo a pantalla completa
        if (tappedAt > 0) return true;
        // historial o resultados de búsqueda: el título (en series puede ir con la temporada, "Betty T2")
        if (inHistory || typed) {
            AccessibilityNodeInfo hit = find(root, n -> n.isVisibleToUser() && !isEditText(n) && isTitle(n));
            // en el historial el nombre puede venir en otro idioma que el del catálogo ("Era de Ultrón" / "Age of
            // Ultron"): se toma la tarjeta que más palabras comparte
            if (hit == null && inHistory) hit = bestMatch(root);
            if (hit != null) {
                boolean ok = inHistory ? openCard(hit) : click(hit);
                if (ok) opened = true;
                return false;
            }
        }
        if (input != null) {
            if (!typed) {
                Bundle b = new Bundle();
                b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, query);
                input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b);
                AccessibilityNodeInfo go = find(root, n -> {
                    String id = String.valueOf(n.getViewIdResourceName()).toLowerCase();
                    String t = n.getText() == null ? "" : n.getText().toString().toLowerCase();
                    return n.getClassName() != null && n.getClassName().toString().endsWith("Button") && (id.contains("search") || t.startsWith("busca"));
                });
                if (go != null) click(go);
                typed = true;
            }
            return false;
        }
        if (inHistory && !opened) {
            // el botón puede abrir la cuenta: se pasa a la pestaña del historial
            AccessibilityNodeInfo tab = find(root, n -> n.isVisibleToUser() && matchesAny(n, HISTORY));
            // se pasa el foco a la pestaña; si en 3 s no cambió la pantalla (no aparece "Historia"), se toca otra vez
            boolean shown = find(root, n -> n.isVisibleToUser() && n.getText() != null && matchesAny(n, new String[]{"historia", "historial", "history"})) != null;
            if (tab != null && !shown && (!historyTab || now - historyAt > 3000) && tabTaps < 4 && focus(tab)) {
                historyTab = true;
                historyAt = now;
                tabTaps++;
                return false;
            }
            // no está en el historial: se vuelve y se busca. La lista llega por internet: se espera a que cargue y solo
            // se rinde antes si la app dice que está vacía ("No record de reproducir")
            boolean empty = find(root, n -> n.isVisibleToUser() && n.getText() != null
                    && n.getText().toString().toLowerCase().matches(".*(no record|sin registro|no hay|empty).*")) != null;
            if (now - historyAt > (empty ? 1500 : 12000)) {
                svc.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK);
                inHistory = false;
                noHistory = true;
            }
            return false;
        }
        // inicio de la app: primero el historial (sigue en el punto exacto), si no la lupa
        if (!noHistory && !opened) {
            AccessibilityNodeInfo rec = find(root, n -> {
                String id = String.valueOf(n.getViewIdResourceName()).toLowerCase();
                return id.contains("record") || id.contains("histor");
            });
            if (rec != null && click(rec)) {
                inHistory = true;
                historyAt = now;
                return false;
            }
        }
        // Xuper tiene dos lupas (la del canal en vivo, abajo, y la del catálogo, en la barra de arriba): la de más arriba
        AccessibilityNodeInfo lupa = findTop(root, n -> n.isVisibleToUser() && (String.valueOf(n.getViewIdResourceName()).toLowerCase().contains("search")
                || String.valueOf(n.getContentDescription()).toLowerCase().startsWith("busc")));
        if (lupa != null && !opened) click(lupa);
        return false;
    }

    /** El nombre buscado, o el mismo con algo corto detrás (la temporada: "Betty T2"). */
    private static final java.util.Set<String> STOP = new java.util.HashSet<>(java.util.Arrays.asList(
            "the", "and", "for", "con", "del", "las", "los", "una", "uno", "por", "que", "age", "era"));

    /** Palabras del nombre sin acentos ni mayúsculas, sin las muy cortas ni las de relleno. */
    private static java.util.Set<String> words(String s) {
        java.util.Set<String> w = new java.util.HashSet<>();
        String t = java.text.Normalizer.normalize(s.toLowerCase(java.util.Locale.ROOT), java.text.Normalizer.Form.NFD);
        for (String x : t.replaceAll("[^a-z0-9 ]", " ").split(" +")) if (x.length() >= 3 && !STOP.contains(x)) w.add(x);
        return w;
    }

    /** El texto visible del historial con más palabras en común con lo buscado (mínimo 60 % de las del más corto). */
    private AccessibilityNodeInfo bestMatch(AccessibilityNodeInfo root) {
        java.util.Set<String> q = words(query);
        if (q.isEmpty()) return null;
        List<AccessibilityNodeInfo> all = new ArrayList<>();
        all.add(root);
        AccessibilityNodeInfo best = null;
        double bestScore = 0.6;
        for (int i = 0; i < all.size() && i < 2000; i++) {
            AccessibilityNodeInfo n = all.get(i);
            if (n == null) continue;
            if (n.isVisibleToUser() && !isEditText(n) && n.getText() != null && n.getText().length() <= 80) {
                java.util.Set<String> w = words(n.getText().toString());
                if (!w.isEmpty()) {
                    int common = 0;
                    for (String x : w) if (q.contains(x)) common++;
                    double score = common / (double) Math.min(w.size(), q.size());
                    if (common > 0 && score >= bestScore) { bestScore = score; best = n; }
                }
            }
            for (int c = 0; c < n.getChildCount(); c++) all.add(n.getChild(c));
        }
        return best;
    }

    private boolean isTitle(AccessibilityNodeInfo n) {
        if (n.getText() == null) return false;
        String t = Catalog.norm(n.getText().toString()), q = Catalog.norm(query);
        return !q.isEmpty() && t.startsWith(q) && t.length() - q.length() <= 4;
    }

    private static boolean isEditText(AccessibilityNodeInfo n) {
        return n.getClassName() != null && n.getClassName().toString().endsWith("EditText");
    }

    private static boolean matchesAny(AccessibilityNodeInfo n, String[] words) {
        if (n.getText() == null) return false;
        String t = n.getText().toString().trim().toLowerCase();
        for (String w : words) if (t.equals(w)) return true;
        return false;
    }

    private static boolean hasText(AccessibilityNodeInfo root, String s) {
        String q = Catalog.norm(s);
        return find(root, n -> n.getText() != null && Catalog.norm(n.getText().toString()).equals(q)) != null;
    }

    /**
     * Toca el nodo o el primero de sus padres que acepte clic; si ninguno (vistas que solo responden al control),
     * un toque en pantalla sobre el nodo.
     */
    private boolean click(AccessibilityNodeInfo n) {
        for (AccessibilityNodeInfo x = n; x != null; x = x.getParent()) {
            if (x.isClickable() && x.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true;
        }
        android.graphics.Rect r = new android.graphics.Rect();
        n.getBoundsInScreen(r);
        if (r.isEmpty()) return false;
        android.graphics.Path p = new android.graphics.Path();
        p.moveTo(r.exactCenterX(), r.exactCenterY());
        return svc.dispatchGesture(new android.accessibilityservice.GestureDescription.Builder()
                .addStroke(new android.accessibilityservice.GestureDescription.StrokeDescription(p, 0, 60)).build(), null, null);
    }

    /**
     * Abre la tarjeta del historial: la carta entera (el primer padre que acepta clic, no solo el texto del título).
     * Alterna el clic de accesibilidad con un toque en pantalla sobre la carta, por si la app ignora uno de los dos.
     */
    private boolean openCard(AccessibilityNodeInfo title) {
        AccessibilityNodeInfo card = title;
        for (AccessibilityNodeInfo x = title.getParent(); x != null; x = x.getParent()) {
            if (x.isClickable()) { card = x; break; }
        }
        if (cardTries++ % 2 == 0 && card.isClickable() && card.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true;
        android.graphics.Rect r = new android.graphics.Rect();
        card.getBoundsInScreen(r);
        // el centro de la carta cae en el póster, no en el texto de abajo
        android.graphics.Path p = new android.graphics.Path();
        p.moveTo(r.exactCenterX(), r.top + r.height() * 0.4f);
        return svc.dispatchGesture(new android.accessibilityservice.GestureDescription.Builder()
                .addStroke(new android.accessibilityservice.GestureDescription.StrokeDescription(p, 0, 60)).build(), null, null);
    }

    /**
     * Pasa el foco al elemento (o al primero de sus padres que lo acepte): los menús de Xuper cambian de página al
     * recibir el foco, como con la flecha del control. Si ninguno lo acepta, un toque en pantalla.
     */
    private boolean focus(AccessibilityNodeInfo n) {
        for (AccessibilityNodeInfo x = n; x != null; x = x.getParent()) {
            if (x.isFocusable() && x.performAction(AccessibilityNodeInfo.ACTION_FOCUS)) return true;
        }
        return tap(n);
    }

    /** Un toque de verdad en pantalla sobre el nodo: hay menús (Mi lista en Xuper) que ignoran el clic de accesibilidad. */
    private boolean tap(AccessibilityNodeInfo n) {
        android.graphics.Rect r = new android.graphics.Rect();
        n.getBoundsInScreen(r);
        if (r.isEmpty()) return false;
        android.graphics.Path p = new android.graphics.Path();
        p.moveTo(r.exactCenterX(), r.exactCenterY());
        return svc.dispatchGesture(new android.accessibilityservice.GestureDescription.Builder()
                .addStroke(new android.accessibilityservice.GestureDescription.StrokeDescription(p, 0, 60)).build(), null, null);
    }

    interface Test { boolean ok(AccessibilityNodeInfo n); }

    /** El nodo que cumple la prueba y está más arriba en la pantalla. */
    private static AccessibilityNodeInfo findTop(AccessibilityNodeInfo root, Test t) {
        List<AccessibilityNodeInfo> q = new ArrayList<>();
        q.add(root);
        AccessibilityNodeInfo best = null;
        int bestTop = Integer.MAX_VALUE;
        android.graphics.Rect r = new android.graphics.Rect();
        for (int i = 0; i < q.size() && i < 2000; i++) {
            AccessibilityNodeInfo n = q.get(i);
            if (n == null) continue;
            if (t.ok(n)) {
                n.getBoundsInScreen(r);
                if (r.top < bestTop) { bestTop = r.top; best = n; }
            }
            for (int c = 0; c < n.getChildCount(); c++) q.add(n.getChild(c));
        }
        return best;
    }

    private static AccessibilityNodeInfo find(AccessibilityNodeInfo root, Test t) {
        List<AccessibilityNodeInfo> q = new ArrayList<>();
        q.add(root);
        for (int i = 0; i < q.size() && i < 2000; i++) {
            AccessibilityNodeInfo n = q.get(i);
            if (n == null) continue;
            if (t.ok(n)) return n;
            for (int c = 0; c < n.getChildCount(); c++) q.add(n.getChild(c));
        }
        return null;
    }
}
