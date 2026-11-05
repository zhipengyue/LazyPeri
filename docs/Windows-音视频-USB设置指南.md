# 懒人外设：Windows 音视频 USB 设置

首次使用时，可在电脑上从 [GitHub 下载 Windows 全功能桥接 ZIP](https://raw.githubusercontent.com/zhipengyue/LazyPeri/main/releases/懒人外设-Windows-全功能-USB-Bridge.zip)（`releases/懒人外设-Windows-全功能-USB-Bridge.zip`），或在手机应用的“摄像头”“音箱”指南页保存同一 ZIP，再传到电脑。下载后先解压；ZIP 只是脚本的打包方式，不是需要安装的软件。若已通过 `start-media-windows.bat` 调试过摄像头，音箱可直接复用同一个脚本，选择模式 `2`，无需再次保存。手机安装新版 APK，开启 USB 调试、允许此电脑调试，并打开“懒人外设”。电脑需安装 Python 3 和 Android Platform Tools (`adb`)。Windows 音视频仍需真机验证。

## 麦克风

1. 从 [VB-Audio 官网](https://vb-audio.com/Cable/) 安装 **VB-CABLE**；按原厂要求重启 Windows。
2. 双击 `start-media-windows.bat`，选 `1`。首次运行会联网下载 Python 依赖。在设备列表中输入 **CABLE Input** 对应的输出设备编号。
3. 手机进入“麦克风”并授予权限；说话时图案应发蓝光。电脑窗口应显示连接。
4. 在会议或录音软件中选择 **CABLE Output** 作为麦克风，录音测试。

## 音箱

电脑声音 → VB-CABLE → USB Bridge → 手机扬声器。手机应用首次进入音箱时会打开独立指南，之后可点右上角“指南”重看。

1. **下载安装。** 从 [VB-Audio 官网](https://vb-audio.com/Cable/)下载并安装 **VB-CABLE**。按官网说明以管理员身份运行安装程序并重启 Windows。它让 Bridge 能读取电脑的声音。电脑首次使用还需 [Python 3](https://www.python.org/downloads/) 和 [Android Platform Tools（adb）](https://developer.android.com/tools/releases/platform-tools)；若摄像头已跑通，直接复用已有环境和 `start-media-windows.bat`。
2. **选择电脑声音输出。** 在“设置 → 系统 → 声音 → 输出”中选择 **CABLE Input**。这是虚拟音频线的播放端，不是下一步要输入的设备编号。
3. **运行桥接命令。** 手机开启 USB 调试、允许此电脑调试，并打开应用“音箱”页面。在现有 `start-media-windows.bat` 所在文件夹打开命令提示符，运行以下命令。提示选择模式时输入 `2`，然后在设备列表中输入 **CABLE Output** 对应的编号，设备应支持 2 路输入。保持窗口打开：

   ```bat
   .\start-media-windows.bat
   ```

4. **播放测试并恢复输出。** 调高手机媒体音量，在电脑播放声音。命令窗口显示“音箱已连接”后，应从手机听到声音。结束后把 Windows 声音输出改回原扬声器或耳机。

## 摄像头

1. **下载安装 OBS。** 从 [OBS 官方下载页](https://obsproject.com/download)下载 Windows 版。OBS 提供给电脑应用选择的 **OBS Virtual Camera** 设备。电脑首次使用还需 [Python 3](https://www.python.org/downloads/) 和 [Android Platform Tools（adb）](https://developer.android.com/tools/releases/platform-tools)；已有 `start-media-windows.bat` 可直接复用。
2. **确认虚拟摄像头可用。** Windows 通常无需像 Mac 一样打开系统扩展开关。若 OBS 中没有“启动虚拟摄像头”，参照 [OBS 官方排障说明](https://obsproject.com/kb/virtual-camera-troubleshooting)修复安装。
3. **运行桥接命令。** 手机开启 USB 调试并允许此电脑调试，在应用中打开“摄像头”。在解压后的工具包文件夹打开命令提示符，运行以下命令，提示选择模式时输入 `3`，保持窗口打开：

   ```bat
   .\start-media-windows.bat
   ```

   在视频会议软件的摄像头设置中选择 **OBS Virtual Camera**。此时 OBS 本身不用保持打开，也不要与 Bridge 同时启动 OBS 的虚拟摄像头。

若手机 USB 设置中有“网络摄像头”，可直接使用手机系统模式；应用内预览与该模式互不控制。

只运行 `start-usb-windows.bat` 不传送音视频。音频固定为 48 kHz、16 位 PCM，虚拟音频设备需支持 48 kHz 双声道。更多失败情况见 [兼容性说明](音视频兼容性与下载说明.md)。
