package com.horizon.home;

import android.content.Context;
import android.service.dreams.DreamService;
import android.util.DisplayMetrics;
import android.view.KeyEvent;

import java.util.ArrayList;
import java.util.List;

/** beacon's system screensaver: the same ambient screen as Home's idle mode (see AmbientView). */
public class BeaconDream extends DreamService {
    private AmbientView view;

    @Override public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(true); // keys reach us: Select opens the title, any other key wakes
        setFullscreen(true);
        setScreenBright(true);
        DisplayMetrics dm = getResources().getDisplayMetrics();
        U.W = Math.max(dm.widthPixels, dm.heightPixels);
        U.H = Math.min(dm.widthPixels, dm.heightPixels);
        U.k = U.W / 1280f;
        V.init(this);
        U.nuvio(this);
        view = new AmbientView(this);
        setContentView(view);
    }

    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        int k = e.getKeyCode();
        if (k == KeyEvent.KEYCODE_VOLUME_UP || k == KeyEvent.KEYCODE_VOLUME_DOWN || k == KeyEvent.KEYCODE_VOLUME_MUTE) return super.dispatchKeyEvent(e);
        if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) {
            boolean select = k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER || k == KeyEvent.KEYCODE_NUMPAD_ENTER;
            if (select && view != null) view.openCurrent();
            finish();
        }
        return true;
    }

    @Override public void onDreamingStarted() {
        super.onDreamingStarted();
        view.start();
        final Context app = getApplicationContext();
        U.IO.execute(() -> {
            try {
                final Nuvio.Result r = Nuvio.load(app, true);
                Nuvio.applyWallpapers(app, r, true);
                final List<Nuvio.Item> pool = new ArrayList<>(r.continueWatching);
                pool.addAll(r.picks);
                U.MAIN.post(() -> { if (view != null) view.setItems(pool); });
                final List<Nuvio.Item> more = Streaming.ambientExtras(app, 5, 8);
                U.MAIN.post(() -> { if (view != null) view.addItems(more); });
            } catch (Throwable ignored) {}
        });
    }

    @Override public void onDreamingStopped() {
        if (view != null) view.stop();
        super.onDreamingStopped();
    }

    @Override public void onDetachedFromWindow() {
        if (view != null) view.stop();
        super.onDetachedFromWindow();
    }
}
