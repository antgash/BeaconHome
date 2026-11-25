package com.horizon.home;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.os.Build;
import android.text.TextUtils;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Looks at this project's newest GitHub release for a newer beacon build, downloads it and hands it to
 * Android's installer (which asks you to confirm). Nothing is sent anywhere except the GitHub request itself.
 *
 * The release notes carry "versionCode: N" (written by the build workflow). Without it, the release date is
 * compared with when this copy was installed.
 */
final class Updater {
    static final String REPO = "antgash/BeaconHome";
    static final String P_AUTO = "update_check";
    static final String P_LAST = "update_last";
    static final String P_PEND_CODE = "update_pend_code";
    static final String P_PEND_PUB = "update_pend_pub";
    static final String ACTION = "com.horizon.home.UPDATE_RESULT";

    static final class Info {
        long code, published;
        String name, notes, url;
        long size;
    }

    interface Check { void done(Info newer, boolean ok); }
    interface Progress { void at(int percent); }
    interface Done { void done(File apk); }

    /** PendingIntent.FLAG_MUTABLE (API 31), written out because the build compiles against API 30. */
    private static final int FLAG_MUTABLE = 1 << 25;

    private Updater() {}

    static PackageInfo self(Context c) {
        try { return c.getPackageManager().getPackageInfo(c.getPackageName(), 0); } catch (Throwable t) { return null; }
    }

    /** Asks GitHub (on a background thread); calls back on the main thread with the newer build, or null. */
    static void check(final Context c, final Check cb) {
        final Context app = c.getApplicationContext();
        U.IO.execute(() -> {
            Info found = null;
            boolean ok = false;
            try {
                HttpURLConnection h = (HttpURLConnection) new URL("https://api.github.com/repos/" + REPO + "/releases/latest").openConnection();
                h.setConnectTimeout(8000);
                h.setReadTimeout(10000);
                h.setRequestProperty("Accept", "application/vnd.github+json");
                h.setRequestProperty("User-Agent", "beacon-updater");
                if (h.getResponseCode() == 200) {
                    JSONObject j = new JSONObject(readAll(h.getInputStream()));
                    ok = true;
                    String notes = j.optString("body", "");
                    JSONArray assets = j.optJSONArray("assets");
                    JSONObject apk = null;
                    for (int i = 0; assets != null && i < assets.length(); i++) {
                        JSONObject a = assets.optJSONObject(i);
                        if (a != null && a.optString("name").endsWith(".apk")) { apk = a; break; }
                    }
                    if (apk != null) {
                        long code = 0;
                        long published = parseDate(j.optString("published_at", ""));
                        Matcher m = Pattern.compile("versionCode:\\s*(\\d+)").matcher(notes);
                        if (m.find()) code = Long.parseLong(m.group(1));
                        PackageInfo me = self(app);
                        boolean newer;
                        if (code > 0 && me != null) {
                            newer = code > longCode(me);
                        } else {
                            // no number in the notes: compare dates (with slack for the time it takes to install)
                            newer = me != null && published > 0 && published > me.lastUpdateTime + 10 * 60 * 1000L;
                        }
                        if (newer) {
                            Info i = new Info();
                            i.code = code;
                            i.published = published;
                            i.name = j.optString("name", "latest");
                            i.notes = notes;
                            i.url = apk.optString("browser_download_url");
                            i.size = apk.optLong("size");
                            found = i;
                        }
                    }
                }
                h.disconnect();
            } catch (Throwable ignored) {
            }
            final Info f = found;
            final boolean good = ok;
            if (good) {
                android.content.SharedPreferences.Editor ed = U.prefs(app).edit().putLong(P_LAST, System.currentTimeMillis());
                if (f != null) ed.putLong(P_PEND_CODE, f.code).putLong(P_PEND_PUB, f.published);
                else ed.remove(P_PEND_CODE).remove(P_PEND_PUB);
                ed.apply();
            }
            U.MAIN.post(() -> cb.done(f, good));
        });
    }

    /** True while a newer build than this one is known to exist (remembered between checks; clears once it is installed). */
    static boolean pending(Context c) {
        android.content.SharedPreferences p = U.prefs(c);
        long code = p.getLong(P_PEND_CODE, 0), pub = p.getLong(P_PEND_PUB, 0);
        PackageInfo me = self(c);
        if (me == null || (code <= 0 && pub <= 0)) return false;
        if (code > 0) return code > longCode(me);
        return pub > me.lastUpdateTime + 10 * 60 * 1000L;
    }

    /** This copy's build number. */
    static long installedCode(Context c) {
        PackageInfo p = self(c);
        return p == null ? 0 : longCode(p);
    }

