#!/usr/bin/env python3
"""Receive trackpad events from the Android app over an adb USB port forward."""

import argparse
import ctypes
import ctypes.util
import base64
import binascii
import os
import platform
import shutil
import socket
import subprocess
import sys
import threading
import time

PORT = 27183


def windows_hid_code(usage):
    if 4 <= usage <= 29:
        return 0x41 + usage - 4
    if 30 <= usage <= 38:
        return 0x31 + usage - 30
    codes = {39: 0x30, 40: 0x0D, 41: 0x1B, 42: 0x08, 43: 0x09, 44: 0x20,
             45: 0xBD, 46: 0xBB, 47: 0xDB, 48: 0xDD, 49: 0xDC,
             51: 0xBA, 52: 0xDE, 53: 0xC0, 54: 0xBC, 55: 0xBE, 56: 0xBF,
             79: 0x27, 80: 0x25, 81: 0x28, 82: 0x26}
    return codes.get(usage)


def mac_hid_code(usage):
    letters = [0, 11, 8, 2, 14, 3, 5, 4, 34, 38, 40, 37, 46,
               45, 31, 35, 12, 15, 1, 17, 32, 9, 13, 7, 16, 6]
    if 4 <= usage <= 29:
        return letters[usage - 4]
    digits = [18, 19, 20, 21, 23, 22, 26, 28, 25, 29]
    if 30 <= usage <= 39:
        return digits[usage - 30]
    codes = {40: 36, 41: 53, 42: 51, 43: 48, 44: 49,
             45: 27, 46: 24, 47: 33, 48: 30, 49: 42,
             51: 41, 52: 39, 53: 50, 54: 43, 55: 47, 56: 44,
             79: 124, 80: 123, 81: 125, 82: 126}
    return codes.get(usage)


class _WinMouseInput(ctypes.Structure):
    _fields_ = [("dx", ctypes.c_long), ("dy", ctypes.c_long), ("mouseData", ctypes.c_ulong),
                ("dwFlags", ctypes.c_ulong), ("time", ctypes.c_ulong), ("dwExtraInfo", ctypes.c_void_p)]


class _WinKeyboardInput(ctypes.Structure):
    _fields_ = [("wVk", ctypes.c_ushort), ("wScan", ctypes.c_ushort),
                ("dwFlags", ctypes.c_ulong), ("time", ctypes.c_ulong), ("dwExtraInfo", ctypes.c_void_p)]


class _WinInputUnion(ctypes.Union):
    _fields_ = [("mi", _WinMouseInput), ("ki", _WinKeyboardInput)]


class _WinInput(ctypes.Structure):
    _fields_ = [("type", ctypes.c_ulong), ("u", _WinInputUnion)]


def find_adb():
    for candidate in (
        shutil.which("adb"),
        os.path.join(os.environ.get("ANDROID_HOME", ""), "platform-tools", "adb"),
        os.path.join(os.environ.get("ANDROID_SDK_ROOT", ""), "platform-tools", "adb"),
        os.path.expanduser("~/Library/Android/sdk/platform-tools/adb"),
        os.path.join(os.environ.get("LOCALAPPDATA", ""), "Android", "Sdk", "platform-tools", "adb.exe"),
    ):
        if candidate and os.path.isfile(candidate):
            return candidate
    raise RuntimeError("找不到 adb。请安装 Android Platform Tools，或将 adb 加入 PATH。")


