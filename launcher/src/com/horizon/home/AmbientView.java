package com.horizon.home;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The Veil-style ambient screen, shared by Home's idle mode and the system screensaver: a slow, drifting
 * slideshow of artwork, the title (or its logo) with where it came from bottom-left, the clock and weather
 * top-right, and the beacon mark top-left.
 */
final class AmbientView extends FrameLayout {
    private final HeroWall wall;
    private final TextView clock, temp, label, title, line;
    private final Icon wIcon;
    private final ImageView logo;
    private final LinearLayout text;
    private final List<Nuvio.Item> items = new ArrayList<>();
    private int idx;
    private boolean running;
    private int token;
    private Nuvio.Item current;
    private String[] currentRes; // imdb id + type found by name for the title on screen

    AmbientView(Context c) {
        super(c);
        setBackgroundColor(0xFF000000);
        wall = new HeroWall(c);
        wall.setDriftMs(17000);
        wall.setFadeMs(2500);
        addView(wall, new LayoutParams(U.MATCH, U.MATCH));

        View left = new View(c);
        left.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[] {0xAA000000, 0x00000000}));
        addView(left, new LayoutParams(U.MATCH, U.MATCH));
        View bottom = new View(c);
        bottom.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, new int[] {0xE6000000, 0x00000000}));
        addView(bottom, new LayoutParams(U.MATCH, (int) (U.H * 0.6f), Gravity.BOTTOM));

        // top-left mark
        LinearLayout mark = new LinearLayout(c);
        mark.setGravity(Gravity.CENTER_VERTICAL);
        Icon mi = new Icon(c, Icon.BEACON);
        mi.setColor(U.alpha(V.TEXT, 0.7f));
        mark.addView(mi, new LinearLayout.LayoutParams(V.dp(12), V.dp(12)));
        TextView mt = V.text(c, "beacon", 14, 800, U.alpha(V.TEXT, 0.7f));
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(U.WRAP, U.WRAP);
        mp.leftMargin = V.dp(6);
        mark.addView(mt, mp);
        LayoutParams ml = new LayoutParams(U.WRAP, U.WRAP, Gravity.TOP | Gravity.START);
        ml.leftMargin = V.dp(48);
        ml.topMargin = V.dp(30);
        addView(mark, ml);

        // top-right clock + weather
        LinearLayout tr = new LinearLayout(c);
        tr.setGravity(Gravity.CENTER_VERTICAL);
        clock = V.text(c, "", 30, 400, V.TEXT);
        tr.addView(clock);
        wIcon = new Icon(c, Icon.SUN);
        wIcon.setColor(U.alpha(V.TEXT, 0.9f));
        wIcon.setVisibility(View.GONE);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(V.dp(22), V.dp(22));
        ip.leftMargin = V.dp(14);
        ip.rightMargin = V.dp(6);
        tr.addView(wIcon, ip);
        temp = V.text(c, "", 24, 400, U.alpha(V.TEXT, 0.9f));
        tr.addView(temp);
        LayoutParams tl = new LayoutParams(U.WRAP, U.WRAP, Gravity.TOP | Gravity.END);
        tl.rightMargin = V.dp(48);
        tl.topMargin = V.dp(24);
        addView(tr, tl);

        // bottom-left: where it's from, title or logo, one line
        text = new LinearLayout(c);
        text.setOrientation(LinearLayout.VERTICAL);
        label = V.caps(c, "", 10, V.accent());
        text.addView(label);
        title = V.text(c, "", 40, 800, V.TEXT);
        title.setLetterSpacing(-0.03f);
        title.setMaxLines(1);
        title.setAutoSizeTextTypeUniformWithConfiguration(18, 40, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(U.MATCH, U.WRAP);
        tp.topMargin = V.dp(6);
        text.addView(title, tp);
        logo = V.image(c, ImageView.ScaleType.FIT_START);
        logo.setVisibility(View.GONE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(V.dp(420), V.dp(120));
        lp.topMargin = V.dp(6);
        text.addView(logo, lp);
        line = V.text(c, "", 14, 400, U.alpha(V.TEXT, 0.85f));
        line.setSingleLine(true);
        LinearLayout.LayoutParams lnp = new LinearLayout.LayoutParams(U.WRAP, U.WRAP);
        lnp.topMargin = V.dp(6);
        text.addView(line, lnp);
        LayoutParams bl = new LayoutParams(V.dp(620), U.WRAP, Gravity.BOTTOM | Gravity.START);
        bl.leftMargin = V.dp(48);
        bl.bottomMargin = V.dp(56);
        addView(text, bl);
        text.setAlpha(0f);

        // small hint along the bottom edge
        TextView hint = V.text(c, "Select to load content, back to exit", 11, 400, U.alpha(V.TEXT, 0.6f));
        hint.setLetterSpacing(0.04f);
        LayoutParams hl = new LayoutParams(U.WRAP, U.WRAP, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        hl.bottomMargin = V.dp(16);
        addView(hint, hl);
    }

    /** The title on screen right now (what Select opens), or null before the first one appears. */
    Nuvio.Item current() { return current; }

    /** Opens the title on screen in Nuvio (its info page, not playback). */
    void openCurrent() {
        Nuvio.Item it = current;
        if (it == null) return;
        if (TextUtils.isEmpty(it.imdbId) && it.tmdbId <= 0 && currentRes == null) Streaming.openDetails(getContext(), it); // lookup not done yet
        else Streaming.openResolved(getContext(), it, currentRes);
    }

    void setItems(List<Nuvio.Item> pool) {
        List<Nuvio.Item> list = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Nuvio.Item it : pool) if (!TextUtils.isEmpty(it.art()) && seen.add(String.valueOf(it.title))) list.add(it);
        Collections.shuffle(list);
        boolean wasEmpty = items.isEmpty();
        items.clear();
        items.addAll(list);
        idx = 0;
        if (running && wasEmpty && !items.isEmpty()) { U.MAIN.removeCallbacks(next); U.MAIN.post(next); }
    }

    /** Adds more titles to the slideshow, mixed in among those still to come. */
    void addItems(List<Nuvio.Item> more) {
        Set<String> seen = new HashSet<>();
        for (Nuvio.Item it : items) seen.add(String.valueOf(it.title));
        List<Nuvio.Item> add = new ArrayList<>();
        for (Nuvio.Item it : more) if (!TextUtils.isEmpty(it.art()) && seen.add(String.valueOf(it.title))) add.add(it);
        if (add.isEmpty()) return;
        boolean wasEmpty = items.isEmpty();
        items.addAll(add);
        if (idx < items.size()) Collections.shuffle(items.subList(idx, items.size()));
        if (running && wasEmpty) { U.MAIN.removeCallbacks(next); U.MAIN.post(next); }
    }

    void start() {
        running = true;
        wall.resume();
        tickClock.run();
        loadWeather();
        U.MAIN.removeCallbacks(next);
        if (!items.isEmpty()) U.MAIN.post(next);
    }

    void stop() {
        running = false;
        token++;
        U.MAIN.removeCallbacks(next);
        U.MAIN.removeCallbacks(tickClock);
        wall.pause();
    }

    private final Runnable tickClock = new Runnable() {
        @Override public void run() {
            if (!running) return;
            clock.setText(DateFormat.getTimeFormat(getContext()).format(new Date()));
            U.MAIN.postDelayed(this, 20000);
        }
    };

    private final Runnable next = new Runnable() {
        @Override public void run() {
            if (!running || items.isEmpty()) return;
            if (idx >= items.size()) { Collections.shuffle(items); idx = 0; }
            final Nuvio.Item it = items.get(idx++);
            final int tk = ++token;
            Img.load(getContext(), Img.sized(it.art(), "w1280"), 1280, bm -> {
                if (!running || tk != token) return;
                if (bm == null) { U.MAIN.postDelayed(next, 1500); return; }
                // the colour of the text above the title follows the picture, as on the home screen
                final boolean tint = U.prefs(getContext()).getBoolean("art_colours", true);
                U.IO.execute(() -> {
                    final Integer c = tint ? ArtColor.extract(bm) : null;
                    U.MAIN.post(() -> {
                        if (!running || tk != token) return;
                        wall.show(bm);
                        setText(it, tk, c == null ? V.accent() : ArtColor.asAccent(c));
                        U.MAIN.postDelayed(next, 14000);
                    });
                });
            });
        }
    };

    private void setText(final Nuvio.Item it, final int tk, final int accent) {
        text.animate().cancel();
        text.animate().alpha(0f).setDuration(tk == 1 ? 0 : 600).withEndAction(() -> {
            if (tk != token) return;
            current = it;
            currentRes = null;
            if (TextUtils.isEmpty(it.imdbId) && it.tmdbId <= 0) U.IO.execute(() -> {
                final String[] r = Streaming.resolveByTitle(getContext().getApplicationContext(), it);
                U.MAIN.post(() -> { if (current == it) currentRes = r; });
            });
            label.setTextColor(accent);
            label.setText(it.watchNext ? "Continue watching" : !TextUtils.isEmpty(it.channel) ? it.channel : "From Nuvio");
            title.setText(it.title);
            title.setVisibility(View.VISIBLE);
            logo.setVisibility(View.GONE);
            logo.setImageDrawable(null);
            String l = !TextUtils.isEmpty(it.episode) ? it.episode : it.meta();
            line.setText(l == null ? "" : l);
            line.setVisibility(TextUtils.isEmpty(l) ? View.GONE : View.VISIBLE);
            if (U.prefs(getContext()).getBoolean("title_logo", true)) {
                if (!TextUtils.isEmpty(it.logo)) showLogo(it.logo, tk);
                else U.IO.execute(() -> {
                    // no artwork on the item yet: look it up by title, the same way the home screen does
                    Context ac = getContext().getApplicationContext();
                    String lg = it.tmdbId > 0 ? Streaming.tmdbLogo(ac, it) : null;
                    if (lg == null) lg = Streaming.logoByTitle(ac, it);
                    final String found = lg;
                    if (found != null) U.MAIN.post(() -> { it.logo = found; if (tk == token && current == it) showLogo(found, tk); });
                });
            }
            text.animate().alpha(1f).setDuration(1200).start();
        }).start();
    }

    private void showLogo(String url, final int tk) {
        Img.load(getContext(), url, V.dp(420), bm -> {
            if (tk != token || bm == null) return;
            logo.setImageBitmap(bm);
            logo.setVisibility(View.VISIBLE);
            title.setVisibility(View.GONE);
        });
    }

    private void loadWeather() {
        final android.content.SharedPreferences p = U.prefs(getContext());
        if (!p.getBoolean("weather", true)) { wIcon.setVisibility(View.GONE); temp.setText(""); return; }
        U.IO.execute(() -> {
            try {
                double lat = Double.longBitsToDouble(p.getLong("wx_lat", Double.doubleToLongBits(Double.NaN)));
                double lon = Double.longBitsToDouble(p.getLong("wx_lon", Double.doubleToLongBits(Double.NaN)));
                if (Double.isNaN(lat)) {
                    JSONObject loc = new JSONObject(Http.get("https://ipapi.co/json/"));
                    lat = loc.getDouble("latitude");
                    lon = loc.getDouble("longitude");
                    p.edit().putLong("wx_lat", Double.doubleToLongBits(lat)).putLong("wx_lon", Double.doubleToLongBits(lon)).apply();
                }
                JSONObject w = new JSONObject(Http.get("https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon
                        + "&current=temperature_2m,weather_code,is_day&temperature_unit=fahrenheit")).getJSONObject("current");
                final int t = (int) Math.round(w.getDouble("temperature_2m"));
                final int code = w.optInt("weather_code");
                final boolean day = w.optInt("is_day", 1) == 1;
                U.MAIN.post(() -> {
                    wIcon.setType(code >= 51 ? Icon.RAIN : code >= 2 ? Icon.CLOUD : day ? Icon.SUN : Icon.MOON);
                    wIcon.setVisibility(View.VISIBLE);
                    temp.setText(t + "°");
                });
            } catch (Throwable ignored) {}
        });
    }
}
