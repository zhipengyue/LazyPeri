package com.mobiletrackpad.app;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.function.Supplier;

/** Full-page guides for the touchpad, keyboard, gamepad, and microphone. */
final class FeatureGuideView extends ScrollView {
    static final int TOUCHPAD = 0, GAMEPAD = 1, KEYBOARD = 2, MICROPHONE = 3;
    private static final int WHITE = Color.WHITE;
    private static final int MUTED = Color.rgb(182, 198, 219);
    private static final int BLUE = Color.rgb(110, 185, 255);
    private final LinearLayout content;
    private final TextView connection;
    private final Supplier<String> connectionSummary;

    FeatureGuideView(Context context, int feature, Supplier<String> connectionSummary,
                     Runnable openFeature, Runnable openConnections, Runnable saveMac, Runnable saveWindows) {
        super(context);
        this.connectionSummary = connectionSummary;
        setFillViewport(true);
        setVerticalScrollBarEnabled(false);
        setBackgroundColor(Color.rgb(15, 20, 31));
        content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(18), dp(18), dp(30));
        addView(content, new ScrollView.LayoutParams(-1, -2));

        String title = feature == TOUCHPAD ? "触摸板" : feature == KEYBOARD ? "键盘"
            : feature == GAMEPAD ? "游戏手柄" : "麦克风";
        text(content, title + " · 使用指南", 23, WHITE, true, 0);
        connection = text(content, "", 14, BLUE, false, 10);
        TextView connections = action(content, "连接设置", openConnections, false, 10);
        connections.setContentDescription("选择已配对的蓝牙电脑");

        if (feature == TOUCHPAD) touchpad();
        else if (feature == KEYBOARD) keyboard();
        else if (feature == GAMEPAD) gamepad();
        else microphone();

