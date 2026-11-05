# 懒人外设：手机作为 Mac 扩展屏幕

本功能把 Mac **已经识别的第二块显示器**画面通过 USB 传到手机。它不会自行创建 macOS 虚拟显示器。**安装 BetterDisplay、连接手机、打开“显示器”页面，都不会自动创建虚拟屏幕。**必须先在 BetterDisplay 中创建并连接它，再运行桥接脚本。

1. **打开 BetterDisplay。** 安装后从“应用程序”打开 BetterDisplay。点 Mac 菜单栏中的 BetterDisplay 图标，再点齿轮进入设置。
2. **创建虚拟屏幕。** 在 BetterDisplay 中打开 **Settings → Displays → Overview → Create New Virtual Screen…**。选择 **Pre-configured settings (recommended)**，再选 **16:9 - HD/4K/5K/6K**，点击 **Create Virtual Screen**。这一步只是保存配置；预设虚拟屏幕的创建和连接不需要 Pro。[BetterDisplay 创建指南](https://betterdisplay.pro/guide/interface-reference/settings/displays/overview/create-virtual-screen/)
3. **连接虚拟屏幕。** 在 BetterDisplay 的 **Settings → Displays** 列表中点刚创建的虚拟屏幕，开启 **Connect this virtual screen**。请保持 BetterDisplay 运行。不要开启“Configure full screen stream to this display”，本项目会自行把画面传给手机。[BetterDisplay 连接步骤](https://betterdisplay.pro/guide/tutorials/dedicated-recording-and-sharing-desktop/)
4. **检查 macOS 是否识别。** 打开“系统设置 → 显示器”，应能看到内建屏幕和新虚拟屏幕两块。选择虚拟屏幕，在“用作”中选择**扩展显示器**，不要选镜像。随后运行下方桥接脚本时，应列出至少两个编号。若仍只有 `1`，回到 BetterDisplay 检查虚拟屏幕是否已连接。[Apple 显示器设置说明](https://support.apple.com/zh-cn/guide/mac-help/mchlb5f905a1/27/mac/27)
5. **准备 USB。** 安装 [Python 3](https://www.python.org/downloads/) 与 [Android Platform Tools（adb）](https://developer.android.com/tools/releases/platform-tools)。手机开启 USB 调试，用可传数据的线连接 Mac 并解锁。下拉手机通知栏，点“正在通过 USB 充电”或“USB 用途”，选择 **“文件传输／传输数据（MTP）”**；再允许这台 Mac 进行 USB 调试。Mac 终端运行 `adb devices`，确认序列号后显示 `device`，然后在应用中打开“显示器”页面。
6. **运行桥接包。** 在 Mac 上从 [GitHub 下载 macOS 全功能桥接 ZIP](https://raw.githubusercontent.com/zhipengyue/LazyPeri/main/releases/懒人外设-macOS-全功能-USB-Bridge.zip)（仓库路径：`releases/懒人外设-macOS-全功能-USB-Bridge.zip`），或者在手机指南页保存同一 ZIP 再传到 Mac。解压后，在脚本所在文件夹双击或运行 `./start-display-mac.command`。首次运行需要联网安装 `mss` 和 `Pillow`。
7. **选择屏幕。** 脚本列出显示器编号，输入虚拟扩展屏幕的编号。保持终端窗口打开，将 Mac 窗口拖到该扩展屏幕，手机即显示画面。若 Mac 请求屏幕录制权限，前往“系统设置 → 隐私与安全性 → 屏幕与系统音频录制”允许实际运行脚本的终端，完全退出终端并重新启动脚本。

当前版本以约 10 帧/秒传输 JPEG 画面。把鼠标指针移到扩展屏幕时，手机画面会显示指针。手机屏幕目前只用于显示，触摸操作请切回本应用的触摸板。受 macOS 屏幕录制限制，受保护内容可能无法显示。
