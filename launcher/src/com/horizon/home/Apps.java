package com.horizon.home;

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Installed apps, sorted into Watch / Play / Listen / Tools automatically. */
final class Apps {
    static final String WATCH = "watch", PLAY = "play", LISTEN = "listen", TOOLS = "tools", OTHER = "other";

    static final class App {
        String pkg;
        ComponentName component;
        String label;
        Drawable banner;
        Drawable icon;
        String category;
        int color = 0xFF2A2F3A;
    }

    private static final Map<String, String> KNOWN = new HashMap<>();
    private static final Set<String> HIDDEN = new HashSet<>();
    /** Default order before the launcher has learned what you use. */
    static final String[] SEED = {
            "com.nuvio.tv", "com.google.android.youtube.tv", "com.amazon.amazonvideo.livingroom",
            "in.startv.hotstar", "com.crunchyroll.crunchyroid", "com.stremio.one", "com.fancode.tv",
            "com.google.android.youtube.tvmusic", "com.nvidia.geforcenow", "com.redphx.betterxc",
            "net.gtvbox.videoplayer", "com.google.android.videos"};

    static {
        String[] watch = {"com.nuvio.tv", "com.google.android.youtube.tv", "com.amazon.amazonvideo.livingroom",
                "in.startv.hotstar", "com.crunchyroll.crunchyroid", "com.fancode.tv", "com.stremio.one",
                "com.google.android.videos", "com.ted.android.tv", "net.gtvbox.videoplayer", "com.netflix.ninja",
                "com.graymatrix.did", "com.sonyliv", "com.jio.jioplay.tv", "com.disney.disneyplus",
                "com.plexapp.android", "org.xbmc.kodi", "org.videolan.vlc", "com.mxtech.videoplayer.ad",
                "com.liskovsoft.smarttubetv.beta", "com.teamsmart.videomanager.tv", "org.jellyfin.androidtv",
                "com.apple.atve.androidtv.appletv", "com.zee5.tv", "com.sonyliv.tv"};
        String[] play = {"com.nvidia.geforcenow", "com.redphx.betterxc", "com.google.android.play.games",
                "com.microsoft.xcloud", "com.valvesoftware.steamlink", "com.retroarch", "com.moonlight_stream.moonlight"};
        String[] listen = {"com.google.android.youtube.tvmusic", "com.soundcloud.android",
                "com.spotify.tv.android", "com.jio.media.jiobeats", "com.gaana", "com.amazon.music.tv",
                "com.apple.android.music", "com.audible.application"};
        String[] tools = {"com.esaba.downloader", "net.gtvbox.installer",
                "com.android.vending", "com.android.tv.settings", "com.termux",
                "com.google.android.tvlauncher", "com.cxinventor.file.explorer",
                "com.estrongs.android.pop"};
        for (String s : watch) KNOWN.put(s, WATCH);
        for (String s : play) KNOWN.put(s, PLAY);
        for (String s : listen) KNOWN.put(s, LISTEN);
        for (String s : tools) KNOWN.put(s, TOOLS);
        Collections.addAll(HIDDEN, "com.android.camera2",
                "com.mediatek.wwtv.webview", "com.thorium.webview", "com.android.tv.settings.system");
    }

    private Apps() {}

    static List<App> load(Context c) {
        PackageManager pm = c.getPackageManager();
        SharedPreferences prefs = U.prefs(c);
        Map<String, App> map = new LinkedHashMap<>();
        Intent tv = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        for (ResolveInfo ri : pm.queryIntentActivities(tv, 0)) add(c, pm, prefs, map, ri);
        Intent phone = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        for (ResolveInfo ri : pm.queryIntentActivities(phone, 0)) add(c, pm, prefs, map, ri);
        List<App> out = new ArrayList<>(map.values());
        Collections.sort(out, (a, b) -> a.label.compareToIgnoreCase(b.label));
        return out;
    }

    private static void add(Context c, PackageManager pm, SharedPreferences prefs, Map<String, App> map, ResolveInfo ri) {
        if (ri.activityInfo == null) return;
        String pkg = ri.activityInfo.packageName;
        // beacon doesn't list itself (opening the home screen from the home screen does nothing)
        if (pkg.equals(c.getPackageName())) return;
        if (map.containsKey(pkg) || HIDDEN.contains(pkg) || prefs.getBoolean("hide_" + pkg, false)) return;
        App a = new App();
        a.pkg = pkg;
        a.component = new ComponentName(pkg, ri.activityInfo.name);
        CharSequence l = ri.loadLabel(pm);
        a.label = l == null ? pkg : l.toString();
        if ("com.android.tv.settings".equals(pkg)) a.label = "Settings";
        try { a.banner = ri.activityInfo.loadBanner(pm); } catch (Throwable ignored) {}
        if (a.banner == null) {
            try { a.banner = pm.getApplicationBanner(pkg); } catch (Throwable ignored) {}
        }
        try { a.icon = ri.loadIcon(pm); } catch (Throwable ignored) {}
        a.category = prefs.getString("cat_" + pkg, null);
        if (a.category == null) a.category = guess(ri.activityInfo.applicationInfo, pkg);
        a.color = dominant(a.icon);
        map.put(pkg, a);
    }