        usbPackages(saveMac, saveWindows, feature == MICROPHONE);
        text(content, "连接方式", 17, WHITE, true, 24);
        text(content, feature == MICROPHONE
            ? "麦克风声音仅通过 USB 音视频 Bridge 传输，蓝牙 HID 不传输音频。"
            : "蓝牙模式先在手机和电脑的系统蓝牙设置中配对。USB 模式需开启手机 USB 调试，并在手机上允许这台电脑调试。",
            14, MUTED, false, 8);
        action(content, "开始使用" + title + "  →", openFeature, true, 20);
    }

    void refresh() {
        connection.setText("当前连接：" + connectionSummary.get());
        post(() -> scrollTo(0, 0));
    }

    private void touchpad() {
        section("触控手势");
        text(content, "单指移动：移动指针\n单指轻点两次：双击\n单指长按、双指轻点：右键\n双指上下滑动：滚动\n三指移动：按住左键拖拽\n四指左右滑动：切换桌面", 14, MUTED, false, 8);
        text(content, "Windows 模式下，顶部左键可按住拖拽，顶部右键为右键。桌面切换使用 Win + Ctrl + ← / →；Mac 使用 Control + ← / →。", 14, MUTED, false, 10);
        text(content, "竖屏输入栏编辑后点击“发送”；中文和其他 Unicode 文字需要 USB Bridge。", 14, MUTED, false, 8);
    }

    private void keyboard() {
        section("按键操作");
        text(content, "点击按键会输入到电脑当前光标处。Shift、Ctrl、Alt、Win/⌘ 是单次修饰键：先点修饰键，再点目标键。键盘页面固定横屏。", 14, MUTED, false, 8);
        text(content, "使用前先点入电脑上的文本框；USB 模式需运行下方桥接包里的 start-usb 脚本。", 14, MUTED, false, 10);
    }

    private void gamepad() {
        section("按键与兼容性");
        text(content, "左摇杆、方向键、右摇杆、ABXY、L1/R1、开始/选择可同时触控。蓝牙发送标准 HID 手柄输入；部分只接受 XInput 的 Windows 游戏可能需要在游戏中改用 DirectInput 或进行按键映射。", 14, MUTED, false, 8);
        text(content, "USB Bridge 发送键盘映射：左摇杆/方向键 → WASD；右摇杆 → 方向键；A/B/X/Y → J/K/U/I；L1/R1 → Q/E；开始/选择 → Enter/Tab。", 14, MUTED, false, 10);
    }

    private void microphone() {
        section("让手机充当电脑麦克风");
        text(content, "手机打开“麦克风”页面并允许录音权限。电脑运行下方桥接包里的 start-media 脚本，选择模式 1，再选择虚拟音频设备编号。保持电脑端窗口打开。", 14, MUTED, false, 8);
        text(content, "Mac 安装 BlackHole 2ch，在会议软件中选择 BlackHole 2ch 作为麦克风；脚本里选择它的双声道输出设备编号。", 14, MUTED, false, 10);
        link(content, "BlackHole 2ch 官方下载", "https://existential.audio/blackhole/");
        text(content, "Windows 安装 VB-CABLE，在会议软件中选择 CABLE Output 作为麦克风；脚本里选择 CABLE Input 的双声道输出设备编号。", 14, MUTED, false, 12);
        link(content, "VB-CABLE 官方下载", "https://vb-audio.com/Cable/");
    }

    private void usbPackages(Runnable saveMac, Runnable saveWindows, boolean media) {
        section("电脑端 USB 桥接包");
        text(content, "桥接包已经打包进 APK。点按钮保存 ZIP 到手机，再传到电脑解压。若电脑上已有本项目源码，也可直接在 releases 目录找到以下文件：", 14, MUTED, false, 8);
        text(content, "releases/懒人外设-macOS-全功能-USB-Bridge.zip\nreleases/懒人外设-Windows-全功能-USB-Bridge.zip", 13, WHITE, false, 10).setTextIsSelectable(true);
        text(content, "ZIP 解压后，在解压目录根目录运行对应脚本：", 14, MUTED, false, 10);
        text(content, media
            ? "macOS：start-media-mac.command\nWindows：start-media-windows.bat"
            : "macOS：start-usb-mac.command\nWindows：start-usb-windows.bat", 14, WHITE, true, 10);
        action(content, "↓  保存 macOS 桥接包到手机", saveMac, true, 12);
        action(content, "↓  保存 Windows 桥接包到手机", saveWindows, true, 10);
        text(content, "电脑首次使用还需安装 Python 3 和 Android Platform Tools（adb）。脚本和 Python 文件在 ZIP 解压目录的根目录；打开该目录运行脚本并保持窗口开启。", 14, MUTED, false, 12);
        link(content, "Python 3 官方下载", "https://www.python.org/downloads/");
        link(content, "Android Platform Tools 官方下载", "https://developer.android.com/tools/releases/platform-tools");
    }

    private void section(String title) { text(content, title, 17, WHITE, true, 24); }

    private TextView action(LinearLayout parent, String label, Runnable handler, boolean filled, int top) {
        TextView view = text(parent, label, 14, WHITE, true, top);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(10), dp(12), dp(10), dp(12));
        view.setBackground(UiKit.pill(getContext(), filled ? Color.rgb(36, 87, 151) : Color.rgb(35, 47, 66)));
        view.setOnClickListener(v -> handler.run());
        return view;
    }

    private TextView text(LinearLayout parent, String value, int size, int color, boolean bold, int top) {
        TextView view = new TextView(getContext());
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(top);
        parent.addView(view, params);
        return view;
    }

    private void link(LinearLayout parent, String label, String url) {
        TextView view = text(parent, label + "：" + url, 13, BLUE, false, 8);
        view.setOnClickListener(v -> {
            try { getContext().startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
            catch (RuntimeException error) { copy(url); }
        });
        view.setOnLongClickListener(v -> { copy(url); return true; });
    }

    private void copy(String value) {
        ClipboardManager clipboard = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) clipboard.setPrimaryClip(ClipData.newPlainText("下载地址", value));
        Toast.makeText(getContext(), "链接已复制", Toast.LENGTH_SHORT).show();
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