    /** Short description of why a build counts as newer, for the settings row. */
    static String why(Context c, Info i) {
        PackageInfo p = self(c);
        if (i.code > 0) return "GitHub build " + i.code + " \u00b7 yours " + installedCode(c);
        java.text.DateFormat f = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT);
        return "released " + f.format(new java.util.Date(i.published)) + " \u00b7 yours installed " + (p == null ? "?" : f.format(new java.util.Date(p.lastUpdateTime)));
    }

    /** True when the automatic check is on and it has been at least half a day since the last one. */
    static boolean due(Context c) {
        return U.prefs(c).getBoolean(P_AUTO, true)
                && System.currentTimeMillis() - U.prefs(c).getLong(P_LAST, 0) > 12L * 3600 * 1000;
    }

    /** Downloads the APK into the app cache; progress and completion arrive on the main thread (apk is null on failure). */
    static void download(final Context c, final Info info, final Progress p, final Done d) {
        final Context app = c.getApplicationContext();
        U.IO.execute(() -> {
            File out = new File(app.getCacheDir(), "beacon-update.apk");
            boolean ok = false;
            try {
                HttpURLConnection h = (HttpURLConnection) new URL(info.url).openConnection();
                h.setConnectTimeout(10000);
                h.setReadTimeout(20000);
                h.setRequestProperty("User-Agent", "beacon-updater");
                long total = h.getContentLengthLong() > 0 ? h.getContentLengthLong() : info.size;
                try (InputStream in = h.getInputStream(); OutputStream o = new FileOutputStream(out)) {
                    byte[] buf = new byte[64 * 1024];
                    long got = 0;
                    int last = -1, n;
                    while ((n = in.read(buf)) > 0) {
                        o.write(buf, 0, n);
                        got += n;
                        final int pct = total > 0 ? (int) (got * 100 / total) : 0;
                        if (pct != last) { last = pct; U.MAIN.post(() -> p.at(pct)); }
                    }
                }
                h.disconnect();
                ok = out.length() > 100 * 1024;
            } catch (Throwable ignored) {
            }
            final File res = ok ? out : null;
            U.MAIN.post(() -> d.done(res));
        });
    }

    /** Hands the APK to Android's installer; the system then asks you to confirm (and, the first time, to allow installs from beacon). */
    static boolean install(Context c, File apk) {
        try {
            PackageInstaller pi = c.getPackageManager().getPackageInstaller();
            PackageInstaller.SessionParams sp = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            sp.setAppPackageName(c.getPackageName());
            int id = pi.createSession(sp);
            try (PackageInstaller.Session s = pi.openSession(id)) {
                try (InputStream in = new FileInputStream(apk); OutputStream out = s.openWrite("beacon", 0, apk.length())) {
                    byte[] buf = new byte[64 * 1024];
                    int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                    s.fsync(out);
                }
                Intent it = new Intent(c, Result.class).setAction(ACTION);
                PendingIntent pend = PendingIntent.getBroadcast(c, id, it,
                        PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? FLAG_MUTABLE : 0));
                // remember to reopen beacon once the new version is in place (see Reopen and HomeButtonService)
                c.getSharedPreferences("beacon_restart", 0).edit().putLong("t", System.currentTimeMillis()).apply();
                s.commit(pend.getIntentSender());
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Receives the installer's answer: shows its confirmation screen, or says what went wrong. */
    public static final class Result extends BroadcastReceiver {
        @Override public void onReceive(Context c, Intent i) {
            int st = i.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
            if (st == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                Intent confirm = i.getParcelableExtra(Intent.EXTRA_INTENT);
                if (confirm != null) {
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    try { c.startActivity(confirm); } catch (Throwable ignored) {}
                }
            } else if (st != PackageInstaller.STATUS_SUCCESS) {
                String msg = i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
                Toast.makeText(c, "Update not installed" + (TextUtils.isEmpty(msg) ? "" : ": " + msg), Toast.LENGTH_LONG).show();
            }
        }
    }

    /** True for 15 minutes after an update was started: beacon should come back to the front. */
    static boolean reopenWanted(Context c) {
        long t = c.getSharedPreferences("beacon_restart", 0).getLong("t", 0);
        return t > 0 && System.currentTimeMillis() - t < 15 * 60 * 1000L;
    }

    static void reopenDone(Context c) {
        c.getSharedPreferences("beacon_restart", 0).edit().remove("t").apply();
    }

    static void reopen(Context c) {
        try {
            c.startActivity(new Intent(c, HomeActivity.class).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP));
        } catch (Throwable ignored) {}
    }

    /** Runs when this app has just been replaced by an update: bring beacon back instead of the old home screen. */
    public static final class Reopen extends BroadcastReceiver {
        @Override public void onReceive(Context c, Intent i) {
            if (reopenWanted(c)) reopen(c);
        }
    }

    // ---- helpers

    @SuppressWarnings("deprecation")
    private static long longCode(PackageInfo p) {
        return Build.VERSION.SDK_INT >= 28 ? p.getLongVersionCode() : p.versionCode;
    }

    private static long parseDate(String iso) {
        try {
            java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US);
            f.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
            return f.parse(iso).getTime();
        } catch (Throwable t) {
            return 0;
        }
    }

    private static String readAll(InputStream in) throws java.io.IOException {
        java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
        in.close();
        return b.toString("UTF-8");
    }
}
