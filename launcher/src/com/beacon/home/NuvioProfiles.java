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