class WindowsInput:
    class Point(ctypes.Structure):
        _fields_ = [("x", ctypes.c_long), ("y", ctypes.c_long)]

    def __init__(self):
        self.user32 = ctypes.windll.user32
        self.down = False

    def move(self, dx, dy):
        point = self.Point()
        if not self.user32.GetCursorPos(ctypes.byref(point)):
            raise ctypes.WinError()
        if not self.user32.SetCursorPos(point.x + dx, point.y + dy):
            raise ctypes.WinError()

    def button(self, down, right=False):
        flag = (0x0008 if down else 0x0010) if right else (0x0002 if down else 0x0004)
        self.user32.mouse_event(flag, 0, 0, 0, 0)
        if not right:
            self.down = down

    def scroll(self, ticks):
        self.user32.mouse_event(0x0800, 0, 0, ctypes.c_uint32(ticks * 120).value, 0)

    def switch(self, right):
        keys = (0x5B, 0x11, 0x27 if right else 0x25)
        for key in keys:
            self.user32.keybd_event(key, 0, 0, 0)
        time.sleep(0.07)
        for key in reversed(keys):
            self.user32.keybd_event(key, 0, 2, 0)

    def key(self, name, down):
        codes = {"UP": 0x26, "DOWN": 0x28, "LEFT": 0x25, "RIGHT": 0x27,
                 "ENTER": 0x0D, "TAB": 0x09}
        code = codes[name] if name in codes else ord(name)
        self.user32.keybd_event(code, 0, 0 if down else 2, 0)

    def hid_key(self, modifiers, usage):
        key = windows_hid_code(usage)
        if key is None:
            return
        held = [code for bit, code in ((8, 0x5B), (1, 0x11), (4, 0x12), (2, 0x10))
                if modifiers & bit]
        for code in held:
            self.user32.keybd_event(code, 0, 0, 0)
        self.user32.keybd_event(key, 0, 0, 0)
        time.sleep(0.035)
        self.user32.keybd_event(key, 0, 2, 0)
        for code in reversed(held):
            self.user32.keybd_event(code, 0, 2, 0)

    def text(self, value):
        # KEYEVENTF_UNICODE enters UTF-16 at the focused application without changing the clipboard.
        for offset in range(0, len(value), 32):
            units = value[offset:offset + 32].encode("utf-16-le")
            for index in range(0, len(units), 2):
                unit = int.from_bytes(units[index:index + 2], "little")
                events = (_WinInput * 2)()
                events[0].type = events[1].type = 1
                events[0].u.ki = _WinKeyboardInput(0, unit, 0x0004, 0, None)
                events[1].u.ki = _WinKeyboardInput(0, unit, 0x0004 | 0x0002, 0, None)
                if self.user32.SendInput(2, events, ctypes.sizeof(_WinInput)) != 2:
                    raise OSError("Windows SendInput failed")


