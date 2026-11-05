#!/bin/bash
cd "$(dirname "$0")" || exit 1
if ! command -v python3 >/dev/null 2>&1; then
    echo "找不到 Python 3。"
    read -r -p "按回车关闭窗口…" _
    exit 1
fi
if [ ! -x .venv/bin/python ]; then python3 -m venv .venv; fi
if ! .venv/bin/python -m pip install -r requirements-display.txt; then
    echo "显示器依赖安装失败。"
    read -r -p "按回车关闭窗口…" _
    exit 1
fi
if ! .venv/bin/python display-bridge.py --list-monitors; then
    read -r -p "按回车关闭窗口…" _
    exit 1
fi
read -r -p "要显示在手机上的扩展屏幕编号：" monitor
.venv/bin/python display-bridge.py --monitor "$monitor"
read -r -p "按回车关闭窗口…" _
