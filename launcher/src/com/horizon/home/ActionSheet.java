package com.horizon.home;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Veil's long-press menu: a small rounded sheet with a dim title and capsule options. The focused option
 * turns solid white with dark text. It opens while OK is still held (a long press), so OK events are
 * swallowed until that press ends; otherwise letting go would "click" the first option.
 */
final class ActionSheet {
    private final Activity act;
    private final String title;
    private final List<String> labels = new ArrayList<>();
    private final List<Integer> icons = new ArrayList<>();
    private final List<Runnable> actions = new ArrayList<>();

    ActionSheet(Activity act, String title) {
        this.act = act;
        this.title = title;
    }

    ActionSheet add(String label, int icon, Runnable run) {
        labels.add(label);
        icons.add(icon);
        actions.add(run);
        return this;
    }

    void show() {
        final Dialog d = new Dialog(act, android.R.style.Theme_DeviceDefault_Dialog_NoActionBar);
        Window w = d.getWindow();
        if (w != null) w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        LinearLayout col = new LinearLayout(act);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(V.dp(9), V.dp(9), V.dp(9), V.dp(9));
        int sheet = (0xED << 24) | (U.mix(V.BG, 0xFFFFFFFF, 0.1f) & 0x00FFFFFF);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(sheet);
        bg.setCornerRadius(V.dpf(18));
        col.setBackground(bg);

        TextView t = V.text(act, title, 10, 500, U.alpha(V.TEXT, 0.65f));
        V.ellipsize(t);
        t.setPadding(V.dp(8), V.dp(4), 0, V.dp(6));
        col.addView(t, new LinearLayout.LayoutParams(U.MATCH, U.WRAP));

        View first = null;
        final LinearLayout list = new LinearLayout(act);
        list.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < labels.size(); i++) {
            final Runnable run = actions.get(i);
            final LinearLayout row = new LinearLayout(act);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(V.dp(11), 0, V.dp(11), 0);
            row.setFocusable(true);
            row.setFocusableInTouchMode(true);
            row.setBackground(V.shape(0, 25, 0));
            Icon ic = new Icon(act, icons.get(i));
            ic.setColor(V.TEXT);
            row.addView(ic, new LinearLayout.LayoutParams(V.dp(12), V.dp(12)));
            TextView lt = V.text(act, labels.get(i), 11.5f, 600, V.TEXT);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(U.WRAP, U.WRAP);
            lp.leftMargin = V.dp(8);
            row.addView(lt, lp);
            row.setOnFocusChangeListener((v, has) -> {
                row.setBackground(V.shape(has ? V.TEXT : 0, 25, 0));
                V.tint(row, has ? V.INK : V.TEXT);
            });
            row.setOnClickListener(v -> { Sfx.select(); d.dismiss(); run.run(); });
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(U.MATCH, V.dp(34));
            rp.topMargin = V.dp(4);
            list.addView(row, rp);
            if (first == null) first = row;
        }
        if (labels.size() > 7) {
            android.widget.ScrollView sv = new android.widget.ScrollView(act);
            sv.setVerticalScrollBarEnabled(false);
            sv.addView(list);
            col.addView(sv, new LinearLayout.LayoutParams(U.MATCH, V.dp(7 * 38 + 4)));
        } else {
            col.addView(list, new LinearLayout.LayoutParams(U.MATCH, U.WRAP));
        }
        d.setContentView(col, new android.view.ViewGroup.LayoutParams(V.dp(220), U.WRAP));

        final boolean[] armed = {false};
        d.setOnKeyListener((dlg, keyCode, ev) -> {
            boolean select = keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER
                    || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER || keyCode == KeyEvent.KEYCODE_BUTTON_A;
            if (armed[0] || !select) return false;
            if (ev.getAction() == KeyEvent.ACTION_DOWN && ev.getRepeatCount() == 0) { armed[0] = true; return false; }
            if (ev.getAction() == KeyEvent.ACTION_UP) armed[0] = true;
            return true;
        });
        d.show();
        Sfx.watch(d.getWindow());
        Sfx.open();
        final View f = first;
        if (f != null) f.post(f::requestFocus);
    }
}
