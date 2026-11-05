#!/bin/bash
cd "$(dirname "$0")" || exit 1
if ! command -v python3 >/dev/null 2>&1; then
    echo "找不到 Python 3。"
    read -r -p "按回车关闭窗口…" _
    exit 1
fi
if [ ! -x .venv/bin/python ]; then python3 -m venv .venv; fi
if ! .venv/bin/python -m pip install -r requirements-media.txt; then
    echo "音视频依赖安装失败。"
    read -r -p "按回车关闭窗口…" _
    exit 1
fi
echo "选择模式：1 麦克风 / 2 音箱 / 3 摄像头"
read -r -p "模式：" choice
if [ "$choice" = 3 ]; then
    .venv/bin/python media-bridge.py --mode camera
elif [ "$choice" = 1 ] || [ "$choice" = 2 ]; then
    .venv/bin/python media-bridge.py --list-devices
    read -r -p "虚拟音频设备编号：" device
    if [ "$choice" = 1 ]; then
        .venv/bin/python media-bridge.py --mode mic --device "$device"
    else
        .venv/bin/python media-bridge.py --mode speaker --device "$device"
    fi
else
    echo "无效模式。"
fi
read -r -p "按回车关闭窗口…" _
