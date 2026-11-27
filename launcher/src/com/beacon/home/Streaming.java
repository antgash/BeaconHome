package com.beacon.home;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The 22 streaming-service folders from the user's Nuvio Personalized setup (same order, same tile
 * art, title logos and backdrops). Titles come from TMDB (same lists and provider filters Nuvio uses)
 * when a TMDB key is set, otherwise from the public Streaming Catalogs addon.
 */
final class Streaming {
    private static final String TAG = "BeaconStreaming";
    static final String CATALOG = "https://7a82163c306e-stremio-netflix-catalog-addon.baby-beamup.club";
    private static final String K = "https://imkaptain.github.io/nuvio-assets/assets/images/";
    private static final String P = "https://raw.githubusercontent.com/bramst0ne/prism-wallpapers/main/collections/";
    private static final String A = "https://raw.githubusercontent.com/ImKaptain/nuvio-art/main/art/streaming-services/";
    private static final String Q = "https://raw.githubusercontent.com/ImKaptain/nuvio-assets/main/collections/";
    private static final String BD = "/backdrops/t2_flat_1080p.jpg";

    static final class Service {
        final String key, name, catalog, cover, logo, hero, providers, region;
        final long tmdbList;
        final int color;
        Service(String key, String name, String catalog, int color, String cover, String logo, String hero,
                String providers, String region, long tmdbList) {
            this.key = key; this.name = name; this.catalog = catalog; this.color = color;
            this.cover = cover; this.logo = logo; this.hero = hero;
            this.providers = providers; this.region = region; this.tmdbList = tmdbList;
        }
        boolean hasCatalog() { return catalog != null; }
    }

    static final class Row {
        final String title;
        final List<Nuvio.Item> items;
        Row(String title, List<Nuvio.Item> items) { this.title = title; this.items = items; }
    }

    static final Service[] ALL = {
            new Service("netflix", "Netflix", "nfx", 0xFFE50914, K + "49571953.webp", P + "networks/213-netflix/logos/color/213_color_1.png", P + "networks/213-netflix" + BD, "8|1796", "US", 8679739),
            new Service("max", "HBO Max", "hbm", 0xFF2B3DF5, K + "b59babf1.webp", K + "6e4c10d3.webp", P + "providers/1899-hbo-max" + BD, "1899|1825", "US", 8679740),
            new Service("appletv", "Apple TV+", "atp", 0xFF9A9AA2, K + "4d8f0a8f.webp", P + "networks/2552-apple-tv/logos/color/2552_color_1.png", P + "networks/2552-apple-tv" + BD, "350", "US", 8679741),
            new Service("shudder", "Shudder", "shd", 0xFFE01C1C, K + "99e24ce1.webp", P + "companies/142877-shudder/logos/color/142877_color_1.png", P + "providers/99-shudder" + BD, "99", "US", 8679751),
            new Service("paramount", "Paramount+", "pmp", 0xFF0064FF, A + "paramount-plus/paramount-plus-cover.png", P + "networks/4330-paramount/logos/color/4330_color_2.png", P + "providers/2303-paramount-plus-premium" + BD, "2303", "US", 8679749),
            new Service("hulu", "Hulu", "hlu", 0xFF1CE783, K + "3a4d1b46.webp", P + "networks/453-hulu/logos/color/453_color_1.png", P + "providers/15-hulu" + BD, "15", "US", 8679743),
            new Service("prime", "Prime Video", "amp", 0xFF1A98FF, K + "5c86c888.webp", K + "f9b1d40a.webp", P + "providers/9-amazon-prime-video" + BD, "9|119|2100", "US", 8679742),
            new Service("peacock", "Peacock", "pcp", 0xFFE8E8E8, K + "970b675f.webp", P + "networks/3353-peacock/logos/color/3353_color_1.png", P + "providers/386-peacock-premium" + BD, "387", "US", 8679750),
            new Service("crunchyroll", "Crunchyroll", "cru", 0xFFF47521, K + "129a4038.webp", "https://upload.wikimedia.org/wikipedia/commons/thumb/d/de/Crunchyroll_logo_2024.png/960px-Crunchyroll_logo_2024.png", P + "providers/283-crunchyroll" + BD, "283", "US", 8679744),
            new Service("mgm", "MGM+", null, 0xFFC8A55A, K + "d490e713.webp", P + "networks/6219-mgm/logos/color/6219_color_1.png", P + "providers/34-mgm-plus" + BD, "34", "US", 8679747),
            new Service("disney", "Disney+", "dnp", 0xFF1A3DBE, K + "b8c0d25b.webp", P + "networks/2739-disney/logos/color/2739_color_4.png", P + "providers/337-disney-plus" + BD, "337", "US", 8679746),
            new Service("discovery", "Discovery+", "dpe", 0xFF2175D9, K + "dbed63f2.webp", K + "4676e4ff.webp", K + "8815e841.webp", "520", "US", 8679745),
            new Service("criterion", "Criterion Channel", "crc", 0xFF8A8A8A, A + "criterion-channel/criterion-channel-cover.png", P + "providers/258-criterion-channel/logos/white/258_white_1.png", P + "providers/258-criterion-channel" + BD, "258", "US", 0),
            new Service("tubi", "Tubi", null, 0xFF7A00F5, A + "tubi/tubi-cover.png", Q + "companies/153734-tubi/logos/color/153734_color_1.png", Q + "companies/153734-tubi" + BD, "73", "US", 8679753)
    };

