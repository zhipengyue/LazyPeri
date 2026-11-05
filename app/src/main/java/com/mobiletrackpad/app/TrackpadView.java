package com.mobiletrackpad.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewConfiguration;

/** Gesture recognition stays local; only mouse and keyboard reports leave the phone. */
final class TrackpadView extends View {
    private final HidController hid;
    private final Paint paint = new Paint(3);
    private boolean windows;
    private int fingers;
    private float lastX, lastY, downX, downY, moveRemainderX, moveRemainderY, scrollRemainder;
    private long downTime;
    private boolean moved, switched, dragging, longPressed, externalLeftHeld;
    private final int touchSlop;
    private final Runnable longPress;

    TrackpadView(Context context, HidController hid) {
        super(context);
        this.hid = hid;
        longPress = () -> {
            if (fingers == 1 && !moved && !dragging && !externalLeftHeld) {
                longPressed = true;
                hid.click(true);
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                invalidate();
            }
        };
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        setBackgroundColor(Color.rgb(24, 28, 35));
    }

    void setWindows(boolean value) { windows = value; invalidate(); }
    void setExternalLeftHeld(boolean held) {
        externalLeftHeld = held;
        if (held) removeCallbacks(longPress);
    }
    void cancelGesture() { reset(); }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float d = getResources().getDisplayMetrics().density;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.2f*d);
        paint.setColor(Color.rgb(64, 72, 82));
        float pad = 14*d;
        canvas.drawRoundRect(pad,pad,getWidth()-pad,getHeight()-pad,24*d,24*d,paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setColor(Color.rgb(124, 135, 148));
        paint.setTextSize(14*d);
        String hint = dragging ? "三指拖拽中" : switched ? "已发送桌面切换" : longPressed ? "右键菜单" : "触摸区域";
        canvas.drawText(hint,getWidth()/2f,getHeight()/2f,paint);
    }

    private float[] centroid(MotionEvent event) {
        float x=0, y=0;
        for (int i=0; i<event.getPointerCount(); i++) { x += event.getX(i); y += event.getY(i); }
        return new float[] {x/event.getPointerCount(), y/event.getPointerCount()};
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        int action = e.getActionMasked();
        if (action == MotionEvent.ACTION_CANCEL) { reset(); return true; }
        if (action == MotionEvent.ACTION_UP) {
            if (dragging) hid.leftUp();
            else if (!externalLeftHeld && !longPressed && !moved && e.getEventTime()-downTime < 260 && fingers == 1) hid.click(false);
            reset(); return true;
        }
        if (action == MotionEvent.ACTION_POINTER_UP) {
            removeCallbacks(longPress);
            // A two-finger tap is a right click; avoid turning its last lifted finger into a left click.
            if (fingers == 2 && !moved && e.getEventTime()-downTime < 260) hid.click(true);
            if (dragging) { hid.leftUp(); dragging = false; invalidate(); }
            fingers = 0;
            return true;
        }
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            removeCallbacks(longPress);
            int count = e.getPointerCount();
            float[] p = centroid(e);
            if (dragging && count != 3) { hid.leftUp(); dragging = false; }
            fingers = count;
            lastX = downX = p[0]; lastY = downY = p[1];
            downTime = e.getEventTime(); moved = false; switched = false; longPressed = false;
            moveRemainderX = moveRemainderY = scrollRemainder = 0;
            if (count == 3) {
                hid.leftDown(); dragging = true;
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            }
            if (count == 1 && !externalLeftHeld)
                postDelayed(longPress, ViewConfiguration.getLongPressTimeout());
            invalidate();
            return true;
        }
        if (action == MotionEvent.ACTION_MOVE) {
            if (fingers != e.getPointerCount() || fingers == 0) return true;
            float[] p = centroid(e);
            float dx = p[0]-lastX, dy = p[1]-lastY;
            float totalX = p[0]-downX, totalY = p[1]-downY;
            if (fingers == 1 && Math.hypot(totalX,totalY) > touchSlop) removeCallbacks(longPress);
            if (longPressed) return true;
            if (Math.hypot(totalX,totalY) > 12*getResources().getDisplayMetrics().density) moved = true;
            if (fingers == 1 || fingers == 3) {
                float gain = fingers == 3 ? 1.8f : 1.25f;
                moveRemainderX += dx*gain;
                moveRemainderY += dy*gain;
                int mx = (int)moveRemainderX, my = (int)moveRemainderY;
                moveRemainderX -= mx; moveRemainderY -= my;
                if (mx != 0 || my != 0) hid.move(mx,my);
            } else if (fingers == 2) {
                scrollRemainder += -dy/(24*getResources().getDisplayMetrics().density);
                int ticks = (int)scrollRemainder;
                scrollRemainder -= ticks;
                if (ticks != 0) hid.scroll(ticks);
            } else if (fingers == 4 && !switched &&
                    Math.abs(totalX) > Math.max(48*getResources().getDisplayMetrics().density, getWidth()*0.06f) &&
                    Math.abs(totalX) > Math.abs(totalY)*1.2f) {
                hid.switchDesktop(windows,totalX > 0);
                switched = true;
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                invalidate();
            }
            lastX=p[0]; lastY=p[1];
            return true;
        }
        return true;
    }

    private void reset() {
        removeCallbacks(longPress);
        if (dragging) hid.leftUp();
        dragging=false; fingers=0; moved=false; switched=false; longPressed=false;
        invalidate();
    }
}
