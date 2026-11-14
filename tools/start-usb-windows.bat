@echo off
setlocal
cd /d "%~dp0"
echo Starting Lazy Peripheral USB Bridge...
py -3 --version >nul 2>&1
if not errorlevel 1 (
    py -3 usb-bridge.py %*
) else (
    python -c "import sys; sys.exit(sys.version_info[0] != 3)" >nul 2>&1
    if errorlevel 1 (
        echo Python 3 was not found. Install Python 3, then run this file again.
    ) else (
        python usb-bridge.py %*
    )
)
echo.
echo If the bridge did not show "Touchpad connected" / "触摸板已连接",
echo check USB debugging authorization on your phone and read the Windows guide.
pause
