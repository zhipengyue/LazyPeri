# 懒人外设：macOS 音视频 USB 设置

首次使用时，可在 Mac 上从 [GitHub 下载 macOS 全功能桥接 ZIP](https://raw.githubusercontent.com/zhipengyue/LazyPeri/main/releases/懒人外设-macOS-全功能-USB-Bridge.zip)（`releases/懒人外设-macOS-全功能-USB-Bridge.zip`），或在手机应用的“摄像头”“音箱”指南页保存同一 ZIP，再传到 Mac。下载后先解压；ZIP 只是脚本的打包方式，不是需要安装的软件。若已通过 `start-media-mac.command` 调试过摄像头，音箱可直接复用同一个脚本，选择模式 `2`，无需再次保存。手机安装新版 APK，开启 USB 调试、允许此 Mac 调试，并打开“懒人外设”。Mac 需安装 Python 3 和 Android Platform Tools (`adb`)。摄像头 USB 链路已完成一次真机试用；音频链路仍需验证。

## 麦克风

1. 从 [BlackHole 官网](https://existential.audio/blackhole/) 安装 **BlackHole 2ch**。
2. 双击 `start-media-mac.command`，选 `1`。首次运行会联网下载 Python 依赖。输入 **BlackHole 2ch** 的输出设备编号。
3. macOS 若请求“终端”使用麦克风，按需允许。手机进入“麦克风”并允许权限；电脑窗口应显示连接。
4. 在会议或录音软件中选择 **BlackHole 2ch** 作为麦克风，录音测试。

## 音箱

电脑声音 → BlackHole 2ch → USB Bridge → 手机扬声器。手机应用首次进入音箱时会打开独立指南，之后可点右上角“指南”重看。

1. **下载安装。** 从 [BlackHole 官网](https://existential.audio/blackhole/)下载并安装 **BlackHole 2ch**，它让 Bridge 能读取电脑的声音。已装 Homebrew 时也可运行 `brew install blackhole-2ch`。电脑首次使用还需 [Python 3](https://www.python.org/downloads/) 和 [Android Platform Tools（adb）](https://developer.android.com/tools/releases/platform-tools)；若摄像头已跑通，直接复用已有环境和 `start-media-mac.command`。
2. **选择电脑声音输出。** 在 Mac“系统设置 → 声音 → 输出”中选 **BlackHole 2ch**。此时电脑原扬声器可能不再播放这一路声音，因为声音已进入虚拟音频线。
3. **运行桥接命令。** 手机开启 USB 调试、允许此 Mac 调试，并打开应用“音箱”页面。在现有 `start-media-mac.command` 所在文件夹打开终端，运行以下命令。提示选择模式时输入 `2`，然后在设备列表中输入 **BlackHole 2ch** 对应的编号，设备应支持 2 路输入。保持窗口打开：

   ```bash
   ./start-media-mac.command
   ```

4. **播放测试并恢复输出。** 调高手机媒体音量，在 Mac 播放声音。终端显示“音箱已连接”后，应从手机听到声音。结束后将 Mac 声音输出改回原扬声器或耳机。若 macOS 请求允许终端使用麦克风，请允许，因为 Bridge 要从虚拟音频输入读取声音。

## 摄像头

1. **下载安装 OBS。** 从 [OBS 官方下载页](https://obsproject.com/download)下载 macOS 版。OBS 提供给电脑应用选择的 **OBS Virtual Camera** 设备。电脑首次使用还需 [Python 3](https://www.python.org/downloads/) 和 [Android Platform Tools（adb）](https://developer.android.com/tools/releases/platform-tools)；已有 `start-media-mac.command` 可直接复用。
2. **开启虚拟摄像头扩展。** 首次打开 OBS，点“启动虚拟摄像头”，再点“停止虚拟摄像头”，然后退出 OBS。若 macOS 拦截，进入“系统设置 → 通用 → 登录项与扩展 → 摄像头扩展”，允许 **OBS Virtual Camera**；必要时重启 OBS。参见 [OBS 官方排障说明](https://obsproject.com/kb/virtual-camera-troubleshooting)。这是一次性设置。
3. **运行桥接命令。** 手机开启 USB 调试并允许此 Mac 调试，在应用中打开“摄像头”。在解压后的工具包文件夹打开终端，运行以下命令，提示选择模式时输入 `3`，保持窗口打开：

   ```bash
   ./start-media-mac.command
   ```

   Mac 的 QuickTime Player 可选“文件 → 新建影片录制”，在录制按钮旁的摄像头菜单中选择 **OBS Virtual Camera**。此时 OBS 本身不用保持打开，也不要与 Bridge 同时启动 OBS 的虚拟摄像头。若预览方向不对，点击手机摄像头页面右上角“画面旋转 90°”，手机和电脑画面会一起旋转。

若手机 USB 设置中有“网络摄像头”，可直接使用手机系统模式；应用内预览与该模式互不控制。

`start-usb-mac.command` 只处理触摸板、键盘、手柄和文字。音视频需运行 `start-media-mac.command`。输入 Bridge 首次还需给终端“隐私与安全性 → 无障碍”权限；读取虚拟音频输入时，macOS 可能另行请求麦克风权限。音频固定为 48 kHz、16 位 PCM；BlackHole 需配置 48 kHz 双声道。更多失败情况见 [兼容性说明](音视频兼容性与下载说明.md)。
