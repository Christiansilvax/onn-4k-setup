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
import java.util.Date;
import java.util.Locale;
import java.util.function.Supplier;

/** Ajustes del launcher: botones, apariencia, apps del inicio, reproducción y respaldo. Se abre con ⚙ en el inicio. */
public class SettingsActivity extends Activity {
    private LauncherSettings st;
    private Theme theme;
    private LinearLayout col;
    private final java.util.List<Runnable> refreshers = new java.util.ArrayList<>();

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        st = new LauncherSettings(this);
        int phase = Weather.phase(null);
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
        FrameLayout frame = new FrameLayout(this);
        frame.setBackground(Theme.backdrop(this, phase));
        frame.addView(sv, new FrameLayout.LayoutParams(-1, -1));
        setContentView(frame);
    }

    @Override protected void onResume() {
        super.onResume();
        for (Runnable r : refreshers) r.run(); // p. ej. permisos que se activaron fuera
    }

    // ---------- secciones ----------

    private void buildButtons() {
        section("Botones del control");
        row(null, "Botones del control", "Elige qué app abre cada botón del control.",
                () -> new Prefs(this).mappedKeys().size() + " configurados  ›",
                () -> startActivity(new Intent(this, MainActivity.class)));
    }

    /** Orden de las filas del inicio: OK sube la fila un puesto. */
    private void buildOrder() {
        section("Orden del inicio");
        final java.util.List<String> ids = new java.util.ArrayList<>(java.util.Arrays.asList("apps", "seguir", "youtube", "porque"));
        for (Object[] r : Catalog.cachedRows(this)) {
            String id = Catalog.sectionOf(r);
            if (!ids.contains(id)) ids.add(id);
        }
        java.util.List<String> order = st.sectionOrder(ids);
        for (int i = 0; i < order.size(); i++) {
            final String id = order.get(i);
            row(null, (i + 1) + ".  " + sectionName(id), i == 0 ? "OK la pasa al final." : "OK la sube un puesto.", () -> "↑",
                    () -> {
                        st.moveUp(id, ids);
                        recreate();
                    });
        }
    }

    private String sectionName(String id) {
        switch (id) {
            case "apps": return "Tus apps";
            case "seguir": return "Seguir mirando";
            case "youtube": return "Recomendaciones de YouTube";
            case "porque": return "Porque viste…";
            default: return "Popular en " + Catalog.label(this, id.substring(4));
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
        toggle("Fondo con la miniatura", "Al enfocar un video, su imagen desenfocada de fondo.", "fondo", st::showBackdrop);
    }

    private void buildApps() {
        section("Apps del inicio");
        row(null, "Restablecer el orden", "Vuelve al orden inicial; las apps que moviste regresan a su sitio.",
                () -> "›",
                () -> {
                    new Prefs(this).resetOrder();
                    toast("Orden restablecido");
                });
        for (final Prefs.AppEntry a : new Prefs(this).listApps(true)) {
            row(a.icon, a.label, null,
                    () -> st.isHidden(a.pkg) ? "Oculta" : "Visible",
                    () -> st.setHidden(a.pkg, !st.isHidden(a.pkg)));
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
        toggle("Recomendaciones de YouTube", "Sugerencias de lo que ves en TizenTube o YouTube.", "recomendaciones", st::showReco);
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
        toggle("Porque viste…", "Títulos parecidos a lo último que viste que estén en tus apps.", "porqueViste", st::showBecause);
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
        java.util.Map<String, Integer> services = Catalog.cachedServices(this, null);
        if (Catalog.enabled() && services.isEmpty()) {
            row(null, "Popular en tus apps", "Tus apps de streaming aparecen aquí después de abrir el inicio con internet.", () -> "", null);
        }
        for (final String pkg : services.keySet()) {
            Drawable icon = null;
            try { icon = getPackageManager().getApplicationIcon(pkg); } catch (Exception ignored) { }
            row(icon, "Popular en " + Catalog.label(this, pkg), null,
                    () -> st.isServiceOff(pkg) ? "No" : "Sí",
                    () -> st.setServiceOff(pkg, !st.isServiceOff(pkg)));
        }
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
    private void row(Drawable icon, String label, String desc, final Supplier<String> value, final Runnable action) {
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
