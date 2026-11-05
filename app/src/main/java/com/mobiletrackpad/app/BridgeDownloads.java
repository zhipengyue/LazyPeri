package com.mobiletrackpad.app;

import android.net.Uri;

/** Public download locations for the bridge packages checked into GitHub. */
final class BridgeDownloads {
    static final String REPOSITORY = "https://github.com/zhipengyue/LazyPeri";
    private static final String RAW_RELEASES =
        "https://raw.githubusercontent.com/zhipengyue/LazyPeri/main/releases/";
    static final String MAC_ZIP = RAW_RELEASES
        + Uri.encode("懒人外设-macOS-全功能-USB-Bridge.zip");
    static final String WINDOWS_ZIP = RAW_RELEASES
        + Uri.encode("懒人外设-Windows-全功能-USB-Bridge.zip");

    private BridgeDownloads() { }
}
