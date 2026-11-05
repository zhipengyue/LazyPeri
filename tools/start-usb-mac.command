#!/bin/bash
cd "$(dirname "$0")"
python3 usb-bridge.py
bridge_exit_code=$?
if [ "$bridge_exit_code" -ne 0 ]; then
    read -r -p "按回车关闭窗口…" _
fi
exit "$bridge_exit_code"