    /** Focus animations from the Nuvio Personalized folders. */
    private static final String[][] GIFS = {
            {"netflix", "https://64.media.tumblr.com/9f93a9fc2e02fb466eb02a7d2247cb6e/a5b604d3737fc559-49/s500x750/403804744183922f6091d48139021fc3da22f786.gifv"},
            {"appletv", "https://64.media.tumblr.com/d717319220a7d26bdaa88e72f6f76889/d9a7a808f588d8f4-63/s500x750/959b0ca57f53153b2ca9adaf414859e45e3734e6.gifv"},
            {"hulu", "https://64.media.tumblr.com/3a7015de27b570e421a0182a66d0a69b/0ec3a7379449b39d-2d/s1280x1920/962f0dcfa0a2458bd6bf923ff47776f2404bf43a.gifv"},
            {"prime", "https://64.media.tumblr.com/5c5ed8bf948c5b3ca63a11544b94c720/55dbe5d6db4b66a5-bf/s500x750/d6686beaab57aa152f1b92a4562f757a1c522a2f.gifv"},
            {"amc", "https://raw.githubusercontent.com/ImKaptain/nuvio-art/main/art/streaming-services/amcplus/amcplus-hover.gif"},
            {"crave", "https://raw.githubusercontent.com/ImKaptain/nuvio-art/main/art/streaming-services/crave/crave-hover.gif"},
            {"criterion", "https://raw.githubusercontent.com/ImKaptain/nuvio-art/main/art/streaming-services/criterion-channel/criterion-channel-hover.gif"},
            {"crunchyroll", "https://cdn.dribbble.com/userupload/42119533/file/original-3c24d9649543096949cc4262451501b7.gif"},
            {"curiosity", "https://raw.githubusercontent.com/ImKaptain/nuvio-art/main/art/streaming-services/curiosity-stream/curiosity-stream-hover.gif"},
            {"discovery", "https://max-streams.gleeze.com/images/Discovery.gif"},
            {"disney", "https://64.media.tumblr.com/ca6dc6d4e8a260c0c5f40c47c7334e57/eb71329b4dc482d5-50/s500x750/e9e82c5bca839be289f3d87863141ebf6d7fca28.gifv"},
            {"hayu", "https://raw.githubusercontent.com/ImKaptain/nuvio-art/main/art/streaming-services/hayu/hayu-hover.gif"},
            {"max", "https://64.media.tumblr.com/cca7a86d443a0bc88536a2ad6ce72aec/b495f88d5c1df470-a2/s640x960/4cb9b614191d17d02c946b4ca59548cd333c06fd.gifv"},
            {"magellan", "https://raw.githubusercontent.com/ImKaptain/nuvio-art/main/art/streaming-services/magellan/magellan-hover.gif"},
            {"mgm", "https://64.media.tumblr.com/9f43b185411e18d71444c9a5a5a79632/5d68ae94e9917470-70/s500x750/0871e87c17045759735ef64c26703c71b585bfb0.gifv"},
            {"mubi", "https://raw.githubusercontent.com/ImKaptain/nuvio-art/main/art/streaming-services/mubi/mubi-hover.gif"},
            {"paramount", "https://raw.githubusercontent.com/ImKaptain/nuvio-art/main/art/streaming-services/paramount-plus/paramount-plus-hover.gif"},
            {"peacock", "https://64.media.tumblr.com/a378bf124ed7d00c8d7430cb8a9ae0cc/9e0da9dbc32641f6-b2/s1280x1920/e8c0ba2f6aadae5b6f532a177926528e94b04d68.gifv"},
            {"pluto", "https://raw.githubusercontent.com/ImKaptain/nuvio-art/main/art/streaming-services/pluto-tv/pluto-tv-hover.gif"},
            {"shudder", "https://raw.githubusercontent.com/luckynumb3rs/stremio-perfect-setup/refs/heads/main/collections/streaming/focused/shudder.gif"},
            {"starz", "https://64.media.tumblr.com/81fe52878aad139bc7b4ff5db7efc766/1e7a62d9e4b13b7a-a2/s500x750/740d23b1e14a96d02282d62190ee61e2a30aed4d.gifv"},
            {"tubi", "https://raw.githubusercontent.com/ImKaptain/nuvio-art/main/art/streaming-services/tubi/tubi-hover.gif"}
    };

