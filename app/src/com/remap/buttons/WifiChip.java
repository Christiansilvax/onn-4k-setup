package com.remap.buttons;

import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Ficha de red junto a la hora: barras de señal y nombre de la red; OK abre el panel de Wi-Fi de la caja.
 * El nombre necesita ACCESS_FINE_LOCATION (instalar.ps1 lo da con pm grant); sin él dice solo "Wi-Fi".
 */
final class WifiChip extends LinearLayout {
    private final Theme theme;
    private final Bars bars;
    private final TextView name;
    private final ConnectivityManager cm;
    private final WifiManager wm;
    private final Handler main = new Handler(Looper.getMainLooper());
    private ConnectivityManager.NetworkCallback callback;

    WifiChip(Context c, Theme theme) {
        super(c);
        this.theme = theme;
        cm = c.getSystemService(ConnectivityManager.class);
        wm = c.getApplicationContext().getSystemService(WifiManager.class);
        setGravity(Gravity.CENTER_VERTICAL);
        setFocusable(true);
        setPadding(dp(14), 0, dp(16), 0);
        setBackground(Glass.chip(c, theme, 22, false));
        bars = new Bars(c);
        addView(bars, new LayoutParams(dp(22), dp(18)));
        name = new TextView(c);
        name.setTextColor(theme.text);
        theme.style(name);
        name.setTextSize(14);
        name.setMaxLines(1);
        name.setMaxWidth(dp(160));
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        name.setPadding(dp(8), 0, 0, 0);
        addView(name);
        setOnClickListener(v -> {
            Intent i = new Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try { getContext().startActivity(i); } catch (Exception ignored) { }
        });
        refresh();
    }

    /** Sigue los cambios de red mientras el inicio está en pantalla. */
    void start() {
        refresh();
        if (callback != null) return;
        callback = new ConnectivityManager.NetworkCallback() {
            @Override public void onCapabilitiesChanged(Network n, NetworkCapabilities nc) { main.post(WifiChip.this::refresh); }
            @Override public void onLost(Network n) { main.post(WifiChip.this::refresh); }
        };
        try { cm.registerDefaultNetworkCallback(callback); } catch (Exception e) { callback = null; }
    }

    void stop() {
        if (callback == null) return;
        try { cm.unregisterNetworkCallback(callback); } catch (Exception ignored) { }
        callback = null;
    }

    private void refresh() {
        NetworkCapabilities nc = cm.getNetworkCapabilities(cm.getActiveNetwork());
        if (nc != null && nc.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
            bars.set(4, true);
            name.setText("Cable");
        } else if (nc != null && nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            WifiInfo wi = wm.getConnectionInfo();
            bars.set(WifiManager.calculateSignalLevel(wi.getRssi(), 5), false);
            String ssid = wi.getSSID();
            if (ssid == null || ssid.contains("unknown ssid")) ssid = "Wi-Fi";
            name.setText(ssid.replaceAll("^\"|\"$", ""));
        } else {
            bars.set(-1, false);
            name.setText(wm.isWifiEnabled() ? "Sin red" : "Wi-Fi apagado");
        }
        setAlpha(nc != null && nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) ? 1f : 0.7f);
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

    /** Abanico de Wi-Fi: arcos encendidos según el nivel (0-4); -1 = sin red (todo tenue con una raya). */
    private final class Bars extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private int level = -1;
        private boolean wired;

        Bars(Context c) {
            super(c);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
        }

        void set(int level, boolean wired) {
            this.level = level;
            this.wired = wired;
            invalidate();
        }

        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), cx = w / 2f, sw = h / 8f;
            p.setStrokeWidth(sw);
            if (wired) {
                // enchufe sencillo: caja con dos patas
                p.setColor(theme.text);
                c.drawRoundRect(w * 0.25f, h * 0.3f, w * 0.75f, h * 0.7f, sw, sw, p);
                c.drawLine(w * 0.38f, h * 0.1f, w * 0.38f, h * 0.3f, p);
                c.drawLine(w * 0.62f, h * 0.1f, w * 0.62f, h * 0.3f, p);
                c.drawLine(cx, h * 0.7f, cx, h * 0.95f, p);
                return;
            }
            // tres arcos y un punto, de abajo hacia arriba
            for (int i = 0; i < 3; i++) {
                float rad = h * (0.3f + 0.27f * i);
                r.set(cx - rad, h - sw - rad, cx + rad, h - sw + rad);
                p.setColor(level >= i + 2 ? theme.text : Theme.alpha(theme.text, 70));
                c.drawArc(r, 225, 90, false, p);
            }
            p.setStyle(Paint.Style.FILL);
            p.setColor(level >= 0 ? theme.text : Theme.alpha(theme.text, 70));
            c.drawCircle(cx, h - sw * 1.5f, sw * 1.1f, p);
            p.setStyle(Paint.Style.STROKE);
            if (level < 0) {
                p.setColor(theme.text);
                c.drawLine(w * 0.15f, h * 0.1f, w * 0.85f, h * 0.9f, p);
            }
        }
    }
}
