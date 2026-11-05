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
        text(content, "手机显示器 · USB 使用指南", 23, WHITE, true, 0);
        text(content, "电脑必须先识别第二块屏幕，并设为扩展桌面。本应用会把那块屏幕的画面传到手机。", 14, MUTED, false, 8);

        renderMac(platformCard("macOS"));
        renderWindows(platformCard("Windows"));

        heading(content, "使用说明");
        text(content, "将电脑窗口和鼠标指针移到扩展屏幕，手机便会显示该屏幕的画面及指针。当前版本不能直接触摸操作屏幕；需要控制电脑时可切回触摸板。蓝牙 HID 不传视频。", 14, MUTED, false, 8);
        action(content, "开始使用显示器  →", openDisplay, 20);
    }

    private LinearLayout platformCard(String name) {
        LinearLayout card = new LinearLayout(getContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(18));
        card.setBackground(UiKit.card(getContext(), Color.rgb(35, 51, 74), Color.rgb(25, 35, 53)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(18);
        content.addView(card, params);
        text(card, name, 21, WHITE, true, 0);
        return card;
    }

    private void renderMac(LinearLayout card) {
        heading(card, "1 · 创建 Mac 扩展屏幕");
        text(card, "安装 BetterDisplay 不会自动出现第二块屏幕。打开 BetterDisplay 菜单栏图标 → 齿轮设置 → Displays → Overview → Create New Virtual Screen。选择 Pre-configured settings 和 16:9 预设，再点 Create Virtual Screen。", 14, MUTED, false, 8);
        link(card, "BetterDisplay 官方网站", "https://betterdisplay.pro/");
        text(card, "创建后还要在 Settings → Displays 中选择新虚拟屏幕，打开 Connect this virtual screen。请保持 BetterDisplay 运行；不需要开启它的全屏串流，本应用会把画面传到手机。", 14, BLUE, false, 12);
        text(card, "然后到 Mac“系统设置 → 显示器”确认已有两块屏幕，并把虚拟屏幕“用作”设为扩展显示器，不要选镜像。建议分辨率约 1280×720。", 14, MUTED, false, 10);

        heading(card, "2 · 准备 macOS 桥接包");
        text(card, "把 APK 内的 ZIP 保存到手机再传到 Mac，或在电脑访问 github.com/zhipengyue/LazyPeri 下载 releases/懒人外设-macOS-全功能-USB-Bridge.zip。下载后先解压。", 14, MUTED, false, 8);
        action(card, "↓  保存 macOS 桥接包到手机", saveMac, 12);
        link(card, "电脑从 GitHub 下载 macOS 桥接包", BridgeDownloads.MAC_ZIP);
        text(card, "Mac 还需安装 Python 3 与 Android Platform Tools（adb）；首次运行脚本会联网安装截图依赖。", 14, MUTED, false, 12);
        link(card, "Python 3 官方下载", "https://www.python.org/downloads/");
        link(card, "Android Platform Tools 官方下载", "https://developer.android.com/tools/releases/platform-tools");

        heading(card, "3 · 连接手机并运行脚本");
        text(card, "手机开启 USB 调试，用可传数据的线连接 Mac 并解锁手机。下拉通知栏，点“正在通过 USB 充电”或“USB 用途”，选择“文件传输／传输数据（MTP）”；再允许这台 Mac 进行 USB 调试。打开本应用“显示器”页面，在 Mac 解压目录运行 start-display-mac.command。", 14, MUTED, false, 8);
        text(card, "首次使用需在“系统设置 → 隐私与安全性 → 屏幕与系统音频录制”中允许终端录制屏幕，然后完全退出终端并重新运行脚本。", 14, MUTED, false, 10);

        heading(card, "4 · 选择扩展屏幕");
        text(card, "脚本应列出至少两个屏幕编号。输入虚拟扩展屏幕的编号，保持终端窗口打开。如果只显示 1，请先回到 BetterDisplay 检查“Connect this virtual screen”是否已开启。", 14, MUTED, false, 8);
    }

    private void renderWindows(LinearLayout card) {
        heading(card, "1 · 创建 Windows 扩展屏幕");
        text(card, "Windows 可使用第二块实体显示器。如果只有一块屏幕，可安装适用于 Windows 10/11 的 Virtual Display Driver 来创建虚拟屏幕。", 14, MUTED, false, 8);
        text(card, "在 PowerShell 或 Windows 终端运行以下命令。长按命令可选中复制。", 14, MUTED, false, 8);
        TextView command = text(card, "winget install --id=VirtualDrivers.Virtual-Display-Driver -e", 14, WHITE, false, 8);
        command.setTypeface(android.graphics.Typeface.MONOSPACE);
        command.setTextIsSelectable(true);
        command.setPadding(dp(12), dp(12), dp(12), dp(12));
        command.setBackground(UiKit.pill(getContext(), Color.rgb(15, 24, 38)));
        text(card, "WinGet 安装后，还要运行 VDD Control 来安装驱动：", 14, WHITE, true, 12);
        text(card, "① 在开始菜单搜索 VDD Control，右键选择“以管理员身份运行”。如果搜不到，把下面的路径粘贴到资源管理器地址栏。", 14, MUTED, false, 8);
        TextView path = text(card, "%LOCALAPPDATA%\\Microsoft\\WinGet\\Packages", 14, BLUE, false, 8);
        path.setTextIsSelectable(true);
        text(card, "找到名称以 VirtualDrivers.Virtual-Display-Driver_ 开头的文件夹，右键以管理员身份运行其中的 VDD Control.exe。", 14, MUTED, false, 8);
        text(card, "② 在 VDD Control 中点击 Install，按提示完成虚拟显示驱动安装。", 14, MUTED, false, 8);
        text(card, "③ 打开“设置 → 系统 → 屏幕”，确认能看到两块屏幕，再按 Win + P 选择“扩展”。", 14, MUTED, false, 8);
        link(card, "Virtual Display Driver 下载与安装说明", "https://github.com/VirtualDrivers/Virtual-Display-Driver");

        heading(card, "2 · 准备 Windows 桥接包");
        text(card, "把 APK 内的 ZIP 保存到手机再传到电脑，或在电脑访问 github.com/zhipengyue/LazyPeri 下载 releases/懒人外设-Windows-全功能-USB-Bridge.zip。下载后先解压。", 14, MUTED, false, 8);
        action(card, "↓  保存 Windows 桥接包到手机", saveWindows, 12);
        link(card, "电脑从 GitHub 下载 Windows 桥接包", BridgeDownloads.WINDOWS_ZIP);
        text(card, "电脑还需安装 Python 3 与 Android Platform Tools（adb）；首次运行脚本会联网安装截图依赖。", 14, MUTED, false, 12);
        link(card, "Python 3 官方下载", "https://www.python.org/downloads/");
        link(card, "Android Platform Tools 官方下载", "https://developer.android.com/tools/releases/platform-tools");

        heading(card, "3 · 连接手机并运行脚本");
        text(card, "手机开启 USB 调试，用可传数据的线连接 Windows 电脑并解锁手机。下拉通知栏，点“正在通过 USB 充电”或“USB 用途”，选择“文件传输／传输数据（MTP）”；再允许这台电脑进行 USB 调试。打开本应用“显示器”页面，在电脑解压目录双击 start-display-windows.bat。", 14, MUTED, false, 8);

        heading(card, "4 · 选择扩展屏幕");
        text(card, "命令窗口列出屏幕编号后，输入扩展屏幕的编号，保持命令窗口打开。", 14, MUTED, false, 8);
    }

    private void heading(LinearLayout parent, String label) { text(parent, label, 17, WHITE, true, 22); }

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

    private void action(LinearLayout parent, String label, Runnable handler, int top) {
        TextView button = text(parent, label, 14, WHITE, true, top);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(12), dp(13), dp(12), dp(13));
        button.setBackground(UiKit.pill(getContext(), Color.rgb(36, 87, 151)));
        button.setOnClickListener(v -> handler.run());
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
        if (clipboard != null) clipboard.setPrimaryClip(ClipData.newPlainText("下载地址", value));
        Toast.makeText(getContext(), "链接已复制", Toast.LENGTH_SHORT).show();
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