class MacInput:
    class Point(ctypes.Structure):
        _fields_ = [("x", ctypes.c_double), ("y", ctypes.c_double)]

    def __init__(self):
        self.cg = ctypes.CDLL(ctypes.util.find_library("ApplicationServices"))
        self.cf = ctypes.CDLL(ctypes.util.find_library("CoreFoundation"))
        self.cg.AXIsProcessTrusted.restype = ctypes.c_bool
        if not self.cg.AXIsProcessTrusted():
            raise RuntimeError(
                "macOS 未授予当前运行 USB Bridge 的程序‘辅助功能’权限。"
                "请在 系统设置 → 隐私与安全性 → 辅助功能 中允许终端（若从终端运行）或 Codex（若从 Codex 运行），"
                "然后完全退出并重新打开该程序，再启动 USB Bridge。"
            )
        self.cg.CGEventCreate.restype = ctypes.c_void_p
        self.cg.CGEventCreate.argtypes = [ctypes.c_void_p]
        self.cg.CGEventGetLocation.restype = self.Point
        self.cg.CGEventGetLocation.argtypes = [ctypes.c_void_p]
        self.cg.CGEventCreateMouseEvent.restype = ctypes.c_void_p
        self.cg.CGEventCreateMouseEvent.argtypes = [ctypes.c_void_p, ctypes.c_uint32, self.Point, ctypes.c_uint32]
        self.cg.CGEventCreateScrollWheelEvent.restype = ctypes.c_void_p
        self.cg.CGEventCreateScrollWheelEvent.argtypes = [ctypes.c_void_p, ctypes.c_uint32, ctypes.c_uint32]
        self.cg.CGEventCreateKeyboardEvent.restype = ctypes.c_void_p
        self.cg.CGEventCreateKeyboardEvent.argtypes = [ctypes.c_void_p, ctypes.c_uint16, ctypes.c_bool]
        self.cg.CGEventSetFlags.argtypes = [ctypes.c_void_p, ctypes.c_uint64]
        self.cg.CGEventKeyboardSetUnicodeString.argtypes = [ctypes.c_void_p, ctypes.c_ulong,
                                                            ctypes.POINTER(ctypes.c_uint16)]
        self.cg.CGEventPost.argtypes = [ctypes.c_uint32, ctypes.c_void_p]
        self.cf.CFRelease.argtypes = [ctypes.c_void_p]
        self.down = False

    def location(self):
        event = self.cg.CGEventCreate(None)
        point = self.cg.CGEventGetLocation(event)
        self.cf.CFRelease(event)
        return point

    def post(self, event):
        self.cg.CGEventPost(0, event)
        self.cf.CFRelease(event)

    def move(self, dx, dy):
        point = self.location()
        point.x += dx
        point.y += dy
        self.post(self.cg.CGEventCreateMouseEvent(None, 6 if self.down else 5, point, 0))

    def button(self, down, right=False):
        self.post(self.cg.CGEventCreateMouseEvent(None, 3 if right and down else 4 if right else 1 if down else 2,
                                                   self.location(), 1 if right else 0))
        if not right:
            self.down = down

    def scroll(self, ticks):
        self.post(self.cg.CGEventCreateScrollWheelEvent(None, 1, 1, ticks))

    def switch(self, right):
        key = 124 if right else 123
        for down in (True, False):
            event = self.cg.CGEventCreateKeyboardEvent(None, key, down)
            self.cg.CGEventSetFlags(event, 1 << 18)  # Control
            self.post(event)
            if down:
                time.sleep(0.07)

    def key(self, name, down):
        codes = {"W": 13, "A": 0, "S": 1, "D": 2, "J": 38, "K": 40,
                 "U": 32, "I": 34, "Q": 12, "E": 14, "ENTER": 36,
                 "TAB": 48, "LEFT": 123, "RIGHT": 124, "DOWN": 125, "UP": 126}
        self.post(self.cg.CGEventCreateKeyboardEvent(None, codes[name], down))

    def hid_key(self, modifiers, usage):
        key = mac_hid_code(usage)
        if key is None:
            return
        flags = ((1 << 18) if modifiers & 1 else 0) | ((1 << 17) if modifiers & 2 else 0) | \
                ((1 << 19) if modifiers & 4 else 0) | ((1 << 20) if modifiers & 8 else 0)
        for down in (True, False):
            event = self.cg.CGEventCreateKeyboardEvent(None, key, down)
            self.cg.CGEventSetFlags(event, flags)
            self.post(event)
            if down:
                time.sleep(0.035)

    def text(self, value):
        # Quartz Unicode keyboard events preserve the computer clipboard.
        for character in value:
            raw = character.encode("utf-16-le")
            units = (ctypes.c_uint16 * (len(raw) // 2)).from_buffer_copy(raw)
            for down in (True, False):
                event = self.cg.CGEventCreateKeyboardEvent(None, 0, down)
                self.cg.CGEventKeyboardSetUnicodeString(event, len(units), units)
                self.post(event)


class PadMapper:
    """USB fallback: translate standard gamepad controls into configurable game keys."""

    def __init__(self):
        self.held = set()

    def update(self, device, buttons, hat, lx, ly, rx, ry):
        desired = set()
        if hat in (0, 1, 7) or ly < -45: desired.add("W")
        if hat in (3, 4, 5) or ly > 45: desired.add("S")
        if hat in (5, 6, 7) or lx < -45: desired.add("A")
        if hat in (1, 2, 3) or lx > 45: desired.add("D")
        if ry < -45: desired.add("UP")
        if ry > 45: desired.add("DOWN")
        if rx < -45: desired.add("LEFT")
        if rx > 45: desired.add("RIGHT")
        for bit, key in enumerate(("ENTER", "K", "U", "I", "Q", "E", "TAB", "ENTER")):
            if buttons & (1 << bit): desired.add(key)
        for key in sorted(self.held - desired): device.key(key, False)
        for key in sorted(desired - self.held): device.key(key, True)
        self.held = desired

    def release(self, device):
        self.update(device, 0, 8, 0, 0, 0, 0)


class XboxPad:
    def __init__(self, send):
        import vgamepad as vg

        self.pad = vg.VX360Gamepad()
        button = vg.XUSB_BUTTON
        self.buttons = (
            button.XUSB_GAMEPAD_A, button.XUSB_GAMEPAD_B, button.XUSB_GAMEPAD_X,
            button.XUSB_GAMEPAD_Y, button.XUSB_GAMEPAD_LEFT_SHOULDER,
            button.XUSB_GAMEPAD_RIGHT_SHOULDER, button.XUSB_GAMEPAD_BACK,
            button.XUSB_GAMEPAD_START,
        )
        up, right, down, left = (
            button.XUSB_GAMEPAD_DPAD_UP, button.XUSB_GAMEPAD_DPAD_RIGHT,
            button.XUSB_GAMEPAD_DPAD_DOWN, button.XUSB_GAMEPAD_DPAD_LEFT,
        )
        self.directions = (up, up | right, right, right | down, down,
                           down | left, left, left | up, 0)

        def rumble(client, target, large_motor, small_motor, led, user_data):
            try:
                if send is not None:
                    send(f"RUMBLE {large_motor} {small_motor}")
            except OSError:
                pass

        self.pad.register_notification(callback_function=rumble)

    def update(self, device, buttons, hat, lx, ly, rx, ry):
        self.pad.reset()
        mask = self.directions[hat] if 0 <= hat < len(self.directions) else 0
        for bit, button in enumerate(self.buttons):
            if buttons & (1 << bit):
                mask |= button
        self.pad.press_button(button=mask)
        self.pad.left_trigger(value=255 if buttons & (1 << 8) else 0)
        self.pad.right_trigger(value=255 if buttons & (1 << 9) else 0)
        self.pad.left_joystick_float(x_value_float=lx / 127, y_value_float=-ly / 127)
        self.pad.right_joystick_float(x_value_float=rx / 127, y_value_float=-ry / 127)
        self.pad.update()

    def release(self, device):
        self.pad.unregister_notification()
        self.pad.reset()
        self.pad.update()


def pad_mapper(send):
    if platform.system() == "Windows":
        try:
            mapper = XboxPad(send)
            print("Xbox 360 虚拟手柄已连接，支持游戏震动。", flush=True)
            return mapper
        except (ImportError, OSError, AssertionError, RuntimeError) as error:
            print(f"Xbox 手柄初始化失败，请检查 ViGEmBus 和 vgamepad 后重启 Bridge：{error}", flush=True)
            return None
    print("手柄使用键盘映射（A = Enter，X = U）。", flush=True)
    return PadMapper()


def handle(line, input_device, send=None):
    parts = line.split()
    if not parts:
        return
    if parts[0] == "MOVE" and len(parts) == 3:
        input_device.move(int(parts[1]), int(parts[2]))
    elif parts[0] == "SCROLL" and len(parts) == 2:
        input_device.scroll(int(parts[1]))
    elif parts[0] == "BUTTON" and len(parts) == 2:
        input_device.button(parts[1] == "1")
    elif parts[0] == "CLICK" and len(parts) == 2:
        right = parts[1] == "2"
        input_device.button(True, right)
        time.sleep(0.04)
        input_device.button(False, right)
    elif parts[0] == "SWITCH" and len(parts) == 3:
        input_device.switch(parts[2] == "R")
    elif parts[0] == "PAD" and len(parts) == 7:
        if not hasattr(input_device, "pad_mapper"):
            input_device.pad_mapper = pad_mapper(send)
        if input_device.pad_mapper is not None:
            input_device.pad_mapper.update(input_device, *(int(value) for value in parts[1:]))
    elif parts[0] == "TEXT" and len(parts) == 3:
        input_device.text(base64.b64decode(parts[2], validate=True).decode("utf-8"))
    elif parts[0] == "KEY" and len(parts) == 3:
        modifiers, usage = int(parts[1]), int(parts[2])
        if 0 <= modifiers <= 15 and 4 <= usage <= 82:
            input_device.hid_key(modifiers, usage)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--xbox-gamepad", action="store_true", help="使用 Windows Xbox 虚拟手柄")
    parser.parse_args()
    system = platform.system()
    if system == "Darwin":
        input_device = MacInput()
        print("macOS 辅助功能权限已确认。")
    elif system == "Windows":
        input_device = WindowsInput()
    else:
        raise RuntimeError("目前仅支持 macOS 和 Windows 电脑。")

    adb = find_adb()
    devices = subprocess.check_output([adb, "devices"], text=True).splitlines()
    ready = [line.split()[0] for line in devices[1:] if line.strip().endswith("\tdevice")]
    if len(ready) != 1:
        status = "; ".join(line.strip() for line in devices[1:] if line.strip()) or "未检测到设备"
        raise RuntimeError(
            "需要恰好一台已授权的 Android 设备。当前 adb 状态：" + status +
            "。请开启 USB 调试，在手机上允许此电脑调试；Windows 若未检测到设备，请检查 ADB 驱动。"
        )
    serial = ready[0]
    subprocess.run([adb, "-s", serial, "forward", f"tcp:{PORT}", f"tcp:{PORT}"], check=True)
    print("USB Bridge 已就绪。请保持手机应用打开；按 Ctrl+C 退出。", flush=True)
    waiting_reported = False
    seen_events = set()
    try:
        while True:
            try:
                with socket.create_connection(("127.0.0.1", PORT), timeout=3) as peer:
                    # The 3-second timeout is only for connecting. Text and gesture
                    # input can be idle for much longer without dropping the bridge.
                    peer.settimeout(None)
                    print("触摸板已连接。", flush=True)
                    waiting_reported = False
                    send_lock = threading.Lock()

                    def send(message, connection=peer, lock=send_lock):
                        with lock:
                            connection.sendall((message + "\n").encode("ascii"))

                    with peer.makefile("r", encoding="ascii") as stream:
                        for line in stream:
                            request_id = None
                            fields = line.split(maxsplit=2)
                            if len(fields) == 3 and fields[0] == "TEXT" and fields[1].isdigit():
                                request_id = fields[1]
                            try:
                                handle(line, input_device, send)
                                event_type = fields[0] if fields else ""
                                if event_type and event_type not in seen_events:
                                    seen_events.add(event_type)
                                    print(f"已收到 {event_type} 输入事件。", flush=True)
                                if request_id:
                                    send(f"ACK {request_id}")
                            except (ValueError, OverflowError, UnicodeDecodeError, binascii.Error, OSError) as error:
                                print(f"输入事件失败：{error}", file=sys.stderr, flush=True)
                                if request_id:
                                    send(f"ERR {request_id}")
            except (OSError, ConnectionError):
                if not waiting_reported:
                    print("等待手机应用连接；请确认手机已打开应用并允许 USB 调试。", flush=True)
                    waiting_reported = True
            finally:
                if input_device.down:
                    input_device.button(False)
                if hasattr(input_device, "pad_mapper"):
                    if input_device.pad_mapper is not None:
                        input_device.pad_mapper.release(input_device)
                    del input_device.pad_mapper
            time.sleep(1)
    except KeyboardInterrupt:
        print("\n已退出。")
    finally:
        subprocess.run([adb, "-s", serial, "forward", "--remove", f"tcp:{PORT}"], check=False)


if __name__ == "__main__":
    try:
        main()
    except (RuntimeError, subprocess.CalledProcessError) as error:
        print(f"错误：{error}", file=sys.stderr)
        sys.exit(1)
