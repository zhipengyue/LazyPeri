#!/usr/bin/env python3
"""Stream one OS-visible display to the phone over an adb loopback channel."""

import argparse
import ctypes
import importlib.util
import io
import os
import socket
import struct
import subprocess
import sys
import time

PORT = 27187
FRAME_INTERVAL = 1 / 10
MAX_FRAME_SIZE = (1280, 800)


def find_adb():
    location = os.path.join(os.path.dirname(__file__), "usb-bridge.py")
    spec = importlib.util.spec_from_file_location("peripheral_usb_bridge", location)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module.find_adb()


def authorized_phone(adb):
    lines = subprocess.check_output([adb, "devices"], text=True).splitlines()
    ready = [line.split()[0] for line in lines[1:] if line.strip().endswith("\tdevice")]
    if len(ready) != 1:
        status = "; ".join(line.strip() for line in lines[1:] if line.strip()) or "未检测到设备"
        raise RuntimeError("需要恰好一台已授权 USB 调试的手机；当前状态：" + status)
    return ready[0]


def describe_monitors(monitors):
    for index, monitor in enumerate(monitors[1:], 1):
        print(f"{index} · 显示器 · {monitor['width']}×{monitor['height']}")


def require_extended_display(monitors):
    if len(monitors) >= 3:
        return
    if sys.platform == "darwin":
        raise RuntimeError("Mac 目前只识别到一块屏幕。请在 BetterDisplay 中创建虚拟屏幕，"
                           "再打开该屏幕的“Connect this virtual screen”；确认“系统设置 → 显示器”出现两块屏幕后重试。")
    raise RuntimeError("电脑只识别到一块屏幕。请先连接或创建第二块显示器，并设置为扩展桌面。")


def cursor_reader():
    """Read the pointer in the same global desktop coordinates as MSS monitors."""
    if sys.platform == "darwin":
        class Point(ctypes.Structure):
            _fields_ = [("x", ctypes.c_double), ("y", ctypes.c_double)]

        core = ctypes.CDLL("/System/Library/Frameworks/CoreGraphics.framework/CoreGraphics")
        core.CGEventCreate.argtypes = [ctypes.c_void_p]
        core.CGEventCreate.restype = ctypes.c_void_p
        core.CGEventGetLocation.argtypes = [ctypes.c_void_p]
        core.CGEventGetLocation.restype = Point
        core.CFRelease.argtypes = [ctypes.c_void_p]
        core.CFRelease.restype = None

        def read_mac_cursor():
            event = core.CGEventCreate(None)
            if not event:
                return None
            try:
                point = core.CGEventGetLocation(event)
                return point.x, point.y
            finally:
                core.CFRelease(event)

        return read_mac_cursor

    if sys.platform == "win32":
        class Point(ctypes.Structure):
            _fields_ = [("x", ctypes.c_long), ("y", ctypes.c_long)]

        get_position = ctypes.windll.user32.GetCursorPos
        get_position.argtypes = [ctypes.POINTER(Point)]
        get_position.restype = ctypes.c_int

        def read_windows_cursor():
            point = Point()
            return (point.x, point.y) if get_position(ctypes.byref(point)) else None

        return read_windows_cursor

    return lambda: None


def draw_cursor(image, monitor, position):
    if position is None:
        return
    x, y = position
    left, top = monitor["left"], monitor["top"]
    width, height = monitor["width"], monitor["height"]
    if not (left <= x < left + width and top <= y < top + height):
        return

    from PIL import ImageDraw

    x = round((x - left) * image.width / width)
    y = round((y - top) * image.height / height)
    outline = [(0, 0), (0, 14), (4, 11), (7, 18), (10, 17),
               (7, 10), (13, 10)]
    points = [(x + dx, y + dy) for dx, dy in outline]
    ImageDraw.Draw(image).polygon(points, fill="black", outline="white", width=1)


def encode_frame(shot, monitor, position):
    from PIL import Image

    image = Image.frombytes("RGB", shot.size, bytes(shot.rgb))
    image.thumbnail(MAX_FRAME_SIZE, Image.Resampling.LANCZOS)
    draw_cursor(image, monitor, position)
    output = io.BytesIO()
    image.save(output, format="JPEG", quality=70, optimize=False)
    payload = output.getvalue()
    if len(payload) > 2_000_000:
        raise RuntimeError("画面帧超过 2 MB，请降低虚拟屏幕分辨率。")
    return struct.pack(">I", len(payload)) + payload


def stream(peer, capture, monitor):
    print("显示器已连接；可把电脑窗口拖到扩展屏幕。按 Ctrl+C 退出。", flush=True)
    read_cursor = cursor_reader()
    while True:
        started = time.monotonic()
        peer.sendall(encode_frame(capture.grab(monitor), monitor, read_cursor()))
        time.sleep(max(0, FRAME_INTERVAL - (time.monotonic() - started)))


def main():
    parser = argparse.ArgumentParser(description="懒人外设 USB 扩展屏幕桥接")
    parser.add_argument("--list-monitors", action="store_true", help="列出电脑当前识别的显示器")
    parser.add_argument("--monitor", type=int, help="要传到手机的显示器编号")
    args = parser.parse_args()

    from mss import MSS
    with MSS() as capture:
        monitors = capture.monitors
        if args.list_monitors:
            describe_monitors(monitors)
            sys.stdout.flush()
            require_extended_display(monitors)
            return
        if args.monitor is None:
            parser.error("请先用 --list-monitors 查看编号，再指定 --monitor")
        require_extended_display(monitors)
        if not 1 <= args.monitor < len(monitors):
            raise RuntimeError("显示器编号不存在；请重新运行 --list-monitors。")
        monitor = monitors[args.monitor]
        print(f"选择显示器 {args.monitor}：{monitor['width']}×{monitor['height']}。请确认它是扩展屏幕。")

        adb = find_adb()
        serial = authorized_phone(adb)
        subprocess.run([adb, "-s", serial, "forward", f"tcp:{PORT}", f"tcp:{PORT}"], check=True)
        try:
            while True:
                try:
                    with socket.create_connection(("127.0.0.1", PORT), timeout=3) as peer:
                        peer.settimeout(None)
                        stream(peer, capture, monitor)
                except (OSError, ConnectionError) as error:
                    print(f"等待手机打开“显示器”页面：{error}", flush=True)
                    time.sleep(2)
        except KeyboardInterrupt:
            print("已停止。")
        finally:
            subprocess.run([adb, "-s", serial, "forward", "--remove", f"tcp:{PORT}"], check=False)


if __name__ == "__main__":
    try:
        main()
    except (RuntimeError, ValueError, ImportError, subprocess.CalledProcessError) as error:
        print(f"错误：{error}", file=sys.stderr)
        sys.exit(1)
