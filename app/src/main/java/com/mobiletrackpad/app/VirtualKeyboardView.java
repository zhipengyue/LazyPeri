package com.mobiletrackpad.app;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;

/** Compact HID keyboard; modifiers apply to the next key and then clear. */
final class VirtualKeyboardView extends LinearLayout {
    private final HidController hid;
    private final ArrayList<TextView> modifierButtons = new ArrayList<>();
    private int modifiers;

    VirtualKeyboardView(Context context, HidController hid) {
        super(context);
        this.hid = hid;
        setOrientation(VERTICAL);
        setPadding(dp(5),dp(5),dp(5),dp(5));
        setBackgroundColor(Color.rgb(24,28,35));
        row(new String[]{"Esc","1","2","3","4","5","6","7","8","9","0","−","=","⌫"},
            new int[]{41,30,31,32,33,34,35,36,37,38,39,45,46,42});
        row(new String[]{"Tab","Q","W","E","R","T","Y","U","I","O","P","[","]","\\"},
            new int[]{43,20,26,8,21,23,28,24,12,18,19,47,48,49});
        row(new String[]{"A","S","D","F","G","H","J","K","L",";","'","Enter"},
            new int[]{4,22,7,9,10,11,13,14,15,51,52,40});
        row(new String[]{"Shift","Z","X","C","V","B","N","M",",",".","/","↑"},
            new int[]{-2,29,27,6,25,5,17,16,54,55,56,82});
        row(new String[]{"Ctrl","Alt","Win/⌘","空格","←","↓","→"},
            new int[]{-1,-4,-8,44,80,81,79});
    }

    private int dp(float value) { return (int)(value*getResources().getDisplayMetrics().density+0.5f); }

    private void row(String[] labels, int[] usages) {
        LinearLayout line = new LinearLayout(getContext());
        line.setGravity(Gravity.CENTER);
        LayoutParams row = new LayoutParams(-1,0,1);
        row.setMargins(0,0,0,dp(3));
        addView(line,row);
        for (int i=0;i<labels.length;i++) {
            final int usage=usages[i];
            TextView key = new TextView(getContext());
            key.setText(labels[i]);
            key.setTextSize(labels.length > 12 ? 12 : 14);
            key.setTypeface(null,Typeface.BOLD);
            key.setTextColor(Color.WHITE);
            key.setGravity(Gravity.CENTER);
            key.setBackground(keyBackground(false));
            float weight = usage == 44 ? 4f : usage == 40 || usage == 42 || usage == 43 ? 1.6f :
                usage < 0 ? 1.5f : 1f;
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0,-1,weight);
            params.setMargins(dp(2),0,dp(2),0);
            line.addView(key,params);
            if (usage < 0) modifierButtons.add(key);
            key.setOnTouchListener((view, event) -> {
                int action = event.getActionMasked();
                if (action == MotionEvent.ACTION_DOWN) {
                    view.animate().cancel();
                    view.setScaleX(0.96f);
                    view.setScaleY(0.90f);
                    view.setTranslationY(dp(2));
                } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    view.animate().scaleX(1f).scaleY(1f).translationY(0f).setDuration(90).start();
                }
                return false;
            });
            key.setOnClickListener(v -> {
                if (usage < 0) {
                    modifiers ^= -usage;
                    refreshModifiers();
                } else {
                    hid.sendKey(modifiers,usage);
                    clearModifiers();
                }
            });
        }
    }

    void clearModifiers() { modifiers=0; refreshModifiers(); }

    private void refreshModifiers() {
        for (TextView key: modifierButtons) {
            int bit = key.getText().toString().equals("Ctrl") ? 1 :
                key.getText().toString().equals("Shift") ? 2 :
                key.getText().toString().equals("Alt") ? 4 : 8;
            key.setBackground(keyBackground((modifiers & bit) != 0));
        }
    }

    private StateListDrawable keyBackground(boolean active) {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed}, keyShape(active, true));
        states.addState(new int[]{}, keyShape(active, false));
        return states;
    }

    private GradientDrawable keyShape(boolean active, boolean pressed) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(pressed ? (active ? Color.rgb(102,155,255) : Color.rgb(88,117,158))
            : (active ? Color.rgb(55,100,204) : Color.rgb(45,53,65)));
        bg.setCornerRadius(dp(7));
        bg.setStroke(dp(1), pressed ? Color.rgb(170,205,255) : Color.rgb(66,78,96));
        return bg;
    }
}
