# 懒人外设

Android 手机通过蓝牙 HID 或 USB 数据线配合电脑端 Bridge 模拟触摸板、游戏手柄和虚拟键盘，在 Mac 与 Windows 上使用。最低 Android 9（API 28）。首页提供“触摸板”“键盘”“游戏手柄”“麦克风”“音箱”“摄像头”“显示器”七个入口，以及“意见反馈”入口；功能页左上角可返回首页。首页和音视频页为竖屏，显示器、独立键盘和游戏手柄页面固定横屏，触摸板默认竖屏且可切换横竖屏。项目使用原生 Android Java；界面增加了项目内复用的 `UiKit` 卡片与按钮组件，无需外部 UI 依赖。

键盘按下时会出现下沉与高亮反馈；触摸板横屏时左右键并入顶部工具栏，缩小顶部占用。旋转按钮使用 Lucide 的 `refresh-cw` SVG 及对应 Android 矢量资源，授权说明见 `design/icons/LICENSE`。

## 显示器功能（USB 试用版）

手机上的“显示器”指南会先要求选择电脑系统，再显示对应步骤。两种系统都必须先识别第二块显示器并设为扩展桌面；本项目不提供虚拟显示器驱动。通过 USB 以约 10 帧/秒传输画面，当前版本不支持直接触摸第二屏。

### macOS

