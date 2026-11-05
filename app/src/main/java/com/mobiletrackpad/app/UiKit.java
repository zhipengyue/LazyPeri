package com.mobiletrackpad.app;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;

/** Small reusable visual components for the native Android UI. */
final class UiKit {
    private UiKit() { }

    static GradientDrawable pill(Context context, int color) {
        float density = context.getResources().getDisplayMetrics().density;
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(18 * density);
        shape.setStroke(Math.max(1, (int) density), Color.rgb(57, 75, 103));
        return shape;
    }

    static GradientDrawable card(Context context, int from, int to) {
        float density = context.getResources().getDisplayMetrics().density;
        GradientDrawable shape = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{from, to});
        shape.setCornerRadius(22 * density);
        shape.setStroke(Math.max(1, (int) density), Color.rgb(63, 83, 111));
        return shape;
    }
}
