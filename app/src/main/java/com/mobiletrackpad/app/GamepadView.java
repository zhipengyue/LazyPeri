package com.mobiletrackpad.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.SparseIntArray;
import android.view.HapticFeedbackConstants;
import android.view.Gravity;
import android.view.MotionEvent;
import android.widget.FrameLayout;
import android.widget.TextView;

/** Multi-touch controls mapped to a standard HID gamepad report. */
final class GamepadView extends FrameLayout {
    private static final int LEFT_STICK=1, DPAD=2, RIGHT_STICK=3;
    private static final int A=4, B=5, X=6, Y=7, L1=8, R1=9, SELECT=10, START=11;
    private static final int LT=12, RT=13;
    private final HidController hid;
    private final Paint paint = new Paint(3);
    private final SparseIntArray zones = new SparseIntArray();
    private final TextView backButton, guideButton;
    private int buttons, hat=8, lx, ly, rx, ry;

    GamepadView(Context context, HidController hid, Runnable back, Runnable guide) {
        super(context);
        this.hid = hid;
        setWillNotDraw(false);
        setMotionEventSplittingEnabled(true);
        setBackgroundColor(Color.rgb(24,28,35));
        backButton = navigationButton("‹", "返回主界面", 24, back);
        guideButton = navigationButton("指南", "打开使用指南", 12, guide);
    }

