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

/** Setup guide for showing an OS-created extended desktop on the phone. */
final class DisplayGuideView extends ScrollView {
    private static final int WHITE = Color.WHITE;
    private static final int MUTED = Color.rgb(182, 198, 219);
    private static final int BLUE = Color.rgb(110, 185, 255);
    private final LinearLayout content;
    private final Runnable openDisplay, saveMac, saveWindows;
    private Boolean mac;

    DisplayGuideView(Context context, Runnable openDisplay, Runnable saveMac, Runnable saveWindows) {
        super(context);
        this.openDisplay = openDisplay;
        this.saveMac = saveMac;
        this.saveWindows = saveWindows;
        setFillViewport(true);
        setVerticalScrollBarEnabled(false);
        setBackgroundColor(Color.rgb(15, 20, 31));
        content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(18), dp(18), dp(30));
        addView(content, new ScrollView.LayoutParams(-1, -2));
        render();
    }

    private void render() {
        content.removeAllViews();
        text("手机显示器 · USB 使用指南", 23, WHITE, true, 0);
        text("先选择你的电脑系统，只看对应步骤。", 14, BLUE, false, 8);
        LinearLayout selector = new LinearLayout(getContext());
        LinearLayout.LayoutParams selectorParams = new LinearLayout.LayoutParams(-1, -2);
        selectorParams.topMargin = dp(14);
        content.addView(selector, selectorParams);
        platformButton(selector, "macOS", true);
        platformButton(selector, "Windows", false);

        if (mac == null) {
            text("请选择电脑系统，再按页面步骤操作。", 14, MUTED, false, 18);
            return;
        }

        text(mac ? "macOS 设置步骤" : "Windows 设置步骤", 20, WHITE, true, 20);
        text("电脑必须先识别第二块屏幕，并设为扩展桌面；本应用把那块屏幕的画面传到手机。", 14, MUTED, false, 8);
        if (mac) renderMac(); else renderWindows();
        heading("使用说明");
        text("将电脑窗口和鼠标指针移到扩展屏幕，手机便会显示该屏幕的画面及指针。当前版本不能直接触摸操作屏幕；需要控制电脑时可切回触摸板。蓝牙 HID 不传视频。", 14, MUTED, false, 8);
        action("开始使用显示器  →", openDisplay, 20);
    }

    private void platformButton(LinearLayout selector, String label, boolean isMac) {
        TextView button = new TextView(getContext());
        button.setText(label);
        button.setTextColor(WHITE);
        button.setTextSize(15);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(10), dp(12), dp(10), dp(12));
        button.setBackground(UiKit.pill(getContext(), mac != null && mac == isMac
            ? Color.rgb(36, 87, 151) : Color.rgb(35, 47, 66)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
        params.setMargins(0, 0, dp(isMac ? 6 : 0), 0);
        selector.addView(button, params);
        button.setOnClickListener(v -> {
            if (mac != null && mac == isMac) return;
            mac = isMac;
            render();
            post(() -> scrollTo(0, 0));
        });
    }

    private void renderMac() {
        heading("1 · 创建 Mac 扩展屏幕");
        text("安装 BetterDisplay 不会自动出现第二块屏幕。打开 BetterDisplay 菜单栏图标 → 齿轮设置 → Displays → Overview → Create New Virtual Screen。选择 Pre-configured settings 和 16:9 预设，再点 Create Virtual Screen。", 14, MUTED, false, 8);
        link("BetterDisplay 官方网站", "https://betterdisplay.pro/");
        text("创建后还要在 Settings → Displays 中选择新虚拟屏幕，打开 Connect this virtual screen。请保持 BetterDisplay 运行；不需要开启它的全屏串流，本应用会把画面传到手机。", 14, BLUE, false, 12);
        text("然后到 Mac“系统设置 → 显示器”确认已有两块屏幕，并把虚拟屏幕“用作”设为扩展显示器，不要选镜像。建议分辨率约 1280×720。", 14, MUTED, false, 10);

        heading("2 · 准备 macOS 桥接包");
        text("把 APK 内的 ZIP 保存到手机再传到 Mac，或在电脑访问 github.com/zhipengyue/LazyPeri 下载 releases/懒人外设-macOS-全功能-USB-Bridge.zip。下载后先解压。", 14, MUTED, false, 8);
        action("↓  保存 macOS 桥接包到手机", saveMac, 12);
        link("电脑从 GitHub 下载 macOS 桥接包", BridgeDownloads.MAC_ZIP);
        text("Mac 还需安装 Python 3 与 Android Platform Tools（adb）；首次运行脚本会联网安装截图依赖。", 14, MUTED, false, 12);
        link("Python 3 官方下载", "https://www.python.org/downloads/");
        link("Android Platform Tools 官方下载", "https://developer.android.com/tools/releases/platform-tools");

        heading("3 · 连接手机并运行脚本");
        text("手机开启 USB 调试，连接 Mac 并允许调试，打开本应用“显示器”页面。在 Mac 解压目录运行 start-display-mac.command。", 14, MUTED, false, 8);
        text("首次使用需在“系统设置 → 隐私与安全性 → 屏幕与系统音频录制”中允许终端录制屏幕，然后完全退出终端并重新运行脚本。", 14, MUTED, false, 10);

        heading("4 · 选择扩展屏幕");
        text("脚本应列出至少两个屏幕编号。输入虚拟扩展屏幕的编号，保持终端窗口打开。如果只显示 1，请先回到 BetterDisplay 检查“Connect this virtual screen”是否已开启。", 14, MUTED, false, 8);
    }

    private void renderWindows() {
        heading("1 · 创建 Windows 扩展屏幕");
        text("先连接第二显示器，或安装你信任的虚拟显示器驱动。打开“设置 → 系统 → 屏幕”，选择“扩展这些显示器”。如果电脑只识别到一块屏幕，先完成这一步。", 14, MUTED, false, 8);

        heading("2 · 准备 Windows 桥接包");
        text("把 APK 内的 ZIP 保存到手机再传到电脑，或在电脑访问 github.com/zhipengyue/LazyPeri 下载 releases/懒人外设-Windows-全功能-USB-Bridge.zip。下载后先解压。", 14, MUTED, false, 8);
        action("↓  保存 Windows 桥接包到手机", saveWindows, 12);
        link("电脑从 GitHub 下载 Windows 桥接包", BridgeDownloads.WINDOWS_ZIP);
        text("电脑还需安装 Python 3 与 Android Platform Tools（adb）；首次运行脚本会联网安装截图依赖。", 14, MUTED, false, 12);
        link("Python 3 官方下载", "https://www.python.org/downloads/");
        link("Android Platform Tools 官方下载", "https://developer.android.com/tools/releases/platform-tools");

        heading("3 · 连接手机并运行脚本");
        text("手机开启 USB 调试，连接 Windows 电脑并允许调试，打开本应用“显示器”页面。在电脑解压目录双击 start-display-windows.bat。", 14, MUTED, false, 8);

        heading("4 · 选择扩展屏幕");
        text("命令窗口列出屏幕编号后，输入扩展屏幕的编号，保持命令窗口打开。", 14, MUTED, false, 8);
    }

    private void heading(String label) { text(label, 17, WHITE, true, 22); }

    private TextView text(String value, int size, int color, boolean bold, int top) {
        TextView view = new TextView(getContext());
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(top);
        content.addView(view, params);
        return view;
    }

    private void action(String label, Runnable handler, int top) {
        TextView button = text(label, 14, WHITE, true, top);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(12), dp(13), dp(12), dp(13));
        button.setBackground(UiKit.pill(getContext(), Color.rgb(36, 87, 151)));
        button.setOnClickListener(v -> handler.run());
    }

    private void link(String label, String url) {
        TextView view = text(label, 13, BLUE, false, 8);
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