    static String guess(ApplicationInfo ai, String pkg) {
        String k = KNOWN.get(pkg);
        if (k != null) return k;
        if (ai != null) {
            if ((ai.flags & ApplicationInfo.FLAG_IS_GAME) != 0) return PLAY;
            switch (ai.category) {
                case ApplicationInfo.CATEGORY_GAME: return PLAY;
                case ApplicationInfo.CATEGORY_AUDIO: return LISTEN;
                case ApplicationInfo.CATEGORY_VIDEO: case ApplicationInfo.CATEGORY_IMAGE: return WATCH;
                case ApplicationInfo.CATEGORY_PRODUCTIVITY: case ApplicationInfo.CATEGORY_NEWS:
                case ApplicationInfo.CATEGORY_MAPS: case ApplicationInfo.CATEGORY_SOCIAL: return TOOLS;
                default: break;
            }
        }
        String p = pkg.toLowerCase(Locale.ROOT);
        if (p.matches(".*(music|radio|audio|podcast|\\.fm|saavn|gaana|spotify).*")) return LISTEN;
        if (p.matches(".*(game|games|xcloud|steam|emulat|arcade).*")) return PLAY;
        if (p.matches(".*(video|movie|stream|player|tv|ott|anime|cinema|film|iptv|live).*")) return WATCH;
        if (p.matches(".*(file|install|download|manager|setting|clean|vpn|browser|remote).*")) return TOOLS;
        return OTHER;
    }

    /** Apps in the order you use them: most recent first, then the seed order. */
    static List<App> favourites(Context c, List<App> all, int max) {
        SharedPreferences p = U.prefs(c);
        List<App> out = new ArrayList<>(all);
        final Map<String, Integer> seed = new HashMap<>();
        for (int i = 0; i < SEED.length; i++) seed.put(SEED[i], i);
        Collections.sort(out, (a, b) -> {
            boolean an = U.nuvio().equals(a.pkg), bn = U.nuvio().equals(b.pkg);
            if (an != bn) return an ? -1 : 1;
            long la = p.getLong("last_" + a.pkg, 0), lb = p.getLong("last_" + b.pkg, 0);
            int ca = p.getInt("count_" + a.pkg, 0), cb = p.getInt("count_" + b.pkg, 0);
            if (ca != cb) return cb - ca;
            if (la != lb) return lb > la ? 1 : -1;
            Integer sa = seed.get(a.pkg), sb = seed.get(b.pkg);
            if (sa != null || sb != null) {
                if (sa == null) return 1;
                if (sb == null) return -1;
                return sa - sb;
            }
            int wa = WATCH.equals(a.category) ? 0 : 1, wb = WATCH.equals(b.category) ? 0 : 1;
            if (wa != wb) return wa - wb;
            return a.label.compareToIgnoreCase(b.label);
        });
        return out.subList(0, Math.min(max, out.size()));
    }

    static List<App> recent(Context c, List<App> all, int max) {
        final SharedPreferences p = U.prefs(c);
        List<App> out = new ArrayList<>();
        for (App a : all) if (p.getLong("last_" + a.pkg, 0) > 0) out.add(a);
        Collections.sort(out, (a, b) -> Long.compare(p.getLong("last_" + b.pkg, 0), p.getLong("last_" + a.pkg, 0)));
        if (out.size() < max) {
            for (App a : favourites(c, all, max * 2)) {
                if (out.size() >= max) break;
                if (!out.contains(a)) out.add(a);
            }
        }
        return out.subList(0, Math.min(max, out.size()));
    }

    // ---- Pins: one ordered list, like Veil (new pins go to the end; the row never fills itself) ----

    /** Package names pinned to Home, in order. First run: your old pins, otherwise the apps you use most. */
    static List<String> pins(Context c, List<App> all) {
        SharedPreferences p = U.prefs(c);
        String saved = p.getString("pins", null);
        List<String> out = new ArrayList<>();
        if (saved != null) {
            for (String s : saved.split("\\|")) if (!s.isEmpty()) out.add(s);
            return out;
        }
        for (App a : all) if (p.getBoolean("pin_" + a.pkg, false)) out.add(a.pkg);
        if (out.isEmpty()) for (App a : favourites(c, all, 7)) out.add(a.pkg);
        if (!all.isEmpty()) savePins(c, out); // only once the app list is known
        return out;
    }