    static String gif(String key) {
        for (String[] g : GIFS) if (g[0].equals(key)) return g[1];
        return null;
    }

    private static final Map<String, List<Row>> mem = new HashMap<>();
    private static final Map<String, Long> memAt = new HashMap<>();

    private Streaming() {}

    static Service find(String key) {
        for (Service s : ALL) if (s.key.equals(key)) return s;
        return ALL[0];
    }

    /** Order of the streaming tiles on Home (saved as pipe-separated keys). */
    static List<Service> ordered(Context c) {
        List<Service> out = new ArrayList<>();
        for (String k : U.prefs(c).getString("svc_order", "").split("\\|")) {
            for (Service s : ALL) if (s.key.equals(k) && !out.contains(s)) out.add(s);
        }
        for (Service s : ALL) if (!out.contains(s)) out.add(s);
        return out;
    }

    static Set<String> hidden(Context c) {
        Set<String> out = new HashSet<>();
        for (String k : U.prefs(c).getString("svc_hidden", "").split("\\|")) if (!k.isEmpty()) out.add(k);
        return out;
    }

    static void setHidden(Context c, String key, boolean hide) {
        Set<String> h = hidden(c);
        if (hide) h.add(key); else h.remove(key);
        StringBuilder sb = new StringBuilder();
        for (String k : h) sb.append(sb.length() > 0 ? "|" : "").append(k);
        U.prefs(c).edit().putString("svc_hidden", sb.toString()).apply();
    }

    static void showAll(Context c) { U.prefs(c).edit().putString("svc_hidden", "").apply(); }

    /** Tiles shown on Home, in order. */
    static List<Service> visible(Context c) {
        Set<String> h = hidden(c);
        List<Service> out = new ArrayList<>();
        for (Service s : ordered(c)) if (!h.contains(s.key)) out.add(s);
        return out;
    }

    /** Saves a new order for the tiles shown on Home; hidden tiles keep their place in the full list. */
    static void setVisibleOrder(Context c, List<String> keys) {
        List<Service> all = ordered(c);
        Set<String> h = hidden(c);
        int n = 0;
        StringBuilder sb = new StringBuilder();
        for (Service s : all) {
            String k = h.contains(s.key) || n >= keys.size() ? s.key : keys.get(n++);
            sb.append(sb.length() > 0 ? "|" : "").append(k);
        }
        U.prefs(c).edit().putString("svc_order", sb.toString()).apply();
    }

    static void move(Context c, String key, int dir) {
        List<Service> l = ordered(c);
        Set<String> h = hidden(c);
        int i = -1;
        for (int k = 0; k < l.size(); k++) if (l.get(k).key.equals(key)) i = k;
        if (i < 0) return;
        int j = i + dir;
        while (j >= 0 && j < l.size() && h.contains(l.get(j).key)) j += dir; // hop over hidden tiles
        if (j < 0 || j >= l.size()) return;
        Service t = l.get(i);
        l.set(i, l.get(j));
        l.set(j, t);
        StringBuilder sb = new StringBuilder();
        for (Service s : l) sb.append(sb.length() > 0 ? "|" : "").append(s.key);
        U.prefs(c).edit().putString("svc_order", sb.toString()).apply();
    }

    /**
     * A handful of titles from some of the streaming services' own lists (Top Movies, Top Series, ...), for the
     * screensaver. Blocks on the network the first time; call it off the main thread.
     */
    static List<Nuvio.Item> ambientExtras(Context c, int services, int perService) {
        List<Service> pool = new ArrayList<>();
        for (Service s : visible(c)) if (canBrowse(c, s)) pool.add(s);
        java.util.Collections.shuffle(pool);
        List<Nuvio.Item> out = new ArrayList<>();
        for (int n = 0; n < pool.size() && n < services; n++) {
            Service s = pool.get(n);
            try {
                List<Nuvio.Item> mine = new ArrayList<>();
                for (Row row : loadSync(c, s.key)) {
                    for (Nuvio.Item it : row.items) {
                        if (TextUtils.isEmpty(it.art())) continue;
                        if (TextUtils.isEmpty(it.channel)) it.channel = s.name + " \u00b7 " + row.title;
                        mine.add(it);
                    }
                }
                java.util.Collections.shuffle(mine);
                out.addAll(mine.subList(0, Math.min(perService, mine.size())));
            } catch (Throwable ignored) {}
        }
        return out;
    }

