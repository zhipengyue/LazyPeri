@echo off
setlocal
cd /d "%~dp0"
py -3 --version >nul 2>&1
if errorlevel 1 (
    echo Python 3 was not found. Install Python 3 first.
    pause
    exit /b 1
)
if not exist ".venv\Scripts\python.exe" py -3 -m venv .venv
".venv\Scripts\python.exe" -m pip install -r requirements-display.txt
if errorlevel 1 (
    echo Could not install display dependencies.
    pause
    exit /b 1
)
".venv\Scripts\python.exe" display-bridge.py --list-monitors
if errorlevel 1 (
    pause
    exit /b 1
)
set /p "monitor=Extended display number to show on phone: "
".venv\Scripts\python.exe" display-bridge.py --monitor "%monitor%"
pause
