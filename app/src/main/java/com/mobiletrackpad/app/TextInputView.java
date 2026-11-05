package com.mobiletrackpad.app;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Phone IME editor for sending text to the focused field on the computer. */
final class TextInputView extends LinearLayout {
    private final HidController hid;
    private final EditText editor;
    private final TextView status;

    TextInputView(Context context, HidController hid) {
        super(context);
        this.hid = hid;
        setOrientation(VERTICAL);
        setFocusableInTouchMode(true);
        setPadding(dp(20),dp(10),dp(20),dp(16));
        setBackgroundColor(Color.rgb(24,28,35));

        TextView title = new TextView(context);
        title.setText("文字输入");
        title.setTextSize(22);
        title.setTypeface(null,Typeface.BOLD);
        title.setTextColor(Color.WHITE);
        addView(title,new LayoutParams(-1,dp(42)));

        status = new TextView(context);
        status.setTextSize(13);
        status.setTextColor(Color.rgb(159,177,201));
        addView(status,new LayoutParams(-1,dp(38)));

        editor = new EditText(context);
        editor.setTextSize(18);
        editor.setTextColor(Color.WHITE);
        editor.setHintTextColor(Color.rgb(129,143,161));
        editor.setHint("在这里输入文字；可使用手机键盘自带的语音输入…");
        editor.setGravity(Gravity.TOP | Gravity.START);
        editor.setInputType(android.text.InputType.TYPE_CLASS_TEXT |
            android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE |
            android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        editor.setBackground(shape(Color.rgb(34,42,54),14));
        editor.setPadding(dp(16),dp(12),dp(16),dp(12));
        addView(editor,new LayoutParams(-1,0,1));

        TextView send = new TextView(context);
        send.setText("发送到电脑光标处");
        send.setTextSize(16);
        send.setTypeface(null,Typeface.BOLD);
        send.setTextColor(Color.WHITE);
        send.setGravity(Gravity.CENTER);
        send.setBackground(shape(Color.rgb(55,100,204),14));
        LayoutParams sendParams = new LayoutParams(-1,dp(56));
        sendParams.topMargin=dp(12);
        addView(send,sendParams);
        send.setOnClickListener(v -> send());
        refreshConnection();
    }

    private int dp(float n) { return (int)(n*getResources().getDisplayMetrics().density+.5f); }
    private GradientDrawable shape(int color,int radius) {
        GradientDrawable d=new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d;
    }

    void refreshConnection() {
        status.setText("连接：" + hid.connectionSummary() + " · 请先在电脑上定位光标");
    }

    void showKeyboard() {
        editor.requestFocus();
        editor.post(() -> {
            InputMethodManager imm=(InputMethodManager)getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm!=null) imm.showSoftInput(editor,InputMethodManager.SHOW_IMPLICIT);
        });
    }

    void hideKeyboard() {
        InputMethodManager imm=(InputMethodManager)getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm!=null) imm.hideSoftInputFromWindow(editor.getWindowToken(),0);
        editor.clearFocus();
    }

    private void send() {
        String value=editor.getText().toString();
        if (value.isEmpty()) { status.setText("请先输入文字"); return; }
        hideKeyboard();
        status.setText("正在发送…");
        hid.sendText(value, result -> {
            if (result==HidController.TEXT_USB) status.setText("电脑端已接收输入事件，请检查当前光标位置");
            else if (result==HidController.TEXT_BLUETOOTH) status.setText("蓝牙按键已排队发送，请检查电脑光标位置");
            else if (result==HidController.TEXT_NEEDS_USB) status.setText("蓝牙 HID 无法直接输入中文；请连接 USB Bridge 后重试");
            else status.setText("发送失败：请检查电脑连接，并更新电脑端 USB Bridge");
        });
    }
}
