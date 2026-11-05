# 懒人外设：手机作为 Windows 扩展屏幕

本功能把 Windows **已经识别的第二块显示器**画面通过 USB 传到手机。它不会安装或创建 Windows 虚拟显示器驱动。首次使用先连接第二块实体显示器，或安装你信任的虚拟显示器驱动，并在“设置 → 系统 → 屏幕”中选择“扩展这些显示器”。

1. 安装 [Python 3](https://www.python.org/downloads/) 和 [Android Platform Tools（adb）](https://developer.android.com/tools/releases/platform-tools)。手机开启 USB 调试，插入数据线并允许这台电脑调试，在应用中打开“显示器”页面。
2. 在电脑上从 [GitHub 下载 Windows 全功能桥接 ZIP](https://raw.githubusercontent.com/zhipengyue/LazyPeri/main/releases/懒人外设-Windows-全功能-USB-Bridge.zip)（仓库路径：`releases/懒人外设-Windows-全功能-USB-Bridge.zip`），或者在手机指南页保存同一 ZIP 再传到电脑。解压后双击 `start-display-windows.bat`，首次运行需要联网安装 `mss` 和 `Pillow`。
3. 批处理文件列出显示器编号，输入扩展屏幕的编号。保持命令窗口打开，将电脑窗口拖到该屏幕，手机即显示画面。若列表只有一块显示器，先检查 Windows 显示设置与虚拟显示器驱动。

当前版本以约 10 帧/秒传输 JPEG 画面。把鼠标指针移到扩展屏幕时，手机画面会显示指针。手机屏幕目前只用于显示，触摸操作请切回本应用的触摸板。受保护内容可能无法被屏幕采集。
