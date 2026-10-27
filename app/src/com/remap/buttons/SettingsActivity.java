package com.remap.buttons;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/** Ajustes del launcher: botones, apariencia, apps del inicio, reproducción y respaldo. Se abre con ⚙ en el inicio. */
public class SettingsActivity extends Activity {
    private LauncherSettings st;
    private Theme theme;
    private LinearLayout col;
    private FrameLayout frame;
    private int phase;
    private final java.util.List<Runnable> refreshers = new java.util.ArrayList<>();
    private Orderable moving; // lista en modo mover (flechas cambian el puesto), o null
    private int movingAt;
    private boolean okDown;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        st = new LauncherSettings(this);
        phase = Weather.phase(null);
        theme = Theme.current(this, phase);

        col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(64), dp(36), dp(64), dp(36));
        col.setClipChildren(false);

        // encabezado
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        ImageView gear = new ImageView(this);
        gear.setImageResource(R.drawable.ic_settings);
        gear.setColorFilter(theme.text);
        head.addView(gear, new LinearLayout.LayoutParams(dp(44), dp(44)));
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setPadding(dp(18), 0, 0, 0);
        titles.addView(text("Ajustes del launcher", 30, theme.text, true));
        titles.addView(text("OK cambia cada opción. Los cambios se guardan solos y también en el respaldo.", 15, theme.textSoft, false));
        head.addView(titles);
        col.addView(head);

        buildButtons();
        buildOrder();
        buildLook();
        buildApps();
        buildPlayback();
        buildCatalog();
        buildBackup();

        ScrollView sv = new ScrollView(this);
        sv.setClipChildren(false);
        sv.addView(col);
        frame = new FrameLayout(this);
        frame.setBackground(Theme.backdrop(this, phase));
        frame.addView(sv, new FrameLayout.LayoutParams(-1, -1));
        setContentView(frame);
    }

    @Override protected void onResume() {
        super.onResume();
        for (Runnable r : refreshers) r.run(); // p. ej. permisos que se activaron fuera
        if (frame.getBackground() instanceof Animated) ((Animated) frame.getBackground()).start(); // vista previa del fondo
    }

    @Override protected void onPause() {
        super.onPause();
        if (moving != null) moving.finish();
        if (frame.getBackground() instanceof Animated) ((Animated) frame.getBackground()).stop();
    }

    /** En modo mover: ↑↓ cambian la fila de puesto; OK o Atrás terminan y guardan. */
    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        if (moving == null) return super.dispatchKeyEvent(e);
        switch (e.getKeyCode()) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (e.getAction() == KeyEvent.ACTION_DOWN) moving.moveBy(e.getKeyCode() == KeyEvent.KEYCODE_DPAD_UP ? -1 : 1);
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_BACK:
                // el OK que eligió "Mover" en el menú suelta aquí: solo cuenta uno pulsado después
                if (e.getAction() == KeyEvent.ACTION_DOWN) okDown = true;
                else if (okDown) moving.finish();
                return true;
            default:
                return super.dispatchKeyEvent(e);
        }
    }

    // ---------- secciones ----------

    private void buildButtons() {
        section("Botones del control");
        row(null, "Botones del control", "Elige qué app abre cada botón del control.",
                () -> new Prefs(this).mappedKeys().size() + " configurados  ›",
                () -> startActivity(new Intent(this, MainActivity.class)));
    }

    /** Filas del inicio: las que se ven, en orden (OK: mover o quitar), y debajo las que se pueden agregar. */
    private void buildOrder() {
        section("Filas del inicio");
        final List<String> ids = new ArrayList<>(Arrays.asList("apps", "seguir", "youtube", "porque"));
        for (Object[] r : Catalog.cachedRows(this)) addOnce(ids, Catalog.sectionOf(r));
        // también las apps de contenido sin fila todavía (o quitadas), para poder agregarlas
        for (String pkg : Catalog.cachedServices(this, null).keySet()) addOnce(ids, "pop:" + pkg);
        for (String pkg : Catalog.screenApps(this)) addOnce(ids, "pop:" + pkg);
        for (String[] it : Catalog.seen(this)) addOnce(ids, "seguir:" + it[3]); // Seguir mirando en Xuper, Netflix…
        final List<String> shown = new ArrayList<>(), hidden = new ArrayList<>();
        for (String id : st.sectionOrder(ids)) (st.isRowHidden(id) ? hidden : shown).add(id);
        final Runnable save = () -> {
            List<String> all = new ArrayList<>(shown);
            all.addAll(hidden);
            st.setSectionOrder(all);
        };
        final Orderable list = new Orderable(shown, save);
        for (final String id : shown) {
            list.add(row(rowIcon(id), sectionName(id), null, () -> "›",
                    () -> menu(sectionName(id), new String[]{"Mover", "Quitar del inicio"}, w -> {
                        if (w == 0) list.startMove(id);
                        else {
                            st.setRowHidden(id, true);
                            recreate();
                        }
                    })));
        }
        if (Catalog.enabled() && Catalog.cachedServices(this, null).isEmpty()) {
            row(null, "Popular en tus apps", "Tus apps de streaming aparecen aquí después de abrir el inicio con internet.", () -> "", null);
        }
        if (hidden.isEmpty()) return;
        subsection("Agregar filas");
        for (final String id : hidden) {
            row(rowIcon(id), sectionName(id), null, () -> "+  Agregar", () -> {
                st.setRowHidden(id, false);
                hidden.remove(id);
                shown.add(id); // entra al final
                save.run();
                recreate();
            });
        }
    }

    private static void addOnce(List<String> l, String id) { if (!l.contains(id)) l.add(id); }

    private Drawable rowIcon(String id) {
        String pkg = id.startsWith("pop:") ? id.substring(4) : id.startsWith("seguir:") ? id.substring(7) : null;
        if (pkg == null) return null;
        try { return getPackageManager().getApplicationIcon(pkg); } catch (Exception e) { return null; }
    }

    private String sectionName(String id) {
        switch (id) {
            case "apps": return "Tus apps";
            case "seguir": return "Seguir mirando en YouTube";
            case "youtube": return "Recomendaciones de YouTube";
            case "porque": return "Porque viste…";
            default: return id.startsWith("seguir:") ? "Seguir mirando en " + Catalog.label(this, id.substring(7))
                    : "Popular en " + Catalog.label(this, id.substring(4));
        }
    }

    private void buildLook() {
        section("Apariencia");
        toggle("Abrir siempre este inicio", "Al encender la caja o si aparece el inicio de Google TV, se abre este. Con \"No\" puedes usar el de Google.",
                "inicioPropio", st::replaceGoogleHome);
        row(null, "Tema", "Automático: claro de día y oscuro de noche, según la hora del sol.",
                () -> {
                    String t = st.theme();
                    return LauncherSettings.THEME_LIGHT.equals(t) ? "Siempre claro"
                            : LauncherSettings.THEME_DARK.equals(t) ? "Siempre oscuro" : "Automático";
                },
                () -> {
                    String t = st.theme();
                    st.setTheme(LauncherSettings.THEME_AUTO.equals(t) ? LauncherSettings.THEME_LIGHT
                            : LauncherSettings.THEME_LIGHT.equals(t) ? LauncherSettings.THEME_DARK : LauncherSettings.THEME_AUTO);
                    recreate(); // esta pantalla también cambia de colores
                });
        toggle("Banner grande", "Videos recomendados y tus apps de streaming arriba del inicio.", "banner", st::showHero);
        toggle("Clima", "Temperatura y lugar junto a la hora.", "clima", st::showWeather);
        toggle("Red", "Señal y nombre del Wi-Fi junto a la hora; OK abre los ajustes de red.", "wifi", st::showWifi);
        row(null, "Fondo animado", "Se mueve solo mientras ves el inicio; al abrir una app se detiene.",
                () -> backgroundName(st.background()),
                () -> {
                    List<String> all = Arrays.asList(LauncherSettings.BACKGROUNDS);
                    st.setBackground(LauncherSettings.BACKGROUNDS[(all.indexOf(st.background()) + 1) % all.size()]);
                    // vista previa aquí mismo, sin perder el cursor
                    if (frame.getBackground() instanceof Animated) ((Animated) frame.getBackground()).stop();
                    frame.setBackground(Theme.backdrop(this, phase));
                    if (frame.getBackground() instanceof Animated) ((Animated) frame.getBackground()).start();
                });
        toggle("Fondo con la miniatura", "Al enfocar un video, su imagen desenfocada de fondo.", "fondo", st::showBackdrop);
    }

    private static String backgroundName(String b) {
        switch (b) {
            case "aurora:cielo": return "Aurora · Cielo";
            case "aurora:atardecer": return "Aurora · Atardecer";
            case "aurora:oceano": return "Aurora · Océano";
            case "aurora:bosque": return "Aurora · Bosque";
            case "aurora:neon": return "Aurora · Neón";
            case "estrellas": return "Estrellas";
            default: return "Sin animación";
        }
    }

    /** Apps del inicio: las visibles en orden (OK: mover u ocultar) y debajo las ocultas para volver a mostrarlas. */
    private void buildApps() {
        section("Apps del inicio");
        final Prefs prefs = new Prefs(this);
        final List<Prefs.AppEntry> all = prefs.listApps(true);
        final List<String> shown = new ArrayList<>();
        final List<Prefs.AppEntry> hidden = new ArrayList<>();
        for (Prefs.AppEntry a : all) {
            if (st.isHidden(a.pkg)) hidden.add(a);
            else shown.add(a.pkg);
        }
        final Orderable list = new Orderable(shown, () -> {
            // las visibles en su orden nuevo; las ocultas quedan detrás
            List<Prefs.AppEntry> out = new ArrayList<>();
            for (String p : shown) for (Prefs.AppEntry a : all) if (a.pkg.equals(p)) out.add(a);
            out.addAll(hidden);
            prefs.saveOrder(out);
        });
        for (final Prefs.AppEntry a : all) {
            if (st.isHidden(a.pkg)) continue;
            list.add(row(a.icon, a.label, null, () -> "›",
                    () -> menu(a.label, new String[]{"Mover", "Ocultar del inicio"}, w -> {
                        if (w == 0) list.startMove(a.pkg);
                        else {
                            st.setHidden(a.pkg, true);
                            recreate();
                        }
                    })));
        }
        row(null, "Restablecer el orden", "Vuelve al orden inicial; las apps que moviste regresan a su sitio.",
                () -> "›",
                () -> {
                    prefs.resetOrder();
                    toast("Orden restablecido");
                    recreate();
                });
        if (hidden.isEmpty()) return;
        subsection("Agregar apps");
        for (final Prefs.AppEntry a : hidden) {
            row(a.icon, a.label, null, () -> "+  Mostrar", () -> {
                st.setHidden(a.pkg, false);
                recreate();
            });
        }
    }

    private void buildPlayback() {
        section("Reproducción y recomendaciones");
        toggle("Barra de reproducción", "La barra con ▶ ⏭ ⛶ ✕ debajo del banner.", "barra", st::showBar);
        row(null, "Cierre automático de la barra", "Si no se reproduce nada, la barra se oculta sola.",
                () -> {
                    int s = st.barCloseSeconds();
                    return s == 0 ? "Nunca" : s >= 60 ? (s / 60) + " min" : s + " s";
                },
                () -> {
                    int s = st.barCloseSeconds();
                    st.setBarCloseSeconds(s == 60 ? 300 : s == 300 ? 0 : 60);
                });
        row(null, "Borrar historial y recomendaciones", "Vacía \"Seguir mirando\", los canales aprendidos y las recomendaciones.",
                () -> "›",
                () -> confirm("¿Borrar el historial?", "Se borran \"Seguir mirando\", los canales aprendidos, las recomendaciones y los minutos guardados.", () -> {
                    Videos.clear(this);
                    toast("Historial borrado");
                }));
    }

    private void buildCatalog() {
        section("Contenido de tus apps");
        if (!Catalog.enabled()) {
            row(null, "Catálogo sin configurar", "Esta versión se compiló sin la clave de TMDB (tmdb.key): solo se aprende lo que ves.", () -> "", null);
        }
        toggle("Aprender lo que veo", "Anota lo que pones en Netflix, Prime, Claro, Paramount+… para \"Seguir mirando\" y \"Porque viste\".",
                "aprender", st::learnApps);
        row(null, "País del catálogo", "Lo que hay en cada app cambia según el país.",
                () -> {
                    String c = st.country();
                    if (!c.isEmpty()) return new Locale("", c).getDisplayCountry(new Locale("es"));
                    String auto = Catalog.autoCountry(this);
                    return "Automático" + (auto.isEmpty() ? "" : " (" + new Locale("", auto).getDisplayCountry(new Locale("es")) + ")");
                },
                () -> {
                    java.util.List<String> all = java.util.Arrays.asList(Catalog.COUNTRIES);
                    st.setCountry(Catalog.COUNTRIES[(all.indexOf(st.country()) + 1) % Catalog.COUNTRIES.length]);
                });
        row(null, "Borrar lo aprendido", "Vacía lo que viste en tus apps y las filas del catálogo.",
                () -> "›",
                () -> confirm("¿Borrar lo aprendido?", "Se borra lo que viste en Netflix, Prime y las demás apps.", () -> {
                    Catalog.clear(this);
                    toast("Borrado");
                }));
        TextView credit = text("Catálogo y \"dónde verlo\": TMDB y JustWatch. Este producto usa la API de TMDB pero no está avalado ni certificado por TMDB.",
                13, theme.textSoft, false);
        credit.setSingleLine(false);
        credit.setPadding(dp(6), dp(4), 0, 0);
        col.addView(credit);
    }

    private void buildBackup() {
        section("Respaldo y datos");
        row(null, "Archivo de respaldo", "Botones, orden, historial y ajustes. No se borra al desinstalar.",
                () -> {
                    if (!Prefs.canBackup()) return "Sin permiso de archivos";
                    File f = Prefs.backupFile();
                    return f.exists() ? "Guardado " + new SimpleDateFormat("d MMM, h:mm a", Locale.getDefault()).format(new Date(f.lastModified())) : "Aún no existe";
                },
                null);
        row(null, "Guardar el respaldo ahora", Prefs.backupFile().getPath().replace("/storage/emulated/0/", ""),
                () -> "›",
                () -> {
                    new Prefs(this).backup();
                    toast(Prefs.canBackup() ? "Respaldo guardado" : "Falta el permiso de archivos (instalar.ps1 lo activa)");
                });
        row(null, "Restaurar desde el respaldo", "Reemplaza botones, orden, historial y ajustes por los del archivo.",
                () -> "›",
                () -> confirm("¿Restaurar el respaldo?", "Lo que hay ahora se reemplaza por lo guardado en el archivo.", () -> {
                    if (new Prefs(this).restoreNow()) {
                        toast("Respaldo restaurado");
                        recreate();
                    } else toast("No hay respaldo o falta el permiso de archivos");
                }));

        section("Permisos");
        permission("Archivos", "Para el respaldo que sobrevive a desinstalar. Lo activa instalar.ps1.",
                Prefs::canBackup, null);
        permission("Ubicación", "Para el clima del lugar donde está la caja. Lo activa instalar.ps1.",
                () -> checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED, null);
        permission("Nombre del Wi-Fi", "Android solo deja leer el nombre de la red con ubicación precisa. Lo activa instalar.ps1.",
                () -> checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED, null);
        permission("Notificaciones", "Para saber qué ves en YouTube, Netflix, Prime… (Seguir mirando, Porque viste, barra).",
                () -> {
                    String l = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
                    return l != null && l.contains(getPackageName() + "/");
                }, "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS");
        permission("Accesibilidad", "Para los botones del control y el botón Home.",
                () -> {
                    String l = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
                    return l != null && l.contains(getPackageName() + "/");
                }, Settings.ACTION_ACCESSIBILITY_SETTINGS);
    }

    // ---------- filas ----------

    private void section(String s) {
        TextView t = text(s.toUpperCase(), 14, theme.accent, true);
        t.setLetterSpacing(0.1f);
        t.setPadding(dp(6), dp(30), 0, dp(10));
        col.addView(t);
    }

    private void toggle(String label, String desc, final String key, final Supplier<Boolean> get) {
        row(null, label, desc, () -> get.get() ? "Sí" : "No", () -> st.setBool(key, !get.get()));
    }

    private void permission(String label, String desc, final Supplier<Boolean> ok, final String settingsAction) {
        row(null, label, desc, () -> ok.get() ? "✔  Activo" : "✖  Falta",
                settingsAction == null ? null : () -> {
                    try { startActivity(new Intent(settingsAction)); }
                    catch (Exception e) { toast("Esta caja no tiene esa pantalla de ajustes"); }
                });
    }

    /** Fila de cristal: ícono opcional, nombre, descripción y valor a la derecha. OK ejecuta la acción y refresca el valor. */
    private View row(Drawable icon, String label, String desc, final Supplier<String> value, final Runnable action) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(20), dp(14), dp(24), dp(14));
        r.setBackground(focusBg());
        r.setFocusable(true);
        r.setClickable(true);
        if (icon != null) {
            ImageView iv = new ImageView(this);
            iv.setImageDrawable(icon);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setOutlineProvider(new ViewOutlineProvider() {
                @Override public void getOutline(View v, Outline o) { o.setOval(0, 0, v.getWidth(), v.getHeight()); }
            });
            iv.setClipToOutline(true);
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(40), dp(40));
            ilp.rightMargin = dp(16);
            r.addView(iv, ilp);
        }
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(text(label, 19, theme.text, true));
        if (desc != null) texts.addView(text(desc, 14, theme.textSoft, false));
        r.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final TextView val = text(value.get(), 17, theme.accent, true);
        val.setPadding(dp(20), 0, 0, 0);
        r.addView(val);
        refreshers.add(() -> val.setText(value.get()));
        r.setOnClickListener(v -> {
            if (action != null) action.run();
            val.setText(value.get());
        });
        r.setOnFocusChangeListener((v, has) -> v.animate().scaleX(has ? 1.015f : 1f).scaleY(has ? 1.015f : 1f).setDuration(120).start());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        col.addView(r, lp);
        return r;
    }

    private void subsection(String s) {
        TextView t = text(s, 16, theme.textSoft, true);
        t.setPadding(dp(6), dp(16), 0, dp(8));
        col.addView(t);
    }

    private void menu(String title, String[] items, java.util.function.IntConsumer pick) {
        new AlertDialog.Builder(this, theme.light ? android.R.style.Theme_DeviceDefault_Light_Dialog_Alert
                : android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(title)
                .setItems(items, (d, w) -> pick.accept(w))
                .show();
    }

    /**
     * Filas seguidas de Ajustes que se pueden reordenar (filas del inicio, apps). Al mover se cambia de lado la fila
     * vecina, no la enfocada, así el cursor no se pierde; al terminar se guarda el orden.
     */
    private final class Orderable {
        final List<String> ids;
        final Runnable save;
        final List<View> views = new ArrayList<>();

        Orderable(List<String> ids, Runnable save) {
            this.ids = ids;
            this.save = save;
        }

        void add(View v) { views.add(v); }

        void startMove(String id) {
            moving = this;
            movingAt = ids.indexOf(id);
            okDown = false;
            value(movingAt, "↕  Moviendo");
            views.get(movingAt).requestFocus();
            toast("Muévela con ↑ ↓. OK o Atrás para terminar.");
        }

        void moveBy(int step) {
            int i = movingAt, j = i + step;
            if (j < 0 || j >= ids.size()) return;
            final View a = views.get(i), b = views.get(j);
            col.removeView(b);
            col.addView(b, col.indexOfChild(a) + (step < 0 ? 1 : 0));
            Collections.swap(ids, i, j);
            Collections.swap(views, i, j);
            movingAt = j;
            a.post(() -> a.requestRectangleOnScreen(new android.graphics.Rect(0, -dp(60), a.getWidth(), a.getHeight() + dp(60))));
        }

        void finish() {
            value(movingAt, "›");
            moving = null;
            save.run();
        }

        private void value(int i, String s) {
            ViewGroup r = (ViewGroup) views.get(i);
            ((TextView) r.getChildAt(r.getChildCount() - 1)).setText(s);
        }
    }

    // ---------- ayudas ----------

    private void confirm(String title, String msg, final Runnable yes) {
        new AlertDialog.Builder(this, theme.light ? android.R.style.Theme_DeviceDefault_Light_Dialog_Alert
                : android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(title)
                .setMessage(msg)
                .setPositiveButton("Sí", (d, w) -> yes.run())
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(Typeface.create(bold ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        t.setEllipsize(TextUtils.TruncateAt.END);
        return t;
    }

    private Drawable focusBg() { return Glass.panel(this, theme, 16); }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
