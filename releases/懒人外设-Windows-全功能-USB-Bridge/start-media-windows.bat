@echo off
setlocal EnableDelayedExpansion
cd /d "%~dp0"
py -3 --version >nul 2>&1
if errorlevel 1 (
    echo Python 3 was not found. Install Python 3 first.
    pause
    exit /b 1
)
if not exist ".venv\Scripts\python.exe" py -3 -m venv .venv
".venv\Scripts\python.exe" -m pip install -r requirements-media.txt
if errorlevel 1 (
    echo Could not install Python media dependencies.
    pause
    exit /b 1
)
echo.
echo Choose mode: 1=Microphone 2=Speaker 3=Camera
set /p "choice=Mode: "
if "%choice%"=="3" (
    ".venv\Scripts\python.exe" media-bridge.py --mode camera
) else (
    ".venv\Scripts\python.exe" media-bridge.py --list-devices
    set /p "device=Virtual audio device number: "
    if "%choice%"=="1" (
        ".venv\Scripts\python.exe" media-bridge.py --mode mic --device "!device!"
    ) else if "%choice%"=="2" (
        ".venv\Scripts\python.exe" media-bridge.py --mode speaker --device "!device!"
    ) else (
        echo Invalid mode.
    )
)
pause