    static void savePins(Context c, List<String> pins) {
        StringBuilder sb = new StringBuilder();
        for (String s : pins) { if (sb.length() > 0) sb.append('|'); sb.append(s); }
        U.prefs(c).edit().putString("pins", sb.toString()).apply();
    }

    static List<App> pinned(Context c, List<App> all) {
        List<App> out = new ArrayList<>();
        for (String pkg : pins(c, all)) { App a = find(all, pkg); if (a != null) out.add(a); }
        return out;
    }

    static boolean isPinned(Context c, List<App> all, String pkg) { return pins(c, all).contains(pkg); }

    static void pin(Context c, List<App> all, String pkg) {
        List<String> l = pins(c, all);
        if (!l.contains(pkg)) { l.add(pkg); savePins(c, l); }
    }

    static void unpin(Context c, List<App> all, String pkg) {
        List<String> l = pins(c, all);
        if (l.remove(pkg)) savePins(c, l);
    }

    /** One step left (-1) or right (+1) among the pinned apps that are installed. */
    static void movePin(Context c, List<App> all, String pkg, int delta) {
        List<String> l = pins(c, all);
        List<String> visible = new ArrayList<>();
        for (String s : l) if (find(all, s) != null) visible.add(s);
        int i = visible.indexOf(pkg), j = i + delta;
        if (i < 0 || j < 0 || j >= visible.size()) return;
        Collections.swap(visible, i, j);
        for (String s : l) if (!visible.contains(s)) visible.add(s); // uninstalled pins stay, at the end
        savePins(c, visible);
    }

    static List<App> inCategory(List<App> all, String cat) {
        List<App> out = new ArrayList<>();
        for (App a : all) if (cat.equals(a.category)) out.add(a);
        return out;
    }

    static App find(List<App> all, String pkg) {
        if (all == null) return null;
        for (App a : all) if (a.pkg.equals(pkg)) return a;
        return null;
    }

    static void noteLaunch(Context c, String pkg) {
        SharedPreferences p = U.prefs(c);
        p.edit().putLong("last_" + pkg, System.currentTimeMillis())
                .putInt("count_" + pkg, p.getInt("count_" + pkg, 0) + 1).apply();
    }

    static boolean launch(Context c, App a) {
        Intent i = new Intent(Intent.ACTION_MAIN);
        i.setComponent(a.component);
        i.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            c.startActivity(i);
            noteLaunch(c, a.pkg);
            return true;
        } catch (Throwable t) {
            Intent li = c.getPackageManager().getLeanbackLaunchIntentForPackage(a.pkg);
            if (li == null) li = c.getPackageManager().getLaunchIntentForPackage(a.pkg);
            if (li != null) {
                try {
                    li.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    c.startActivity(li);
                    noteLaunch(c, a.pkg);
                    return true;
                } catch (Throwable ignored) {}
            }
            Toast.makeText(c, "Couldn't open " + a.label, Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    static boolean launchPackage(Context c, String pkg) {
        Intent li = c.getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
        if (li == null) li = c.getPackageManager().getLaunchIntentForPackage(pkg);
        if (li == null) return false;
        li.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            c.startActivity(li);
            noteLaunch(c, pkg);
            return true;
        } catch (ActivityNotFoundException e) {
            return false;
        }
    }

    static boolean start(Context c, Intent i) {
        try {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    static int dominant(Drawable d) {
        if (d == null) return 0xFF2A2F3A;
        try {
            Bitmap b = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888);
            Canvas cv = new Canvas(b);
            d.setBounds(0, 0, 16, 16);
            d.draw(cv);
            long r = 0, g = 0, bl = 0, n = 0;
            for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
                int px = b.getPixel(x, y);
                if (Color.alpha(px) < 200) continue;
                float[] hsv = new float[3];
                Color.colorToHSV(px, hsv);
                int w = hsv[1] > 0.25f ? 3 : 1;
                r += Color.red(px) * w; g += Color.green(px) * w; bl += Color.blue(px) * w; n += w;
            }
            if (n == 0) return 0xFF2A2F3A;
            int col = Color.rgb((int) (r / n), (int) (g / n), (int) (bl / n));
            float[] hsv = new float[3];
            Color.colorToHSV(col, hsv);
            hsv[2] = Math.min(hsv[2], 0.55f);
            hsv[1] = Math.min(1f, hsv[1] * 1.1f);
            return Color.HSVToColor(hsv);
        } catch (Throwable t) {
            return 0xFF2A2F3A;
        }
    }
}
