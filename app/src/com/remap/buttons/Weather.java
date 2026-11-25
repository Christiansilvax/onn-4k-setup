package com.remap.buttons;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import java.util.Calendar;
import java.util.Locale;
import org.json.JSONObject;

/**
 * Clima actual (Open-Meteo). La ubicación es la que Google calcula en la caja por Wi-Fi (permiso de ubicación
 * aproximada, lo da instalar.ps1) y el nombre del lugar sale de BigDataCloud; si no hay, se usa la IP (ipwho.is).
 * Ninguno pide clave ni cuenta.
 */
final class Weather {
    private static final long DAY = 24 * 3600 * 1000L;

    /** El último clima cargado: así el momento del día (y el tema) no cambia al reconstruir la pantalla. */
    static volatile Weather last;

    final int temp, code;
    final boolean day;
    final String city;
    final long sunrise, sunset; // epoch ms

    private Weather(int temp, int code, boolean day, String city, long sunrise, long sunset) {
        this.temp = temp;
        this.code = code;
        this.day = day;
        this.city = city;
        this.sunrise = sunrise;
        this.sunset = sunset;
    }

    /** Hace red: llamar fuera del hilo principal. Devuelve null si no hay ubicación. */
    static Weather load(Context c) throws Exception {
        SharedPreferences sp = c.getSharedPreferences("weather", Context.MODE_PRIVATE);
        long now = System.currentTimeMillis();
        Location l = deviceLocation(c);
        if (l != null) {
            // el nombre del lugar solo se busca si la ubicación cambió (~1 km)
            String key = String.format(Locale.US, "%.2f,%.2f", l.getLatitude(), l.getLongitude());
            SharedPreferences.Editor ed = sp.edit().putFloat("lat", (float) l.getLatitude()).putFloat("lon", (float) l.getLongitude())
                    .putLong("locAt", now);
            if (!key.equals(sp.getString("geoKey", ""))) {
                try {
                    JSONObject g = new JSONObject(Videos.get(String.format(Locale.US,
                            "https://api.bigdatacloud.net/data/reverse-geocode-client?latitude=%.4f&longitude=%.4f&localityLanguage=es",
                            l.getLatitude(), l.getLongitude())));
                    String city = g.optString("city");
                    ed.putString("city", city.isEmpty() ? g.optString("locality") : city).putString("geoKey", key);
                } catch (Exception ignored) { }
            }
            ed.apply();
        } else if (!sp.contains("lat") || now - sp.getLong("locAt", 0) > DAY) {
            JSONObject ip = new JSONObject(Videos.get("https://ipwho.is/?fields=success,city,latitude,longitude"));
            if (ip.optBoolean("success")) {
                sp.edit().putString("city", ip.optString("city", ""))
                        .putFloat("lat", (float) ip.getDouble("latitude"))
                        .putFloat("lon", (float) ip.getDouble("longitude"))
                        .putLong("locAt", now).apply();
            }
        }
        if (!sp.contains("lat")) return null;
        String url = String.format(Locale.US, "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f"
                + "&current=temperature_2m,weather_code,is_day&daily=sunrise,sunset&forecast_days=1&timezone=auto&timeformat=unixtime",
                sp.getFloat("lat", 0), sp.getFloat("lon", 0));
        JSONObject j = new JSONObject(Videos.get(url));
        JSONObject cur = j.getJSONObject("current");
        JSONObject d = j.getJSONObject("daily");
        last = new Weather((int) Math.round(cur.getDouble("temperature_2m")), cur.getInt("weather_code"),
                cur.getInt("is_day") == 1, sp.getString("city", ""),
                d.getJSONArray("sunrise").getLong(0) * 1000, d.getJSONArray("sunset").getLong(0) * 1000);
        return last;
    }

    /** La ubicación más reciente que tenga el sistema (fused o red), o null sin permiso o sin datos. */
    private static Location deviceLocation(Context c) {
        if (c.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) return null;
        LocationManager lm = (LocationManager) c.getSystemService(Context.LOCATION_SERVICE);
        Location best = null;
        for (String p : new String[]{"fused", LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER}) {
            try {
                Location l = lm.getLastKnownLocation(p);
                if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
            } catch (Exception ignored) { }
        }
        return best;
    }

    /** Icono según el código WMO. */
    /** Ícono a color (vector): los emoji de niebla o nubes son casi blancos y no se ven en el tema claro. */
    int icon() {
        if (code == 0) return day ? R.drawable.wx_sun : R.drawable.wx_moon;
        if (code <= 2) return day ? R.drawable.wx_partly_day : R.drawable.wx_partly_night;
        if (code == 3) return R.drawable.wx_cloud;
        if (code == 45 || code == 48) return R.drawable.wx_fog;
        if (code >= 95) return R.drawable.wx_storm;
        if (code >= 71 && code <= 77 || code == 85 || code == 86) return R.drawable.wx_snow;
        if (code >= 80) return day ? R.drawable.wx_showers : R.drawable.wx_rain;
        return R.drawable.wx_rain;
    }

    String description() {
        if (code == 0) return "Despejado";
        if (code <= 2) return "Parcialmente nublado";
        if (code == 3) return "Nublado";
        if (code == 45 || code == 48) return "Niebla";
        if (code >= 95) return "Tormenta";
        if (code >= 71 && code <= 77 || code == 85 || code == 86) return "Nieve";
        if (code >= 80) return "Chubascos";
        if (code >= 61) return "Lluvia";
        return "Llovizna";
    }

    // ---------- momento del día, para el fondo del inicio ----------

    static final int NIGHT = 0, DAWN = 1, DAYTIME = 2, DUSK = 3;

    /** Usa amanecer y atardecer reales si hay clima; si no, 6:00 y 18:00. */
    static int phase(Weather w) {
        if (w == null) w = last;
        long now = System.currentTimeMillis();
        long rise, set;
        if (w != null) {
            rise = w.sunrise;
            set = w.sunset;
        } else {
            Calendar c = Calendar.getInstance();
            c.set(Calendar.HOUR_OF_DAY, 6);
            c.set(Calendar.MINUTE, 0);
            rise = c.getTimeInMillis();
            set = rise + 12 * 3600 * 1000L;
        }
        long h = 3600 * 1000L;
        if (now >= rise - h * 3 / 4 && now < rise + h) return DAWN;
        if (now >= set - h && now < set + h * 3 / 4) return DUSK;
        if (now >= rise + h && now < set - h) return DAYTIME;
        return NIGHT;
    }

    /** Degradados del fondo: claros al amanecer y de día, oscuros al atardecer y de noche (ver Theme). */
    static int[] colors(int phase) {
        switch (phase) {
            case DAWN: return new int[]{0xFFFDEBDD, 0xFFF6DCE6, 0xFFE2E6F8};
            case DAYTIME: return new int[]{0xFFEAF3FF, 0xFFD6E7FC, 0xFFBDD6F4};
            case DUSK: return new int[]{0xFF241038, 0xFF7A2852, 0xFFC4552E};
            default: return new int[]{0xFF1B1038, 0xFF0B1B3F, 0xFF06283D};
        }
    }
}
