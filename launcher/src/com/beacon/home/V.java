package com.beacon.home;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.HashMap;
import java.util.Map;

/**
 * Design tokens and building blocks in the style of Veil (github.com/trbrnt/Veil): calm dark base,
 * artwork-led accent, Manrope type, glass pills that turn solid white when focused.
 * Sizes are in Veil's dp: the UI is drawn for 1920x1080 at 2 px per dp.
 */
final class V {
    private V() {}

    static int BG = 0xFF15171C;
    static final int TEXT = 0xFFF4F5F7;
    static final int INK = 0xFF111318;
    // Pill and button surfaces. "Frosted glass" is a light veil with a faint edge; "Smoke" is a
    // semi-transparent near-black and "Slate" a solid cool grey-blue lifted by a soft shadow, both with no edge. loadTheme() picks one from the "surface" setting.
    static int surface;   // 0 frosted glass, 1 smoke, 2 slate
    static int GLASS = 0x14FFFFFF;
    static int GLASS_STRONG = 0x1FFFFFFF;
    static int LINE = 0x29FFFFFF;
    /** Menu rows sit one step lighter than the panel; the panel itself is built from MENU_PANEL. */
    static int MENU_ROW = 0x14FFFFFF;

    /** Themes: id, name, background, accent. "ember" is the Mars-orange house theme. */
    static final String[][] THEMES = {
            {"ember", "Ember", "#1B1411", "#FFA45C"},
            {"veil", "Veil", "#15171C", "#D9FF6B"},
            {"aurora", "Aurora", "#0E1621", "#5EF0C2"},
            {"haze", "Haze", "#17131F", "#C9B8FF"},
            {"glacier", "Glacier", "#0F1820", "#8FD3FF"},
            {"bloom", "Bloom", "#1D1217", "#FF8FB1"},
            {"mono", "Mono", "#121212", "#F4F5F7"},
            {"gilded", "Gilded", "#0F1116", "#F2C94C"},
    };

    static int themeAccent = 0xFFFFA45C;
    /** Accent picked from the current artwork (null = theme accent). */
    static Integer artAccent;

    static int accent() { return artAccent != null ? artAccent : themeAccent; }

    /** Focus style: true = accent ring + glow, false = the original solid fill. */
    static boolean glowFocus = true;

    static void loadTheme(Context c) {
        glowFocus = U.prefs(c).getBoolean("focus_glow", true);
        surface = U.prefs(c).getInt("surface", 0);
        if (surface == 1) { GLASS = 0x9C0A0A0E; GLASS_STRONG = 0xB00A0A0E; LINE = 0; MENU_ROW = 0x1EFFFFFF; }          // smoke
        else if (surface == 2) { GLASS = 0xA62E3544; GLASS_STRONG = 0xB8363E4E; LINE = 0; MENU_ROW = 0x22FFFFFF; }      // slate (semi-transparent)
        else { GLASS = 0x14FFFFFF; GLASS_STRONG = 0x1FFFFFFF; LINE = 0x29FFFFFF; MENU_ROW = 0x14FFFFFF; }
        String id = U.prefs(c).getString("theme", "ember");
        for (String[] t : THEMES) if (t[0].equals(id)) { BG = Color.parseColor(t[2]); themeAccent = Color.parseColor(t[3]); return; }
    }

    static float scale = 1f;

    static void init(Context c) {
        int w = Math.max(c.getResources().getDisplayMetrics().widthPixels, c.getResources().getDisplayMetrics().heightPixels);
        scale = w / 960f; // 960 dp across the screen, like Veil
        loadTheme(c);
    }

    static int dp(float v) { return Math.round(v * scale); }
    static float dpf(float v) { return v * scale; }

    // ------------------------------------------------------------------ Manrope

    private static final Map<Integer, Typeface> fonts = new HashMap<>();

