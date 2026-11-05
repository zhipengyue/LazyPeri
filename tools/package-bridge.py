#!/usr/bin/env python3
"""Rebuild the Mac and Windows full USB bridge ZIPs embedded in the APK."""

from pathlib import Path
import shutil
from zipfile import ZIP_DEFLATED, ZipFile, ZipInfo

ROOT = Path(__file__).resolve().parent.parent
TOOLS = ROOT / "tools"
DOCS = ROOT / "docs"
RELEASES = ROOT / "releases"
ASSETS = ROOT / "app" / "src" / "main" / "assets"
COMMON = [
    TOOLS / "usb-bridge.py",
    TOOLS / "media-bridge.py",
    TOOLS / "display-bridge.py",
    TOOLS / "requirements-media.txt",
    TOOLS / "requirements-display.txt",
    DOCS / "音视频兼容性与下载说明.md",
]
PLATFORMS = {
    "macOS": [
        TOOLS / "start-usb-mac.command",
        TOOLS / "start-media-mac.command",
        TOOLS / "start-display-mac.command",
        DOCS / "macOS-USB-授权指南.md",
        DOCS / "macOS-音视频-USB设置指南.md",
        DOCS / "macOS-显示器-USB设置指南.md",
    ],
    "Windows": [
        TOOLS / "start-usb-windows.bat",
        TOOLS / "start-media-windows.bat",
        TOOLS / "start-display-windows.bat",
        DOCS / "Windows-USB-授权指南.md",
        DOCS / "Windows-音视频-USB设置指南.md",
        DOCS / "Windows-显示器-USB设置指南.md",
    ],
}


def write_bridge_archive(archive, files):
    with ZipFile(archive, "w", ZIP_DEFLATED) as output:
        for source in files:
            info = ZipInfo(source.name, (1980, 1, 1, 0, 0, 0))
            info.compress_type = ZIP_DEFLATED
            info.external_attr = (0o755 if source.suffix in (".command", ".py") else 0o644) << 16
            data = source.read_bytes()
            if source.suffix == ".bat":
                data = data.replace(b"\r\n", b"\n").replace(b"\n", b"\r\n")
            output.writestr(info, data)


def main():
    guide_archive = DOCS / "懒人外设-USB授权指南.zip"
    with ZipFile(guide_archive, "w", ZIP_DEFLATED) as output:
        for source in (DOCS / "macOS-USB-授权指南.md", DOCS / "Windows-USB-授权指南.md"):
            info = ZipInfo(source.name, (1980, 1, 1, 0, 0, 0))
            info.compress_type = ZIP_DEFLATED
            info.external_attr = 0o644 << 16
            output.writestr(info, source.read_bytes())

    basic_packages = {
        "macOS": [TOOLS / "start-usb-mac.command", TOOLS / "usb-bridge.py",
                  DOCS / "macOS-USB-授权指南.md", DOCS / "macOS-USB-快速开始.txt"],
        "Windows": [TOOLS / "start-usb-windows.bat", TOOLS / "usb-bridge.py",
                    DOCS / "Windows-USB-授权指南.md", DOCS / "Windows-USB-快速开始.txt"],
    }
    for platform, files in basic_packages.items():
        archive = RELEASES / f"懒人外设-{platform}-USB-Bridge.zip"
        write_bridge_archive(archive, files)

    for platform, unique in PLATFORMS.items():
        files = COMMON + unique
        archive = RELEASES / f"懒人外设-{platform}-全功能-USB-Bridge.zip"
        write_bridge_archive(archive, files)
        shutil.copy2(archive, ASSETS / ("bridge-macos.zip" if platform == "macOS" else "bridge-windows.zip"))
        if platform == "macOS":
            expanded = RELEASES / "懒人外设-macOS-全功能-USB-Bridge"
            for source in files:
                shutil.copy2(source, expanded / source.name)
        print(archive)


if __name__ == "__main__":
    main()
