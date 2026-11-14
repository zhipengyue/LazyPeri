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
        else microphone(saveMac, saveWindows);

        if (feature != MICROPHONE) usbPackages(saveMac, saveWindows, false);
        text(content, "连接方式", 17, WHITE, true, 24);
        text(content, feature == MICROPHONE
            ? "麦克风声音仅通过 USB 音视频 Bridge 传输，蓝牙 HID 不传输音频。"
            : "蓝牙模式先在手机和电脑的系统蓝牙设置中配对。USB 模式需开启手机 USB 调试，并在手机上允许这台电脑调试。",
            14, MUTED, false, 8);
        text(content, "USB 连接步骤：用可传数据的线连接电脑并解锁手机 → 从屏幕顶部下拉通知栏 → 点“正在通过 USB 充电”或“USB 用途” → 选择“文件传输／传输数据（MTP）” → 出现“允许 USB 调试”时点“允许”。选项名称因手机而异。", 14, BLUE, false, 10);
        action(content, "开始使用" + title + "  →", openFeature, true, 20);
    }

    void refresh() {
        connection.setText("当前连接：" + connectionSummary.get());
        post(() -> scrollTo(0, 0));
    }

    private void touchpad() {
        section("触控手势");
        text(content, "单指移动：移动指针\n单指轻点两次：双击\n单指长按（可在设置中关闭）、双指轻点：右键\n双指上下滑动：滚动\n三指移动：按住左键拖拽\n四指左右滑动：切换桌面", 14, MUTED, false, 8);
        text(content, "Windows 模式下，顶部左键可按住拖拽，顶部右键为右键。桌面切换使用 Win + Ctrl + ← / →；Mac 使用 Control + ← / →。", 14, MUTED, false, 10);
        text(content, "触摸板页点 ⚙ 可切换电脑系统、开关“长按触发右键”，并用滑块调整指针灵敏度（0.50×～2.50×）；设置会保存。", 14, MUTED, false, 10);
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
        text(content, "Windows USB 模拟 Xbox 360 手柄。LB/RB 是肩键，LT/RT 是独立扳机。电脑需安装 ViGEmBus 和 vgamepad（python -m pip install vgamepad）。先打开手机手柄页面，等 Bridge 显示 Xbox 360 虚拟手柄已连接，再启动游戏。", 14, MUTED, false, 10);
        link(content, "vgamepad 安装说明", "https://github.com/yannbouteiller/vgamepad#installation");
        text(content, "Windows 桥接窗口若提示 Xbox 手柄初始化失败，请检查 ViGEmBus 和 vgamepad 后重启桥接。测试时可先断开实体手柄，确保游戏选中手机的虚拟手柄。", 14, MUTED, false, 10);
    }

    private void microphone(Runnable saveMac, Runnable saveWindows) {
        section("让手机充当电脑麦克风");
        text(content, "先选择电脑系统，只看对应步骤。虚拟麦克风设备由电脑端驱动安装后自动创建。", 14, MUTED, false, 8);
        LinearLayout selector = new LinearLayout(getContext());
        LinearLayout.LayoutParams selectorParams = new LinearLayout.LayoutParams(-1, -2);
        selectorParams.topMargin = dp(12);
        content.addView(selector, selectorParams);
        LinearLayout steps = new LinearLayout(getContext());
        steps.setOrientation(LinearLayout.VERTICAL);
        content.addView(steps, new LinearLayout.LayoutParams(-1, -2));
        TextView macButton = action(selector, "macOS", () -> { }, false, 0);
        TextView windowsButton = action(selector, "Windows", () -> { }, false, 0);
        macButton.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
        windowsButton.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
        macButton.setOnClickListener(v -> {
            macButton.setBackground(UiKit.pill(getContext(), Color.rgb(36, 87, 151)));
            windowsButton.setBackground(UiKit.pill(getContext(), Color.rgb(35, 47, 66)));
            renderMicrophoneSteps(steps, true, saveMac);
        });
        windowsButton.setOnClickListener(v -> {
            windowsButton.setBackground(UiKit.pill(getContext(), Color.rgb(36, 87, 151)));
            macButton.setBackground(UiKit.pill(getContext(), Color.rgb(35, 47, 66)));
            renderMicrophoneSteps(steps, false, saveWindows);
        });
        text(steps, "请选择 macOS 或 Windows。", 14, BLUE, false, 12);
    }

    private void renderMicrophoneSteps(LinearLayout parent, boolean mac, Runnable savePackage) {
        parent.removeAllViews();
        text(parent, mac ? "macOS · BlackHole 2ch" : "Windows · VB-CABLE", 17, WHITE, true, 18);
        if (mac) {
            text(parent, "1. 从官网下载并安装 BlackHole 2ch；已装 Homebrew 也可运行 brew install blackhole-2ch。安装后会自动出现 BlackHole 2ch，不用手动新建麦克风。打开“音频 MIDI 设置”确认设备存在，并设为 48,000 Hz、2 声道。", 14, MUTED, false, 8);
            link(parent, "BlackHole 2ch 官方下载", "https://existential.audio/blackhole/");
        } else {
            text(parent, "1. 从 VB-Audio 官网下载 VB-CABLE ZIP，先解压，再右键安装程序选择“以管理员身份运行”，安装后重启。系统“声音”设置中：输出列表应有 CABLE Input，输入列表应有 CABLE Output。无需手动新建麦克风。", 14, MUTED, false, 8);
            link(parent, "VB-CABLE 官方下载与安装说明", "https://vb-audio.com/Cable/");
        }
        text(parent, "2. 电脑需安装 Python 3 和 adb。手机打开“麦克风”并允许录音权限；用可传数据的线连接电脑，USB 用途选“文件传输／传输数据”，允许 USB 调试。", 14, MUTED, false, 12);
        text(parent, mac
            ? "3. 解压 macOS 桥接包，运行 start-media-mac.command，选择模式 1；设备列表中输入 BlackHole 2ch 且有 2 路输出的编号。保持终端打开。"
            : "3. 解压 Windows 桥接包，运行 start-media-windows.bat，选择模式 1；设备列表中输入 CABLE Input 且有 2 路输出的编号。保持命令窗口打开。", 14, MUTED, false, 12);
        text(parent, mac
            ? "4. 在会议软件的麦克风设置中选择 BlackHole 2ch，对着手机说话测试。"
            : "4. 在会议软件的麦克风设置中选择 CABLE Output，对着手机说话测试。", 14, BLUE, false, 12);
        action(parent, mac ? "↓  保存 macOS 桥接包到手机" : "↓  保存 Windows 桥接包到手机",
            savePackage, true, 16);
        link(parent, mac ? "电脑从 GitHub 下载 macOS 桥接包" : "电脑从 GitHub 下载 Windows 桥接包",
            mac ? BridgeDownloads.MAC_ZIP : BridgeDownloads.WINDOWS_ZIP);
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
        text(content, "电脑也可访问 github.com/zhipengyue/LazyPeri，进入 releases 目录下载对应 ZIP；下方链接可点按打开、长按复制。", 14, MUTED, false, 12);
        link(content, "GitHub 下载 macOS 桥接包", BridgeDownloads.MAC_ZIP);
        link(content, "GitHub 下载 Windows 桥接包", BridgeDownloads.WINDOWS_ZIP);
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