    static Typeface font(Context c, int weight) {
        Typeface t = fonts.get(weight);
        if (t != null) return t;
        try {
            t = new Typeface.Builder(c.getAssets(), "manrope.ttf")
                    .setFontVariationSettings("'wght' " + weight)
                    .setWeight(weight)
                    .build();
        } catch (Throwable e) {
            t = Typeface.create("sans-serif", weight >= 600 ? Typeface.BOLD : Typeface.NORMAL);
        }
        fonts.put(weight, t);
        return t;
    }

    static TextView text(Context c, String s, float sp, int weight, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, dpf(sp));
        t.setTypeface(font(c, weight));
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        return t;
    }

    static TextView caps(Context c, String s, float sp, int color) {
        TextView t = text(c, s, sp, 700, color);
        t.setLetterSpacing(0.14f);
        t.setAllCaps(true);
        return t;
    }

    static GradientDrawable shape(int fill, float radiusDp, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dpf(radiusDp));
        if (stroke != 0) g.setStroke(Math.max(1, dp(1)), stroke);
        return g;
    }

    static void round(View v, final float radiusDp) {
        v.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline o) {
                float r = radiusDp < 0 ? view.getHeight() / 2f : dpf(radiusDp);
                o.setRoundRect(0, 0, view.getWidth(), view.getHeight(), r);
            }
        });
        v.setClipToOutline(true);
    }

    static void circle(View v) {
        v.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline o) { o.setOval(0, 0, view.getWidth(), view.getHeight()); }
        });
        v.setClipToOutline(true);
    }

    // ------------------------------------------------------------------ pills

    /** A Veil pill: glass capsule with a hairline, solid white with dark text when focused. */
    static LinearLayout pill(Context c, float heightDp, float padStart, float padEnd) {
        final LinearLayout p = new LinearLayout(c);
        p.setOrientation(LinearLayout.HORIZONTAL);
        p.setGravity(Gravity.CENTER_VERTICAL);
        p.setPadding(dp(padStart), 0, dp(padEnd), 0);
        p.setFocusable(true);
        p.setFocusableInTouchMode(true);
        p.setBackground(shape(GLASS, heightDp / 2f, LINE));
        p.setElevation(0.01f);
        p.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(heightDp)));
        final float r = heightDp / 2f;
        p.setOnFocusChangeListener((v, has) -> stylePill(p, has, r, null));
        return p;
    }

    static void stylePill(ViewGroup p, boolean focused, float radiusDp, Runnable extra) {
        stylePill(p, focused, radiusDp, extra, GLASS);
    }

    static void stylePill(ViewGroup p, boolean focused, float radiusDp, Runnable extra, int fillColor) {
        if (!glowFocus) {
            p.setBackground(focused ? shape(TEXT, radiusDp, 0) : shape(fillColor, radiusDp, LINE));
            tint(p, focused ? INK : TEXT);
        } else {
            p.setBackground(focused ? glowShape(radiusDp) : shape(fillColor, radiusDp, LINE));
            tint(p, TEXT);
            if (focused) tintIcons(p, accent());
            glow(p, focused);
        }
        p.animate().scaleX(focused ? 1.03f : 1f).scaleY(focused ? 1.03f : 1f)
                .setDuration(160).setInterpolator(new DecelerateInterpolator()).start();
        if (extra != null) extra.run();
    }

    /** Focused look: the glass stays, with a ring in the theme colour and a soft glow drawn around it. */
    static android.graphics.drawable.Drawable glowShape(float radiusDp) {
        return new Glow(radiusDp, 0.14f);
    }

    /** A rounded ring with a blurred halo in the theme colour. Draws outside its bounds (parents must not clip). */
    static class Glow extends android.graphics.drawable.Drawable {
        private final float r;
        private final float fillAlpha;
        private final android.graphics.Paint halo = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.Paint fill = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.Paint ring = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.RectF rect = new android.graphics.RectF();
        private final android.graphics.Path path = new android.graphics.Path();

        private final int mode; // 0 ring + halo, 1 ring only, 2 halo only

        Glow(float radiusDp, float fillAlpha) { this(radiusDp, fillAlpha, 0); }

        Glow(float radiusDp, float fillAlpha, int mode) {
            this.mode = mode;
            this.r = dpf(radiusDp);
            this.fillAlpha = fillAlpha;
            int a = accent();
            halo.setStyle(android.graphics.Paint.Style.STROKE);
            halo.setStrokeWidth(dpf(6));
            halo.setColor(U.alpha(a, 0.5f));
            halo.setMaskFilter(new android.graphics.BlurMaskFilter(dpf(7), android.graphics.BlurMaskFilter.Blur.NORMAL));
            fill.setColor(U.alpha(a, fillAlpha));
            ring.setStyle(android.graphics.Paint.Style.STROKE);
            ring.setStrokeWidth(Math.max(2, dp(2)));
            ring.setColor(glowFocus ? a : TEXT);
        }

        @Override public void draw(android.graphics.Canvas c) {
            // Read the accent every time: with "colours from artwork" it changes a moment after focus lands.
            int a = accent();
            halo.setColor(U.alpha(a, 0.5f));
            fill.setColor(U.alpha(a, fillAlpha));
            ring.setColor(glowFocus ? a : TEXT);
            android.graphics.Rect b = getBounds();
            float s = ring.getStrokeWidth() / 2f;
            rect.set(b.left + s, b.top + s, b.right - s, b.bottom - s);
            if (mode != 1 && glowFocus) {
                // Only outward: cut the shape itself out of the halo so nothing spills inside the item.
                c.save();
                path.rewind();
                path.addRoundRect(new android.graphics.RectF(b), r, r, android.graphics.Path.Direction.CW);
                c.clipOutPath(path);
                c.drawRoundRect(rect, r, r, halo);
                c.restore();
            }
            if (mode == 2) return;
            if (fillAlpha > 0f) c.drawRoundRect(rect, r, r, fill);
            c.drawRoundRect(rect, r, r, ring);
        }
        @Override public void setAlpha(int alpha) {}
        @Override public void setColorFilter(android.graphics.ColorFilter cf) {}
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }

    private static final int R_HALO = 0x7f0f0102;

    /**
     * Glow for a tile that clips itself to rounded corners: the halo is drawn in the row's overlay instead,
     * sized to the tile's focused (scaled) bounds, and removed again on blur.
     */
    static void tileGlow(View t, boolean on, float radiusDp, float scale) {
        if (!glowFocus || !(t.getParent() instanceof ViewGroup)) return;
        ViewGroup row = (ViewGroup) t.getParent();
        row.setClipChildren(false);
        Object old = t.getTag(R_HALO);
        if (old instanceof android.graphics.drawable.Drawable) {
            row.getOverlay().remove((android.graphics.drawable.Drawable) old);
            t.setTag(R_HALO, null);
        }
        if (!on) return;
        android.graphics.drawable.Drawable d = new Glow(radiusDp * scale, 0f, 2);
        float cx = (t.getLeft() + t.getRight()) / 2f, cy = (t.getTop() + t.getBottom()) / 2f;
        float hw = t.getWidth() * scale / 2f, hh = t.getHeight() * scale / 2f;
        d.setBounds(Math.round(cx - hw), Math.round(cy - hh), Math.round(cx + hw), Math.round(cy + hh));
        row.getOverlay().add(d);
        t.setTag(R_HALO, d);
    }

    /** Lets a view's glow spill past its edges. */
    static void glow(View v, boolean on) {
        if (v instanceof ViewGroup) ((ViewGroup) v).setClipChildren(false);
        if (v.getParent() instanceof ViewGroup) ((ViewGroup) v.getParent()).setClipChildren(false);
    }

    /** Recolour only the drawn icons (not text) inside a view. */
    static void tintIcons(View v, int color) {
        if (v instanceof Icon) ((Icon) v).setColor(color);
        else if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) tintIcons(g.getChildAt(i), color);
        }
    }

    /** Recolour text and icons inside a pill (dim lines keep their dimness). */
    static void tint(View v, int color) {
        if (v instanceof TextView) {
            TextView t = (TextView) v;
            Object dim = t.getTag(R_DIM);
            t.setTextColor(dim != null ? U.alpha(color, (Float) dim) : color);
        } else if (v instanceof Icon) {
            ((Icon) v).setColor(color);
        } else if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) tint(g.getChildAt(i), color);
        }
    }

    static final int R_DIM = 0x7f0f0101;

    static TextView dim(TextView t, float alpha) {
        t.setTag(R_DIM, alpha);
        t.setTextColor(U.alpha(TEXT, alpha));
        return t;
    }

    static void ellipsize(TextView t) {
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
    }

    /** Round glass icon button (30 dp), white when focused. */
    /** Shrinks the whole page a little so edges and focus rings stay on the physical screen (projector overscan). */
    /**
     * Focused items grow a little and their outline sits on their edge, so no container may cut them off:
     * every container stops clipping at its padding, and non-scrolling containers stop clipping children.
     * Re-applied after each layout so rows filled in later are covered too (setters are no-ops when unchanged).
     */
    static void unclipAll(final View root) {
        unclip(root);
        root.getViewTreeObserver().addOnGlobalLayoutListener(() -> unclip(root));
    }

    private static void unclip(View v) {
        if (!(v instanceof android.view.ViewGroup)) return;
        android.view.ViewGroup g = (android.view.ViewGroup) v;
        g.setClipToPadding(false);
        boolean scrolls = g instanceof android.widget.ScrollView || g instanceof android.widget.HorizontalScrollView
                || g instanceof android.widget.AbsListView;
        if (!scrolls) g.setClipChildren(false);
        for (int i = 0; i < g.getChildCount(); i++) unclip(g.getChildAt(i));
    }

    static int edge(Context c) { return U.prefs(c).getInt("edge", 64); }

    /** Backgrounds (the first {@code bleed} children of root) stay full screen; the content moves in from the edges. */
    static void fit(android.app.Activity a, int bleed) {
        View content = a.findViewById(android.R.id.content);
        if (content != null) unclipAll(content);
        if (!(content instanceof android.view.ViewGroup)) return;
        android.view.ViewGroup holder = (android.view.ViewGroup) content;
        if (holder.getChildCount() == 0 || !(holder.getChildAt(0) instanceof android.view.ViewGroup)) return;
        android.view.ViewGroup root = (android.view.ViewGroup) holder.getChildAt(0);
        float s = 1f - 2f * (edge(a) - 48) / 960f;
        for (int i = bleed; i < root.getChildCount(); i++) {
            root.getChildAt(i).setScaleX(s);
            root.getChildAt(i).setScaleY(s);
        }
    }

    static View iconButton(Context c, Icon icon, String label) {
        final android.widget.FrameLayout b = new android.widget.FrameLayout(c);
        b.setFocusable(true);
        b.setFocusableInTouchMode(true);
        b.setContentDescription(label);
        b.setBackground(shape(GLASS_STRONG, 15, 0));
        b.addView(icon, U.flp(dp(13), dp(13), Gravity.CENTER));
        icon.setColor(TEXT);
        b.setOnFocusChangeListener((v, has) -> {
            b.setBackground(shape(has ? TEXT : GLASS_STRONG, 15, 0));
            icon.setColor(has ? INK : TEXT);
            b.animate().scaleX(has ? 1.1f : 1f).scaleY(has ? 1.1f : 1f).setDuration(150).start();
        });
        return b;
    }

    static ImageView image(Context c, ImageView.ScaleType st) {
        ImageView iv = new ImageView(c);
        iv.setScaleType(st);
        return iv;
    }
}
