# 懒人外设：Windows USB 授权与连接指南

适用于通过 USB 数据线将 Android 手机上的“懒人外设”连接到 Windows 电脑。仅插入数据线不会使普通 Android 手机自动成为 USB 鼠标；Windows 上需要运行本项目的 USB Bridge。

## 1. 授权手机的 USB 调试

1. 在手机的“开发者选项”中开启 **USB 调试**。不同品牌的入口可能不同；如尚未显示“开发者选项”，通常需在“关于手机”中连续点击版本号。
2. 用支持数据传输的 USB 线连接 Windows 电脑，保持手机解锁。只支持充电的线无法完成后续连接。
3. 从手机屏幕顶部下拉通知栏，点 **“正在通过 USB 为此设备充电”**、**“USB 用途”**或类似通知；在“USB 的用途”里选 **“文件传输”**（有些手机写作“传输文件”“传输数据”或“MTP”）。若看不到通知，可在手机“设置”中搜索“USB”或重新插拔数据线。
4. 手机弹出“允许 USB 调试？”时，核对是自己的电脑，再点“允许”。这一步与“文件传输”是两个不同选项，都要检查。随后在 Windows 命令提示符运行 `adb devices`：显示序列号及 `device` 表示就绪；`unauthorized` 表示需要在手机上确认授权；列表为空时先检查数据线、USB 用途和驱动。

## 2. 准备 Windows 电脑

1. 安装 **Python 3** 与 **Android Platform Tools**，使 `py -3`（或 `python`）和 `adb` 可在命令提示符中运行。可从 [GitHub 下载 Windows 全功能桥接 ZIP](https://raw.githubusercontent.com/zhipengyue/LazyPeri/main/releases/懒人外设-Windows-全功能-USB-Bridge.zip)，解压到电脑本地文件夹；不要直接在 ZIP 压缩包内双击启动文件。
2. 若 `adb devices` 看不到手机，可能需要安装该手机厂商的 **ADB USB 驱动**；Google 设备使用 Google USB Driver。驱动来源请以手机厂商或 Android 开发者文档为准。
3. **Windows 没有与 macOS“无障碍”相同的全局授权开关。**正常情况下，Bridge 无需“以管理员身份运行”。如果目标程序自身以管理员身份运行，普通权限的 Bridge 可能无法向它输入；可先在未提升权限的“记事本”中测试，必要时再让 Bridge 与目标程序使用相同权限级别。不要把管理员权限作为常规设置。

## 3. 启动并测试

1. 在手机上打开“懒人外设”。
2. 双击项目中的 `tools/start-usb-windows.bat`，或解压工具包后双击其中的同名文件；保持命令窗口开启。电脑端脚本与手机应用必须同时运行。
3. 正常时依次看到 **“USB Bridge 已就绪。”**、**“触摸板已连接。”**
4. 在 Windows 上打开“记事本”，先点入编辑区域，再在手机上移动指针或发送文字。文字发往电脑当前获得输入焦点的位置。

## 常见问题

- **找不到 adb**：安装 Android Platform Tools，并把 `adb` 加入 PATH；如果电脑已安装 Android Studio，也可使用其中的 SDK Platform Tools。
- **“py”不是内部或外部命令**：安装带有 Python Launcher 的 Python 3，或在确认 `python` 可用后，从命令提示符运行 `python tools\usb-bridge.py`。
- **需要恰好一台已授权的 Android 设备**：运行 `adb devices`。请只连接一台调试设备；`unauthorized` 时在手机上允许 USB 调试；未列出设备时先解锁手机，选择“文件传输／传输数据”，再检查数据线和 ADB 驱动。
- **Bridge 已就绪，但没有“触摸板已连接”**：确认手机应用已经打开，USB 数据线未断开，并重新启动 Bridge。
- **窗口显示“等待手机应用连接”**：检查手机上是否安装了新版“懒人外设”，并保持应用打开；退出旧版 Bridge，使用此工具包中的新版脚本重试。
- **显示“已收到 MOVE / KEY 输入事件”，但电脑无反应**：说明 USB 通道已经接通。先在桌面和记事本测试，再检查目标程序是否以管理员身份运行或是否拦截模拟输入。若仍失败，请把窗口中的错误提示发给开发者。
- **显示已连接但文字未进入电脑**：先点击目标文本框，让它成为前台窗口；先在“记事本”测试。若只在某个以管理员身份运行的程序中失败，请检查 Bridge 与目标程序的权限级别。某些程序也可能不接受模拟输入。

参考：[Android：在硬件设备上运行应用及 RSA 调试授权](https://developer.android.com/studio/run/device)、[Android：Windows USB 驱动](https://developer.android.com/studio/run/win-usb)、[Microsoft：SendInput 与权限级别](https://learn.microsoft.com/windows/win32/api/winuser/nf-winuser-sendinput)。
