package com.beacon.home;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
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

/**
 * Downloads Nuvio Beacon (antgash/NuvioTVbeacon: Nuvio with launcher profile switching) from its newest GitHub
 * release and hands it to Android's installer, the same way beacon updates itself. It installs next to official
 * Nuvio; after a first install beacon switches its Nuvio app to it.
 */
final class NuvioBeaconInstaller {
    static final String REPO = "antgash/NuvioTVbeacon";
    static final String ACTION = "com.beacon.home.NUVIO_BEACON_RESULT";
    private static final String EXTRA_FIRST = "first_install";
    /** PendingIntent.FLAG_MUTABLE (API 31), written out because the build compiles against API 30. */
    private static final int FLAG_MUTABLE = 1 << 25;

    static final class Release {
        String tag, url;
        long size;
    }

    interface Found { void done(Release r, boolean ok); }

    /** Installed version name, or null when Nuvio Beacon isn't installed. */
    static String installed(Context c) {
        try { return c.getPackageManager().getPackageInfo(U.NUVIO_BEACON, 0).versionName; } catch (Throwable t) { return null; }
    }

    /** Finds the newest release (betas included: Nuvio Beacon follows Nuvio's betas) and the APK for this device. */
    static void latest(Context c, Found cb) {
        U.IO.execute(() -> {
            Release r = null;
            boolean ok = false;
            try {
                HttpURLConnection h = (HttpURLConnection) new URL("https://api.github.com/repos/" + REPO + "/releases?per_page=10").openConnection();
                h.setConnectTimeout(10000);
                h.setReadTimeout(15000);
                h.setRequestProperty("Accept", "application/vnd.github+json");
                h.setRequestProperty("User-Agent", "beacon");
                if (h.getResponseCode() == 200) {
                    StringBuilder sb = new StringBuilder();
                    try (InputStream in = h.getInputStream()) {
                        byte[] buf = new byte[16384];
                        int n;
                        while ((n = in.read(buf)) > 0) sb.append(new String(buf, 0, n, "UTF-8"));
                    }
                    JSONArray all = new JSONArray(sb.toString());
                    ok = true;
                    for (int i = 0; i < all.length() && r == null; i++) {
                        JSONObject rel = all.getJSONObject(i);
                        if (rel.optBoolean("draft")) continue;
                        JSONObject apk = pickApk(rel.optJSONArray("assets"));
                        if (apk == null) continue;
                        r = new Release();
                        r.tag = rel.optString("tag_name");
                        r.url = apk.optString("browser_download_url");
                        r.size = apk.optLong("size");
                    }
                }
                h.disconnect();
            } catch (Throwable ignored) {
            }
            final Release res = r;
            final boolean fine = ok;
            U.MAIN.post(() -> cb.done(res, fine));
        });
    }

    /** The APK for this device's processor (as Nuvio's own updater picks), else the universal one. */
    private static JSONObject pickApk(JSONArray assets) {
        if (assets == null) return null;
        for (String abi : Build.SUPPORTED_ABIS) {
            for (int i = 0; i < assets.length(); i++) {
                JSONObject a = assets.optJSONObject(i);
                String n = a == null ? "" : a.optString("name");
                if (n.endsWith(".apk") && n.contains(abi)) return a;
            }
        }
        for (int i = 0; i < assets.length(); i++) {
            JSONObject a = assets.optJSONObject(i);
            String n = a == null ? "" : a.optString("name");
            if (n.endsWith(".apk") && n.contains("universal")) return a;
        }
        return null;
    }

    /** Downloads the APK into the cache, then starts the install. Progress and the outcome arrive on the main thread. */
    static void downloadAndInstall(Context c, Release r, Updater.Progress p, java.util.function.Consumer<String> state) {
        final Context app = c.getApplicationContext();
        final boolean first = installed(app) == null;
        U.IO.execute(() -> {
            File out = new File(app.getCacheDir(), "nuvio-beacon.apk");
            boolean ok = false;
            try {
                HttpURLConnection h = (HttpURLConnection) new URL(r.url).openConnection();
                h.setConnectTimeout(10000);
                h.setReadTimeout(30000);
                h.setRequestProperty("User-Agent", "beacon");
                long total = h.getContentLengthLong() > 0 ? h.getContentLengthLong() : r.size;
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
                ok = out.length() > 1024 * 1024;
            } catch (Throwable ignored) {
            }
            if (!ok) { out.delete(); U.MAIN.post(() -> state.accept("Download failed · select to retry")); return; }
            final boolean started = install(app, out, first);
            U.MAIN.post(() -> state.accept(started ? "Confirm the install on screen" : "Couldn't start the installer · select to retry"));
        });
    }

    private static boolean install(Context c, File apk, boolean first) {
        try {
            PackageInstaller pi = c.getPackageManager().getPackageInstaller();
            PackageInstaller.SessionParams sp = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            sp.setAppPackageName(U.NUVIO_BEACON);
            int id = pi.createSession(sp);
            try (PackageInstaller.Session s = pi.openSession(id)) {
                try (InputStream in = new FileInputStream(apk); OutputStream out = s.openWrite("nuvio-beacon", 0, apk.length())) {
                    byte[] buf = new byte[64 * 1024];
                    int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                    s.fsync(out);
                }
                Intent it = new Intent(c, Result.class).setAction(ACTION).putExtra(EXTRA_FIRST, first);
                PendingIntent pend = PendingIntent.getBroadcast(c, id, it,
                        PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? FLAG_MUTABLE : 0));
                s.commit(pend.getIntentSender());
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** The installer's answer: shows its confirmation screen, reports failures, and after a first install uses it. */
    public static final class Result extends BroadcastReceiver {
        @Override public void onReceive(Context c, Intent i) {
            int st = i.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
            if (st == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                Intent confirm = i.getParcelableExtra(Intent.EXTRA_INTENT);
                if (confirm != null) {
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    try { c.startActivity(confirm); } catch (Throwable ignored) {}
                }
            } else if (st == PackageInstaller.STATUS_SUCCESS) {
                new File(c.getCacheDir(), "nuvio-beacon.apk").delete();
                if (i.getBooleanExtra(EXTRA_FIRST, false)) {
                    U.setNuvio(c, U.NUVIO_BEACON);
                    Toast.makeText(c, "Nuvio Beacon installed · beacon now uses it for Continue watching and profiles", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(c, "Nuvio Beacon updated", Toast.LENGTH_SHORT).show();
                }
            } else {
                new File(c.getCacheDir(), "nuvio-beacon.apk").delete();
                String msg = i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
                Toast.makeText(c, "Nuvio Beacon not installed" + (TextUtils.isEmpty(msg) ? "" : ": " + msg), Toast.LENGTH_LONG).show();
            }
        }
    }

    private NuvioBeaconInstaller() {}
}
