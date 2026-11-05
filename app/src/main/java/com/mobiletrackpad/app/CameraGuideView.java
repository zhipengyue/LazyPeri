package com.mobiletrackpad.app;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** Full-page USB camera setup guide for both desktop systems. */
final class CameraGuideView extends ScrollView {
    private static final int WHITE = Color.WHITE;
    private static final int MUTED = Color.rgb(182, 198, 219);
    private static final int BLUE = Color.rgb(110, 185, 255);
    private final LinearLayout content;

    CameraGuideView(Context context, Runnable openCamera, Runnable saveMac, Runnable saveWindows) {
        super(context);
        setFillViewport(true);
        setVerticalScrollBarEnabled(false);
        setBackgroundColor(Color.rgb(15, 20, 31));
        content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(18), dp(18), dp(30));
        addView(content, new ScrollView.LayoutParams(-1, -2));

        text(content, "手机摄像头 · USB 使用指南", 23, WHITE, true, 0);
        text(content, "手机采集画面 → USB Bridge 传输 → OBS Virtual Camera → 电脑应用选择摄像头", 14, MUTED, false, 8);
        text(content, "OBS 提供电脑可识别的虚拟摄像头；桥接程序把手机画面送进去。蓝牙连接不传视频。", 14, MUTED, false, 10);
        text(content, "电脑端桥接文件已包含在本 APK 中。点击下方按钮保存到手机，再传到电脑解压；保存过程无需联网。项目源码中也可在 releases/懒人外设-macOS-全功能-USB-Bridge.zip 或 releases/懒人外设-Windows-全功能-USB-Bridge.zip 找到对应文件。", 14, BLUE, false, 10);

        platform("macOS",
            "打开 OBS，点一次“启动虚拟摄像头”再点“停止虚拟摄像头”，然后退出 OBS。"
                + "若系统拦截，前往“系统设置 → 通用 → 登录项与扩展 → 摄像头扩展”，开启 OBS Virtual Camera 并授权。",
            "./start-media-mac.command",
            "在 start-media-mac.command 所在文件夹打开“终端”，运行上面的命令，提示选择模式时输入 3。保持终端窗口打开。", true, saveMac);
        platform("Windows",
            "Windows 通常无需单独打开系统虚拟摄像头开关。安装 OBS 后，确认视频应用的摄像头列表中有 OBS Virtual Camera。"
                + "如果 OBS 中没有“启动虚拟摄像头”，请按 OBS 官方排障说明修复安装。",
            ".\\start-media-windows.bat",
            "在 start-media-windows.bat 所在文件夹打开“命令提示符”，运行上面的命令，提示选择模式时输入 3。保持命令窗口打开。", false, saveWindows);

        text(content, "最后在电脑应用中选择 OBS Virtual Camera", 17, WHITE, true, 24);
        text(content, "Mac 可用 QuickTime Player → 文件 → 新建影片录制 → 录制按钮旁的小箭头 → 摄像头：OBS Virtual Camera。"
            + "Windows 可在视频会议软件的摄像头设置中选择同名设备。桥接程序运行时，无需再打开 OBS。", 14, MUTED, false, 8);
        link(content, "OBS 官方虚拟摄像头排障说明", "https://obsproject.com/kb/virtual-camera-troubleshooting");
        TextView start = text(content, "开始使用摄像头  →", 16, WHITE, true, 24);
        start.setGravity(Gravity.CENTER);
        start.setBackground(UiKit.card(context, Color.rgb(48, 119, 226), Color.rgb(30, 79, 165)));
        start.setPadding(dp(12), dp(15), dp(12), dp(15));
        start.setOnClickListener(v -> openCamera.run());
    }

    private void platform(String name, String permission, String command,
                          String commandHelp, boolean mac, Runnable savePackage) {
        LinearLayout card = new LinearLayout(getContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(18));
        card.setBackground(UiKit.card(getContext(), Color.rgb(35, 51, 74), Color.rgb(25, 35, 53)));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, -2);
        cardParams.topMargin = dp(18);
        content.addView(card, cardParams);
        text(card, name, 21, WHITE, true, 0);

        step(card, "1 · 下载并安装 OBS", "OBS 提供 OBS Virtual Camera。请从官网下载适合电脑系统的版本。", 16);
        link(card, "https://obsproject.com/download", "https://obsproject.com/download");
        text(card, "电脑首次使用还需 Python 3、adb 和本项目提供的 start-media 桥接脚本。已有脚本可直接复用，无需重复下载。", 13, MUTED, false, 8);
        TextView save = text(card, "↓  保存 " + name + " 桥接包到手机", 14, WHITE, true, 12);
        save.setGravity(Gravity.CENTER);
        save.setPadding(dp(10), dp(12), dp(10), dp(12));
        save.setBackground(UiKit.pill(getContext(), Color.rgb(36, 87, 151)));
        save.setOnClickListener(v -> savePackage.run());
        link(card, "Python 3：https://www.python.org/downloads/", "https://www.python.org/downloads/");
        link(card, "adb：https://developer.android.com/tools/releases/platform-tools",
            "https://developer.android.com/tools/releases/platform-tools");

        step(card, "2 · " + (mac ? "允许虚拟摄像头扩展" : "确认虚拟摄像头可用"), permission, 16);
        step(card, "3 · 运行 USB 桥接命令", "手机开启 USB 调试，允许这台电脑调试，并打开本应用的摄像头页面。" + commandHelp, 16);
        TextView commandView = text(card, command, 15, WHITE, true, 10);
        commandView.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        commandView.setTextIsSelectable(true);
        commandView.setPadding(dp(12), dp(12), dp(12), dp(12));
        commandView.setBackground(UiKit.pill(getContext(), Color.rgb(15, 24, 38)));
        TextView copy = text(card, "复制命令", 13, BLUE, true, 8);
        copy.setOnClickListener(v -> copy(command));
    }

    private void step(LinearLayout parent, String heading, String body, int top) {
        text(parent, heading, 16, WHITE, true, top);
        text(parent, body, 14, MUTED, false, 7);
    }

    private TextView text(LinearLayout parent, String value, int size, int color, boolean bold, int top) {
        TextView view = new TextView(getContext());
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(top);
        parent.addView(view, params);
        return view;
    }

    private void link(LinearLayout parent, String label, String url) {
        TextView view = text(parent, label, 13, BLUE, false, 8);
        view.setOnClickListener(v -> {
            try { getContext().startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
            catch (RuntimeException error) { copy(url); }
        });
        view.setOnLongClickListener(v -> { copy(url); return true; });
    }

    private void copy(String value) {
        ClipboardManager clipboard = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) clipboard.setPrimaryClip(ClipData.newPlainText("摄像头设置", value));
        Toast.makeText(getContext(), "已复制", Toast.LENGTH_SHORT).show();
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
