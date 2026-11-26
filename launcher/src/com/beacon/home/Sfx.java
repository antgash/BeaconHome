package com.beacon.home;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;

/**
 * beacon's interface sounds, ported from Veil. Each sound is synthesised once from soft sine tones (no
 * audio files are bundled or downloaded), saved to the app cache as a tiny WAV and played through
 * SoundPool for low latency. They are quieter while music is playing. On/off and an 8-step volume live in
 * the beacon menu.
 *
 * Hooking in is two lines per screen: Sfx.attach(this) in onCreate and Sfx.key(this, e) in dispatchKeyEvent.
 * attach() listens for focus moving anywhere in the window, so every list, row and menu ticks on its own.
 */
final class Sfx {
    enum S { MOVE, SELECT, OPEN, BACK, ON, OFF, SUCCESS, ERROR }

    static final String P_ON = "sounds";
    static final String P_VOL = "sound_vol";
    static final int LEVELS = 8;
    static final int DEFAULT_LEVEL = 5;
    /** Views tagged with this skip the automatic "select" pop (toggles play their own on/off sound). */
    static final String QUIET = "sfx-quiet";

    private static final int VERSION = 1; // bump when the sound design changes, to re-synthesise
    private static final int RATE = 44100;

    private static SoundPool pool;
    private static final Map<S, Integer> ids = new HashMap<>();
    private static AudioManager audio;
    private static long lastMove;
    private static volatile boolean enabled = true;
    private static volatile int level = DEFAULT_LEVEL;

    private Sfx() {}

    static synchronized void init(Context context) {
        if (pool != null) return;
        final Context app = context.getApplicationContext();
        SharedPreferences sp = U.prefs(app);
        enabled = sp.getBoolean(P_ON, true);
        level = clampLevel(sp.getInt(P_VOL, DEFAULT_LEVEL));
        audio = (AudioManager) app.getSystemService(Context.AUDIO_SERVICE);
        final SoundPool p = new SoundPool.Builder().setMaxStreams(4).setAudioAttributes(
                new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build();
        pool = p;
        Thread th = new Thread(() -> {
            File dir = new File(app.getCacheDir(), "sfx");
            dir.mkdirs();
            for (S s : S.values()) {
                File f = new File(dir, s.name().toLowerCase() + "_v" + VERSION + ".wav");
                if (!f.exists()) {
                    try { writeWav(f, synth(s)); } catch (Throwable ignored) {}
                }
                if (f.exists()) {
                    int id = p.load(f.getPath(), 1);
                    synchronized (ids) { ids.put(s, id); }
                }
            }
        }, "beacon-sfx");
        th.start();
    }

    /** Call from onCreate after the content view is set. */
    static void attach(Activity a) {
        init(a);
        watch(a.getWindow());
    }

    /** Ticks whenever focus moves from one view to another inside this window (also used for dialogs). */
    static void watch(Window w) {
        if (w == null) return;
        w.getDecorView().getViewTreeObserver().addOnGlobalFocusChangeListener(
                (oldFocus, newFocus) -> { if (oldFocus != null && newFocus != null && oldFocus != newFocus) move(1f); });
    }

    /** Call at the top of dispatchKeyEvent: OK plays the select pop, Back plays the falling tone. */
    static void key(Activity a, KeyEvent e) {
        int k = e.getKeyCode();
        if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0
                && (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER || k == KeyEvent.KEYCODE_NUMPAD_ENTER)) {
            View f = a.getCurrentFocus();
            if (f == null || !QUIET.equals(f.getTag())) select();
        } else if (e.getAction() == KeyEvent.ACTION_UP && k == KeyEvent.KEYCODE_BACK) {
            back();
        }
    }

    // ---- settings

    static boolean isEnabled() { return enabled; }
    static int level() { return level; }

    static void setEnabled(Context c, boolean on) {
        enabled = on;
        U.prefs(c).edit().putBoolean(P_ON, on).apply();
    }

    static void setLevel(Context c, int l) {
        level = clampLevel(l);
        U.prefs(c).edit().putInt(P_VOL, level).apply();
    }

    private static int clampLevel(int l) { return Math.max(1, Math.min(LEVELS, l)); }

    // ---- playing

    /** Selection moved; rate-limited so a held D-pad key does not turn into a buzz. */
    static void move(float pitch) {
        long now = SystemClock.elapsedRealtime();
        if (now - lastMove < 45) return;
        lastMove = now;
        play(S.MOVE, 0.55f, pitch);
    }
    static void select() { play(S.SELECT, 0.7f, 1f); }
    static void open() { play(S.OPEN, 0.6f, 1f); }
    static void back() { play(S.BACK, 0.6f, 1f); }
    static void on() { play(S.ON, 0.65f, 1f); }
    static void off() { play(S.OFF, 0.65f, 1f); }
    static void success() { play(S.SUCCESS, 0.65f, 1f); }
    static void error() { play(S.ERROR, 0.65f, 1f); }

