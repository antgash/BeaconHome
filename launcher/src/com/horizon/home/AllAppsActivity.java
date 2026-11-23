package com.horizon.home;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/** All apps, Veil-style: big app banners on a calm background, one alphabetical list. */
public class AllAppsActivity extends Activity {
    private LinearLayout list;
    private SharedPreferences prefs;
    private List<Apps.App> allApps = new java.util.ArrayList<>();

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        U.init(this);
        V.init(this);
        prefs = U.prefs(this);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(V.BG);
        View glow = Ui.gradient(this, GradientDrawable.Orientation.BL_TR, U.alpha(V.accent(), 0.10f), 0x00000000, 0x00000000);
        root.addView(glow, new FrameLayout.LayoutParams(U.MATCH, U.MATCH));
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.setVerticalFadingEdgeEnabled(true);
        sv.setFadingEdgeLength(V.dp(30));
        sv.setClipToPadding(false);
        list = U.col(this);
        list.setPadding(V.dp(48), V.dp(34), V.dp(48), V.dp(40));
        list.setClipChildren(false);
        sv.addView(list, new FrameLayout.LayoutParams(U.MATCH, U.WRAP));
        root.addView(sv, new FrameLayout.LayoutParams(U.MATCH, U.MATCH));
        setContentView(root);
        V.fit(this, 1);
        U.IO.execute(() -> {
            final List<Apps.App> apps = Apps.load(getApplicationContext());
            U.MAIN.post(() -> build(apps));
        });
    }

    private void build(List<Apps.App> apps) {
        list.removeAllViews();
        list.addView(V.text(this, "All apps", 24, 800, V.TEXT));
        allApps = apps;
        View first = null;
        List<Apps.App> sorted = new java.util.ArrayList<>(apps);
        java.util.Collections.sort(sorted, (x, y) -> x.label.compareToIgnoreCase(y.label));
        {
            List<Apps.App> in = sorted;
            ((LinearLayout.LayoutParams) list.getChildAt(0).getLayoutParams()).bottomMargin = V.dp(16);
            int per = 5, gap = V.dp(12);
            int w = (U.W - V.dp(96) - gap * (per - 1)) / per, ht = Math.round(w * 9f / 16f);
            LinearLayout row = null;
            for (int i = 0; i < in.size(); i++) {
                if (i % per == 0) {
                    row = U.row(this);
                    row.setClipChildren(false);
                    LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(U.MATCH, U.WRAP);
                    rp.bottomMargin = gap;
                    list.addView(row, rp);
                }
                View t = tile(in.get(i), w, ht);
                LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(w, ht);
                tp.rightMargin = i % per == per - 1 ? 0 : gap;
                row.addView(t, tp);
                if (first == null) first = t;
            }
        }
        if (first != null) { final View f = first; f.post(f::requestFocus); }
    }

    private View tile(final Apps.App a, int w, int h) {
        final FrameLayout t = new FrameLayout(this);
        t.setFocusable(true);
        t.setFocusableInTouchMode(true);
        V.round(t, 14);
        if (a.banner != null) {
            t.setBackgroundColor(0xFF1A1C22);
            ImageView iv = V.image(this, ImageView.ScaleType.CENTER_CROP);
            iv.setImageDrawable(a.banner);
            t.addView(iv, new FrameLayout.LayoutParams(U.MATCH, U.MATCH));
        } else {
            t.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    new int[] {U.mix(a.color, 0xFFFFFFFF, 0.1f), U.mix(a.color, 0xFF000000, 0.5f)}));
            ImageView iv = V.image(this, ImageView.ScaleType.FIT_CENTER);
            iv.setImageDrawable(a.icon);
            int is = Math.round(h * 0.42f);
            t.addView(iv, U.flp(is, is, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, h * 0.14f / U.k, 0, 0));
            TextView tv = V.text(this, a.label, 12, 600, V.TEXT);
            tv.setGravity(Gravity.CENTER);
            V.ellipsize(tv);
            t.addView(tv, U.flp(U.MATCH, U.WRAP, Gravity.BOTTOM, 8, 0, 8, 9));
        }
        final View ring = new View(this);
        GradientDrawable g = V.shape(0, 14, 0);
        g.setStroke(V.dp(2), V.TEXT);
        ring.setBackground(g);
        ring.setAlpha(0f);
        t.addView(ring, new FrameLayout.LayoutParams(U.MATCH, U.MATCH));
        t.setOnFocusChangeListener((v, has) -> {
            v.animate().scaleX(has ? 1.06f : 1f).scaleY(has ? 1.06f : 1f).translationZ(has ? V.dpf(12) : 0)
                    .setDuration(160).setInterpolator(new DecelerateInterpolator()).start();
            ring.animate().alpha(has ? 1f : 0f).setDuration(140).start();
        });
        t.setOnClickListener(v -> Apps.launch(this, a));
        t.setOnLongClickListener(v -> {
            final boolean pinned = Apps.isPinned(this, allApps, a.pkg);
            String[] items = {pinned ? "Unpin from Home" : "Pin to Home", "Open", "App info", "Hide"};
            new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert).setTitle(a.label).setItems(items, (d, which) -> {
                switch (which) {
                    case 0:
                        if (pinned) Apps.unpin(this, allApps, a.pkg); else Apps.pin(this, allApps, a.pkg);
                        break;
                    case 1: Apps.launch(this, a); break;
                    case 2: Apps.start(this, new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + a.pkg))); break;
                    case 3: prefs.edit().putBoolean("hide_" + a.pkg, true).apply(); recreate(); break;
                }
            }).show();
            return true;
        });
        t.setContentDescription(a.label);
        return t;
    }
}
