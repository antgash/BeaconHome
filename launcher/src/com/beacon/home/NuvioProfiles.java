package com.beacon.home;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Nuvio's profiles, read from the provider Nuvio publishes for launchers
 * (content://&lt;nuvio package&gt;.launcher/profiles). Nuvio builds without it simply return null,
 * and beacon hides the profile button.
 */
final class NuvioProfiles {
    private static final String TAG = "BeaconProfiles";
    static final String SWITCH = "switch_profile";
    static final String EXTRA_SHOW_PROFILES = "com.nuvio.tv.extra.SHOW_PROFILES";

    static final class Profile {
        int id;
        String name, color, avatar;
        /** The accent of this profile's Nuvio theme ("#RRGGBB"), or null when Nuvio doesn't share it. */
        String themeColor;
        /** The Nuvio app these profiles came from. */
        String pkg;
        boolean active, pin;

        String initial() {
            String n = name == null ? "" : name.trim();
            return n.isEmpty() ? "?" : n.substring(0, n.offsetByCodePoints(0, 1)).toUpperCase();
        }

        /** The colour beacon follows: Nuvio's theme accent for this profile, else the profile's own colour. */
        int accentInt() {
            try { if (themeColor != null) return android.graphics.Color.parseColor(themeColor); } catch (Exception ignored) {}
            return colorInt();
        }

        int colorInt() {
            try { return android.graphics.Color.parseColor(color); } catch (Exception e) { return 0xFF1E88E5; }
        }
    }

    static Uri uri(String pkg) { return Uri.parse("content://" + pkg + ".launcher/profiles"); }

    // ------------------------------------------------------------------ Continue watching refresh

    static final String CW_REFRESH = "cw_refresh";
    private static final long CW_REFRESH_EVERY_MS = 3 * 60_000L;
    private static long cwRefreshAt;

    /**
     * Asks Nuvio Beacon to fetch the active profile's latest progress in the background and republish
     * its Continue watching, so a title watched on another TV shows here without opening Nuvio. beacon's
     * row updates by itself when it lands (it watches the TV provider). At most every few minutes;
     * Nuvio builds without it simply ignore the call.
     */
    static void requestCwRefresh(Context c) {
        long now = android.os.SystemClock.elapsedRealtime();
        if (cwRefreshAt != 0 && now - cwRefreshAt < CW_REFRESH_EVERY_MS) return;
        cwRefreshAt = now;
        final Context app = c.getApplicationContext();
        U.IO.execute(() -> {
            for (String pkg : sources()) {
                try {
                    Bundle r = app.getContentResolver().call(uri(pkg), CW_REFRESH, null, null);
                    if (r != null) {
                        Log.d(TAG, "Continue watching refresh: " + r.getString("result") + " (" + pkg + ")");
                        return;
                    }
                } catch (Throwable t) {
                    Log.d(TAG, "Continue watching refresh not offered by " + pkg);
                }
            }
        });
    }

    // ------------------------------------------------------------------ last known list

    private static final String CACHE = "nuvio_profiles_cache";

    /** Remembers the list, so the profile button can show at once next time (before Nuvio has answered). */
    static void save(Context c, List<Profile> list) {
        try {
            org.json.JSONArray a = new org.json.JSONArray();
            for (Profile p : list) {
                org.json.JSONObject o = new org.json.JSONObject();
                o.put("id", p.id).put("name", p.name).put("color", p.color).put("avatar", p.avatar)
                        .put("theme", p.themeColor).put("pkg", p.pkg).put("active", p.active).put("pin", p.pin);
                a.put(o);
            }
            U.prefs(c).edit().putString(CACHE, a.toString()).apply();
        } catch (Throwable ignored) {}
    }

    /** The last list Nuvio gave, while the app it came from is still installed (null otherwise). */
    static List<Profile> cached(Context c) {
        try {
            String raw = U.prefs(c).getString(CACHE, null);
            if (raw == null) return null;
            org.json.JSONArray a = new org.json.JSONArray(raw);
            List<Profile> out = new ArrayList<>();
            for (int i = 0; i < a.length(); i++) {
                org.json.JSONObject o = a.getJSONObject(i);
                Profile p = new Profile();
                p.id = o.optInt("id");
                p.name = o.optString("name", null);
                p.color = o.optString("color", null);
                p.avatar = o.isNull("avatar") ? null : o.optString("avatar", null);
                p.themeColor = o.isNull("theme") ? null : o.optString("theme", null);
                p.pkg = o.optString("pkg", null);
                p.active = o.optBoolean("active");
                p.pin = o.optBoolean("pin");
                out.add(p);
            }
            if (out.isEmpty() || out.get(0).pkg == null) return null;
            c.getPackageManager().getPackageInfo(out.get(0).pkg, 0);   // throws when it was uninstalled
            return out;
        } catch (Throwable t) {
            return null;
        }
    }

    static void forget(Context c) { U.prefs(c).edit().remove(CACHE).apply(); }

    /** Where profiles are read from: the chosen Nuvio app, then Nuvio Beacon (which always offers them). */
    static String[] sources() {
        String pkg = U.nuvio();
        return U.NUVIO_BEACON.equals(pkg) ? new String[] {pkg} : new String[] {pkg, U.NUVIO_BEACON};
    }

    /** All profiles from the first source that offers them, or null when none does. Call off the main thread. */
    static List<Profile> load(Context c) {
        for (String pkg : sources()) {
            List<Profile> list = load(c, pkg);
            if (list != null && !list.isEmpty()) return list;
        }
        return null;
    }

    private static List<Profile> load(Context c, String pkg) {
        Cursor cur = null;
        try {
            cur = c.getContentResolver().query(uri(pkg), null, null, null, null);
            if (cur == null) return null;
            List<Profile> out = new ArrayList<>();
            int id = cur.getColumnIndex("_id"), name = cur.getColumnIndex("name"), color = cur.getColumnIndex("color"),
                    avatar = cur.getColumnIndex("avatar_uri"), theme = cur.getColumnIndex("theme_color"), active = cur.getColumnIndex("is_active"),
                    pin = cur.getColumnIndex("has_pin");
            while (cur.moveToNext()) {
                Profile p = new Profile();
                p.pkg = pkg;
                p.id = cur.getInt(id);
                p.name = cur.getString(name);
                p.color = color < 0 ? null : cur.getString(color);
                p.avatar = avatar < 0 ? null : cur.getString(avatar);
                p.themeColor = theme < 0 ? null : cur.getString(theme);
                p.active = active >= 0 && cur.getInt(active) == 1;
                p.pin = pin >= 0 && cur.getInt(pin) == 1;
                out.add(p);
            }
            return out;
        } catch (Throwable t) {
            // Not installed, an older Nuvio without the provider, or not visible to us.
            return null;
        } finally {
            if (cur != null) cur.close();
        }
    }

    static Profile active(List<Profile> list) {
        if (list == null) return null;
        for (Profile p : list) if (p.active) return p;
        return null;
    }

    /** Asks Nuvio to switch. Returns "ok", "pin_required", "not_found" or "failed". Call off the main thread. */
    static String switchTo(Context c, Profile p) {
        try {
            Bundle b = c.getContentResolver().call(uri(p.pkg), SWITCH, String.valueOf(p.id), null);
            String r = b == null ? null : b.getString("result");
            return r == null ? "failed" : r;
        } catch (Throwable t) {
            Log.w(TAG, "switch", t);
            return "failed";
        }
    }

    /** Opens Nuvio on its own profile screen (PIN-protected profiles are chosen there). */
    static boolean openPicker(Context c, String pkg) {
        Intent i = c.getPackageManager().getLaunchIntentForPackage(pkg);
        if (i == null) return false;
        i.putExtra(EXTRA_SHOW_PROFILES, true);
        // SINGLE_TOP + CLEAR_TOP so a Nuvio that is already open still gets the request (onNewIntent).
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        try { c.startActivity(i); return true; } catch (Throwable t) { return false; }
    }

    private NuvioProfiles() {}
}