    /** The series' own landscape wallpaper from the public Cinemeta catalogue ("" when there is no exact match). */
    static String seriesWallpaper(String title) throws Exception {
        String url = "https://v3-cinemeta.strem.io/catalog/series/top/search="
                + java.net.URLEncoder.encode(title, "UTF-8").replace("+", "%20") + ".json";
        JSONArray m = new JSONObject(Http.get(url)).optJSONArray("metas");
        if (m == null) return "";
        for (int i = 0; i < m.length(); i++) {
            JSONObject o = m.optJSONObject(i);
            if (o == null || !title.equalsIgnoreCase(o.optString("name"))) continue;
            String bg = o.optString("background", "");
            if (!TextUtils.isEmpty(bg)) return bg;
        }
        return "";
    }

    static String tmdbKey(Context c) {
        String k = U.prefs(c).getString("tmdb_key", "");
        return TextUtils.isEmpty(k) ? Keys.TMDB : k;
    }

    /** Services we can show titles for right now. */
    static boolean canBrowse(Context c, Service s) {
        return s.hasCatalog() || !TextUtils.isEmpty(tmdbKey(c));
    }

    interface Cb { void done(List<Row> rows); }

    /** Top picks for the home row: the first row's titles. */
    static List<Nuvio.Item> cached(String key) {
        List<Row> r = mem.get(key);
        if (r == null) return null;
        // Lead with what's popular on the service (the page itself opens on Latest).
        for (int i = 0; i < r.size(); i++) if ("Popular Movies".equals(r.get(i).title) && i > 0) {
            r = new ArrayList<>(r);
            r.add(0, r.remove(i));
            break;
        }
        List<Nuvio.Item> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Row row : r) for (Nuvio.Item it : row.items) {
            if (out.size() >= 16) return out;
            if (seen.add(it.type + it.imdbId)) out.add(it);
            if (out.size() >= 8 && row == r.get(0)) break;
        }
        return out;
    }

    static void load(Context c, final String key, final Cb cb) {
        List<Row> hit = mem.get(key);
        Long at = memAt.get(key);
        if (hit != null && at != null && System.currentTimeMillis() - at < 6 * 3600_000L && at / SHUFFLE_MS == window()) { cb.done(hit); return; }
        final Context app = c.getApplicationContext();
        U.IO.execute(() -> {
            List<Row> rows = loadSync(app, key);
            U.MAIN.post(() -> cb.done(rows));
        });
    }

    static synchronized List<Row> loadSync(Context c, String key) {
        Service s = find(key);
        String tk = tmdbKey(c);
        List<Row> rows = new ArrayList<>();
        if (!TextUtils.isEmpty(tk) && !TextUtils.isEmpty(s.providers)) {
            // Every service: Latest, Top and Popular, for movies then series. Genres have their own pages.
            rows.addAll(categoryRows(c, s, tk, null));
        } else if (s.hasCatalog()) {
            rows.addAll(catalogRows(c, s));
            rows.addAll(catalogGenreRows(c, s));
        }
        if (!rows.isEmpty()) {
            mem.put(key, rows);
            memAt.put(key, System.currentTimeMillis());
        }
        return rows;
    }

    // ---------------------------------------------------------------- categories and genres

    /** A genre tile on a service page: its TMDB ids for movies and for series (0 when TMDB has no such TV genre). */
    static final class Genre {
        final String name;
        final int movie, tv, icon;
        Genre(String name, int movie, int tv, int icon) { this.name = name; this.movie = movie; this.tv = tv; this.icon = icon; }
    }

    static final Genre[] GENRES = {
            new Genre("Action", 28, 10759, Icon.G_ACTION),
            new Genre("Comedy", 35, 35, Icon.G_COMEDY),
            new Genre("Drama", 18, 18, Icon.G_DRAMA),
            new Genre("Sci-Fi", 878, 10765, Icon.G_SCIFI),
            new Genre("Horror", 27, 0, Icon.G_HORROR),
            new Genre("Thriller", 53, 0, Icon.G_THRILLER),
            new Genre("Crime", 80, 80, Icon.G_CRIME),
            new Genre("Animation", 16, 16, Icon.G_ANIMATION),
            new Genre("Documentary", 99, 99, Icon.G_DOCUMENTARY),
            new Genre("Family", 10751, 10751, Icon.G_FAMILY),
            new Genre("Romance", 10749, 0, Icon.G_ROMANCE),
            new Genre("Mystery", 9648, 9648, Icon.G_MYSTERY),
    };

    static Genre genre(String name) {
        for (Genre g : GENRES) if (g.name.equals(name)) return g;
        return null;
    }

    /**
     * Latest, Top and Popular rows (movies, then series) for a service, optionally only one genre. Top is the
     * best rated with enough votes to mean something; Latest is the newest released.
     */
    private static List<Row> categoryRows(final Context c, final Service s, final String k, final Genre g) {
        final String base = "https://api.themoviedb.org/3";
        final String prov = "&with_watch_providers=" + Uri.encode(s.providers) + "&watch_region=" + s.region;
        final String today = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT).format(new java.util.Date());
        final boolean movies = g == null || g.movie > 0, series = g == null || g.tv > 0;
        final String mg = g == null ? "" : "&with_genres=" + g.movie;
        final String tg = g == null ? "" : "&with_genres=" + g.tv;
        // With a genre there are fewer titles, so the vote floors are lower.
        final int latestVotes = g == null ? 5 : 2, topMovieVotes = g == null ? 300 : 120, topTvVotes = g == null ? 200 : 80;
        final String noDocs = g == null ? "&without_genres=99" : "";
        // Paired by category: Latest movies then Latest series, then Top, then Popular.
        List<java.util.concurrent.Callable<Row>> jobs = new ArrayList<>();
        if (movies)
            jobs.add(() -> new Row("Latest Movies", parseTmdb(cachedGet(c, base + "/discover/movie?api_key=" + k + prov + mg + noDocs
                    + "&sort_by=primary_release_date.desc&primary_release_date.lte=" + today + "&vote_count.gte=" + latestVotes, "results"), "movie", s)));
        if (series)
            jobs.add(() -> new Row("Latest Series", parseTmdb(cachedGet(c, base + "/discover/tv?api_key=" + k + prov + tg
                    + "&sort_by=first_air_date.desc&first_air_date.lte=" + today + "&vote_count.gte=" + latestVotes, "results"), "series", s)));
        if (movies)
            jobs.add(() -> new Row("Top Movies", parseTmdb(cachedGet(c, base + "/discover/movie?api_key=" + k + prov + mg
                    + "&sort_by=vote_average.desc&vote_count.gte=" + topMovieVotes, "results"), "movie", s)));
        if (series)
            jobs.add(() -> new Row("Top Series", parseTmdb(cachedGet(c, base + "/discover/tv?api_key=" + k + prov + tg
                    + "&sort_by=vote_average.desc&vote_count.gte=" + topTvVotes, "results"), "series", s)));
        if (movies)
            jobs.add(() -> new Row("Popular Movies", parseTmdb(cachedGet(c, base + "/discover/movie?api_key=" + k + prov + mg
                    + "&sort_by=popularity.desc&vote_count.gte=25", "results"), "movie", s)));
        if (series)
            jobs.add(() -> new Row("Popular Series", parseTmdb(cachedGet(c, base + "/discover/tv?api_key=" + k + prov + tg
                    + "&sort_by=popularity.desc&vote_count.gte=25", "results"), "series", s)));
        return parallel(jobs);   // keeps the order above
    }

    /** One genre's page for a service: the same Latest, Top and Popular rows, for that genre only. */
    static void loadGenre(Context c, final String key, final String genreName, final Cb cb) {
        final String mk = key + "|" + genreName;
        List<Row> hit = mem.get(mk);
        Long at = memAt.get(mk);
        if (hit != null && at != null && System.currentTimeMillis() - at < 6 * 3600_000L) { cb.done(hit); return; }
        final Context app = c.getApplicationContext();
        U.IO.execute(() -> {
            Service s = find(key);
            Genre g = genre(genreName);
            String tk = tmdbKey(app);
            List<Row> rows = s == null || g == null || TextUtils.isEmpty(tk) || TextUtils.isEmpty(s.providers)
                    ? new ArrayList<>() : categoryRows(app, s, tk, g);
            if (!rows.isEmpty()) { mem.put(mk, rows); memAt.put(mk, System.currentTimeMillis()); }
            U.MAIN.post(() -> cb.done(rows));
        });
    }

    // ---------------------------------------------------------------- genre rows (catalogue fallback)

    private static final String[] CAT_GENRES = {"Action", "Comedy", "Drama", "Horror", "Sci-Fi", "Thriller", "Animation", "Documentary"};

    private static final long SHUFFLE_MS = 12 * 3600_000L;

    private static long window() { return System.currentTimeMillis() / SHUFFLE_MS; }

    /** Mixes the genre rows (and the titles inside them); the mix only changes every 12 hours. */
    private static List<Row> shuffled(List<Row> rows, String key) {
        java.util.Random r = new java.util.Random(window() * 31 + key.hashCode());
        List<Row> out = new ArrayList<>();
        for (Row row : rows) {
            List<Nuvio.Item> items = new ArrayList<>(row.items);
            java.util.Collections.shuffle(items, r);
            out.add(new Row(row.title, items));
        }
        java.util.Collections.shuffle(out, r);
        return out;
    }

    /** Runs the row builders side by side so a service with many rows still opens quickly. */
    private static List<Row> parallel(List<java.util.concurrent.Callable<Row>> jobs) {
        List<Row> out = new ArrayList<>();
        java.util.concurrent.ExecutorService ex = java.util.concurrent.Executors.newFixedThreadPool(6);
        try {
            List<java.util.concurrent.Future<Row>> fs = new ArrayList<>();
            for (java.util.concurrent.Callable<Row> j : jobs) fs.add(ex.submit(j));
            for (java.util.concurrent.Future<Row> f : fs) {
                try { Row r = f.get(); if (r != null && !r.items.isEmpty()) out.add(r); } catch (Throwable ignored) {}
            }
        } finally {
            ex.shutdown();
        }
        return out;
    }

