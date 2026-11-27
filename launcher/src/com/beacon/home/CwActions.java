package com.beacon.home;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

import java.util.function.Consumer;

/**
 * The long-press options Nuvio's own Continue watching row has (Go to details, Start from beginning, Mark as watched,
 * Remove), for Nuvio Beacon's items. The first two reopen Nuvio with the item's own launch intent plus a request;
 * the other two are performed by Nuvio Beacon through its launcher content provider.
 */
final class CwActions {
    private static final String TAG = "CwActions";

    /** The launch intent Nuvio published for this item, or null when there isn't one beacon can read. */
    private static Intent intentOf(Nuvio.Item it) {
        if (it == null || TextUtils.isEmpty(it.intentUri)) return null;
        try {
            Intent i = Intent.parseUri(it.intentUri, Intent.URI_INTENT_SCHEME);
            return i.getStringExtra("contentId") == null ? null : i;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Only Nuvio Beacon understands these requests (official Nuvio has no such provider). */
    static boolean available(Nuvio.Item it) {
        return it != null && U.NUVIO_BEACON.equals(it.pkg) && intentOf(it) != null;
    }

    static boolean details(Context c, Nuvio.Item it) {
        Intent i = intentOf(it);
        if (i == null) return false;
        i.setPackage(it.pkg);
        i.putExtra("launchMode", "detail");
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return Apps.start(c, i);
    }

    static boolean fromBeginning(Context c, Nuvio.Item it) {
        Intent i = intentOf(it);
        if (i == null) return false;
        i.setPackage(it.pkg);
        i.putExtra("startFromBeginning", true);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return Apps.start(c, i);
    }

    static void remove(Context c, Nuvio.Item it, Consumer<Boolean> done) { call(c, it, "cw_remove", done); }

    static void markWatched(Context c, Nuvio.Item it, Consumer<Boolean> done) { call(c, it, "cw_mark_watched", done); }

    private static void call(Context c, final Nuvio.Item it, final String method, final Consumer<Boolean> done) {
        final Context app = c.getApplicationContext();
        final Intent i = intentOf(it);
        U.IO.execute(() -> {
            boolean ok = false;
            if (i != null) {
                try {
                    Bundle x = new Bundle();
                    x.putBoolean("next_up", it.watchNext);
                    if (i.hasExtra("season")) x.putInt("season", i.getIntExtra("season", -1));
                    if (i.hasExtra("episode")) x.putInt("episode", i.getIntExtra("episode", -1));
                    x.putString("content_type", i.getStringExtra("contentType"));
                    x.putString("name", i.getStringExtra("name"));
                    x.putString("video_id", i.getStringExtra("videoId"));
                    x.putString("episode_title", i.getStringExtra("episodeTitle"));
                    x.putString("poster", i.getStringExtra("poster"));
                    x.putString("backdrop", i.getStringExtra("backdrop"));
                    x.putString("logo", i.getStringExtra("logo"));
                    Bundle r = app.getContentResolver().call(NuvioProfiles.uri(it.pkg), method, i.getStringExtra("contentId"), x);
                    ok = r != null && "ok".equals(r.getString("result"));
                } catch (Throwable t) {
                    Log.w(TAG, method, t);
                }
            }
            final boolean res = ok;
            U.MAIN.post(() -> done.accept(res));
        });
    }

    private CwActions() {}
}
