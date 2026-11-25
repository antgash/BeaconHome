package com.horizon.home;

import android.Manifest;
import android.app.Activity;
import android.graphics.Color;
import android.widget.ScrollView;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.AnimatedImageDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.EditText;
import android.widget.Toast;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * beacon, Veil-style: artwork (or a living video scene) fills the wall, a calm hero tells you
 * what's next, and three pill rows hold Continue watching, Nuvio's streaming services and your apps.
 */
public class HomeActivity extends Activity implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static float EDGE = 64;
    /** Backdrops sit under a dark shade and drift slowly: 1280 px wide looks the same and uses half the memory of 1920. */
    static final int WALL_W = 1280;

    private SharedPreferences prefs;
    private FrameLayout root, ui;
    private String mode;
    private ScenePlayer player;
    private HeroWall wall;
    private Shade shade;

    // top bar
    private TextView clock, weatherText, memText;
    private FrameLayout memBar;
    private View memFill;
    private final Runnable memTick = new Runnable() {
        @Override public void run() {
            updateMemory();
            if (started) U.MAIN.postDelayed(this, 15000);
        }
    };
    private Icon weatherIcon, wifiIcon;

    // hero
    private LinearLayout hero;
    private TextView heroLabel, heroTitle, heroLine, heroRemaining;
    private ImageView heroLogo;
    private LinearLayout heroProgRow;
    private View heroProgFill;
    private Object heroKey;
    private int heroToken;

    // rows
    private LinearLayout cwBlock, cwRow, svcRow, appsRow;
    private LinearLayout volumeRow;
    private TextView cwCount, cwTonight;
    private int defaultFocusId = View.NO_ID;
    private boolean firstFocusDone;

    // data
    private List<Apps.App> apps = new ArrayList<>();
    private Nuvio.Result nuvio;
    private long nuvioAt;

    /**
     * Nuvio rewrites its home-screen rows in the TV provider when you switch profile. Watching that
     * provider lets Continue Watching follow the change at once, with no need to reopen Nuvio first.
     */
    private final Runnable nuvioReload = this::loadNuvio;
    private final android.database.ContentObserver nuvioObserver = new android.database.ContentObserver(U.MAIN) {
        @Override public void onChange(boolean selfChange) {
            nuvioAt = 0; // stale: reload now if Home is showing, otherwise as soon as it comes back
            if (!started) return;
            U.MAIN.removeCallbacks(nuvioReload);
            U.MAIN.postDelayed(nuvioReload, 1200); // Nuvio writes the rows in a burst; wait for it to finish
        }
    };
    private final List<Nuvio.Item> cwItems = new ArrayList<>();
    private final java.util.Set<String> cwKeys = new java.util.HashSet<>();
    private boolean cwLoadedOnce;
    private TextView cwHead;

    // state
    private boolean ambient, started, coldStart = true, swallowUp, receiversOn;
    private long lastInput = System.currentTimeMillis();
    private ConnectivityManager.NetworkCallback netCb;
    private View gifHost;
    private AnimatedImageDrawable gifPlaying;

    private final Runnable idleTick = new Runnable() {
        @Override public void run() {
            if (!started) return;
            int mins = prefs.getInt(U.P_AMBIENT_MIN, 3);
            if (!ambient && mins > 0 && System.currentTimeMillis() - lastInput > mins * 60000L) enterAmbient();
            U.MAIN.postDelayed(this, 10000);
        }
    };

    /**
     * Safety net next to the provider observer: while Home is on screen, look for new Continue watching data
     * every 20 seconds. It is one small provider query; the row is only redrawn when something actually changed.
     */
    private final Runnable cwPoll = new Runnable() {
        @Override public void run() {
            if (!started) return;
            if (!ambient && !menuOpen && System.currentTimeMillis() - nuvioAt > 15000) loadNuvio(true);
            U.MAIN.postDelayed(this, 20000);
        }
    };
    private boolean observing;

    private void observeNuvio() {
        if (observing) return;
        try {
            getContentResolver().registerContentObserver(android.media.tv.TvContract.WatchNextPrograms.CONTENT_URI, true, nuvioObserver);
            getContentResolver().registerContentObserver(android.media.tv.TvContract.PreviewPrograms.CONTENT_URI, true, nuvioObserver);
            observing = true;
        } catch (Throwable ignored) {} // permission not granted yet: retried on the next resume
    }

    private String cwSig = "";

    private final Runnable resumeWall = () -> { if (started && !ambient && wall != null) wall.resume(); };

    private final BroadcastReceiver timeRx = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { updateClock(); }
    };
    private final BroadcastReceiver pkgRx = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { loadApps(); }
    };

    // ================================================================== lifecycle

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        U.init(this);
        V.init(this);
        prefs = U.prefs(this);
        applyIntentExtras(getIntent());
        prefs.registerOnSharedPreferenceChangeListener(this);
        root = new FrameLayout(this);
        root.setBackgroundColor(V.BG);
        setContentView(root);
        Sfx.attach(this);
        getWindow().setBackgroundDrawable(null); // root paints the background; skip one full-screen fill per frame
        V.unclipAll(root);
        askPermissions();
        build();
        loadApps();
        observeNuvio();
        Img.trim(this);
    }

    @Override protected void onStart() {
        super.onStart();
        started = true;
        lastInput = System.currentTimeMillis();
        registerReceivers();
        updateClock();
        updateNetwork();
        loadWeather();
        startBackground();
        U.MAIN.removeCallbacks(idleTick);
        U.MAIN.postDelayed(idleTick, 10000);
        U.MAIN.removeCallbacks(cwPoll);
        U.MAIN.postDelayed(cwPoll, 20000);
    }

    @Override protected void onResume() {
        super.onResume();
        lastInput = System.currentTimeMillis();
        observeNuvio();
        if (System.currentTimeMillis() - nuvioAt > 5000) loadNuvio();
        refreshUpdatePill();
        if (Updater.due(this)) U.MAIN.postDelayed(() -> Updater.check(this, (info, ok) -> refreshUpdatePill()), 6000);
    }

    /** Shows the "Update available" pill while a newer build is known, hides it once that build is installed. */
    private void refreshUpdatePill() {
        if (updatePill == null) return;
        boolean on = Updater.pending(this);
        if (!on && updatePill.hasFocus()) menuPill.requestFocus();
        updatePill.setVisibility(on ? View.VISIBLE : View.GONE);
    }

    @Override protected void onStop() {
        super.onStop();
        started = false;
        if (player != null) player.stop();
        if (wall != null) wall.pause();
        stopGif();
        U.MAIN.removeCallbacks(idleTick);
        U.MAIN.removeCallbacks(cwPoll);
        U.MAIN.removeCallbacks(memTick);
        U.MAIN.removeCallbacks(resumeWall);
        unregisterReceivers();
        if (ambient) exitAmbient(false);
    }

    @Override public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level >= TRIM_MEMORY_UI_HIDDEN) {
            // Home is out of sight (a film is playing): hand back cached pictures and animations.
            Img.clear();
            stopGif();
        }
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        try { getContentResolver().unregisterContentObserver(nuvioObserver); } catch (Throwable ignored) {}
        U.MAIN.removeCallbacks(nuvioReload);
        prefs.unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        applyIntentExtras(intent);
        // `started` is false while another app covers Home, so true here = Home pressed on Home.
        endRearrange(false);
        boolean alreadyHome = started;
        if (ambient) exitAmbient(true);
        if (alreadyHome) focusFirstApp(); else focusDefault();
    }

    @Override public void onBackPressed() {
        if (ambient) { exitAmbient(true); return; }
        if (menuOpen) { closeMenu(); return; }
        View f = getCurrentFocus();
        if (f != null && f.getId() != defaultFocusId) focusDefault(); else enterAmbient();
    }

    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        lastInput = System.currentTimeMillis();
        if (ambient) {
            if (e.getAction() == KeyEvent.ACTION_DOWN) {
                int k = e.getKeyCode();
                if (k == KeyEvent.KEYCODE_VOLUME_UP || k == KeyEvent.KEYCODE_VOLUME_DOWN || k == KeyEvent.KEYCODE_VOLUME_MUTE) return super.dispatchKeyEvent(e);
                boolean select = k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER || k == KeyEvent.KEYCODE_NUMPAD_ENTER;
                AmbientView shown = select ? ambientView : null;
                if (shown != null) shown.openCurrent();
                exitAmbient(true);
                swallowUp = true;
            }
            return true;
        }
        if (swallowUp && e.getAction() == KeyEvent.ACTION_UP) { swallowUp = false; return true; }
        Sfx.key(this, e);
        if (rearrangeView != null && handleRearrangeKey(e)) return true;
        if (!menuOpen && e.getAction() == KeyEvent.ACTION_DOWN && (e.getKeyCode() == KeyEvent.KEYCODE_DPAD_UP || e.getKeyCode() == KeyEvent.KEYCODE_DPAD_DOWN)
                && moveBetweenRows(e.getKeyCode() == KeyEvent.KEYCODE_DPAD_DOWN ? 1 : -1)) return true;
        if (wall != null && e.getAction() == KeyEvent.ACTION_DOWN) {
            wall.pause();
            U.MAIN.removeCallbacks(resumeWall);
            U.MAIN.postDelayed(resumeWall, 1500);
        }
        return super.dispatchKeyEvent(e);
    }

    @Override public void onSharedPreferenceChanged(SharedPreferences p, String key) {
        if (key == null || key.startsWith("last_") || key.startsWith("count_") || key.equals("svc")) return;
        if (U.P_MODE.equals(key) || "edge".equals(key) || "theme".equals(key) || "art_colours".equals(key) || "focus_glow".equals(key)) {
            V.loadTheme(this);
            if (player != null) player.stop();
            build();
            if (started) startBackground();
            fillRows();
        } else if (U.P_SCENE_SECS.equals(key) || U.P_SCENES_OFF.equals(key) || U.P_LIFT.equals(key) || U.P_SOUND.equals(key)) {
            if (player != null) { player.stop(); configurePlayer(); if (started) player.start(false); }
        } else if (U.P_NAME.equals(key) || U.P_GREET.equals(key)) {
            if (heroKey == null) showGreeting();
        } else if (U.P_HERO_FILL.equals(key) || "tmdb_key".equals(key)) {
            loadNuvio();
            fillRows();
        } else if (key.startsWith("cat_") || key.startsWith("hide_") || key.equals("pins")) {
            loadApps();
        }
    }

    private void applyIntentExtras(Intent i) {
        if (i == null) return;
        String m = i.getStringExtra("mode");
        if (U.MODE_NUVIO.equals(m) || U.MODE_SCENES.equals(m)) U.prefs(this).edit().putString(U.P_MODE, m).apply();
        String os = i.getStringExtra("open_service");
        if (os != null) startActivity(new Intent(this, ServiceActivity.class).putExtra("service", os));
        String tk = i.getStringExtra("tmdb");
        if (tk != null) U.prefs(this).edit().putString("tmdb_key", tk.trim()).apply();
        String nm = i.getStringExtra("name");
        if (nm != null) U.prefs(this).edit().putString(U.P_NAME, nm.trim()).apply();
        String lt = i.getStringExtra("livetv");   // your Live TV addon link (without /manifest.json)
        if (lt != null) U.prefs(this).edit().putString(GuideActivity.P_SRC, lt.trim().replaceAll("/manifest\\.json$", "")).apply();
    }

    private void askPermissions() {
        List<String> need = new ArrayList<>();
        for (String p : new String[] {Manifest.permission.READ_EXTERNAL_STORAGE,
                "com.android.providers.tv.permission.READ_EPG_DATA", "android.permission.READ_TV_LISTINGS"}) {
            if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) need.add(p);
        }
        if (!need.isEmpty()) try { requestPermissions(need.toArray(new String[0]), 7); } catch (Throwable ignored) {}
    }

    @Override public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (player != null) { player.stop(); configurePlayer(); if (started) player.start(true); }
        loadNuvio();
    }

    // ================================================================== build

    private boolean artMode() { return U.MODE_NUVIO.equals(mode); }

    private void build() {
        mode = U.mode(this);
        EDGE = V.edge(this);
        wallArt = null; memPanel = null; scrim = null; menuPanel = null; menuHost = null; menuOpen = false;
        root.removeAllViews();
        root.setBackgroundColor(V.BG);
        player = null;
        wall = null;
        heroKey = null;
        ambient = false;
        V.artAccent = null;

        if (artMode()) {
            wall = new HeroWall(this);
            wall.setDriftMs(24000);
            root.addView(wall, new FrameLayout.LayoutParams(U.MATCH, U.MATCH));
        } else {
            player = new ScenePlayer(this);
            root.addView(player, new FrameLayout.LayoutParams(U.MATCH, U.MATCH));
            configurePlayer();
            player.setListener(new ScenePlayer.Listener() {
                @Override public void onScene(Scene s, int index, int count) {
                    if (prefs.getBoolean("art_colours", true)) setAccent(s.accent);
                    if (heroKey == null) showGreeting();
                }
                @Override public void onProgress(float f) {}
            });
        }
        shade = new Shade(this);
        root.addView(shade, new FrameLayout.LayoutParams(U.MATCH, U.MATCH));
        shade.set(V.BG, null);

        ui = new FrameLayout(this);
        root.addView(ui, new FrameLayout.LayoutParams(U.MATCH, U.MATCH));
        buildTopBar();

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setClipChildren(false);
        col.setClipToPadding(false);
        ui.addView(col, U.flp(U.MATCH, U.WRAP, Gravity.BOTTOM, 0, 0, 0, 0));
        col.setPadding(0, 0, 0, V.dp(22 + (EDGE - 48) / 2));
        buildHero(col);
        col.addView(space(24));
        buildContinue(col);
        buildServices(col);
        col.addView(space(14));
        buildApps(col);
        showGreeting();
        updateClock();
        updateNetwork();
        loadWeather();
        if (reopenMenu) { reopenMenu = false; U.MAIN.post(this::showMenu); }
    }

    private View space(float dp) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, V.dp(dp)));
        return v;
    }

    // ------------------------------------------------------------------ top bar

    private void buildTopBar() {
        FrameLayout bar = new FrameLayout(this);
        bar.setClipChildren(false);
        ui.addView(bar, U.flp(U.MATCH, V.dp(34), Gravity.TOP, 0, 0, 0, 0));
        topBar = bar;
        ((FrameLayout.LayoutParams) bar.getLayoutParams()).topMargin = V.dp(26 + (EDGE - 48) / 2);
        bar.setPadding(V.dp(EDGE), 0, V.dp(EDGE), 0);

        // Left: beacon menu pill (customise Home)
        LinearLayout menu = V.pill(this, 34, 12, 12);
        Icon mi = new Icon(this, Icon.BEACON);
        menu.addView(mi, new LinearLayout.LayoutParams(V.dp(14), V.dp(14)));
        TextView mt = V.text(this, "beacon", 15, 800, V.TEXT);
        mt.setLetterSpacing(-0.02f);
        menu.addView(mt, lp(U.WRAP, U.WRAP, 7, 0, 6, 0));
        Icon chev = new Icon(this, Icon.CHEVRON_DOWN);
        menu.addView(chev, new LinearLayout.LayoutParams(V.dp(11), V.dp(11)));
        V.tint(menu, V.TEXT);
        menu.setContentDescription("Customise Home");
        menu.setOnClickListener(v -> { if (menuOpen) closeMenu(); else showMenu(); });
        menuPill = menu;
        LinearLayout left = U.row(this);
        left.setClipChildren(false);
        left.addView(menu, new LinearLayout.LayoutParams(U.WRAP, V.dp(34)));

        // Next to the menu, only while a newer beacon build exists: Select opens Settings at the update options.
        LinearLayout up = V.pill(this, 34, 12, 14);
        View dot = new View(this);
        dot.setBackground(V.shape(V.accent(), 4, 0));
        up.addView(dot, new LinearLayout.LayoutParams(V.dp(8), V.dp(8)));
        TextView ut = V.text(this, "Update available", 13, 700, V.TEXT);
        up.addView(ut, lp(U.WRAP, U.WRAP, 8, 0, 0, 0));
        V.tint(up, V.TEXT);
        up.setContentDescription("Update available");
        up.setVisibility(View.GONE);
        up.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class).putExtra(SettingsActivity.EXTRA_UPDATES, true)));
        updatePill = up;
        left.addView(up, lp(U.WRAP, V.dp(34), 10, 0, 0, 0));
        bar.addView(left, U.flp(U.WRAP, U.WRAP, Gravity.START | Gravity.CENTER_VERTICAL));

        // Centre: clock + weather
        LinearLayout mid = U.row(this);
        clock = V.text(this, "", 17, 400, V.TEXT);
        clock.setLetterSpacing(0.02f);
        mid.addView(clock);
        weatherIcon = new Icon(this, Icon.SUN);
        weatherIcon.setColor(U.alpha(V.TEXT, 0.9f));
        weatherIcon.setVisibility(View.GONE);
        mid.addView(weatherIcon, lp(V.dp(14), V.dp(14), 12, 0, 4, 0));
        weatherText = V.text(this, "", 14, 400, U.alpha(V.TEXT, 0.9f));
        mid.addView(weatherText);
        bar.addView(mid, U.flp(U.WRAP, U.WRAP, Gravity.CENTER));

        // Right: HDMI, Wi-Fi, projector settings
        LinearLayout right = U.row(this);
        right.setClipChildren(false);
        right.addView(roundButton(Icon.HDMI, "HDMI", v -> openHdmi()), lp(V.dp(30), V.dp(30), 9, 0, 0, 0));
        wifiIcon = new Icon(this, Icon.WIFI);
        View wifi = V.iconButton(this, wifiIcon, "Wi-Fi");
        wifi.setOnClickListener(v -> openSetting(Settings.ACTION_WIFI_SETTINGS));
        right.addView(wifi, lp(V.dp(30), V.dp(30), 9, 0, 0, 0));
        right.addView(roundButton(Icon.GEAR, "Projector settings", v -> openProjectorSettings()), lp(V.dp(30), V.dp(30), 9, 0, 0, 0));
        bar.addView(right, U.flp(U.WRAP, U.WRAP, Gravity.END | Gravity.CENTER_VERTICAL));
    }


    // ------------------------------------------------------------------ beacon menu (Veil-style dropdown)

    private LinearLayout menuPanel;
    private View menuPill, updatePill;
    private boolean menuOpen, reopenMenu;

    private void showMenu() {
        if (menuOpen || ambient) return;
        menuOpen = true;
        if (scrim == null) {
            scrim = new View(this);
            scrim.setBackgroundColor(0x8C000000);
            scrim.setAlpha(0f);
            ui.addView(scrim, new FrameLayout.LayoutParams(U.MATCH, U.MATCH));
        }
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.setFillViewport(false);
        menuPanel = U.col(this);
        menuPanel.setPadding(V.dp(16), V.dp(16), V.dp(16), V.dp(16));
        sv.addView(menuPanel, new FrameLayout.LayoutParams(U.MATCH, U.WRAP));
        sv.setBackground(V.shape(U.alpha(U.mix(V.BG, 0xFFFFFFFF, 0.10f), 0.98f), 20, 0x40FFFFFF));
        sv.setClipToOutline(true);
        int top = V.dp(26 + (EDGE - 48) / 2 + 34 + 8);
        FrameLayout.LayoutParams lp = U.flp(V.dp(330), U.WRAP, Gravity.TOP | Gravity.START);
        lp.topMargin = top;
        lp.leftMargin = V.dp(EDGE);
        int maxH = getResources().getDisplayMetrics().heightPixels - top - V.dp(24);
        ui.addView(sv, lp);
        menuHost = sv;
        sv.setAlpha(0f);

        menuPanel.addView(V.text(this, "beacon", 24, 800, V.TEXT));
        menuPanel.addView(V.dim(V.caps(this, "Home & screensaver", 9, V.TEXT), 0.55f), lp(U.WRAP, U.WRAP, 0, 2, 0, 14));

        View first = menuButton(Icon.PLAY, "Start screensaver", "Preview it now · press any key to leave", () -> {
            closeMenu();
            U.MAIN.postDelayed(this::enterAmbient, 350);
        });
        menuPanel.addView(first, lp(U.MATCH, V.dp(48), 0, 0, 0, 14));

        menuPanel.addView(menuLabel("Screensaver starts after"));
        final int[] mins = {0, 1, 3, 5, 10, 20};
        String[] names = {"Off", "1m", "3m", "5m", "10m", "20m"};
        int cur = prefs.getInt(U.P_AMBIENT_MIN, 3), sel = 2;
        for (int i = 0; i < mins.length; i++) if (mins[i] == cur) sel = i;
        menuPanel.addView(chipRow(names, sel, i -> {
            prefs.edit().putInt(U.P_AMBIENT_MIN, mins[i]).apply();
            lastInput = System.currentTimeMillis();
        }), lp(U.MATCH, V.dp(30), 0, 0, 0, 14));

        menuPanel.addView(menuLabel("Artwork changes every"));
        final int[] secs = {8, 12, 20, 30, 60};
        String[] sNames = {"8s", "12s", "20s", "30s", "1m"};
        int curS = prefs.getInt(U.P_HERO_SECS, 12), selS = 1;
        for (int i = 0; i < secs.length; i++) if (secs[i] == curS) selS = i;
        menuPanel.addView(chipRow(sNames, selS, i -> prefs.edit().putInt(U.P_HERO_SECS, secs[i]).apply()),
                lp(U.MATCH, V.dp(30), 0, 0, 0, 14));

        menuPanel.addView(menuLabel("Theme"));
        menuPanel.addView(themeRow(), lp(U.MATCH, V.dp(34), 0, 0, 0, 8));
        menuPanel.addView(toggleButton(Icon.EYE, "Colours from artwork", "art_colours", true), lp(U.MATCH, V.dp(48), 0, 0, 0, 8));

        menuPanel.addView(focusStyleToggle(), lp(U.MATCH, V.dp(48), 0, 0, 0, 8));
        menuPanel.addView(cwStyleToggle(), lp(U.MATCH, V.dp(48), 0, 0, 0, 8));
        menuPanel.addView(toggleButton(Icon.EYE, "Title artwork", "title_logo", true), lp(U.MATCH, V.dp(48), 0, 0, 0, 14));


        menuPanel.addView(menuLabel("Sound"));
        menuPanel.addView(soundToggle(), lp(U.MATCH, V.dp(48), 0, 0, 0, 8));
        volumeRow = buildVolumeRow();
        volumeRow.setAlpha(Sfx.isEnabled() ? 1f : 0.45f);
        menuPanel.addView(volumeRow, lp(U.MATCH, V.dp(48), 0, 0, 0, 14));

        menuPanel.addView(menuLabel("Weather"));
        menuPanel.addView(toggleButton(Icon.SUN, "Show weather by the clock", "weather", true), lp(U.MATCH, V.dp(48), 0, 0, 0, 14));

        String city = prefs.getString("wx_city", "");
        menuPanel.addView(menuButton(Icon.SEARCH, "Weather location", TextUtils.isEmpty(city) ? "Automatic \u00b7 search for a city" : city + " \u00b7 search for another", () -> {
            closeMenu();
            U.MAIN.postDelayed(this::editWeatherCity, 250);
        }), lp(U.MATCH, V.dp(48), 0, 0, 0, 14));

        menuPanel.addView(menuLabel("System"));
        menuPanel.addView(menuButton(Icon.MOON, "Use as Android TV screensaver", "Opens the system screensaver picker", this::openDreamSettings), lp(U.MATCH, V.dp(48), 0, 0, 0, 8));
        menuPanel.addView(menuButton(Icon.GEAR, "More settings", "Display, general, and updates", () -> {
            closeMenu();
            startActivity(new Intent(this, SettingsActivity.class));
        }), lp(U.MATCH, V.dp(48), 0, 0, 0, 14));
        menuPanel.addView(menuButton(Icon.LEFT, "Back to close", null, this::closeMenu), lp(U.MATCH, V.dp(40), 0, 0, 0, 0));

        // nothing behind the menu may take focus
        for (int i = 0; i < ui.getChildCount(); i++) {
            View c = ui.getChildAt(i);
            if (c != sv && c != scrim && c instanceof ViewGroup) ((ViewGroup) c).setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        }
        scrim.bringToFront();
        sv.bringToFront();
        scrim.animate().cancel();
        scrim.animate().alpha(1f).setDuration(200).start();
        sv.setTranslationY(-V.dpf(12));
        sv.animate().alpha(1f).translationY(0).setDuration(200).withLayer().start();
        sv.post(() -> {
            if (sv.getHeight() > maxH) sv.getLayoutParams().height = maxH;
            sv.requestLayout();
            first.requestFocus();
        });
    }

    private View menuHost;

    private void closeMenu() {
        if (!menuOpen) return;
        menuOpen = false;
        final View host = menuHost;
        menuHost = null;
        menuPanel = null;
        for (int i = 0; i < ui.getChildCount(); i++) {
            View c = ui.getChildAt(i);
            if (c instanceof ViewGroup) ((ViewGroup) c).setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        }
        if (scrim != null) scrim.animate().alpha(0f).setDuration(160).start();
        if (host != null) host.animate().alpha(0f).translationY(-V.dpf(8)).setDuration(160).withEndAction(() -> ui.removeView(host)).start();
        if (menuPill != null) menuPill.requestFocus();
    }

    private void editWeatherCity() {
        final EditText et = new EditText(this);
        et.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        et.setSingleLine(true);
        et.setHint("City name, e.g. Austin");
        et.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        new android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Weather location")
                .setView(et)
                .setPositiveButton("Search", (d, w) -> searchCity(et.getText().toString().trim()))
                .setNeutralButton("Automatic", (d, w) -> {
                    prefs.edit().remove("wx_lat").remove("wx_lon").remove("wx_city").putBoolean("weather", true).apply();
                    loadWeather();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void searchCity(final String q) {
        if (q.isEmpty()) return;
        U.IO.execute(() -> {
            try {
                JSONObject r = new JSONObject(Http.get("https://geocoding-api.open-meteo.com/v1/search?count=6&language=en&format=json&name="
                        + java.net.URLEncoder.encode(q, "UTF-8")));
                final org.json.JSONArray res = r.optJSONArray("results");
                U.MAIN.post(() -> {
                    if (res == null || res.length() == 0) { Toast.makeText(this, "No place found for \u201c" + q + "\u201d", Toast.LENGTH_LONG).show(); return; }
                    ActionSheet sheet = new ActionSheet(this, "Choose your location");
                    for (int i = 0; i < res.length(); i++) {
                        final JSONObject o = res.optJSONObject(i);
                        if (o == null) continue;
                        StringBuilder sb = new StringBuilder(o.optString("name"));
                        String reg = o.optString("admin1"), ctry = o.optString("country_code");
                        if (!reg.isEmpty()) sb.append(", ").append(reg);
                        if (!ctry.isEmpty()) sb.append(", ").append(ctry);
                        final String label = sb.toString();
                        sheet.add(label, Icon.SEARCH, () -> {
                            prefs.edit().putLong("wx_lat", Double.doubleToLongBits(o.optDouble("latitude")))
                                    .putLong("wx_lon", Double.doubleToLongBits(o.optDouble("longitude")))
                                    .putString("wx_city", o.optString("name")).putBoolean("weather", true).apply();
                            loadWeather();
                            Toast.makeText(this, "Weather set to " + label, Toast.LENGTH_SHORT).show();
                        });
                    }
                    sheet.show();
                });
            } catch (Throwable t) {
                U.MAIN.post(() -> Toast.makeText(this, "Couldn't search right now. Check the connection.", Toast.LENGTH_LONG).show());
            }
        });
    }

    private void openDreamSettings() {
        String[] tries = {"android.settings.DREAM_SETTINGS", "android.settings.DISPLAY_SETTINGS", Settings.ACTION_SETTINGS};
        for (String a : tries) {
            try { closeMenu(); startActivity(new Intent(a).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return; } catch (Throwable ignored) {}
        }
        Toast.makeText(this, "Open Settings › Device Preferences › Screen saver and pick beacon", Toast.LENGTH_LONG).show();
    }

    private TextView menuLabel(String t) {
        TextView v = V.dim(V.caps(this, t, 8, V.TEXT), 0.55f);
        v.setLetterSpacing(0.12f);
        v.setPadding(0, 0, 0, V.dp(6));
        return v;
    }

    private LinearLayout menuButton(int icon, String title, String sub, Runnable r) {
        final LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(V.dp(9), 0, V.dp(12), 0);
        row.setFocusable(true);
        row.setFocusableInTouchMode(true);
        row.setBackground(V.shape(V.GLASS, 14, V.LINE));
        FrameLayout tile = new FrameLayout(this);
        tile.setBackground(V.shape(0x22FFFFFF, 9, 0));
        Icon ic = new Icon(this, icon);
        tile.addView(ic, new FrameLayout.LayoutParams(V.dp(15), V.dp(15), Gravity.CENTER));
        row.addView(tile, new LinearLayout.LayoutParams(V.dp(28), V.dp(28)));
        LinearLayout tx = U.col(this);
        tx.addView(V.text(this, title, 12, 700, V.TEXT));
        if (sub != null) tx.addView(V.dim(V.text(this, sub, 9, 500, V.TEXT), 0.6f));
        row.addView(tx, lp(U.WRAP, U.WRAP, 10, 0, 0, 0));
        V.tint(row, V.TEXT);
        row.setOnFocusChangeListener((v, has) -> {
            row.setBackground(has ? V.shape(V.TEXT, 14, 0) : V.shape(V.GLASS, 14, V.LINE));
            tile.setBackground(V.shape(has ? 0x22000000 : 0x22FFFFFF, 9, 0));
            V.tint(row, has ? V.INK : V.TEXT);
        });
        row.setOnClickListener(v -> r.run());
        return row;
    }

    /** Navigation sounds on/off. Plays its own up/down chime, so the automatic select pop is skipped. */
    private View soundToggle() {
        final LinearLayout row = menuButton(Icon.SPEAKER, "Navigation sounds", null, () -> {});
        row.setTag(Sfx.QUIET);
        final TextView state = V.text(this, "", 10, 800, V.TEXT);
        final Runnable paint = () -> state.setText(Sfx.isEnabled() ? "ON" : "OFF");
        paint.run();
        View sp = new View(this);
        row.addView(sp, new LinearLayout.LayoutParams(0, 1, 1f));
        row.addView(state);
        V.tint(row, V.TEXT);
        row.setOnClickListener(v -> {
            boolean on = !Sfx.isEnabled();
            if (on) { Sfx.setEnabled(this, true); Sfx.on(); }
            else { Sfx.off(); Sfx.setEnabled(this, false); }
            paint.run();
            if (volumeRow != null) volumeRow.setAlpha(on ? 1f : 0.45f);
        });
        return row;
    }

    /** Volume as 8 rising bars: Left/Right change it by one step and play a tick at the new level. */
    private LinearLayout buildVolumeRow() {
        final LinearLayout row = menuButton(Icon.SPEAKER, "Volume", null, () -> Sfx.preview());
        row.setTag(Sfx.QUIET);
        View sp = new View(this);
        row.addView(sp, new LinearLayout.LayoutParams(0, 1, 1f));
        final LinearLayout bars = U.row(this);
        bars.setGravity(Gravity.CENTER_VERTICAL);
        final View[] seg = new View[Sfx.LEVELS];
        for (int i = 0; i < Sfx.LEVELS; i++) {
            View b = new View(this);
            seg[i] = b;
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(V.dp(5), V.dp(6 + i * 2));
            bp.leftMargin = i == 0 ? 0 : V.dp(3);
            bars.addView(b, bp);
        }
        row.addView(bars);
        final Runnable paint = () -> {
            int base = row.hasFocus() ? V.INK : V.TEXT;
            for (int i = 0; i < seg.length; i++) seg[i].setBackground(V.shape(i < Sfx.level() ? base : U.alpha(base, 0.25f), 2, 0));
        };
        paint.run();
        final View.OnFocusChangeListener baseFocus = row.getOnFocusChangeListener();
        row.setOnFocusChangeListener((v, has) -> { if (baseFocus != null) baseFocus.onFocusChange(v, has); paint.run(); });
        row.setOnKeyListener((v, code, ev) -> {
            if (ev.getAction() != KeyEvent.ACTION_DOWN) return false;
            int d = code == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : code == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : 0;
            if (d == 0) return false;
            int n = Math.max(1, Math.min(Sfx.LEVELS, Sfx.level() + d));
            if (n != Sfx.level()) { Sfx.setLevel(this, n); paint.run(); Sfx.preview(); }
            return true;
        });
        return row;
    }

    private View toggleButton(int icon, String title, String key, boolean def) {
        final LinearLayout row = menuButton(icon, title, null, () -> {});
        final TextView state = V.text(this, "", 10, 800, V.TEXT);
        final Runnable paint = () -> state.setText(prefs.getBoolean(key, def) ? "ON" : "OFF");
        paint.run();
        View sp = new View(this);
        row.addView(sp, new LinearLayout.LayoutParams(0, 1, 1f));
        row.addView(state);
        V.tint(row, V.TEXT);
        row.setOnClickListener(v -> {
            prefs.edit().putBoolean(key, !prefs.getBoolean(key, def)).apply();
            paint.run();
            if ("weather".equals(key)) loadWeather();
        });
        return row;
    }

    private static final String[] CW_STYLES = {"PILLS", "RINGS", "POSTER CARDS"};

    private int cwStyle() {
        int d = prefs.getBoolean("cw_rings", true) ? 1 : 0;
        return Math.max(0, Math.min(2, prefs.getInt("cw_style", d)));
    }

    /** Continue watching look: cycles pills, progress rings, poster cards, featured + compact. */
    private View cwStyleToggle() {
        final LinearLayout row = menuButton(Icon.BEACON, "Card style", null, () -> {});
        final TextView state = V.text(this, CW_STYLES[cwStyle()], 10, 800, V.TEXT);
        View sp = new View(this);
        row.addView(sp, new LinearLayout.LayoutParams(0, 1, 1f));
        row.addView(state);
        V.tint(row, V.TEXT);
        row.setOnClickListener(v -> {
            int n = (cwStyle() + 1) % CW_STYLES.length;
            prefs.edit().putInt("cw_style", n).apply();
            state.setText(CW_STYLES[n]);
            cwSig = "";
            fillContinue();
        });
        return row;
    }

    /** Picture-first Continue watching tile: title and time left on the art, progress along the bottom. */
    private View cwCard(final Nuvio.Item it, final int idx, final boolean big) {
        final int w = V.dp(big ? 340 : 124), h = V.dp(big ? 92 : 66);
        final FrameLayout t = new FrameLayout(this);
        t.setFocusable(true);
        t.setFocusableInTouchMode(true);
        t.setBackground(V.shape(0x1AFFFFFF, 12, 0));
        V.round(t, 12);
        t.setLayoutParams(new LinearLayout.LayoutParams(w, h));
        ImageView iv = V.image(this, ImageView.ScaleType.CENTER_CROP);
        t.addView(iv, new FrameLayout.LayoutParams(U.MATCH, U.MATCH));
        Img.load(this, Img.sized(it.cardArt(), "w500"), V.dp(big ? 340 : 124), b -> { if (b != null) iv.setImageBitmap(b); });
        View shade = big
                ? Ui.gradient(this, GradientDrawable.Orientation.LEFT_RIGHT, 0xEB0A0808, 0x330A0808)
                : Ui.gradient(this, GradientDrawable.Orientation.TOP_BOTTOM, 0x00000000, 0xD0000000);
        t.addView(shade, new FrameLayout.LayoutParams(U.MATCH, U.MATCH));
        long left = it.durationMs > 0 ? Math.max(0, it.durationMs - it.positionMs) : 0;
        String sub = !TextUtils.isEmpty(it.episode) ? it.episode : remainingLine(it);
        if (left > 0) sub = (!TextUtils.isEmpty(it.episode) ? it.episode + "  ·  " : "") + Nuvio.duration(left) + " left";
        LinearLayout tx = U.col(this);
        tx.setGravity(big ? Gravity.CENTER_VERTICAL : Gravity.BOTTOM);
        if (big) {
            TextView lab = V.text(this, "RESUME", 8, 800, V.accent());
            tx.addView(lab, lp(U.WRAP, U.WRAP, 0, 0, 0, 3));
        }
        TextView title = V.text(this, it.title, big ? 15 : 9.5f, 700, 0xFFFFFFFF);
        V.ellipsize(title);
        tx.addView(title, new LinearLayout.LayoutParams(big ? V.dp(230) : U.MATCH, U.WRAP));
        TextView st = V.text(this, sub == null ? "Movie" : sub, big ? 10 : 8, 500, 0xDDFFFFFF);
        V.ellipsize(st);
        tx.addView(st, lp(big ? V.dp(230) : U.MATCH, U.WRAP, 0, 2, 0, 0));
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(U.MATCH, U.MATCH);
        tlp.setMargins(V.dp(big ? 14 : 8), V.dp(big ? 8 : 4), V.dp(8), V.dp(big ? 16 : 11));
        t.addView(tx, tlp);
        if (it.progress() > 0) {
            FrameLayout bar = new FrameLayout(this);
            bar.setBackground(V.shape(0x59FFFFFF, 2, 0));
            View fill = new View(this);
            fill.setBackground(V.shape(V.accent(), 2, 0));
            fill.setPivotX(0);
            fill.setScaleX(it.progress());
            bar.addView(fill, new FrameLayout.LayoutParams(U.MATCH, U.MATCH));
            FrameLayout.LayoutParams blp = U.flp(U.MATCH, V.dp(3), Gravity.BOTTOM);
            blp.setMargins(V.dp(big ? 14 : 8), 0, V.dp(big ? 14 : 8), V.dp(5));
            t.addView(bar, blp);
        }
        final View ring = new View(this);
        ring.setBackground(new V.Glow(12, 0f, 1));
        ring.setAlpha(0f);
        t.addView(ring, new FrameLayout.LayoutParams(U.MATCH, U.MATCH));
        t.setContentDescription(it.title);
        t.setOnFocusChangeListener((v, has) -> {
            t.animate().scaleX(has ? 1.06f : 1f).scaleY(has ? 1.06f : 1f)
                    .setDuration(170).setInterpolator(new DecelerateInterpolator()).start();
            ring.animate().alpha(has ? 1f : 0f).setDuration(150).start();
            V.tileGlow(t, has, 12, 1.06f);
            if (has) {
                cwCount.setText(cwItems.size() > 1 ? (idx + 1) + " of " + cwItems.size() : "");
                showItem(it, "Continue");
            }
        });
        t.setOnClickListener(v -> Nuvio.open(this, it));
        return t;
    }

    /** Focus look on the home screen: accent glow (new) or the original solid fill. */
    private View focusStyleToggle() {
        final LinearLayout row = menuButton(Icon.BEACON, "Focus style", null, () -> {});
        final TextView state = V.text(this, prefs.getBoolean("focus_glow", true) ? "GLOW" : "FILL", 10, 800, V.TEXT);
        View sp = new View(this);
        row.addView(sp, new LinearLayout.LayoutParams(0, 1, 1f));
        row.addView(state);
        V.tint(row, V.TEXT);
        row.setOnClickListener(v -> {
            reopenMenu = true;
            prefs.edit().putBoolean("focus_glow", !prefs.getBoolean("focus_glow", true)).apply();
        });
        return row;
    }

    private LinearLayout chipRow(String[] labels, int selected, java.util.function.IntConsumer onPick) {
        final LinearLayout row = U.row(this);
        final TextView[] chips = new TextView[labels.length];
        final int[] sel = {selected};
        final Runnable paint = () -> {
            for (int i = 0; i < chips.length; i++) {
                boolean on = i == sel[0];
                if (!chips[i].hasFocus()) {
                    chips[i].setBackground(on ? V.shape(U.alpha(V.accent(), 0.28f), 15, V.accent()) : V.shape(V.GLASS, 15, V.LINE));
                    chips[i].setTextColor(V.TEXT);
                }
            }
        };
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            final TextView c = V.text(this, labels[i], 10, 700, V.TEXT);
            c.setGravity(Gravity.CENTER);
            c.setFocusable(true);
            c.setFocusableInTouchMode(true);
            chips[i] = c;
            c.setOnFocusChangeListener((v, has) -> {
                if (has) { c.setBackground(V.shape(V.TEXT, 15, 0)); c.setTextColor(V.INK); }
                else paint.run();
            });
            c.setOnClickListener(v -> { sel[0] = idx; onPick.accept(idx); paint.run(); c.setBackground(V.shape(V.TEXT, 15, 0)); c.setTextColor(V.INK); });
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, U.MATCH, 1f);
            p.rightMargin = i < labels.length - 1 ? V.dp(5) : 0;
            row.addView(c, p);
        }
        paint.run();
        return row;
    }

    private LinearLayout themeRow() {
        final LinearLayout row = U.row(this);
        String cur = prefs.getString("theme", "ember");
        for (final String[] t : V.THEMES) {
            final boolean on = t[0].equals(cur);
            final int acc = Color.parseColor(t[3]);
            final View b = new View(this);
            b.setFocusable(true);
            b.setFocusableInTouchMode(true);
            b.setContentDescription(t[1]);
            final android.graphics.drawable.GradientDrawable idle = new android.graphics.drawable.GradientDrawable();
            idle.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            idle.setColor(acc);
            idle.setStroke(V.dp(on ? 2.5f : 1), on ? V.TEXT : 0x55FFFFFF);
            b.setBackground(idle);
            b.setOnFocusChangeListener((v, has) -> {
                b.animate().scaleX(has ? 1.25f : 1f).scaleY(has ? 1.25f : 1f).setDuration(140).start();
                android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
                g.setShape(android.graphics.drawable.GradientDrawable.OVAL);
                g.setColor(acc);
                g.setStroke(V.dp(has ? 3 : on ? 2.5f : 1), has || on ? V.TEXT : 0x55FFFFFF);
                b.setBackground(g);
            });
            b.setOnClickListener(v -> { reopenMenu = true; prefs.edit().putString("theme", t[0]).apply(); });
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(V.dp(24), V.dp(24));
            p.rightMargin = V.dp(9);
            p.topMargin = V.dp(4);
            row.addView(b, p);
        }
        return row;
    }

    private View scrim, topBar;

    // ------------------------------------------------------------------ memory panel

    private LinearLayout memPanel;
    private final Runnable openMem = this::showMem;

    private void showMem() {
        if (memPanel == null) {
            memPanel = U.col(this);
            memPanel.setPadding(V.dp(16), V.dp(16), V.dp(16), V.dp(16));
            memPanel.setBackground(V.shape(U.alpha(U.mix(V.BG, 0xFFFFFFFF, 0.16f), 0.98f), 20, 0x40FFFFFF));
            FrameLayout.LayoutParams lp = U.flp(V.dp(330), U.WRAP, Gravity.TOP | Gravity.END);
            lp.topMargin = V.dp(26 + (EDGE - 48) / 2 + 34 + 8);
            lp.rightMargin = V.dp(EDGE + 3 * 39);
            if (scrim == null) {
                scrim = new View(this);
                scrim.setBackgroundColor(0x8C000000);
                scrim.setAlpha(0f);
                ui.addView(scrim, new FrameLayout.LayoutParams(U.MATCH, U.MATCH));
            }
            ui.addView(memPanel, lp);
            memPanel.setAlpha(0f);
        }
        scrim.bringToFront();
        topBar.bringToFront();
        memPanel.bringToFront();
        fillMem(MemStats.cached(), null);
        scrim.animate().cancel();
        scrim.animate().alpha(1f).setDuration(200).start();
        memPanel.animate().cancel();
        memPanel.setTranslationY(-V.dpf(12));
        memPanel.animate().alpha(1f).translationY(0).setDuration(200).withLayer().start();
        memLoading = true;
        U.IO.execute(() -> {
            final MemStats.Stats st = MemStats.load(getApplicationContext(), false);
            U.MAIN.post(() -> { memLoading = false; if (memPanel != null && memPanel.getAlpha() > 0) fillMem(st, null); });
        });
        U.MAIN.postDelayed(() -> {
            if (memLoading && "waiting".equals(AdbLocal.state) && memPanel != null && memPanel.getAlpha() > 0) {
                MemStats.Stats w = new MemStats.Stats();
                w.noPermission = true;
                MemStats.Stats c = MemStats.cached();
                if (c != null) { w.totalKb = c.totalKb; w.availKb = c.availKb; }
                fillMem(c == null ? null : w, null);
                if (c == null) fillMem(w, null);
            }
        }, 1500);
    }

    private void hideMem() {
        if (scrim != null) { scrim.animate().cancel(); scrim.animate().alpha(0f).setDuration(120).start(); }
        if (memPanel != null) memPanel.animate().alpha(0f).translationY(-V.dpf(12)).setDuration(120).withLayer().start();
    }

    private boolean freeing, memLoading;

    private void freeUpMemory() {
        if (freeing) return;
        freeing = true;
        if (memPanel != null) fillMem(MemStats.cached(), "Closing background apps\u2026");
        U.IO.execute(() -> {
            final long freed = MemStats.freeUp(getApplicationContext());
            final MemStats.Stats st = MemStats.cached();
            U.MAIN.post(() -> {
                freeing = false;
                updateMemory();
                if (memPanel != null && memPanel.getAlpha() > 0) fillMem(st, freed > 1024 ? "Freed " + MemStats.mb(freed) : "Nothing more to free right now");
            });
        });
    }

    private void fillMem(MemStats.Stats st, String note) {
        memPanel.removeAllViews();
        LinearLayout head = U.row(this);
        LinearLayout ht = U.col(this);
        ht.addView(V.text(this, "Memory", 13, 800, V.TEXT));
        String sub = st == null ? "Checking\u2026" : MemStats.mb(st.availKb) + " free of " + MemStats.mb(st.totalKb);
        ht.addView(V.dim(V.text(this, sub, 9, 500, V.TEXT), 0.7f), lp(U.WRAP, U.WRAP, 0, 2, 0, 0));
        head.addView(ht, new LinearLayout.LayoutParams(0, U.WRAP, 1f));
        memPanel.addView(head);
        if (st != null) {
            FrameLayout track = new FrameLayout(this);
            track.setBackground(V.shape(0x33FFFFFF, 3, 0));
            View fill = new View(this);
            float used = 1f - st.availKb / (float) Math.max(1, st.totalKb);
            fill.setBackground(V.shape(used > 0.85f ? 0xFFFF6B5C : V.accent(), 3, 0));
            track.addView(fill, new FrameLayout.LayoutParams(Math.round(V.dp(298) * used), U.MATCH));
            memPanel.addView(track, lp(U.MATCH, V.dp(5), 0, 10, 0, 0));
        }
        if (st != null && st.noPermission) {
            String msg;
            if ("waiting".equals(AdbLocal.state)) msg = "On screen now: \u201cAllow network debugging?\u201d \u2014 tick Always allow and choose Allow. beacon only uses it to read memory and close background apps.";
            else if ("denied".equals(AdbLocal.state)) msg = "Permission was declined. Open this again to get the prompt back.";
            else msg = "Network debugging is off on the projector, so per-app memory can't be read. Turn it on in Developer options to see it here.";
            memPanel.addView(V.dim(V.text(this, msg, 10, 500, V.TEXT), 0.8f), lp(U.MATCH, U.WRAP, 0, 12, 0, 0));
        } else if (st != null) {
            memPanel.addView(V.dim(V.caps(this, "Using the most", 8, V.TEXT), 0.6f), lp(U.WRAP, U.WRAP, 0, 14, 0, 6));
            long max = st.apps.isEmpty() ? 1 : st.apps.get(0).kb;
            int shown = 0;
            for (MemStats.App a : st.apps) {
                if (shown >= 7) break;
                shown++;
                LinearLayout r = U.row(this);
                TextView n = V.text(this, a.label + (a.self ? " (this home screen)" : ""), 10, 600, V.TEXT);
                V.ellipsize(n);
                r.addView(n, new LinearLayout.LayoutParams(0, U.WRAP, 1f));
                FrameLayout bar = new FrameLayout(this);
                View f = new View(this);
                f.setBackground(V.shape(a.system ? 0x59FFFFFF : V.accent(), 2, 0));
                bar.addView(f, new FrameLayout.LayoutParams(Math.max(V.dp(2), Math.round(V.dp(60) * a.kb / (float) max)), U.MATCH));
                r.addView(bar, lp(V.dp(60), V.dp(4), 8, 0, 8, 0));
                TextView mbv = V.dim(V.text(this, MemStats.mb(a.kb), 10, 500, V.TEXT), 0.75f);
                mbv.setGravity(Gravity.END);
                r.addView(mbv, new LinearLayout.LayoutParams(V.dp(52), U.WRAP));
                memPanel.addView(r, lp(U.MATCH, U.WRAP, 0, 3, 0, 3));
            }
        }
        String foot = note != null ? note : "OK closes background apps you aren't using \u00b7 grey = Android itself";
        memPanel.addView(V.dim(V.text(this, foot, 8.5f, 500, V.TEXT), note != null ? 0.9f : 0.5f), lp(U.WRAP, U.WRAP, 0, 12, 0, 0));
    }

    private void updateMemory() {
        if (memText == null) return;
        try {
            android.app.ActivityManager am = (android.app.ActivityManager) getSystemService(ACTIVITY_SERVICE);
            android.app.ActivityManager.MemoryInfo mi = new android.app.ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            float used = 1f - mi.availMem / (float) mi.totalMem;
            memFill.setScaleX(Math.max(0.02f, Math.min(1f, used)));
            memFill.setBackground(V.shape(used > 0.85f ? 0xFFFF6B5C : V.accent(), 2, 0));
            String free = mi.availMem >= 1L << 30
                    ? String.format(java.util.Locale.US, "%.1f GB", mi.availMem / (float) (1L << 30))
                    : (mi.availMem >> 20) + " MB";
            memText.setText(free + " free");
        } catch (Throwable ignored) {}
    }

    private View roundButton(int icon, String label, View.OnClickListener l) {
        View b = V.iconButton(this, new Icon(this, icon), label);
        b.setOnClickListener(l);
        return b;
    }

    private LinearLayout.LayoutParams lp(int w, int h, float l, float t, float r, float b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins(V.dp(l), V.dp(t), V.dp(r), V.dp(b));
        return p;
    }

    // ------------------------------------------------------------------ hero

    private void buildHero(LinearLayout col) {
        hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(V.dp(EDGE), 0, V.dp(EDGE), 0);
        heroLabel = V.caps(this, "", 10, V.accent());
        hero.addView(heroLabel);
        heroLogo = V.image(this, ImageView.ScaleType.FIT_START);
        heroLogo.setAdjustViewBounds(true);
        heroLogo.setMaxHeight(V.dp(78));
        heroLogo.setMaxWidth(V.dp(420));
        heroLogo.setVisibility(View.GONE);
        hero.addView(heroLogo, lp(U.WRAP, U.WRAP, 0, 8, 0, 0));
        heroTitle = V.text(this, "", 44, 800, V.TEXT);
        heroTitle.setLetterSpacing(-0.03f);
        heroTitle.setMaxLines(1);
        heroTitle.setEllipsize(TextUtils.TruncateAt.END);
        // one row: long titles shrink to fit instead of wrapping or being cut off
        heroTitle.setAutoSizeTextTypeUniformWithConfiguration(18, 44, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
        hero.addView(heroTitle, lp(V.dp(600), U.WRAP, 0, 6, 0, 0));
        heroLine = V.text(this, "", 14, 400, U.alpha(V.TEXT, 0.85f));
        V.ellipsize(heroLine);
        heroLine.setMaxWidth(V.dp(560));
        hero.addView(heroLine, lp(U.WRAP, U.WRAP, 0, 8, 0, 0));
        heroProgRow = U.row(this);
        FrameLayout track = new FrameLayout(this);
        track.setBackground(V.shape(0x40FFFFFF, 2, 0));
        heroProgFill = new View(this);
        heroProgFill.setBackground(V.shape(V.accent(), 2, 0));
        heroProgFill.setPivotX(0);
        track.addView(heroProgFill, new FrameLayout.LayoutParams(V.dp(120), U.MATCH));
        heroProgRow.addView(track, new LinearLayout.LayoutParams(V.dp(120), V.dp(2.5f)));
        heroRemaining = V.text(this, "", 11, 400, U.alpha(V.TEXT, 0.8f));
        heroProgRow.addView(heroRemaining, lp(U.WRAP, U.WRAP, 9, 0, 0, 0));
        hero.addView(heroProgRow, lp(U.WRAP, U.WRAP, 0, 10, 0, 0));
        col.addView(hero, new LinearLayout.LayoutParams(U.MATCH, V.dp(150)));
        hero.setGravity(Gravity.BOTTOM);
        hero.setClipChildren(false);
        hero.setClipToPadding(false);
    }

    /** Hero text glides in: old fades out quickly, new fades in and rises 12 dp. */
    private Runnable pendingHero;

    private void setHero(final Object key, final String label, final String title, final String logoUrl, final String line,
                         final float progress, final String remaining, final String art) {
        if (key != null && key.equals(heroKey)) { if (pendingHero != null) U.MAIN.removeCallbacks(pendingHero); return; }
        if (pendingHero != null) U.MAIN.removeCallbacks(pendingHero);
        pendingHero = () -> setHeroNow(key, label, title, logoUrl, line, progress, remaining, art);
        U.MAIN.postDelayed(pendingHero, heroKey == null && "greet".equals(key) ? 0 : 140);
    }

    private Object wantKey;
    private String wantLogo;
    private boolean heroSwapping;

    private void loadHeroLogo(final int token, String url) {
        Img.load(this, url, V.dp(420), bm -> {
            if (token != heroToken || bm == null) return;
            heroLogo.setImageBitmap(bm);
            heroLogo.setVisibility(View.VISIBLE);
            heroTitle.setVisibility(View.GONE);
        });
    }

    /** A logo found after the hero was already queued: shown now if the text has landed, else when it does. */
    private void applyHeroLogo(Object key, String url) {
        wantKey = key;
        wantLogo = url;
        if (key != null && key.equals(heroKey) && !heroSwapping) loadHeroLogo(heroToken, url);
    }

    private void setHeroNow(Object key, String label, String title, String logoUrl, String line,
                            float progress, String remaining, String art) {
        if (key != null && key.equals(heroKey)) return;
        heroKey = key;
        final int token = ++heroToken;
        heroSwapping = true;
        hero.animate().cancel();
        hero.animate().alpha(0f).setDuration(140).withLayer().withEndAction(() -> {
            if (token != heroToken) return;
            heroLabel.setText(label);
            heroLabel.setTextColor(V.accent());
            heroTitle.setText(title);
            heroTitle.setVisibility(View.VISIBLE);
            heroLogo.setVisibility(View.GONE);
            heroLogo.setImageDrawable(null);
            heroLine.setText(line == null ? "" : line);
            U.gone(heroLine, TextUtils.isEmpty(line));
            heroProgRow.setVisibility(progress > 0 || !TextUtils.isEmpty(remaining) ? View.VISIBLE : View.GONE);
            heroProgFill.setScaleX(Math.max(0.01f, progress));
            heroProgFill.setVisibility(progress > 0 ? View.VISIBLE : View.INVISIBLE);
            ((View) heroProgFill.getParent()).setVisibility(progress > 0 ? View.VISIBLE : View.GONE);
            heroRemaining.setText(remaining == null ? "" : remaining);
            hero.setTranslationY(V.dpf(12));
            hero.animate().alpha(ambient ? 0f : 1f).translationY(0).setStartDelay(60).setDuration(420)
                    .setInterpolator(new DecelerateInterpolator(1.6f)).withLayer().start();
            heroSwapping = false;
            if (!TextUtils.isEmpty(logoUrl)) loadHeroLogo(token, logoUrl);
            else if (key != null && key.equals(wantKey) && wantLogo != null) loadHeroLogo(token, wantLogo);
        }).start();
        if (!TextUtils.isEmpty(art)) {
            // Full-screen fades are the heaviest thing this GPU draws, so the backdrop only changes once
            // focus has rested; flicking along a row moves just the text. The colour glow follows after
            // the backdrop has finished fading, never at the same time.
            final boolean first = wall == null || !wall.hasImage();
            U.MAIN.postDelayed(() -> {
                if (token != heroToken) return;
                if (art.equals(wallArt) && !first) return; // same picture already up: don't fade or re-zoom it
                Img.load(this, art, WALL_W, bm -> {
                    if (token != heroToken || bm == null) return;
                    if (art.equals(wallArt) && !first) return;
                    if (wall != null) {
                        wallArt = art;
                        wall.show(bm);
                        if (root.getBackground() != null) root.setBackground(null); // the picture covers the screen now
                    }
                    if (artMode() && prefs.getBoolean("art_colours", true)) {
                        U.IO.execute(() -> {
                            final Integer c = ArtColor.extract(bm);
                            U.MAIN.postDelayed(() -> { if (token == heroToken) setAccent(c); }, 750);
                        });
                    }
                });
            }, first ? 0 : 420);
        }
    }

    private void setAccent(Integer raw) {
        V.artAccent = raw == null ? null : ArtColor.asAccent(raw);
        shade.set(V.BG, raw == null ? null : ArtColor.asGlow(raw));
        heroLabel.setTextColor(V.accent());
        heroProgFill.setBackground(V.shape(V.accent(), 2, 0));
        Ui.accent = V.accent();
    }

    private void showGreeting() {
        Calendar cal = Calendar.getInstance();
        String name = prefs.getString(U.P_NAME, "");
        String hi = U.greeting(cal.get(Calendar.HOUR_OF_DAY)) + (TextUtils.isEmpty(name) ? "" : ", " + name);
        String label;
        label = "beacon";
        heroKey = null;
        setHero("greet", label, hi, null, DateFormat.format("EEEE, d MMMM", new Date()).toString(), 0, null, null);
    }

    // ------------------------------------------------------------------ rows

    private TextView header(LinearLayout block, String title) {
        LinearLayout h = U.row(this);
        h.setGravity(Gravity.BOTTOM);
        h.setPadding(V.dp(EDGE), 0, V.dp(EDGE), 0);
        h.addView(V.text(this, title, 12, 700, V.TEXT));
        TextView extra = V.text(this, "", 9, 400, U.alpha(V.TEXT, 0.6f));
        h.addView(extra, lp(U.WRAP, U.WRAP, 7, 0, 0, 1));
        block.addView(h);
        return extra;
    }

    private LinearLayout row(LinearLayout block, float spacing) {
        HorizontalScrollView hs = new AnchoredRow(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setHorizontalFadingEdgeEnabled(false);
        hs.setFocusable(false);
        hs.setClipToPadding(false);
        LinearLayout r = U.row(this);
        r.setPadding(V.dp(EDGE), V.dp(6), V.dp(EDGE), V.dp(8));
        r.setClipChildren(false);
        r.setTag(spacing);
        hs.addView(r, new FrameLayout.LayoutParams(U.WRAP, U.WRAP));
        // Room for the focus glow: the scroller clips what it draws, so pad it and pull the layout back with margins.
        int gp = V.dp(30);
        hs.setPadding(0, gp, 0, gp);
        hs.setClipChildren(false);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(U.MATCH, U.WRAP);
        hp.topMargin = -gp;
        hp.bottomMargin = -gp;
        block.addView(hs, hp);
        return r;
    }

    private void buildContinue(LinearLayout col) {
        cwBlock = U.col(this);
        LinearLayout h = U.row(this);
        h.setGravity(Gravity.BOTTOM);
        h.setPadding(V.dp(EDGE), 0, V.dp(EDGE), 0);
        cwHead = V.text(this, "Continue watching", 12, 700, V.TEXT);
        h.addView(cwHead);
        cwCount = V.text(this, "", 9, 400, U.alpha(V.TEXT, 0.6f));
        h.addView(cwCount, lp(U.WRAP, U.WRAP, 7, 0, 0, 1));
        cwTonight = V.text(this, "", 9, 400, U.alpha(V.TEXT, 0.6f));
        h.addView(cwTonight, lp(U.WRAP, U.WRAP, 7, 0, 0, 1));
        cwBlock.addView(h);
        cwRow = row(cwBlock, 11);
        cwBlock.addView(space(8));
        col.addView(cwBlock, new LinearLayout.LayoutParams(U.MATCH, U.WRAP));
        cwBlock.setVisibility(View.GONE);
    }

    private void buildServices(LinearLayout col) {
        LinearLayout block = U.col(this);
        header(block, "Streaming services").setText("on Nuvio");
        svcRow = row(block, 12);
        col.addView(block, new LinearLayout.LayoutParams(U.MATCH, U.WRAP));
    }

    private void buildApps(LinearLayout col) {
        LinearLayout block = U.col(this);
        appsRow = row(block, 10);
        col.addView(block, new LinearLayout.LayoutParams(U.MATCH, U.WRAP));
    }

    private void addToRow(LinearLayout r, View v) {
        float sp = (Float) r.getTag();
        if (v.getId() == View.NO_ID) v.setId(View.generateViewId());
        LinearLayout.LayoutParams p = v.getLayoutParams() instanceof LinearLayout.LayoutParams
                ? (LinearLayout.LayoutParams) v.getLayoutParams()
                : new LinearLayout.LayoutParams(U.WRAP, U.WRAP);
        p.rightMargin = V.dp(sp);
        r.addView(v, p);
    }

    private void fillRows() {
        if (cwRow == null) return;
        boolean hadFocus = getCurrentFocus() != null && ui.hasFocus();
        fillContinue();
        fillServices();
        fillApps();
        stopAtEdges(cwRow);
        stopAtEdges(svcRow);
        stopAtEdges(appsRow);
        defaultFocusId = cwRow.getChildCount() > 0 ? cwRow.getChildAt(0).getId()
                : svcRow.getChildCount() > 0 ? svcRow.getChildAt(0).getId()
                : appsRow.getChildCount() > 0 ? appsRow.getChildAt(0).getId() : View.NO_ID;
        if (!hadFocus || (!firstFocusDone && !apps.isEmpty())) {
            focusDefault();
            if (!apps.isEmpty()) firstFocusDone = true;
        }
    }

    private final int[] rowMemory = {-1, -1, -1};
    /** Set on a profile switch: Continue watching is entered at its first item until you leave that row once. */
    private boolean cwFresh;
    private int memIdx(int row) { return row == 0 && cwFresh ? 0 : rowMemory[row]; }

    /**
     * Up/Down between Home's rows returns to the item you were last on in the row you're going to
     * (instead of the nearest one by position). Returns false when there's nothing remembered yet.
     */
    private int lastRowIdx = -1; // the row we last left going up to the top bar

    private boolean inTopBar(View v) {
        for (android.view.ViewParent p = v.getParent(); p != null; p = p.getParent()) if (p == topBar) return true;
        return v == topBar;
    }

    private boolean moveBetweenRows(int dir) {
        LinearLayout[] rows = {cwRow, svcRow, appsRow};
        View f = getCurrentFocus();
        if (f == null || ambient) return false;
        if (dir > 0 && topBar != null && inTopBar(f)) {
            // Down from the top bar goes back to the row we came from, at the place we left it
            int li = lastRowIdx;
            if (li < 0 || rows[li] == null || rows[li].getChildCount() == 0 || memIdx(li) < 0) return false;
            View t = rows[li].getChildAt(Math.min(memIdx(li), rows[li].getChildCount() - 1));
            if (t == null) return false;
            t.requestFocus();
            return true;
        }
        int from = -1;
        for (int i = 0; i < rows.length; i++) if (rows[i] != null && f.getParent() == rows[i]) from = i;
        if (from < 0) return false;
        rowMemory[from] = rows[from].indexOfChild(f);
        if (from == 0) cwFresh = false; // you've been in the row since the switch
        if (dir < 0) {
            boolean rowAbove = false;
            for (int to = from - 1; to >= 0; to--) if (rows[to] != null && rows[to].getChildCount() > 0) rowAbove = true;
            if (!rowAbove) lastRowIdx = from; // Up from the first row heads to the top bar
        }
        for (int to = from + dir; to >= 0 && to < rows.length; to += dir) {
            LinearLayout r = rows[to];
            if (r == null || r.getChildCount() == 0) continue;
            if (memIdx(to) < 0) return false; // never visited: the normal nearest-item move
            View target = r.getChildAt(Math.min(memIdx(to), r.getChildCount() - 1));
            if (target == null) return false;
            target.requestFocus();
            return true;
        }
        return false;
    }

    /** Focus the left-most pinned app on the bottom row (falls back to the normal default). */
    private void focusFirstApp() {
        final View v = appsRow != null && appsRow.getChildCount() > 0 ? appsRow.getChildAt(0) : null;
        if (v == null) { focusDefault(); return; }
        v.post(() -> {
            v.requestFocus();
            if (appsRow.getParent() instanceof HorizontalScrollView) ((HorizontalScrollView) appsRow.getParent()).smoothScrollTo(0, 0);
        });
    }

    /** Left/Right stop at the first and last item of a row instead of jumping to another row. */
    private static void stopAtEdges(LinearLayout row) {
        int n = row.getChildCount();
        if (n == 0) return;
        View first = row.getChildAt(0), last = row.getChildAt(n - 1);
        first.setNextFocusLeftId(first.getId());
        last.setNextFocusRightId(last.getId());
    }

    private void focusDefault() {
        if (defaultFocusId == View.NO_ID) return;
        final View v = findViewById(defaultFocusId);
        if (v != null) v.post(() -> {
            v.requestFocus();
            ViewGroup p = (ViewGroup) v.getParent();
            if (p != null && p.getParent() instanceof HorizontalScrollView) ((HorizontalScrollView) p.getParent()).smoothScrollTo(0, 0);
        });
    }

    // --- continue watching (from Nuvio's own Continue Watching list)

    private void fillContinue() {
        cwRow.removeAllViews();
        cwItems.clear();
        if (nuvio != null) for (Nuvio.Item it : nuvio.continueWatching) { if (cwItems.size() < 12) cwItems.add(it); }
        cwBlock.setVisibility(cwItems.isEmpty() ? View.GONE : View.VISIBLE);
        int queued = 0;
        for (int i = 0; i < cwItems.size(); i++) {
            final Nuvio.Item it = cwItems.get(i);
            final int idx = i;
            long left = it.durationMs > 0 ? Math.max(0, it.durationMs - it.positionMs) : 0;
            queued += left / 60000;
            final int style = cwStyle();
            if (style >= 2) {
                View card = cwCard(it, idx, false);
                addToRow(cwRow, card);
                continue;
            }
            final boolean rings = style == 1;
            LinearLayout p = rings ? V.pill(this, 62, 5, 20) : V.pill(this, 54, 6, 17);
            if (rings) {
                FrameLayout box = new FrameLayout(this);
                final ProgressRing ring = new ProgressRing(this, it.progress());
                box.addView(ring, new FrameLayout.LayoutParams(U.MATCH, U.MATCH));
                FrameLayout thumb = new FrameLayout(this);
                thumb.setBackground(V.shape(0x1AFFFFFF, 21, 0));
                V.circle(thumb);
                ImageView iv = V.image(this, ImageView.ScaleType.CENTER_CROP);
                thumb.addView(iv, new FrameLayout.LayoutParams(U.MATCH, U.MATCH));
                Img.load(this, Img.sized(it.cardArt(), "w500"), V.dp(80), b2 -> { if (b2 != null) iv.setImageBitmap(b2); });
                box.addView(thumb, U.flp(V.dp(44), V.dp(44), Gravity.CENTER));
                p.addView(box, new LinearLayout.LayoutParams(V.dp(53), V.dp(53)));
            } else {
            FrameLayout thumb = new FrameLayout(this);
            thumb.setBackground(V.shape(0x1AFFFFFF, 21, 0));
            V.round(thumb, -1);
            ImageView iv = V.image(this, ImageView.ScaleType.CENTER_CROP);
            thumb.addView(iv, new FrameLayout.LayoutParams(U.MATCH, U.MATCH));
            Img.load(this, Img.sized(it.cardArt(), "w500"), V.dp(80), b -> { if (b != null) iv.setImageBitmap(b); });
            if (it.progress() > 0) {
                View shadeV = Ui.gradient(this, GradientDrawable.Orientation.TOP_BOTTOM, 0x00000000, 0x8C000000);
                thumb.addView(shadeV, U.flp(U.MATCH, V.dp(14), Gravity.BOTTOM));
                FrameLayout bar = new FrameLayout(this);
                bar.setBackground(V.shape(0x59FFFFFF, 2, 0));
                View fill = new View(this);
                fill.setBackground(V.shape(V.accent(), 2, 0));
                fill.setPivotX(0);
                fill.setScaleX(it.progress());
                bar.addView(fill, new FrameLayout.LayoutParams(U.MATCH, U.MATCH));
                FrameLayout.LayoutParams blp = U.flp(U.MATCH, V.dp(2), Gravity.BOTTOM);
                blp.setMargins(V.dp(13), 0, V.dp(13), V.dp(5));
                thumb.addView(bar, blp);
            }
            p.addView(thumb, new LinearLayout.LayoutParams(V.dp(74), V.dp(42)));
            }
            LinearLayout tx = U.col(this);
            TextView t = V.text(this, it.title, 12, 700, V.TEXT);
            V.ellipsize(t);
            String sub = !TextUtils.isEmpty(it.episode) ? it.episode : remainingLine(it);
            if (rings && !TextUtils.isEmpty(it.episode) && it.durationMs > 0) sub = it.episode + "  ·  " + remainingLine(it);
            TextView s = V.dim(V.text(this, sub == null ? "Movie" : sub, 9.5f, 500, V.TEXT), 0.72f);
            V.ellipsize(s);
            // Fixed width from the longer line (wrap_content cut titles short on this TV).
            int tw = (int) Math.ceil(Math.max(t.getPaint().measureText(t.getText().toString()),
                    s.getPaint().measureText(s.getText().toString()))) + V.dp(2);
            tw = Math.min(tw, V.dp(220));
            tx.addView(t, new LinearLayout.LayoutParams(tw, U.WRAP));
            tx.addView(s, lp(tw, U.WRAP, 0, 3, 0, 0));
            p.addView(tx, lp(U.WRAP, U.WRAP, 10, 0, 0, 0));
            final float r = rings ? 31 : 27;
            p.setOnFocusChangeListener((v, has) -> {
                V.stylePill(p, has, r, null);
                if (has) {
                    cwCount.setText(cwItems.size() > 1 ? (idx + 1) + " of " + cwItems.size() : "");
                    showItem(it, "Continue");
                }
            });
            p.setOnClickListener(v -> Nuvio.open(this, it));
            addToRow(cwRow, p);
        }
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        cwTonight.setText((hour >= 17 || hour < 3) && queued >= 10 ? "·  Tonight · about " + Nuvio.duration(queued * 60000L) + " to catch up" : "");
    }

    /** Thin accent arc around a circular thumbnail showing how far through you are. */
    private static final class ProgressRing extends View {
        private final float frac;
        private final android.graphics.Paint track = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.Paint arc = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.RectF box = new android.graphics.RectF();
        ProgressRing(android.content.Context c, float frac) {
            super(c);
            this.frac = frac;
            float w = V.dpf(2.5f);
            track.setStyle(android.graphics.Paint.Style.STROKE); track.setStrokeWidth(w); track.setColor(0x3CFFFFFF);
            arc.setStyle(android.graphics.Paint.Style.STROKE); arc.setStrokeWidth(w); arc.setColor(V.accent());
            arc.setStrokeCap(android.graphics.Paint.Cap.ROUND);
        }
        @Override protected void onDraw(android.graphics.Canvas c) {
            float in = V.dpf(2.5f);
            box.set(in, in, getWidth() - in, getHeight() - in);
            c.drawOval(box, track);
            if (frac > 0) c.drawArc(box, -90, 360f * frac, false, arc);
        }
    }

    private String remainingLine(Nuvio.Item it) {
        if (it.durationMs <= 0) return it.meta();
        long left = Math.max(0, it.durationMs - it.positionMs);
        return Nuvio.duration(left) + " left";
    }

    private void showItem(Nuvio.Item it, String label) {
        String line = !TextUtils.isEmpty(it.episode) ? it.episode : it.meta();
        String remaining = null;
        if (it.durationMs > 0) {
            long left = Math.max(0, it.durationMs - it.positionMs);
            Calendar c = Calendar.getInstance();
            c.add(Calendar.MILLISECOND, (int) left);
            remaining = Nuvio.duration(left) + " left · done by " + DateFormat.getTimeFormat(this).format(c.getTime());
        }
        final boolean art = prefs.getBoolean("title_logo", true);
        String logo = art ? it.logo : null;
        setHero(it.key(), label, it.title, logo, line, it.progress(), remaining, Img.sized(it.art(), "w1280"));
        if (art && TextUtils.isEmpty(logo)) {
            final Object k = it.key();
            U.IO.execute(() -> {
                Context ac = getApplicationContext();
                String l = it.tmdbId > 0 ? Streaming.tmdbLogo(ac, it) : null;
                if (l == null) l = Streaming.logoByTitle(ac, it);
                final String found = l;
                if (found != null) U.MAIN.post(() -> { it.logo = found; applyHeroLogo(k, found); });
            });
        }
    }

    // --- streaming services (Nuvio Personalized folders)

    private void fillServices() {
        svcRow.removeAllViews();
        int th = V.dp(63), tw = V.dp(112);
        addLiveTile(tw, th);
        for (final Streaming.Service sv : Streaming.visible(this)) {
            final FrameLayout t = new FrameLayout(this);
            t.setTag(sv.key);
            t.setOnLongClickListener(v -> { svcMenu(sv); return true; });
            t.setFocusable(true);
            t.setFocusableInTouchMode(true);
            t.setLayoutParams(new LinearLayout.LayoutParams(tw, th));
            V.round(t, 12);
            t.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    new int[] {U.mix(sv.color, 0xFF000000, 0.55f), 0xFF08090C}));
            final TextView name = V.text(this, sv.name, 12, 800, V.TEXT);
            name.setGravity(Gravity.CENTER);
            name.setMaxLines(2);
            t.addView(name, U.flp(U.MATCH, U.WRAP, Gravity.CENTER, 4, 0, 4, 0));
            final ImageView cover = V.image(this, ImageView.ScaleType.CENTER_CROP);
            t.addView(cover, new FrameLayout.LayoutParams(U.MATCH, U.MATCH));
            Img.load(this, sv.cover, tw, b -> { if (b != null) { cover.setImageBitmap(b); name.setVisibility(View.GONE); } });
            final ImageView gif = V.image(this, ImageView.ScaleType.CENTER_CROP);
            gif.setAlpha(0f);
            t.addView(gif, new FrameLayout.LayoutParams(U.MATCH, U.MATCH));
            final View ring = new View(this);
            ring.setBackground(new V.Glow(11, 0f, 1));
            ring.setAlpha(0f);
            t.addView(ring, new FrameLayout.LayoutParams(U.MATCH, U.MATCH));
            t.setContentDescription(sv.name);
            t.setOnFocusChangeListener((v, has) -> {
                t.animate().scaleX(has ? 1.08f : 1f).scaleY(has ? 1.08f : 1f)
                        .setDuration(170).setInterpolator(new DecelerateInterpolator()).start();
                ring.animate().alpha(has ? 1f : 0f).setDuration(150).start();
                V.tileGlow(t, has, 12, 1.08f);
                if (has) {
                    playGif(t, gif, Streaming.gif(sv.key), tw, th);
                    setHero("svc:" + sv.key, "Streaming on Nuvio", sv.name, sv.logo,
                            "Top 10 movies · Popular series · New releases", 0, null, sv.hero);
                } else if (gifHost == t) {
                    stopGif();
                }
            });
            t.setOnClickListener(v -> openService(sv.key));
            addToRow(svcRow, t);
        }
        if (!Streaming.hidden(this).isEmpty()) addPlusTile(tw, th);
    }

    private void addPlusTile(int tw, int th) {
        final FrameLayout t = new FrameLayout(this);
        t.setTag("+");
        t.setFocusable(true);
        t.setFocusableInTouchMode(true);
        t.setLayoutParams(new LinearLayout.LayoutParams(tw, th));
        V.round(t, 12);
        t.setBackground(V.shape(V.GLASS, 12, V.LINE));
        Icon plus = new Icon(this, Icon.PLUS);
        plus.setColor(V.TEXT);
        t.addView(plus, new FrameLayout.LayoutParams(V.dp(22), V.dp(22), Gravity.CENTER));
        final View ring = new View(this);
        ring.setBackground(new V.Glow(11, 0f, 1));
        ring.setAlpha(0f);
        t.addView(ring, new FrameLayout.LayoutParams(U.MATCH, U.MATCH));
        t.setContentDescription("Add streaming services back");
        t.setOnFocusChangeListener((v, has) -> {
            t.animate().scaleX(has ? 1.08f : 1f).scaleY(has ? 1.08f : 1f)
                    .setDuration(170).setInterpolator(new DecelerateInterpolator()).start();
            ring.animate().alpha(has ? 1f : 0f).setDuration(150).start();
                V.tileGlow(t, has, 12, 1.08f);
        });
        t.setOnClickListener(v -> hiddenMenu());
        addToRow(svcRow, t);
    }

    private void hiddenMenu() {
        final List<Streaming.Service> hid = new ArrayList<>();
        java.util.Set<String> h = Streaming.hidden(this);
        for (Streaming.Service s : Streaming.ordered(this)) if (h.contains(s.key)) hid.add(s);
        if (hid.isEmpty()) return;
        ActionSheet sheet = new ActionSheet(this, "Add back to Home");
        if (hid.size() > 1) sheet.add("Add all", Icon.PLUS, () -> { Streaming.showAll(this); refreshServices(null); });
        for (final Streaming.Service s : hid) sheet.add(s.name, Icon.PLUS, () -> { Streaming.setHidden(this, s.key, false); refreshServices(s.key); });
        sheet.show();
    }

    /** Rebuilds the streaming row and puts focus on the tile with this key (or the first tile). */
    private void refreshServices(final String focusKey) {
        fillServices();
        stopAtEdges(svcRow);
        U.MAIN.post(() -> {
            View target = null;
            for (int i = 0; i < svcRow.getChildCount(); i++) {
                View c = svcRow.getChildAt(i);
                if (focusKey != null && focusKey.equals(c.getTag())) { target = c; break; }
            }
            if (target == null && svcRow.getChildCount() > 0) target = svcRow.getChildAt(0);
            if (target != null) target.requestFocus();
        });
    }

    // ------------------------------------------------------------------ rearrange (apps and streaming tiles)

    private View rearrangeView;
    private LinearLayout rearrangeRow;
    private long rearrangeAt;

    private boolean movable(View c) {
        return c != null && c.getTag() instanceof String && !"+".equals(c.getTag());
    }

    /** Pick the tile up: Left/Right slide it along its row, OK puts it down, Back puts everything back. */
    private void startRearrange(LinearLayout row, final String tag) {
        View target = null;
        for (int i = 0; i < row.getChildCount(); i++) if (tag.equals(row.getChildAt(i).getTag())) target = row.getChildAt(i);
        if (target == null) return;
        rearrangeRow = row;
        rearrangeView = target;
        rearrangeAt = System.currentTimeMillis();
        target.requestFocus();
        liftTile(target, true);
        Toast.makeText(this, "Left / Right to move \u00b7 OK to place", Toast.LENGTH_LONG).show();
    }

    private void liftTile(final View v, boolean up) {
        v.animate().cancel();
        v.animate().scaleX(up ? 1.18f : 1.08f).scaleY(up ? 1.18f : 1.08f).translationY(up ? -V.dpf(5) : 0).setDuration(150).start();
    }

    private boolean handleRearrangeKey(KeyEvent e) {
        int k = e.getKeyCode();
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        if (k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT) {
            if (down) slideTile(k == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1);
            return true;
        }
        if (k == KeyEvent.KEYCODE_DPAD_UP || k == KeyEvent.KEYCODE_DPAD_DOWN) return true;
        if (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER || k == KeyEvent.KEYCODE_NUMPAD_ENTER || k == KeyEvent.KEYCODE_BUTTON_A) {
            // the OK press that chose "Rearrange" is still ending: ignore it
            if (!down && System.currentTimeMillis() - rearrangeAt > 450) endRearrange(true);
            return true;
        }
        if (k == KeyEvent.KEYCODE_BACK) {
            if (!down) endRearrange(false);
            return true;
        }
        return false;
    }

    private void slideTile(int dir) {
        LinearLayout row = rearrangeRow;
        View v = rearrangeView;
        int i = row.indexOfChild(v), j = i + dir;
        if (j < 0 || j >= row.getChildCount() || !movable(row.getChildAt(j))) return;
        ViewGroup.LayoutParams lp = v.getLayoutParams();
        row.removeView(v);
        row.addView(v, j, lp);
        v.requestFocus();
        liftTile(v, true);
        Sfx.move(1.3f);
    }

    private void endRearrange(boolean save) {
        final View v = rearrangeView;
        final LinearLayout row = rearrangeRow;
        if (v == null || row == null) return;
        rearrangeView = null;
        rearrangeRow = null;
        final String tag = (String) v.getTag();
        List<String> order = new ArrayList<>();
        for (int i = 0; i < row.getChildCount(); i++) if (movable(row.getChildAt(i))) order.add((String) row.getChildAt(i).getTag());
        if (save) {
            if (row == svcRow) Streaming.setVisibleOrder(this, order);
            else {
                List<String> cur = Apps.pins(this, apps);
                for (String p : cur) if (!order.contains(p)) order.add(p); // uninstalled pins stay, at the end
                Apps.savePins(this, order);
            }
        }
        // rebuild from what's saved (this also puts everything back when cancelled)
        if (row == svcRow) { fillServices(); stopAtEdges(svcRow); } else { fillApps(); stopAtEdges(appsRow); }
        U.MAIN.post(() -> {
            for (int i = 0; i < row.getChildCount(); i++) if (tag.equals(row.getChildAt(i).getTag())) { row.getChildAt(i).requestFocus(); break; }
        });
    }

    private void svcMenu(final Streaming.Service sv) {
        List<Streaming.Service> l = Streaming.visible(this);
        int at = -1;
        for (int i = 0; i < l.size(); i++) if (l.get(i).key.equals(sv.key)) at = i;
        ActionSheet sheet = new ActionSheet(this, sv.name);
        if (l.size() > 1 && at >= 0) sheet.add("Rearrange", Icon.APPS, () -> startRearrange(svcRow, sv.key));
        final String next = at >= 0 && at + 1 < l.size() ? l.get(at + 1).key : at > 0 ? l.get(at - 1).key : null;
        sheet.add("Hide from Home", Icon.UNPIN, () -> { Streaming.setHidden(this, sv.key, true); refreshServices(next); });
        sheet.show();
    }

    private void addLiveTile(int tw, int th) {
        final FrameLayout t = new FrameLayout(this);
        t.setFocusable(true);
        t.setFocusableInTouchMode(true);
        t.setLayoutParams(new LinearLayout.LayoutParams(tw, th));
        V.round(t, 12);
        t.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[] {0xFF7A1420, 0xFF1A0A0E}));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        col.setPadding(V.dp(14), 0, V.dp(10), 0);
        TextView dot = V.caps(this, "● Live", 8.5f, 0xFFFF6B6B);
        dot.setSingleLine(true);
        col.addView(dot, new LinearLayout.LayoutParams(-2, -2));
        TextView name = V.text(this, "TV Guide", 13, 800, V.TEXT);
        name.setSingleLine(true);
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(-2, -2);
        np.topMargin = V.dp(2);
        col.addView(name, np);
        t.addView(col, new FrameLayout.LayoutParams(tw, th, Gravity.CENTER));
        final View ring = new View(this);
        ring.setBackground(new V.Glow(11, 0f, 1));
        ring.setAlpha(0f);
        t.addView(ring, new FrameLayout.LayoutParams(-1, -1));
        t.setContentDescription("Live TV guide");
        t.setOnFocusChangeListener((v, has) -> {
            t.animate().scaleX(has ? 1.08f : 1f).scaleY(has ? 1.08f : 1f)
                    .setDuration(170).setInterpolator(new DecelerateInterpolator()).start();
            ring.animate().alpha(has ? 1f : 0f).setDuration(150).start();
                V.tileGlow(t, has, 12, 1.08f);
            if (has) {
                stopGif();
                setHero("svc:live", "Live TV", "TV Guide", null,
                        "Channels with a full TV guide · watch here or in Nuvio", 0, null, "asset:live-backdrop-v4.jpg");
            }
        });
        t.setOnClickListener(v -> startActivity(new Intent(this, GuideActivity.class)));
        addToRow(svcRow, t);
    }

    private void playGif(final View host, final ImageView target, String url, int w, int h) {
        stopGif();
        gifHost = host;
        if (url == null) return;
        Anim.load(this, url, w, h, d -> {
            if (gifHost != host || !host.hasFocus() || d == null) return;
            target.setImageDrawable(d);
            if (d instanceof AnimatedImageDrawable) { gifPlaying = (AnimatedImageDrawable) d; gifPlaying.start(); }
            target.animate().alpha(1f).setDuration(220).start();
        });
    }

    private void stopGif() {
        if (gifPlaying != null) { try { gifPlaying.stop(); } catch (Throwable ignored) {} gifPlaying = null; }
        if (gifHost != null) {
            View g = ((ViewGroup) gifHost).getChildAt(2);
            if (g instanceof ImageView) { g.animate().alpha(0f).setDuration(150).start(); final ImageView gi = (ImageView) g; g.postDelayed(() -> { if (gifHost == null || gifHost != gi.getParent()) gi.setImageDrawable(null); }, 200); }
        }
        gifHost = null;
    }

    private void openService(String key) {
        Streaming.Service s = Streaming.find(key);
        if (!Streaming.canBrowse(this, s)) { Apps.launchPackage(this, U.NUVIO); return; }
        startActivity(new Intent(this, ServiceActivity.class).putExtra("service", key));
    }

    // --- apps (pinned) + All apps

    private void fillApps() {
        appsRow.removeAllViews();
        for (final Apps.App a : pinned()) {
            LinearLayout p = V.pill(this, 42, 6, 16);
            ImageView ic = V.image(this, ImageView.ScaleType.CENTER_CROP);
            ic.setImageDrawable(a.icon);
            ic.setBackground(V.shape(V.GLASS_STRONG, 15, 0));
            V.circle(ic);
            p.addView(ic, new LinearLayout.LayoutParams(V.dp(30), V.dp(30)));
            p.addView(V.text(this, a.label, 11.5f, 600, V.TEXT), lp(U.WRAP, U.WRAP, 10, 0, 0, 0));
            p.setContentDescription(a.label);
            p.setTag(a.pkg);
            p.setOnClickListener(v -> Apps.launch(this, a));
            p.setOnLongClickListener(v -> { appMenu(a); return true; });
            p.setOnKeyListener((v, keyCode, ev) -> {
                if (keyCode != KeyEvent.KEYCODE_DPAD_DOWN) return false;
                if (ev.getAction() == KeyEvent.ACTION_DOWN && ev.getRepeatCount() == 0)
                    startActivity(new Intent(this, AllAppsActivity.class));
                return true; // Down on the bottom row never goes anywhere else
            });
            addToRow(appsRow, p);
        }
        // The All apps button only remains when nothing is pinned (the row needs something to focus).
        if (appsRow.getChildCount() > 0) return;
        LinearLayout all = V.pill(this, 42, 6, 18);
        FrameLayout dot = new FrameLayout(this);
        dot.setBackground(V.shape(0x24FFFFFF, 15, 0));
        Icon g = new Icon(this, Icon.GRID4);
        dot.addView(g, U.flp(V.dp(13), V.dp(13), Gravity.CENTER));
        all.addView(dot, new LinearLayout.LayoutParams(V.dp(30), V.dp(30)));
        all.addView(V.text(this, "All apps", 11.5f, 600, V.TEXT), lp(U.WRAP, U.WRAP, 10, 0, 0, 0));
        V.tint(all, V.TEXT);
        all.setOnClickListener(v -> startActivity(new Intent(this, AllAppsActivity.class)));
        all.setOnKeyListener((v, keyCode, ev) -> {
            if (keyCode != KeyEvent.KEYCODE_DPAD_DOWN) return false;
            if (ev.getAction() == KeyEvent.ACTION_DOWN && ev.getRepeatCount() == 0)
                startActivity(new Intent(this, AllAppsActivity.class));
            return true;
        });
        addToRow(appsRow, all);
    }

    /** Pinned apps, in the order you set (Pin to Home adds to the end; Move left / right reorders). */
    private List<Apps.App> pinned() {
        return Apps.pinned(this, apps);
    }

    private void appMenu(final Apps.App a) {
        final List<Apps.App> pins = pinned();
        int at = -1;
        for (int i = 0; i < pins.size(); i++) if (pins.get(i).pkg.equals(a.pkg)) at = i;
        ActionSheet sheet = new ActionSheet(this, a.label).add("Open", Icon.PLAY, () -> Apps.launch(this, a));
        if (pins.size() > 1 && at >= 0) sheet.add("Rearrange", Icon.APPS, () -> startRearrange(appsRow, a.pkg));
        sheet.add("Unpin from Home", Icon.UNPIN, () -> Apps.unpin(this, apps, a.pkg));
        sheet.show();
    }

    // ================================================================== data

    private void loadApps() {
        U.IO.execute(() -> {
            final List<Apps.App> list = Apps.load(getApplicationContext());
            U.MAIN.post(() -> { apps = list; fillRows(); });
        });
    }

    /**
     * Nuvio doesn't say which profile is active, but switching profile replaces most of the Continue
     * Watching list. When under 40% of the titles carry over, treat it as a profile switch.
     */
    private boolean detectCwSwitch(Nuvio.Result r) {
        java.util.Set<String> now = new java.util.HashSet<>();
        int n = 0;
        for (Nuvio.Item it : r.continueWatching) {
            if (n++ >= 12) break;
            if (it.title != null) now.add(it.title);
        }
        boolean sw = false;
        if (cwLoadedOnce) {
            int inter = 0;
            for (String s : now) if (cwKeys.contains(s)) inter++;
            int union = cwKeys.size() + now.size() - inter;
            sw = union > 0 && inter / (float) union < 0.4f;
        }
        cwLoadedOnce = true;
        cwKeys.clear();
        cwKeys.addAll(now);
        return sw;
    }

    /** After a switch the row starts again from its first item, wherever it was scrolled to. */
    /** After a profile switch, put the selection on the first Continue watching item. */
    private void selectFirstCw() {
        if (menuOpen || ambient || !started || cwRow == null || cwRow.getChildCount() == 0) return;
        View first = cwRow.getChildAt(0);
        if (first != null && first.isFocusable()) first.requestFocus();
    }

    private void scrollCwToStart() {
        if (!(cwRow.getParent() instanceof HorizontalScrollView)) return;
        final HorizontalScrollView hs = (HorizontalScrollView) cwRow.getParent();
        hs.scrollTo(0, 0);
        hs.post(() -> hs.scrollTo(0, 0));
    }

    /** The flash: the row slides and fades in fresh, the heading pulses in the accent colour. */
    private void flashCw() {
        cwBlock.animate().cancel();
        cwBlock.setAlpha(0.15f);
        cwBlock.setTranslationX(V.dpf(36));
        cwBlock.animate().alpha(1f).translationX(0).setDuration(450).start();
        if (cwHead != null) {
            android.animation.ValueAnimator a = android.animation.ValueAnimator.ofObject(new android.animation.ArgbEvaluator(), V.accent(), V.TEXT);
            a.setDuration(1100);
            a.addUpdateListener(an -> cwHead.setTextColor((Integer) an.getAnimatedValue()));
            a.start();
        }
        cwBlock.post(this::showCwNotice);
    }

    private View cwNotice;

    /** The "Continue watching updated" popup, styled like the long-press sheets and lifted over the row. */
    private void showCwNotice() { showNotice("Continue watching updated"); }

    private void showNotice(String text) {
        if (cwNotice != null) { ui.removeView(cwNotice); cwNotice = null; }
        // A see-through pill like the other pills on the home screen, sitting just above the Continue watching row.
        LinearLayout sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.HORIZONTAL);
        sheet.setGravity(Gravity.CENTER_VERTICAL);
        sheet.setPadding(V.dp(14), 0, V.dp(18), 0);
        sheet.setBackground(V.shape(V.GLASS, 25, V.LINE));
        Icon ic = new Icon(this, Icon.BEACON);
        ic.setColor(V.accent());
        sheet.addView(ic, new LinearLayout.LayoutParams(V.dp(13), V.dp(13)));
        TextView tv = V.text(this, text, 12f, 600, V.TEXT);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(U.WRAP, U.WRAP);
        tp.leftMargin = V.dp(9);
        sheet.addView(tv, tp);

        int[] a = new int[2], b = new int[2];
        cwBlock.getLocationInWindow(a);
        ui.getLocationInWindow(b);
        int h = V.dp(36);
        int top = cwBlock.getVisibility() == View.VISIBLE ? a[1] - b[1] - h - V.dp(8) : ui.getHeight() * 5 / 10;
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(U.WRAP, h, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        lp.topMargin = Math.max(V.dp(40), top);
        ui.addView(sheet, lp);
        cwNotice = sheet;
        sheet.setAlpha(0f);
        sheet.setTranslationY(V.dpf(14));
        sheet.animate().alpha(1f).translationY(0).setDuration(240).start();
        final View me = sheet;
        U.MAIN.postDelayed(() -> {
            if (cwNotice != me) return;
            me.animate().alpha(0f).translationY(-V.dpf(8)).setDuration(260).withEndAction(() -> {
                if (cwNotice == me) cwNotice = null;
                ui.removeView(me);
            }).start();
        }, 2600);
    }

    private void loadNuvio() { loadNuvio(false); }

    /** quiet = a background check: nothing is redrawn (and focus is left alone) unless the data changed. */
    private void loadNuvio(final boolean quiet) {
        nuvioAt = System.currentTimeMillis();
        final boolean fill = prefs.getBoolean(U.P_HERO_FILL, true);
        U.IO.execute(() -> {
            final Nuvio.Result r = Nuvio.load(getApplicationContext(), fill);
            Nuvio.applyWallpapers(getApplicationContext(), r, false);
            final StringBuilder sg = new StringBuilder();
            for (Nuvio.Item it : r.continueWatching) sg.append(it.key()).append('@').append(Math.round(it.progress() * 100)).append(';');
            sg.append('|');
            for (Nuvio.Item it : r.picks) sg.append(it.key()).append(';');
            final String sig = sg.toString();
            final Runnable show = () -> {
                if (quiet && sig.equals(cwSig) && nuvio != null) return; // nothing new
                cwSig = sig;
                nuvio = r;
                View f = getCurrentFocus();
                boolean onCw = f != null && f.getParent() == cwRow;
                final int focusedAt = onCw ? cwRow.indexOfChild(f) : -1;
                boolean switched = detectCwSwitch(r);
                fillContinue();
                stopAtEdges(cwRow);
                defaultFocusId = cwRow.getChildCount() > 0 ? cwRow.getChildAt(0).getId()
                        : svcRow.getChildCount() > 0 ? svcRow.getChildAt(0).getId() : defaultFocusId;
                if (switched) { rowMemory[0] = 0; cwFresh = true; scrollCwToStart(); flashCw(); cwRow.post(this::selectFirstCw); } // row 0 is Continue watching: forget the old place
                if (onCw && !switched && focusedAt >= 0 && cwRow.getChildCount() > 0) cwRow.getChildAt(Math.min(focusedAt, cwRow.getChildCount() - 1)).requestFocus(); // stay where you were
                else if (onCw || getCurrentFocus() == null) focusDefault();
            };
            U.MAIN.post(show);
            // episodes not seen before: fetch their series wallpaper, then refresh once
            if (Nuvio.applyWallpapers(getApplicationContext(), r, true) > 0) U.MAIN.post(() -> { if (r == nuvio) show.run(); });
        });
    }

    private void loadWeather() {
        if (!prefs.getBoolean("weather", true)) { weatherIcon.setVisibility(View.GONE); weatherText.setText(""); return; }
        U.IO.execute(() -> {
            try {
                double lat = Double.longBitsToDouble(prefs.getLong("wx_lat", Double.doubleToLongBits(Double.NaN)));
                double lon = Double.longBitsToDouble(prefs.getLong("wx_lon", Double.doubleToLongBits(Double.NaN)));
                if (Double.isNaN(lat)) {
                    JSONObject loc;
                    try {
                        loc = new JSONObject(Http.get("https://ipapi.co/json/"));
                        lat = loc.getDouble("latitude");
                        lon = loc.getDouble("longitude");
                    } catch (Throwable first) {
                        loc = new JSONObject(Http.get("https://ipwho.is/")); // second opinion if the first is rate-limited
                        lat = loc.getDouble("latitude");
                        lon = loc.getDouble("longitude");
                    }
                    prefs.edit().putLong("wx_lat", Double.doubleToLongBits(lat)).putLong("wx_lon", Double.doubleToLongBits(lon))
                            .putString("wx_city", loc.optString("city")).apply();
                }
                JSONObject w = new JSONObject(Http.get("https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon
                        + "&current=temperature_2m,weather_code,is_day&temperature_unit=fahrenheit")).getJSONObject("current");
                final int temp = (int) Math.round(w.getDouble("temperature_2m"));
                final int code = w.optInt("weather_code");
                final boolean day = w.optInt("is_day", 1) == 1;
                U.MAIN.post(() -> {
                    weatherIcon.setType(code >= 51 ? Icon.RAIN : code >= 2 ? Icon.CLOUD : day ? Icon.SUN : Icon.MOON);
                    weatherIcon.setVisibility(View.VISIBLE);
                    weatherText.setText(temp + "°");
                });
            } catch (Throwable ignored) {}
        });
    }

    // ================================================================== background

    private void configurePlayer() {
        if (player == null) return;
        player.configure(Scene.rotation(this), prefs.getInt(U.P_SCENE_SECS, 30),
                prefs.getBoolean(U.P_LIFT, true), prefs.getBoolean(U.P_SOUND, false));
    }

    private void startBackground() {
        if (player != null) {
            if (player.count() == 0) configurePlayer();
            player.start(coldStart && prefs.getBoolean(U.P_START_MARS, true));
            coldStart = false;
        }
        if (wall != null) wall.resume();
        loadNuvio();
    }

    // ================================================================== ambient

    private AmbientView ambientView;
    private String wallArt;

    private void enterAmbient() {
        if (ambient) return;
        ambient = true;
        stopGif();
        if (wall != null) wall.pause();
        final AmbientView av = new AmbientView(this);
        List<Nuvio.Item> pool = new ArrayList<>(cwItems);
        if (nuvio != null) { pool.addAll(nuvio.continueWatching); pool.addAll(nuvio.picks); }
        av.setItems(pool);
        av.setAlpha(0f);
        root.addView(av, new FrameLayout.LayoutParams(U.MATCH, U.MATCH));
        av.start();
        av.animate().alpha(1f).setDuration(900).start();
        ambientView = av;
        Nuvio.Result nr = nuvio;
        if (nr != null) {
            final Nuvio.Result fr = nr;
            U.IO.execute(() -> {
                final List<Nuvio.Item> more = Streaming.ambientExtras(getApplicationContext(), 5, 8);
                U.MAIN.post(() -> { if (ambientView == av) av.addItems(more); });
                if (Nuvio.applyWallpapers(getApplicationContext(), fr, true) > 0) U.MAIN.post(() -> {
                    if (ambientView == av) {
                        List<Nuvio.Item> p2 = new ArrayList<>(fr.continueWatching);
                        p2.addAll(fr.picks);
                        av.setItems(p2);
                    }
                });
            });
        }
        ui.animate().cancel();
        ui.animate().alpha(0f).setDuration(900).withLayer().start();
        shade.animate().alpha(0.35f).setDuration(900).start();
        if (player != null) player.animateLift(0f);
    }

    private void exitAmbient(boolean animate) {
        ambient = false;
        final AmbientView av = ambientView;
        ambientView = null;
        if (av != null) {
            av.stop();
            av.animate().cancel();
            if (animate) av.animate().alpha(0f).setDuration(420).withEndAction(() -> root.removeView(av)).start();
            else root.removeView(av);
        }
        if (wall != null) wall.resume();
        int d = animate ? 420 : 0;
        ui.animate().cancel();
        ui.animate().alpha(1f).setDuration(d).withLayer().start();
        hero.setAlpha(1f);
        shade.animate().alpha(1f).setDuration(d).start();
        if (player != null) player.animateLift(1f);
        if (getCurrentFocus() == null) focusDefault();
    }

    // ================================================================== top bar data

    private void updateClock() {
        if (clock == null) return;
        clock.setText(DateFormat.getTimeFormat(this).format(new Date()));
    }

    private void updateNetwork() {
        if (wifiIcon == null) return;
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            Network n = cm.getActiveNetwork();
            NetworkCapabilities nc = n == null ? null : cm.getNetworkCapabilities(n);
            if (nc == null) { wifiIcon.setType(Icon.WIFI_OFF); return; }
            if (nc.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) { wifiIcon.setType(Icon.ETHERNET); return; }
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
            WifiInfo wi = wm.getConnectionInfo();
            int level = wi == null ? 0 : WifiManager.calculateSignalLevel(wi.getRssi(), 5);
            wifiIcon.setType(Icon.WIFI);
            wifiIcon.setLevel(Math.max(1, level));
        } catch (Throwable t) {
            wifiIcon.setType(Icon.WIFI);
        }
    }

    private void registerReceivers() {
        if (receiversOn) return;
        receiversOn = true;
        IntentFilter tf = new IntentFilter();
        tf.addAction(Intent.ACTION_TIME_TICK);
        tf.addAction(Intent.ACTION_TIME_CHANGED);
        tf.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        registerReceiver(timeRx, tf);
        IntentFilter pf = new IntentFilter();
        pf.addAction(Intent.ACTION_PACKAGE_ADDED);
        pf.addAction(Intent.ACTION_PACKAGE_REMOVED);
        pf.addAction(Intent.ACTION_PACKAGE_CHANGED);
        pf.addDataScheme("package");
        registerReceiver(pkgRx, pf);
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            netCb = new ConnectivityManager.NetworkCallback() {
                @Override public void onAvailable(Network network) { U.MAIN.post(HomeActivity.this::updateNetwork); }
                @Override public void onLost(Network network) { U.MAIN.post(HomeActivity.this::updateNetwork); }
                @Override public void onCapabilitiesChanged(Network network, NetworkCapabilities c) { U.MAIN.post(HomeActivity.this::updateNetwork); }
            };
            cm.registerDefaultNetworkCallback(netCb);
        } catch (Throwable ignored) {}
    }

    private void unregisterReceivers() {
        if (!receiversOn) return;
        receiversOn = false;
        try { unregisterReceiver(timeRx); } catch (Throwable ignored) {}
        try { unregisterReceiver(pkgRx); } catch (Throwable ignored) {}
        try { if (netCb != null) ((ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE)).unregisterNetworkCallback(netCb); } catch (Throwable ignored) {}
        netCb = null;
    }

    // ================================================================== actions

    private void openSetting(String action) {
        if (!Apps.start(this, new Intent(action))) Apps.start(this, new Intent(Settings.ACTION_SETTINGS));
    }

    private void openProjectorSettings() {
        Intent i = new Intent(Intent.ACTION_MAIN);
        i.setComponent(new ComponentName("com.android.tv.settings", "com.android.tv.settings.MainSettings"));
        if (!Apps.start(this, i)) openSetting(Settings.ACTION_SETTINGS);
    }

    private void openHdmi() {
        Intent i = new Intent(Intent.ACTION_MAIN);
        try {
            TvInputManager tim = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
            if (tim != null) for (TvInputInfo info : tim.getTvInputList()) {
                if (info.isPassthroughInput() && info.getType() == TvInputInfo.TYPE_HDMI) {
                    if (Apps.start(this, new Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(info.getId())))) return;
                }
            }
        } catch (Throwable ignored) {}
        Toast.makeText(this, "Couldn't open the HDMI input", Toast.LENGTH_SHORT).show();
    }

    /**
     * Rows scroll so the focused card always sits at the left margin (like Google TV / Apple TV),
     * so nothing is ever pushed against the screen edge where the focus ring and scale get clipped.
     */
    static final class AnchoredRow extends HorizontalScrollView {
        AnchoredRow(Context c) { super(c); setSmoothScrollingEnabled(true); }
        @Override protected int computeScrollDeltaToGetChildRectOnScreen(android.graphics.Rect r) {
            if (getChildCount() == 0) return 0;
            int max = Math.max(0, getChildAt(0).getWidth() - getWidth());
            int target = Math.max(0, Math.min(max, r.left - V.dp(EDGE)));
            return target - getScrollX();
        }
    }
}