    /** Plays the tick at the current volume even when the sounds are off (never used while off). */
    static void preview() { play(S.MOVE, 0.7f, 1.1f); }

    private static void play(S s, float volume, float rate) {
        if (!enabled) return;
        SoundPool p = pool;
        if (p == null) return;
        Integer id;
        synchronized (ids) { id = ids.get(s); }
        if (id == null) return;
        // Volume steps follow a gentle curve so the 8 levels feel evenly spaced to the ear.
        float gain = (float) Math.pow(level / (float) LEVELS, 1.5);
        float duck = (audio != null && audio.isMusicActive()) ? 0.55f : 1f;
        float v = Math.max(0f, Math.min(1f, volume * 1.45f * gain * duck));
        p.play(id, v, v, 1, 0, Math.max(0.5f, Math.min(2f, rate)));
    }

    // ---- synthesis

    /** A soft sine "glass" note: quick attack, exponential decay, a touch of 2nd harmonic for warmth. */
    private static void note(float[] buf, int startMs, double freq, int durMs, double tauMs, float amp, double glideTo) {
        int start = startMs * RATE / 1000;
        int n = durMs * RATE / 1000;
        double phase = 0;
        for (int i = 0; i < n; i++) {
            int idx = start + i;
            if (idx >= buf.length) break;
            double t = (double) i / RATE;
            double f = freq + (glideTo - freq) * ((double) i / n);
            phase += 2 * Math.PI * f / RATE;
            double attack = Math.min(t / 0.003, 1.0);
            double env = attack * Math.exp(-t * 1000.0 / tauMs);
            buf[idx] += (float) (amp * env * (Math.sin(phase) + 0.15 * Math.sin(2 * phase)));
        }
    }

    private static void note(float[] buf, int startMs, double freq, int durMs, double tauMs, float amp) {
        note(buf, startMs, freq, durMs, tauMs, amp, freq);
    }

    private static float[] synth(S s) {
        int lenMs;
        switch (s) {
            case MOVE: lenMs = 60; break;
            case SELECT: lenMs = 110; break;
            case BACK: lenMs = 120; break;
            case SUCCESS: lenMs = 380; break;
            default: lenMs = 260; break;
        }
        float[] b = new float[lenMs * RATE / 1000];
        switch (s) {
            case MOVE: note(b, 0, 1180.0, 55, 9.0, 0.32f); break; // soft tick
            case SELECT: note(b, 0, 520.0, 90, 26.0, 0.42f, 780.0); note(b, 0, 2600.0, 8, 2.0, 0.08f); break; // pop
            case OPEN: note(b, 0, 880.0, 200, 55.0, 0.32f); note(b, 45, 1318.5, 200, 60.0, 0.28f); break; // chime
            case BACK: note(b, 0, 700.0, 110, 32.0, 0.38f, 460.0); break; // falling
            case ON: note(b, 0, 784.0, 110, 32.0, 0.34f); note(b, 60, 1046.5, 160, 45.0, 0.34f); break; // up
            case OFF: note(b, 0, 1046.5, 110, 32.0, 0.34f); note(b, 60, 784.0, 160, 45.0, 0.34f); break; // down
            case SUCCESS:
                note(b, 0, 1046.5, 200, 55.0, 0.28f);
                note(b, 60, 1318.5, 200, 60.0, 0.28f);
                note(b, 120, 1568.0, 240, 80.0, 0.28f);
                break;
            default: note(b, 0, 330.0, 110, 40.0, 0.4f); note(b, 95, 262.0, 140, 45.0, 0.4f); break; // ERROR
        }
        return b;
    }

    private static void writeWav(File f, float[] samples) throws java.io.IOException {
        ByteBuffer data = ByteBuffer.allocate(samples.length * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (float x : samples) data.putShort((short) (Math.max(-1f, Math.min(1f, x)) * 32767));
        ByteBuffer h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
        h.put("RIFF".getBytes()); h.putInt(36 + data.capacity()); h.put("WAVE".getBytes());
        h.put("fmt ".getBytes()); h.putInt(16); h.putShort((short) 1); h.putShort((short) 1);
        h.putInt(RATE); h.putInt(RATE * 2); h.putShort((short) 2); h.putShort((short) 16);
        h.put("data".getBytes()); h.putInt(data.capacity());
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(h.array());
            out.write(data.array());
        }
    }
}