1. 打开 [BetterDisplay](https://betterdisplay.pro/)，依次进入 **Settings → Displays → Overview → Create New Virtual Screen**，选 16:9 预设并创建；再在新屏幕设置中打开 **Connect this virtual screen**。只有连接后，Mac 才会识别到第二块屏幕。在 Mac 显示器设置中确认它“用作”扩展显示器。
2. 在手机指南的 **macOS** 页保存 Mac 桥接包，或在电脑上从 [GitHub 直接下载 macOS 全功能桥接包](https://raw.githubusercontent.com/zhipengyue/LazyPeri/main/releases/懒人外设-macOS-全功能-USB-Bridge.zip)。安装 Python 3 与 adb，手机开启 USB 调试并允许这台 Mac。
3. 打开手机“显示器”页面，在解压目录运行 `start-display-mac.command`，输入扩展屏幕编号。首次使用需给终端屏幕录制权限。完整步骤见 [macOS 显示器指南](docs/macOS-显示器-USB设置指南.md)。

### Windows

1. 连接第二块显示器，或安装你信任的虚拟显示器驱动；在 Windows 显示设置中选择“扩展这些显示器”。
2. 在手机指南的 **Windows** 页保存 Windows 桥接包，或在电脑上从 [GitHub 直接下载 Windows 全功能桥接包](https://raw.githubusercontent.com/zhipengyue/LazyPeri/main/releases/懒人外设-Windows-全功能-USB-Bridge.zip)。安装 Python 3 与 adb，手机开启 USB 调试并允许这台电脑。
3. 打开手机“显示器”页面，在解压目录运行 `start-display-windows.bat`，输入扩展屏幕编号。完整步骤见 [Windows 显示器指南](docs/Windows-显示器-USB设置指南.md)。

## 音视频功能（USB 试用版）

- **麦克风**：手机本地检测声音，灰色麦克风随说话发蓝光。使用新的 USB 音视频 Bridge 传送 48 kHz PCM 到电脑的虚拟音频线；会议软件选择虚拟音频线的录音端。
- **音箱**：首次进入先显示独立的 Mac/Windows 四步安装指南，音箱页右上角“指南”可随时重看；不再使用音箱说明弹窗。电脑将声音输出到虚拟音频线，USB 音视频 Bridge 采集后传到手机播放。
- **摄像头**：首次进入先显示独立的 Mac/Windows 三步安装指南，摄像头页右上角的“指南”可随时重看；不再使用摄像头说明弹窗。手机页面直接显示与电脑桥接相同的 JPEG 帧，未连接电脑时也可预览。右上角可切换前后镜头，并同时旋转手机与电脑画面，每次 90°；角度设置会保留。USB 音视频 Bridge 将 JPEG 帧送至电脑的 OBS 虚拟摄像头。部分 Android 14 QPR1 及以上且厂商启用 UVC 的手机，也可在**系统 USB 设置**中选择“网络摄像头”，直接由系统作为电脑摄像头；这与应用内预览模式是两个独立路径。参考 [Android 官方说明](https://source.android.com/docs/core/camera/webcam)。

电脑端依赖：Python 3、Android Platform Tools (`adb`)；音频需 Windows 的 VB-CABLE 或 Mac 的 BlackHole；摄像头桥接需 Windows/Mac 的 OBS 虚拟摄像头。Mac 和 Windows 桥接 ZIP 已作为离线资产内置 APK；用户可在各功能指南页点击“保存桥接包到手机”，通过系统文件选择器保存后再传到电脑解压。首次运行启动脚本会创建 Python 虚拟环境并从网络安装 `sounddevice`、`numpy`、`Pillow`、`pyvirtualcam`。这些驱动由原作者提供，项目工具包不重新分发。**蓝牙 HID 仍只支持触摸板、键盘和手柄，不传输音视频。** Mac 摄像头 USB 链路已完成一次真机试用；Windows 摄像头与音频链路仍需真机验证。详细步骤与故障提示见 [音视频兼容性与下载说明](docs/音视频兼容性与下载说明.md)。

触摸板、键盘、游戏手柄和麦克风的右上角也提供整页“指南”；麦克风首次进入时直接展示指南。七个功能的指南均可保存 APK 内置的 Mac 或 Windows 桥接包。电脑也可访问 [GitHub 仓库的 releases 目录](https://github.com/zhipengyue/LazyPeri/tree/main/releases)下载对应 ZIP，解压后运行本系统的启动脚本。

## 意见反馈

在首页点击“意见反馈”，可填写问题或建议，再点“邮件发送”，由手机已安装的邮件应用创建发往 `eleven.zhi@outlook.com` 的邮件。用户需要在邮件应用中确认发送；应用自身不需要反馈后端，也不会在后台自动发送。反馈页同时显示微信号 `kaka20491010`，可一键复制后到微信联系。若手机未安装可处理邮件链接的应用，可直接使用页面中的邮箱地址或微信号。

## 使用

1. 安装 [GitHub 上的 Android APK](https://raw.githubusercontent.com/zhipengyue/LazyPeri/main/releases/懒人外设-Android-v0.9.9.apk)（仓库路径：`releases/懒人外设-Android-v0.9.9.apk`），打开应用。蓝牙模式需允许“附近设备”权限。
2. 蓝牙模式：在电脑与手机的系统蓝牙设置中配对；应用打开时会自动尝试连接已配对的电脑。如需手动选择，打开相应功能的“指南”→“连接设置”。手机必须支持 Bluetooth HID Device。
3. USB 模式：在手机的开发者选项中开启 **USB 调试**，用数据线连接电脑并接受手机上的调试授权。电脑需安装 Python 3 和 Android Platform Tools（`adb`）。打开手机应用，再运行 `tools/start-usb-mac.command` 或 `tools/start-usb-windows.bat`。Windows 可下载下方的“Windows USB 工具包”，解压后运行其中的批处理文件。也可运行 `python3 tools/usb-bridge.py`（Windows 可用 `py -3`）。
4. macOS 首次使用 USB Bridge 时，在“系统设置 → 隐私与安全性 → 无障碍（部分版本称辅助功能）”中允许实际运行 Bridge 的程序控制电脑；Windows 无需开启同名系统权限。详细步骤和故障排查见下方的 USB 授权指南。点击应用右上角“指南”可查看手势与连接状态；“⚙”可选择 Mac 或 Windows 的桌面切换快捷键。

### USB 授权指南下载

- [macOS USB 授权指南](docs/macOS-USB-授权指南.md)
- [Windows USB 授权指南](docs/Windows-USB-授权指南.md)
电脑打开 [GitHub 仓库](https://github.com/zhipengyue/LazyPeri)，进入 `releases` 目录即可找到以下文件；推荐下载含所有桥接脚本的全功能包：

- [两份指南打包下载](https://raw.githubusercontent.com/zhipengyue/LazyPeri/main/docs/懒人外设-USB授权指南.zip)
- [Windows USB 工具包下载](https://raw.githubusercontent.com/zhipengyue/LazyPeri/main/releases/懒人外设-Windows-USB-Bridge.zip)
- [macOS USB 工具包下载](https://raw.githubusercontent.com/zhipengyue/LazyPeri/main/releases/懒人外设-macOS-USB-Bridge.zip)
- [Windows 全功能 USB 工具包（含音视频与显示器试用版）](https://raw.githubusercontent.com/zhipengyue/LazyPeri/main/releases/懒人外设-Windows-全功能-USB-Bridge.zip)
- [macOS 全功能 USB 工具包（含音视频与显示器试用版）](https://raw.githubusercontent.com/zhipengyue/LazyPeri/main/releases/懒人外设-macOS-全功能-USB-Bridge.zip)

USB 数据线连接本身不会让普通 Android 手机变成 USB HID 鼠标。使用 USB 功能时，电脑端必须运行本项目的 `tools/usb-bridge.py`，并保持窗口打开。macOS 若显示“未授予辅助功能权限”，请在系统设置中允许**实际启动脚本的程序**（从 `.command` 启动通常是“终端”，从 Codex 启动则是 Codex），完全退出并重新打开该程序后再启动 Bridge。Windows 无需 macOS 式的无障碍授权，但手机必须确认 USB 调试弹窗。文字发送还要先在电脑的目标文本框放置光标。

触控页可见时会保持屏幕常亮。若手动锁屏，连接由前台服务维持，通知栏会显示“触摸板连接保持中”；从最近任务中移除应用或停止服务会断开连接。

单指移动/轻点对应指针/左击；连续轻点两次为双击；单指长按弹出右键菜单；双指轻点对应右击，双指上下滑动对应滚轮；三指移动对应按住左键拖拽；四指左右滑动对应切换桌面。触摸板页的方向按钮可切换横屏和竖屏，选择会保存。`⚙` 设置提供 Windows / Mac 单选，默认 Windows；Windows 模式下触摸区上方有独立左右键，按住左键并在触摸区移动可拖拽，顶部左键不会触发长按右键。Mac 使用 Control + ←/→，Windows 使用 Win + Ctrl + ←/→。电脑上需已有多个桌面，相关快捷键也需处于启用状态。

竖屏触摸板右上角有键盘按钮，点击后手机系统输入法在下方显示，触摸板保留在上方；输入栏会显示已输入文字，点击“发送”后才传给电脑。普通蓝牙 HID 键盘无法直接传输中文字符，中文需使用 USB Bridge；发送失败时输入内容会保留。三指触下时会有短振动，触控区显示“ 三指拖拽中 ”；四指横移达到触发距离时会再次振动并显示“ 已发送桌面切换 ”。四指操作发送一次桌面切换命令，不支持像苹果原生触控板一样随手指连续拖动桌面。

## 游戏手柄

首页点击“游戏手柄”，可同时使用左摇杆、方向键、右摇杆、ABXY、L1/R1、开始/选择。蓝牙模式发送标准 HID 游戏手柄报告（16 个按键、方向帽、双摇杆），电脑端不需要运行额外软件。首次升级到此版本后，如果电脑仍只识别旧鼠标/键盘配置，请在电脑与手机两端删除旧蓝牙配对后重新配对，让电脑读取新的 HID 描述符。

USB Bridge 模式将手柄映射为键盘：左摇杆和方向键 → WASD，右摇杆 → 方向键，A/B/X/Y → J/K/U/I，L1/R1 → Q/E，开始/选择 → Enter/Tab。Windows 上仅支持 XInput 的游戏可能不接受标准蓝牙 HID 手柄；USB 键盘映射也需要游戏允许自定义键位。Mac 的手柄识别同样由游戏决定。

## 虚拟键盘

首页点击“键盘”进入横屏键盘页。点击按键会在电脑当前光标位置输入；Shift、Ctrl、Alt、Win/⌘ 为单次修饰键，先点击修饰键再点击目标键。支持字母、数字、常用符号、空格、退格、回车、方向键及组合键。蓝牙和 USB Bridge 均可使用；USB 模式需重新运行本项目提供的新版 Bridge 脚本。

## 文字输入

在触摸板页点击右上角“文字”，在文本框中输入或修改文字；点击文本框会使用手机系统键盘，也可以使用该键盘自带的语音输入。先在电脑上将光标放到目标文本框，再点击“发送到电脑光标处”。输入内容会保留，方便重试。

USB Bridge 支持中文及其他 Unicode 文本。必须运行本项目的新版 Bridge 脚本；手机会等待电脑端回传接收结果，未收到确认会显示发送失败。电脑端确认表示输入事件已投递，最终是否进入目标程序仍取决于电脑当前焦点和程序对模拟输入的支持。蓝牙 HID 只支持英文、数字和常用半角符号，中文需使用 USB Bridge。macOS 使用 USB Bridge 需要给终端或 Python 辅助功能权限。

## 构建

用 Android Studio 打开本目录，构建 `app` 的 Debug APK。或在装有 Android SDK 35、Build Tools 35.0.1、JDK 17+ 的电脑上运行：

```bash
bash tools/build-local.sh
```

## 边界

蓝牙模式向电脑发送标准鼠标滚轮及键盘快捷键，不会被 macOS 识别为 Apple 原生多点触控板。滚动惯性、压力点击、系统级原生触控板手势无法精确复制。普通 Android 手机无法保证直接充当 USB HID 外设，因此 USB 模式需要 USB 调试和电脑端 Bridge。实际连接与手感仍需在真机及目标电脑上验证。