    private TextView navigationButton(String label, String description, int size, Runnable action) {
        TextView button = new TextView(getContext());
        button.setText(label);
        button.setContentDescription(description);
        button.setTextSize(size);
        button.setTextColor(Color.WHITE);
        button.setGravity(Gravity.CENTER);
        button.setIncludeFontPadding(false);
        button.setBackground(UiKit.pill(getContext(), Color.rgb(57,69,85)));
        button.setOnClickListener(v -> action.run());
        addView(button, new LayoutParams((int)(42*dp()), (int)(32*dp())));
        return button;
    }

    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        layoutNavigation(backButton, .32f);
        layoutNavigation(guideButton, .68f);
    }

    private void layoutNavigation(TextView button, float horizontalPosition) {
        int width = Math.min(button.getMeasuredWidth(), Math.round(getWidth() * .12f));
        int height = button.getMeasuredHeight();
        int x = Math.round(getWidth() * horizontalPosition - width / 2f);
        int y = Math.round(getHeight() * .14f - height / 2f);
        button.layout(x,y,x+width,y+height);
    }

    void releaseAll() {
        zones.clear(); buttons=0; hat=8; lx=ly=rx=ry=0;
        hid.releaseGamepad();
        invalidate();
    }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        float w=getWidth(), h=getHeight(), d=getResources().getDisplayMetrics().density;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(32,39,50));
        c.drawRoundRect(8*d,8*d,w-8*d,h-8*d,26*d,26*d,paint);

        drawShoulder(c,"LT",w*.15f,h*.085f,w*.21f,h*.12f,(buttons & (1<<8))!=0);
        drawShoulder(c,"RT",w*.85f,h*.085f,w*.21f,h*.12f,(buttons & (1<<9))!=0);
        drawShoulder(c,"LB",w*.15f,h*.215f,w*.21f,h*.12f,(buttons & (1<<4))!=0);
        drawShoulder(c,"RB",w*.85f,h*.215f,w*.21f,h*.12f,(buttons & (1<<5))!=0);
        drawPill(c,"SELECT",w*.44f,h*.14f,(buttons & (1<<6))!=0);
        drawPill(c,"START",w*.56f,h*.14f,(buttons & (1<<7))!=0);

        float leftR=Math.min(h*.25f,w*.105f);
        float rightR=Math.min(h*.23f,w*.095f);
        drawStick(c,"L",w*.14f,h*.66f,leftR,lx,ly);
        drawDpad(c,w*.36f,h*.66f,Math.min(h*.19f,w*.085f));
        drawStick(c,"R",w*.62f,h*.66f,rightR,rx,ry);

        float faceR=Math.min(h*.075f,w*.034f);
        drawFace(c,"Y",w*.85f,h*.44f,faceR,Color.rgb(239,195,73),(buttons & (1<<3))!=0);
        drawFace(c,"X",w*.78f,h*.62f,faceR,Color.rgb(83,155,238),(buttons & (1<<2))!=0);
        drawFace(c,"A",w*.85f,h*.80f,faceR,Color.rgb(74,205,151),(buttons & 1)!=0);
        drawFace(c,"B",w*.92f,h*.62f,faceR,Color.rgb(235,103,109),(buttons & (1<<1))!=0);
    }

    private void drawShoulder(Canvas c,String name,float x,float y,float width,float height,boolean active) {
        paint.setColor(active ? Color.rgb(72,119,222) : Color.rgb(57,69,85));
        c.drawRoundRect(x-width/2,y-height/2,x+width/2,y+height/2,12*dp(),12*dp(),paint);
        label(c,name,x,y+5*dp(),15);
    }
    private void drawPill(Canvas c,String name,float x,float y,boolean active) {
        float d=dp();
        paint.setColor(active ? Color.rgb(72,119,222) : Color.rgb(57,69,85));
        float halfWidth = Math.min(34*d,getWidth()*.055f);
        c.drawRoundRect(x-halfWidth,y-14*d,x+halfWidth,y+14*d,12*d,12*d,paint);
        label(c,name,x,y+4*d,Math.min(11,halfWidth/(2*d)));
    }
    private void drawStick(Canvas c,String name,float x,float y,float radius,int ax,int ay) {
        paint.setColor(Color.rgb(15,19,25)); c.drawCircle(x,y,radius,paint);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(2*dp());
        paint.setColor(Color.rgb(78,96,122)); c.drawCircle(x,y,radius,paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(75,111,177));
        c.drawCircle(x+ax/127f*radius*.55f,y+ay/127f*radius*.55f,radius*.43f,paint);
        label(c,name,x+ax/127f*radius*.55f,y+ay/127f*radius*.55f+5*dp(),14);
    }
    private void drawDpad(Canvas c,float x,float y,float radius) {
        paint.setColor(Color.rgb(17,22,29)); c.drawCircle(x,y,radius,paint);
        drawDirection(c,"▲",x,y-radius*.58f,hat==0 || hat==1 || hat==7);
        drawDirection(c,"▶",x+radius*.58f,y,hat==1 || hat==2 || hat==3);
        drawDirection(c,"▼",x,y+radius*.58f,hat==3 || hat==4 || hat==5);
        drawDirection(c,"◀",x-radius*.58f,y,hat==5 || hat==6 || hat==7);
    }
    private void drawDirection(Canvas c,String glyph,float x,float y,boolean active) {
        float r=15*dp();
        paint.setColor(active ? Color.rgb(82,140,241) : Color.rgb(62,76,96));
        c.drawRoundRect(x-r,y-r,x+r,y+r,7*dp(),7*dp(),paint);
        label(c,glyph,x,y+5*dp(),13);
    }
    private void drawFace(Canvas c,String name,float x,float y,float radius,int color,boolean active) {
        if (active) {
            paint.setColor((color & 0x00ffffff) | 0x44000000);
            c.drawCircle(x,y,radius + 5*dp(),paint);
            radius *= .90f;
        }
        paint.setColor(active ? color : Color.rgb(52,61,75)); c.drawCircle(x,y,radius,paint);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(2*dp());
        paint.setColor(color); c.drawCircle(x,y,radius,paint);
        paint.setStyle(Paint.Style.FILL);
        label(c,name,x,y+5*dp(),15);
    }
    private void label(Canvas c,String text,float x,float y,float sp) {
        paint.setColor(Color.WHITE); paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(sp*dp()); paint.setFakeBoldText(true);
        c.drawText(text,x,y,paint); paint.setFakeBoldText(false);
    }
    private float dp() { return getResources().getDisplayMetrics().density; }

    private int pickZone(float x,float y) {
        float w=getWidth(), h=getHeight();
        if (y<h*.28f) {
            if (x<w*.29f) return y<h*.15f ? LT : L1;
            if (x>w*.71f) return y<h*.15f ? RT : R1;
            return x<w*.5f ? SELECT : START;
        }
        if (x<w*.25f) return LEFT_STICK;
        if (x<w*.49f) return DPAD;
        if (x<w*.72f) return RIGHT_STICK;
        float[][] faces = {{w*.85f,h*.80f},{w*.92f,h*.62f},{w*.78f,h*.62f},{w*.85f,h*.44f}};
        int best=0; float distance=Float.MAX_VALUE;
        for (int i=0;i<faces.length;i++) {
            float dx=x-faces[i][0], dy=y-faces[i][1], score=dx*dx+dy*dy;
            if (score<distance) {distance=score; best=i;}
        }
        return A+best;
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        int action=event.getActionMasked();
        if (action==MotionEvent.ACTION_CANCEL) { releaseAll(); return true; }
        if (action==MotionEvent.ACTION_DOWN || action==MotionEvent.ACTION_POINTER_DOWN) {
            int index=event.getActionIndex();
            zones.put(event.getPointerId(index),pickZone(event.getX(index),event.getY(index)));
        } else if (action==MotionEvent.ACTION_UP || action==MotionEvent.ACTION_POINTER_UP) {
            zones.delete(event.getPointerId(event.getActionIndex()));
        }
        updateState(event);
        return true;
    }

    private void updateState(MotionEvent e) {
        int nextButtons=0, nextHat=8, nextLx=0, nextLy=0, nextRx=0, nextRy=0;
        float w=getWidth(), h=getHeight();
        for (int i=0;i<zones.size();i++) {
            int index=e.findPointerIndex(zones.keyAt(i));
            if (index<0) continue;
            int zone=zones.valueAt(i);
            float x=e.getX(index), y=e.getY(index);
            if (zone==LEFT_STICK) {
                int[] axes=axes(x,y,w*.14f,h*.66f,Math.min(h*.25f,w*.105f));
                nextLx=axes[0]; nextLy=axes[1];
            } else if (zone==RIGHT_STICK) {
                int[] axes=axes(x,y,w*.62f,h*.66f,Math.min(h*.23f,w*.095f));
                nextRx=axes[0]; nextRy=axes[1];
            } else if (zone==DPAD) {
                float dx=x-w*.36f, dy=y-h*.66f;
                if (Math.hypot(dx,dy)>Math.min(h*.19f,w*.085f)*.22f)
                    nextHat=(Math.round((float)((Math.atan2(dy,dx)+Math.PI/2)/(Math.PI/4)))+8)&7;
            } else {
                nextButtons |= 1<<(zone-A);
            }
        }
        if ((nextButtons & ~buttons) != 0 || (nextHat != 8 && nextHat != hat))
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        buttons=nextButtons; hat=nextHat; lx=nextLx; ly=nextLy; rx=nextRx; ry=nextRy;
        hid.gamepadState(buttons,hat,lx,ly,rx,ry);
        invalidate();
    }

    private int[] axes(float x,float y,float cx,float cy,float radius) {
        float dx=(x-cx)/radius, dy=(y-cy)/radius;
        float length=(float)Math.hypot(dx,dy);
        if (length<.12f) return new int[] {0,0};
        if (length>1) {dx/=length; dy/=length;}
        return new int[] {Math.round(dx*127),Math.round(dy*127)};
    }
}
