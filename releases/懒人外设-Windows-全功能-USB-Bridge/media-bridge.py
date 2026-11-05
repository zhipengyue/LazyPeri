#!/usr/bin/env python3
"""USB audio/video bridge for Android and a manually installed virtual device.

Audio PCM is 48 kHz, signed 16-bit little endian. Camera frames are length-prefixed
JPEG at roughly 10 fps. The transport is limited to adb loopback forwarding.
"""

import argparse
import io
import os
import socket
import subprocess
import sys
import time

import importlib.util

PORTS = {"mic": 27184, "speaker": 27185, "camera": 27186}


def load_usb_helpers():
    location = os.path.join(os.path.dirname(__file__), "usb-bridge.py")
    spec = importlib.util.spec_from_file_location("peripheral_usb_bridge", location)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def select_device(sounddevice, selector, direction):
    if selector is None:
        raise RuntimeError("请先用 --list-devices 查看编号，并通过 --device 指定已安装的虚拟音频设备。")
    devices = sounddevice.query_devices()
    if selector.isdigit():
        index = int(selector)
        if index >= len(devices):
            raise RuntimeError("音频设备编号不存在。")
    else:
        matches = [i for i, item in enumerate(devices) if selector.casefold() in item["name"].casefold()]
        if len(matches) != 1:
            raise RuntimeError("音频设备名称匹配结果不是一个；请改用 --list-devices 显示的编号。")
        index = matches[0]
    channels = devices[index]["max_output_channels" if direction == "output" else "max_input_channels"]
    if channels < 2:
        raise RuntimeError("此虚拟音频设备不支持双声道；请选择另一设备。")
    print(f"音频设备：{index} · {devices[index]['name']}", flush=True)
    return index


def read_exact(peer, count):
    chunks = bytearray()
    while len(chunks) < count:
        part = peer.recv(count - len(chunks))
        if not part:
            raise ConnectionError("手机已断开连接")
        chunks.extend(part)
    return bytes(chunks)


def receive_mic(peer, device):
    import numpy as np
    import sounddevice as sd
    index = select_device(sd, device, "output")
    print("麦克风已连接；请在电脑应用里选择虚拟音频线的录音端。", flush=True)
    pending = bytearray()
    with sd.RawOutputStream(samplerate=48000, channels=2, dtype="int16", device=index) as output:
        while True:
            received = peer.recv(8192)
            if not received:
                raise ConnectionError("手机麦克风已断开")
            pending.extend(received)
            whole = len(pending) // 2 * 2
            if whole:
                mono = np.frombuffer(bytes(pending[:whole]), dtype="<i2")
                output.write(np.repeat(mono, 2).astype("<i2").tobytes())
                del pending[:whole]


def send_speaker(peer, device):
    import sounddevice as sd
    index = select_device(sd, device, "input")
    print("音箱已连接；请把电脑声音输出设为虚拟音频线的播放端。", flush=True)
    with sd.RawInputStream(samplerate=48000, channels=2, dtype="int16", device=index,
                           blocksize=960) as source:
        while True:
            data, overflowed = source.read(960)
            if overflowed:
                print("警告：电脑音频采集发生溢出。", flush=True)
            peer.sendall(data)


def receive_camera(peer):
    import numpy as np
    from PIL import Image, ImageOps
    import pyvirtualcam
    with pyvirtualcam.Camera(width=640, height=480, fps=10, fmt=pyvirtualcam.PixelFormat.RGB) as device:
        print(f"摄像头已连接；请在电脑应用中选择 {device.device}。", flush=True)
        while True:
            size = int.from_bytes(read_exact(peer, 4), "big")
            if not 100 <= size <= 2_000_000:
                raise ValueError("摄像头数据长度异常")
            encoded = read_exact(peer, size)
            rotation = encoded[0] & 3
            front = bool(encoded[0] & 4)
            with Image.open(io.BytesIO(encoded[1:])) as image:
                frame = image.convert("RGB").rotate(-90 * rotation, expand=True)
                if front:
                    frame = ImageOps.mirror(frame)
                frame = ImageOps.contain(frame, (640, 480))
                canvas = Image.new("RGB", (640, 480), (10, 15, 25))
                canvas.paste(frame, ((640 - frame.width) // 2, (480 - frame.height) // 2))
                device.send(np.asarray(canvas))


def main():
    parser = argparse.ArgumentParser(description="懒人外设 USB 音视频桥接")
    parser.add_argument("--mode", choices=PORTS, help="mic / speaker / camera")
    parser.add_argument("--device", help="虚拟音频设备编号或唯一名称；摄像头不需要")
    parser.add_argument("--list-devices", action="store_true", help="列出电脑音频设备")
    args = parser.parse_args()
    if args.list_devices:
        import sounddevice as sd
        print(sd.query_devices())
        return
    if args.mode is None:
        parser.error("请指定 --mode")
    if args.mode in ("mic", "speaker") and args.device is None:
        parser.error("麦克风或音箱模式需要 --device；先运行 --list-devices")

    usb = load_usb_helpers()
    adb = usb.find_adb()
    lines = subprocess.check_output([adb, "devices"], text=True).splitlines()
    ready = [line.split()[0] for line in lines[1:] if line.strip().endswith("\tdevice")]
    if len(ready) != 1:
        status = "; ".join(line.strip() for line in lines[1:] if line.strip()) or "未检测到设备"
        raise RuntimeError("需要一台已允许 USB 调试的手机；当前状态：" + status)
    serial = ready[0]
    port = PORTS[args.mode]
    subprocess.run([adb, "-s", serial, "forward", f"tcp:{port}", f"tcp:{port}"], check=True)
    print("已建立 USB 转发，等待手机进入对应页面…", flush=True)
    try:
        while True:
            try:
                with socket.create_connection(("127.0.0.1", port), timeout=3) as peer:
                    peer.settimeout(None)
                    if args.mode == "mic": receive_mic(peer, args.device)
                    elif args.mode == "speaker": send_speaker(peer, args.device)
                    else: receive_camera(peer)
            except (OSError, ConnectionError) as error:
                print(f"等待连接：{error}", flush=True)
                time.sleep(2)
    except KeyboardInterrupt:
        print("已停止。", flush=True)
    finally:
        subprocess.run([adb, "-s", serial, "forward", "--remove", f"tcp:{port}"], check=False)


if __name__ == "__main__":
    try:
        main()
    except (RuntimeError, ValueError, subprocess.CalledProcessError, ImportError) as error:
        print(f"错误：{error}", file=sys.stderr)
        sys.exit(1)