private static List<Row> catalogGenreRows(final Context c, final Service s) {
        List<java.util.concurrent.Callable<Row>> jobs = new ArrayList<>();
        for (final String g : CAT_GENRES) {
            jobs.add(() -> new Row(g + " Movies", parseCatalog(cachedGet(c, CATALOG + "/catalog/movie/" + s.catalog + "/genre=" + Uri.encode(g) + ".json", "metas"), "movie", s)));
            jobs.add(() -> new Row(g + " Series", parseCatalog(cachedGet(c, CATALOG + "/catalog/series/" + s.catalog + "/genre=" + Uri.encode(g) + ".json", "metas"), "series", s)));
        }
        return shuffled(parallel(jobs), s.key);
    }

    // ---------------------------------------------------------------- TMDB (same sources as Nuvio)

    private static void add(List<Row> rows, String title, List<Nuvio.Item> items) {
        if (!items.isEmpty()) rows.add(new Row(title, items));
    }

    private static List<Nuvio.Item> parseTmdb(JSONArray arr, String fallbackType, Service s) {
        List<Nuvio.Item> out = new ArrayList<>();
        if (arr == null) return out;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            String mt = o.optString("media_type", "");
            String type = "tv".equals(mt) ? "series" : "movie".equals(mt) ? "movie" : fallbackType;
            Nuvio.Item it = new Nuvio.Item();
            it.pkg = U.nuvio();
            it.type = type;
            it.tmdbId = o.optLong("id");
            it.imdbId = "tmdb:" + it.tmdbId;
            it.title = o.optString("movie".equals(type) ? "title" : "name", o.optString("title", o.optString("name")));
            if (TextUtils.isEmpty(it.title) || it.tmdbId == 0) continue;
            it.desc = o.optString("overview", null);
            String bd = o.optString("backdrop_path", null), ps = o.optString("poster_path", null);
            if (bd != null && !"null".equals(bd)) { it.poster = "https://image.tmdb.org/t/p/original" + bd; it.thumb = "https://image.tmdb.org/t/p/w780" + bd; }
            if (ps != null && !"null".equals(ps)) it.posterTall = "https://image.tmdb.org/t/p/w342" + ps;
            String date = o.optString("movie".equals(type) ? "release_date" : "first_air_date", "");
            if (date.length() >= 4) it.year = date.substring(0, 4);
            double v = o.optDouble("vote_average", 0);
            if (v > 0) it.rating = "★ " + String.format(java.util.Locale.ROOT, "%.1f", v);
            it.channel = s.name;
            out.add(it);
        }
        return out;
    }

    /** Title logo (the film's own wordmark) from TMDB, if there is one. */
    static String tmdbLogo(Context c, Nuvio.Item it) {
        String k = tmdbKey(c);
        if (TextUtils.isEmpty(k) || it.tmdbId == 0) return null;
        JSONArray logos = cachedGet(c, "https://api.themoviedb.org/3/" + ("series".equals(it.type) ? "tv" : "movie") + "/" + it.tmdbId
                + "/images?api_key=" + k + "&include_image_language=en,null", "logos");
        if (logos == null || logos.length() == 0) return null;
        JSONObject best = logos.optJSONObject(0);
        return best == null ? null : "https://image.tmdb.org/t/p/w500" + best.optString("file_path");
    }

    /**
     * Title artwork for an item that only has a name (Continue watching from Nuvio carries no ids): searches the
     * public Cinemeta catalogue, no key needed, and returns the logo of the best match (null when none).
     */
    static String logoByTitle(Context c, Nuvio.Item it) {
        if (it == null || TextUtils.isEmpty(it.title)) return null;
        boolean series = !TextUtils.isEmpty(it.episode) || "series".equals(it.type);
        String[] types = series ? new String[] {"series", "movie"} : new String[] {"movie", "series"};
        for (String type : types) {
            String l = logoFor(c, it, type);
            if (l != null) return l;
        }
        return null;
    }

    private static Set<String> words(String s) {
        Set<String> out = new HashSet<>();
        if (s == null) return out;
        for (String w : s.toLowerCase(java.util.Locale.ROOT).replaceAll("['\u2019]", "").split("[^a-z0-9]+")) if (!w.isEmpty()) out.add(w);
        return out;
    }

    private static String logoFor(Context c, Nuvio.Item it, String type) {
        JSONObject m = metaFor(c, it, type);
        if (m == null) return null;
        String logo = m.optString("logo", "");
        if (!TextUtils.isEmpty(logo) && !"null".equals(logo)) return logo;
        String imdb = m.optString("imdb_id", m.optString("id", ""));
        return imdb.startsWith("tt") ? "https://images.metahub.space/logo/medium/" + imdb + "/img" : null;
    }

    private static JSONObject metaFor(Context c, Nuvio.Item it, String type) {
        String q = it.title;
        JSONObject m = metaFromSearch(c, it, type, q);
        if (m != null) return m;
        // "Roald Dahl's The Witches" is usually listed as just "The Witches": retry without the "Someone's" lead-in.
        java.util.regex.Matcher mm = java.util.regex.Pattern.compile("^.+?['\u2019]s\\s+(.+)$").matcher(q);
        return mm.matches() ? metaFromSearch(c, it, type, mm.group(1)) : null;
    }

    /** The IMDb id and type ("movie" or "series") of a title found by name, or null. Call off the main thread. */
    static String[] resolveByTitle(Context c, Nuvio.Item it) {
        if (it == null || TextUtils.isEmpty(it.title)) return null;
        boolean series = !TextUtils.isEmpty(it.episode) || "series".equals(it.type);
        String[] types = series ? new String[] {"series", "movie"} : new String[] {"movie", "series"};
        for (String type : types) {
            JSONObject m = metaFor(c, it, type);
            if (m == null) continue;
            String imdb = m.optString("imdb_id", m.optString("id", ""));
            if (imdb.startsWith("tt")) return new String[] {imdb, type};
        }
        return null;
    }

    /**
     * A result only counts when its name and the title agree word for word (one contains all the words of the
     * other). Better no artwork than the wrong artwork. Among agreeing results, an exact name and a matching year win.
     */
    private static JSONObject metaFromSearch(Context c, Nuvio.Item it, String type, String query) {
        try {
            String url = "https://v3-cinemeta.strem.io/catalog/" + type + "/top/search=" + Uri.encode(query) + ".json";
            JSONArray metas = cachedGet(c, url, "metas");
            if (metas == null || metas.length() == 0) return null;
            Set<String> want = words(it.title);
            JSONObject pick = null;
            int best = -1;
            for (int i = 0; i < metas.length() && i < 10; i++) {
                JSONObject o = metas.optJSONObject(i);
                if (o == null) continue;
                Set<String> got = words(o.optString("name"));
                if (got.isEmpty()) continue;
                boolean exact = got.equals(want);
                boolean agrees = exact || want.containsAll(got) || got.containsAll(want);
                if (!agrees) continue;
                boolean year = !TextUtils.isEmpty(it.year) && o.optString("releaseInfo", "").startsWith(it.year);
                int score = (exact ? 4 : 0) + (year ? 2 : 0) + (i == 0 ? 1 : 0);
                if (score > best) { best = score; pick = o; }
            }
            if (pick == null) Log.w(TAG, "no title match for " + it.title + " (" + type + ")");
            return pick;
        } catch (Throwable t) {
            Log.w(TAG, "metaFromSearch", t);
            return null;
        }
    }

    // ---------------------------------------------------------------- public catalog fallback

    private static List<Row> catalogRows(Context c, Service s) {
        List<Row> rows = new ArrayList<>();
        add(rows, "Top Movies", parseCatalog(cachedGet(c, CATALOG + "/catalog/movie/" + s.catalog + ".json", "metas"), "movie", s));
        add(rows, "Top Series", parseCatalog(cachedGet(c, CATALOG + "/catalog/series/" + s.catalog + ".json", "metas"), "series", s));
        return rows;
    }

    private static List<Nuvio.Item> parseCatalog(JSONArray metas, String type, Service s) {
        List<Nuvio.Item> out = new ArrayList<>();
        if (metas == null) return out;
        for (int k = 0; k < metas.length(); k++) {
            JSONObject o = metas.optJSONObject(k);
            if (o == null) continue;
            Nuvio.Item it = new Nuvio.Item();
            it.pkg = U.nuvio();
            it.type = type;
            it.imdbId = o.optString("imdb_id", o.optString("id"));
            it.title = o.optString("name");
            if (TextUtils.isEmpty(it.title) || TextUtils.isEmpty(it.imdbId)) continue;
            it.desc = o.optString("description", null);
            it.posterTall = o.optString("poster", null);
            it.poster = o.optString("background", null);
            it.thumb = it.poster;
            it.logo = o.optString("logo", null);
            it.year = o.optString("releaseInfo", null);
            if (it.year != null && it.year.length() > 9) it.year = it.year.substring(0, 9);
            String r = o.optString("imdbRating", null);
            if (!TextUtils.isEmpty(r)) it.rating = "IMDb " + r;
            JSONArray g = o.optJSONArray("genres");
            if (g != null && g.length() > 0) {
                StringBuilder sb = new StringBuilder();
                for (int x = 0; x < Math.min(2, g.length()); x++) { if (x > 0) sb.append(", "); sb.append(g.optString(x)); }
                it.genre = sb.toString();
            }
            it.channel = s.name;
            out.add(it);
        }
        return out;
    }

    // ---------------------------------------------------------------- open in Nuvio

    static boolean open(Context c, Nuvio.Item it) {
        if (it == null) return false;
        return openLink(c, it.type, it.imdbId, it.tmdbId);
    }

    private static boolean openLink(Context c, String type, String imdb, long tmdb) {
        List<String> links = new ArrayList<>();
        if (tmdb > 0) {
            links.add("nuvio://tmdb/" + type + "/" + tmdb);
        } else {
            links.add("nuvio://" + type + "/" + imdb);
            links.add("nuvio://detail/" + type + "/" + imdb);
        }
        for (String l : links) {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(l)).setPackage(U.nuvio());
            if (Apps.start(c, i)) { Apps.noteLaunch(c, U.nuvio()); return true; }
        }
        return Apps.launchPackage(c, U.nuvio());
    }

    /**
     * Opens the title's information page in Nuvio (never starts playback). Titles that only have a name, such as
     * Continue watching entries, are looked up first; if that fails, Nuvio is simply opened.
     */
    static void openResolved(Context c, Nuvio.Item it, String[] res) {
        String type = res != null ? res[1] : it.type, imdb = res != null ? res[0] : it.imdbId;
        if (TextUtils.isEmpty(type)) type = !TextUtils.isEmpty(it.episode) ? "series" : "movie";
        if (!TextUtils.isEmpty(imdb) || it.tmdbId > 0) openLink(c, type, imdb, it.tmdbId);
        else Apps.launchPackage(c, U.nuvio());
    }

    static void openDetails(final Context c, final Nuvio.Item it) {
        if (it == null) return;
        final Context app = c.getApplicationContext();
        U.IO.execute(() -> {
            String type = it.type, imdb = it.imdbId;
            long tmdb = it.tmdbId;
            if (TextUtils.isEmpty(imdb) && tmdb <= 0) {
                String[] r = resolveByTitle(app, it);
                if (r != null) { imdb = r[0]; type = r[1]; }
            }
            if (TextUtils.isEmpty(type)) type = !TextUtils.isEmpty(it.episode) ? "series" : "movie";
            final String ft = type, fi = imdb;
            final long fm = tmdb;
            U.MAIN.post(() -> {
                if (!TextUtils.isEmpty(fi) || fm > 0) openLink(app, ft, fi, fm);
                else Apps.launchPackage(app, U.nuvio());
            });
        });
    }

    // ---------------------------------------------------------------- http + disk cache

    private static JSONArray cachedGet(Context c, String url, String arrayKey) {
        try {
            File f = Img.file(c, "json:" + url);
            String body = null;
            boolean fresh = f.exists() && System.currentTimeMillis() - f.lastModified() < 6 * 3600_000L;
            if (!fresh) {
                body = get(url);
                if (body != null) try (FileOutputStream o = new FileOutputStream(f)) { o.write(body.getBytes("UTF-8")); }
            }
            if (body == null && f.exists()) body = read(f);
            if (body == null) return null;
            return new JSONObject(body).optJSONArray(arrayKey);
        } catch (Throwable t) {
            Log.w(TAG, "get " + url.replaceAll("api_key=[^&]+", "api_key=…"), t);
            return null;
        }
    }

    private static String get(String url) {
        HttpURLConnection con = null;
        try {
            con = (HttpURLConnection) new URL(url).openConnection();
            con.setConnectTimeout(8000);
            con.setReadTimeout(15000);
            con.setRequestProperty("User-Agent", "BeaconHome/1.2 (Android TV)");
            if (con.getResponseCode() != 200) return null;
            try (InputStream in = con.getInputStream()) {
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                byte[] b = new byte[16384];
                int n;
                while ((n = in.read(b)) > 0) bo.write(b, 0, n);
                return bo.toString("UTF-8");
            }
        } catch (Throwable t) {
            return null;
        } finally {
            if (con != null) con.disconnect();
        }
    }

    private static String read(File f) throws Exception {
        try (InputStream in = new FileInputStream(f)) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] b = new byte[16384];
            int n;
            while ((n = in.read(b)) > 0) bo.write(b, 0, n);
            return bo.toString("UTF-8");
        }
    }
}
