package com.mobiletrackpad.app;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** Full-page setup guide for routing computer audio to the phone speaker. */
final class SpeakerGuideView extends ScrollView {
    private static final int WHITE = Color.WHITE;
    private static final int MUTED = Color.rgb(182, 198, 219);
    private static final int BLUE = Color.rgb(110, 185, 255);
    private final LinearLayout content;

    SpeakerGuideView(Context context, Runnable openSpeaker, Runnable saveMac, Runnable saveWindows) {
        super(context);
        setFillViewport(true);
        setVerticalScrollBarEnabled(false);
        setBackgroundColor(Color.rgb(15, 20, 31));
        content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(18), dp(18), dp(30));
        addView(content, new ScrollView.LayoutParams(-1, -2));

        text(content, "手机音箱 · USB 使用指南", 23, WHITE, true, 0);
        text(content, "电脑声音 → 虚拟音频线 → USB Bridge → 手机扬声器", 14, MUTED, false, 8);
        text(content, "虚拟音频线负责让桥接程序读到电脑声音；蓝牙 HID 连接不传声音。", 14, MUTED, false, 10);
        text(content, "已用过本项目的摄像头？电脑上的 start-media 脚本可以直接复用；音箱模式选择 2，不用重新下载桥接文件。", 14, BLUE, false, 10);
        text(content, "首次使用时，可点击下方按钮把 APK 内的电脑端桥接文件保存到手机，再传到电脑解压；保存过程无需联网。项目源码中也可在 releases/懒人外设-macOS-全功能-USB-Bridge.zip 或 releases/懒人外设-Windows-全功能-USB-Bridge.zip 找到对应文件。", 14, MUTED, false, 10);

        platform("macOS", "BlackHole 2ch", "https://existential.audio/blackhole/",
            "在“系统设置 → 声音 → 输出”中选择 BlackHole 2ch。电脑原扬声器暂时不会播放这一路声音。",
            "./start-media-mac.command",
            "在已有 start-media-mac.command 所在文件夹打开终端，运行命令，选择模式 2；随后在设备列表中输入 BlackHole 2ch 的编号（需支持 2 路输入）。保持终端窗口打开。",
            "结束后把 Mac 声音输出改回原来的扬声器或耳机。", saveMac);
        platform("Windows", "VB-CABLE", "https://vb-audio.com/Cable/",
            "按 VB-Audio 官网说明以管理员身份安装驱动并重启。然后在“设置 → 系统 → 声音 → 输出”中选择 CABLE Input（VB-Audio Virtual Cable）。",
            ".\\start-media-windows.bat",
            "在已有 start-media-windows.bat 所在文件夹打开命令提示符，运行命令，选择模式 2；随后在设备列表中输入 CABLE Output 的编号（需支持 2 路输入）。保持命令窗口打开。",
            "结束后把 Windows 声音输出改回原来的扬声器或耳机。", saveWindows);

        text(content, "如何确认成功", 17, WHITE, true, 24);
        text(content, "手机打开“音箱”页面并调高媒体音量；电脑播放一段声音。终端应显示“音箱已连接”，声音从手机播放。"
            + "首次运行脚本会联网安装 Python 依赖；Mac 若请求终端的麦克风权限，请允许，因为它要读取虚拟音频输入。", 14, MUTED, false, 8);
        TextView start = text(content, "开始使用音箱  →", 16, WHITE, true, 24);
        start.setGravity(Gravity.CENTER);
        start.setBackground(UiKit.card(context, Color.rgb(48, 119, 226), Color.rgb(30, 79, 165)));
        start.setPadding(dp(12), dp(15), dp(12), dp(15));
        start.setOnClickListener(v -> openSpeaker.run());
    }

    private void platform(String name, String cable, String cableUrl, String output,
                          String command, String bridgeHelp, String restore, Runnable savePackage) {
        LinearLayout card = new LinearLayout(getContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(18));
        card.setBackground(UiKit.card(getContext(), Color.rgb(35, 51, 74), Color.rgb(25, 35, 53)));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, -2);
        cardParams.topMargin = dp(18);
        content.addView(card, cardParams);
        text(card, name, 21, WHITE, true, 0);

        step(card, "1 · 下载并安装虚拟音频线", "安装 " + cable + "，让电脑声音能被桥接程序读取。", 16);
        link(card, cableUrl, cableUrl);
        text(card, "电脑首次使用还需 Python 3、adb 和本项目提供的 start-media 桥接脚本。若摄像头已跑通，这些文件可直接复用。", 13, MUTED, false, 8);
        TextView save = text(card, "↓  保存 " + name + " 桥接包到手机", 14, WHITE, true, 12);
        save.setGravity(Gravity.CENTER);
        save.setPadding(dp(10), dp(12), dp(10), dp(12));
        save.setBackground(UiKit.pill(getContext(), Color.rgb(36, 87, 151)));
        save.setOnClickListener(v -> savePackage.run());
        link(card, "Python 3：https://www.python.org/downloads/", "https://www.python.org/downloads/");
        link(card, "adb：https://developer.android.com/tools/releases/platform-tools",
            "https://developer.android.com/tools/releases/platform-tools");

        step(card, "2 · 将电脑声音输出到虚拟音频线", output, 16);
        step(card, "3 · 运行 USB 桥接命令", "手机开启 USB 调试、允许这台电脑调试，然后打开应用的“音箱”页面。" + bridgeHelp, 16);
        TextView commandView = text(card, command, 15, WHITE, true, 10);
        commandView.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        commandView.setTextIsSelectable(true);
        commandView.setPadding(dp(12), dp(12), dp(12), dp(12));
        commandView.setBackground(UiKit.pill(getContext(), Color.rgb(15, 24, 38)));
        TextView copy = text(card, "复制命令", 13, BLUE, true, 8);
        copy.setOnClickListener(v -> copy(command));

        step(card, "4 · 播放测试并恢复输出", "电脑播放声音，检查手机是否响起。" + restore, 16);
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
        if (clipboard != null) clipboard.setPrimaryClip(ClipData.newPlainText("音箱设置", value));
        Toast.makeText(getContext(), "已复制", Toast.LENGTH_SHORT).show();
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
