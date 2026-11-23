package com.horizon.home;

import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.service.dreams.DreamService;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/** Horizon's system screensaver: Nuvio artwork drifting slowly, with the clock and weather in the middle. */
public class HorizonDream extends DreamService {
    private final Handler h = new Handler(Looper.getMainLooper());
    private HeroWall wall;
    private TextView clock, weather, caption;
    private final List<Nuvio.Item> items = new ArrayList<>();
    private int idx;
    private boolean running;

    @Override public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        setScreenBright(true);

        DisplayMetrics dm = getResources().getDisplayMetrics();
        U.W = Math.max(dm.widthPixels, dm.heightPixels);
        U.H = Math.min(dm.widthPixels, dm.heightPixels);
        U.k = U.W / 1280f;
        V.init(this);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        wall = new HeroWall(this);
        wall.setDriftMs(16000);
        root.addView(wall, new FrameLayout.LayoutParams(U.MATCH, U.MATCH));

        View shade = new View(this);
        shade.setBackgroundColor(0x66000000);
        root.addView(shade, new FrameLayout.LayoutParams(U.MATCH, U.MATCH));

        LinearLayout mid = new LinearLayout(this);
        mid.setOrientation(LinearLayout.HORIZONTAL);
        mid.setGravity(Gravity.CENTER_VERTICAL);
        clock = V.text(this, "", 56, 300, 0xFFFFFFFF);
        mid.addView(clock);
        weather = V.text(this, "", 30, 300, 0xE6FFFFFF);
        LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(U.WRAP, U.WRAP);
        wp.leftMargin = V.dp(22);
        mid.addView(weather, wp);
        root.addView(mid, new FrameLayout.LayoutParams(U.WRAP, U.WRAP, Gravity.CENTER));

        caption = V.text(this, "", 14, 600, 0xCCFFFFFF);
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(U.WRAP, U.WRAP, Gravity.BOTTOM | Gravity.START);
        cp.leftMargin = V.dp(48);
        cp.bottomMargin = V.dp(40);
        root.addView(caption, cp);

        setContentView(root);
    }

    @Override public void onDreamingStarted() {
        super.onDreamingStarted();
        running = true;
        tickClock.run();
        loadWeather();
        final android.content.Context app = getApplicationContext();
        U.IO.execute(() -> {
            try {
                Nuvio.Result r = Nuvio.load(app, true);
                final List<Nuvio.Item> all = new ArrayList<>();
                for (Nuvio.Item it : r.continueWatching) if (!TextUtils.isEmpty(it.art())) all.add(it);
                for (Nuvio.Item it : r.picks) if (!TextUtils.isEmpty(it.art())) all.add(it);
                Collections.shuffle(all);
                h.post(() -> { items.clear(); items.addAll(all); idx = 0; if (running) slide.run(); });
            } catch (Throwable ignored) {}
        });
    }

    @Override public void onDreamingStopped() {
        running = false;
        h.removeCallbacksAndMessages(null);
        if (wall != null) wall.pause();
        super.onDreamingStopped();
    }

    @Override public void onDetachedFromWindow() {
        running = false;
        h.removeCallbacksAndMessages(null);
        super.onDetachedFromWindow();
    }

    private final Runnable tickClock = new Runnable() {
        @Override public void run() {
            if (!running) return;
            clock.setText(DateFormat.getTimeFormat(HorizonDream.this).format(new Date()));
            h.postDelayed(this, 20000);
        }
    };

    private final Runnable slide = new Runnable() {
        @Override public void run() {
            if (!running || items.isEmpty()) return;
            final Nuvio.Item it = items.get(idx++ % items.size());
            Img.load(HorizonDream.this, Img.sized(it.art(), "w1280"), 1280, bm -> {
                if (!running || bm == null) return;
                wall.show(bm);
                caption.setText(it.title == null ? "" : it.title);
            });
            h.postDelayed(this, 14000);
        }
    };

    private void loadWeather() {
        final android.content.SharedPreferences prefs = U.prefs(this);
        if (!prefs.getBoolean("weather", true)) return;
        U.IO.execute(() -> {
            try {
                double lat = Double.longBitsToDouble(prefs.getLong("wx_lat", Double.doubleToLongBits(Double.NaN)));
                double lon = Double.longBitsToDouble(prefs.getLong("wx_lon", Double.doubleToLongBits(Double.NaN)));
                if (Double.isNaN(lat)) return; // Home fetches the location the first time it runs
                JSONObject w = new JSONObject(Http.get("https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon
                        + "&current=temperature_2m&temperature_unit=fahrenheit")).getJSONObject("current");
                final int temp = (int) Math.round(w.getDouble("temperature_2m"));
                h.post(() -> { if (running) weather.setText(temp + "°"); });
            } catch (Throwable ignored) {}
        });
    }
}
