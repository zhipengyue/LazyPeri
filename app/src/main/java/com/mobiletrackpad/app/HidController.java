package com.mobiletrackpad.app;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHidDevice;
import android.bluetooth.BluetoothHidDeviceAppSdpSettings;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/** Bluetooth Classic HID mouse + keyboard. All public methods are called on the main thread. */
final class HidController {
    interface Listener { void onStatus(String status, boolean connected); }
    static final int TEXT_NO_CONNECTION=0, TEXT_USB=1, TEXT_BLUETOOTH=2, TEXT_NEEDS_USB=3;

    private static final int MOUSE_ID = 1;
    private static final int KEYBOARD_ID = 2;
    private static final int GAMEPAD_ID = 3;
    private static final int MOD_CTRL = 0x01;
    private static final int MOD_GUI = 0x08;
    private static final int KEY_LEFT = 0x50;
    private static final int KEY_RIGHT = 0x4f;

    // Report 1: mouse; report 2: keyboard; report 3: 16-button, hat and two-stick gamepad.
    private static final byte[] DESCRIPTOR = new byte[] {
        0x05,0x01, 0x09,0x02, (byte)0xa1,0x01, (byte)0x85,0x01,
        0x09,0x01, (byte)0xa1,0x00, 0x05,0x09, 0x19,0x01, 0x29,0x03,
        0x15,0x00, 0x25,0x01, (byte)0x95,0x03, 0x75,0x01, (byte)0x81,0x02,
        (byte)0x95,0x01, 0x75,0x05, (byte)0x81,0x03,
        0x05,0x01, 0x09,0x30, 0x09,0x31, 0x09,0x38,
        0x15,(byte)0x81, 0x25,0x7f, 0x75,0x08, (byte)0x95,0x03, (byte)0x81,0x06,
        (byte)0xc0, (byte)0xc0,
        0x05,0x01, 0x09,0x06, (byte)0xa1,0x01, (byte)0x85,0x02,
        0x05,0x07, 0x19,(byte)0xe0, 0x29,(byte)0xe7, 0x15,0x00, 0x25,0x01,
        0x75,0x01, (byte)0x95,0x08, (byte)0x81,0x02,
        (byte)0x95,0x01, 0x75,0x08, (byte)0x81,0x03,
        (byte)0x95,0x06, 0x75,0x08, 0x15,0x00, 0x25,0x65,
        0x05,0x07, 0x19,0x00, 0x29,0x65, (byte)0x81,0x00,
        (byte)0xc0,
        0x05,0x01, 0x09,0x05, (byte)0xa1,0x01, (byte)0x85,0x03,
        0x05,0x09, 0x19,0x01, 0x29,0x10, 0x15,0x00, 0x25,0x01,
        0x75,0x01, (byte)0x95,0x10, (byte)0x81,0x02,
        0x05,0x01, 0x09,0x39, 0x15,0x00, 0x25,0x07,
        0x75,0x04, (byte)0x95,0x01, (byte)0x81,0x42,
        0x75,0x04, (byte)0x95,0x01, (byte)0x81,0x03,
        0x09,0x30, 0x09,0x31, 0x09,0x33, 0x09,0x34,
        0x15,(byte)0x81, 0x25,0x7f, 0x75,0x08, (byte)0x95,0x04, (byte)0x81,0x02,
        (byte)0xc0
    };

    private final Context context;
    private final Listener listener;
    private final BluetoothAdapter adapter;
    private final UsbBridge usb;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ArrayDeque<int[]> textKeys = new ArrayDeque<>();
    private BluetoothHidDevice hid;
    private BluetoothDevice device;
    private BluetoothDevice requestedDevice;
    private boolean registered;
    private boolean proxyRequested;
    private boolean connecting;
    private boolean stopping;
    private int buttons;
    private int leftHolds;
    private boolean leftClickPending;
    private boolean typingText;
    private final byte[] gamepad = new byte[] {0,0,8,0,0,0,0};

    HidController(Context context, Listener listener) {
        this.context = context;
        this.listener = listener;
        BluetoothManager manager = context.getSystemService(BluetoothManager.class);
        adapter = manager == null ? null : manager.getAdapter();
        usb = new UsbBridge(() -> context.getMainExecutor().execute(() -> {
            if (device != null && buttons != 0) {
                buttons = 0;
                mouseBluetooth(0, 0, 0);
            }
        }));
    }

    boolean isBluetoothReady() { return adapter != null && adapter.isEnabled(); }
    boolean isConnected() { return hid != null && device != null; }
    String connectionSummary() {
        if (usb.isConnected()) return "USB 已连接";
        if (device != null) return "蓝牙已连接 · " + name(device);
        return "尚未连接电脑";
    }
    boolean hasPermission() {
        return Build.VERSION.SDK_INT < 31 || context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
    }

    void start() {
        if (!hasPermission()) { listener.onStatus("需要附近设备权限", false); return; }
        if (!isBluetoothReady()) { listener.onStatus("请开启蓝牙", false); return; }
        if (hid == null && !proxyRequested) {
            proxyRequested = adapter.getProfileProxy(context, serviceListener, BluetoothProfile.HID_DEVICE);
            if (!proxyRequested) listener.onStatus("此手机不支持蓝牙 HID", false);
        }
    }

    List<BluetoothDevice> bondedDevices() {
        List<BluetoothDevice> devices = new ArrayList<>();
        if (adapter != null && hasPermission()) devices.addAll(adapter.getBondedDevices());
        devices.sort(Comparator.comparing(d -> d.getName() == null ? "" : d.getName(), String.CASE_INSENSITIVE_ORDER));
        return devices;
    }

    void connect(BluetoothDevice target) {
        requestedDevice = target;
        if (!registered || hid == null) {
            listener.onStatus("正在准备连接…", false);
            start();
            return;
        }
        if (connecting) return;
        connecting = true;
        listener.onStatus("正在连接 " + name(target) + "…", false);
        if (!hid.connect(target)) {
            connecting = false;
            listener.onStatus("连接失败，请检查电脑蓝牙配对", false);
        } else {
            mainHandler.postDelayed(() -> {
                if (connecting && device == null) {
                    connecting = false;
                    autoConnect();
                }
            }, 6000);
        }
    }

    void autoConnect() {
        if (!registered || device != null || connecting || !hasPermission()) return;
        String address = context.getSharedPreferences("trackpad", Context.MODE_PRIVATE).getString("last_host", null);
        List<BluetoothDevice> candidates = bondedDevices();
        if (address != null) for (BluetoothDevice d : candidates)
            if (address.equals(d.getAddress())) { connect(d); return; }
        for (BluetoothDevice d : candidates) {
            BluetoothClass type = d.getBluetoothClass();
            if (type != null && type.getMajorDeviceClass() == BluetoothClass.Device.Major.COMPUTER) {
                connect(d); return;
            }
        }
    }

    void stop() {
        stopping = true;
        textKeys.clear();
        usb.stop();
        if (hid != null) {
            if (registered) hid.unregisterApp();
            if (adapter != null) adapter.closeProfileProxy(BluetoothProfile.HID_DEVICE, hid);
        }
        hid = null;
        device = null;
        registered = false;
        proxyRequested = false;
    }

    private final BluetoothProfile.ServiceListener serviceListener = new BluetoothProfile.ServiceListener() {
        @Override public void onServiceConnected(int profile, BluetoothProfile proxy) {
            hid = (BluetoothHidDevice) proxy;
            registerHid();
        }
        @Override public void onServiceDisconnected(int profile) {
            hid = null; device = null; registered = false; proxyRequested = false; connecting = false;
            listener.onStatus("蓝牙服务已断开", false);
            if (!stopping) mainHandler.postDelayed(() -> { if (!stopping) start(); }, 1500);
        }
    };

    private void registerHid() {
        if (stopping || hid == null || registered || !hasPermission() || !isBluetoothReady()) return;
        BluetoothHidDeviceAppSdpSettings sdp = new BluetoothHidDeviceAppSdpSettings(
            "懒人外设", "手机触摸板与键盘", "懒人外设",
            (byte)(BluetoothHidDevice.SUBCLASS1_COMBO | BluetoothHidDevice.SUBCLASS2_GAMEPAD), DESCRIPTOR);
        if (!hid.registerApp(sdp, null, null, context.getMainExecutor(), callback))
            listener.onStatus("HID 注册失败，设备可能不支持", false);
    }

    private final BluetoothHidDevice.Callback callback = new BluetoothHidDevice.Callback() {
        @Override public void onAppStatusChanged(BluetoothDevice pluggedDevice, boolean isRegistered) {
            registered = isRegistered;
            if (!registered) {
                device = null; connecting = false;
                listener.onStatus("HID 已停止，正在重试", false);
                if (!stopping) mainHandler.postDelayed(this::retryRegistration, 3000);
            } else if (requestedDevice != null) {
                BluetoothDevice target = requestedDevice;
                requestedDevice = null;
                connect(target);
            } else if (pluggedDevice != null) {
                device = pluggedDevice;
                listener.onStatus("已连接 · " + name(pluggedDevice), true);
            } else {
                listener.onStatus("正在寻找已配对电脑", false);
                autoConnect();
            }
        }
        private void retryRegistration() { if (!stopping && !registered) registerHid(); }
        @Override public void onGetReport(BluetoothDevice target, byte type, byte id, int bufferSize) {
            if (hid == null) return;
            if (type != BluetoothHidDevice.REPORT_TYPE_INPUT || (id != MOUSE_ID && id != KEYBOARD_ID && id != GAMEPAD_ID)) {
                hid.reportError(target, BluetoothHidDevice.ERROR_RSP_INVALID_RPT_ID);
                return;
            }
            byte[] report = new byte[Math.max(id == MOUSE_ID ? 4 : id == GAMEPAD_ID ? 7 : 8, Math.min(bufferSize, 64))];
            if (id == MOUSE_ID) report[0] = (byte) buttons;
            if (id == GAMEPAD_ID) System.arraycopy(gamepad, 0, report, 0, gamepad.length);
            hid.replyReport(target, type, id, report);
        }
        @Override public void onConnectionStateChanged(BluetoothDevice target, int state) {
            if (state == BluetoothProfile.STATE_CONNECTED) {
                device = target;
                connecting = false;
                if (hid != null) hid.sendReport(target, GAMEPAD_ID, gamepad);
                context.getSharedPreferences("trackpad", Context.MODE_PRIVATE).edit().putString("last_host", target.getAddress()).apply();
                listener.onStatus("已连接 · " + name(target), true);
            } else if (state == BluetoothProfile.STATE_DISCONNECTED && (device == null || device.equals(target))) {
                device = null; buttons = 0; leftHolds = 0; connecting = false;
                listener.onStatus("未连接 · 点击选择电脑", false);
                if (!stopping) mainHandler.postDelayed(() -> { if (!stopping) autoConnect(); }, 1200);
            }
        }
    };

    private String name(BluetoothDevice d) { String n = d.getName(); return n == null ? d.getAddress() : n; }
    private static int clamp(int x) { return Math.max(-127, Math.min(127, x)); }

    void move(int dx, int dy) {
        if (usb.send("MOVE " + dx + " " + dy)) return;
        // Relative HID reports are signed 8-bit. Split large deltas rather than dropping movement.
        while (dx != 0 || dy != 0) {
            int x = clamp(dx), y = clamp(dy);
            mouse(x, y, 0);
            dx -= x; dy -= y;
        }
    }
    void scroll(int ticks) {
        if (usb.send("SCROLL " + ticks)) return;
        while (ticks != 0) {
            int step = clamp(ticks);
            mouse(0, 0, step);
            ticks -= step;
        }
    }
    void leftDown() {
        if (++leftHolds != 1) return;
        buttons |= 1;
        if (!usb.send("BUTTON 1")) mouse(0,0,0);
    }
    void leftUp() {
        if (leftHolds == 0 || --leftHolds != 0) return;
        buttons &= ~1;
        if (!usb.send("BUTTON 0")) mouse(0,0,0);
    }
    void click(boolean right) {
        if (!right && leftHolds > 0) return;
        if (!right && leftClickPending) {
            mainHandler.postDelayed(() -> click(false), 55);
            return;
        }
        if (usb.send("CLICK " + (right ? 2 : 1))) return;
        int mask = right ? 2 : 1;
        if (!right) leftClickPending = true;
        buttons |= mask; mouse(0,0,0);
        mainHandler.postDelayed(() -> {
            if (right || leftHolds == 0) buttons &= ~mask;
            mouse(0,0,0);
            if (!right) leftClickPending = false;
        }, 45);
    }
    void sendKey(int modifiers, int usage) {
        if (usb.send("KEY " + modifiers + " " + usage)) return;
        if (!isConnected()) return;
        textKeys.add(new int[]{modifiers, usage});
        if (!typingText) typeNextKey();
    }
    void switchDesktop(boolean windows, boolean right) {
        if (usb.send("SWITCH " + (windows ? "W" : "M") + " " + (right ? "R" : "L"))) return;
        int modifiers = MOD_CTRL | (windows ? MOD_GUI : 0);
        keyboard(modifiers, right ? KEY_RIGHT : KEY_LEFT);
        mainHandler.postDelayed(() -> keyboard(0, 0), 70);
    }
    void gamepadState(int buttonMask, int hat, int leftX, int leftY, int rightX, int rightY) {
        byte[] next = new byte[] {
            (byte)buttonMask, (byte)(buttonMask >>> 8), (byte)hat,
            (byte)leftX, (byte)leftY, (byte)rightX, (byte)rightY
        };
        if (java.util.Arrays.equals(gamepad, next)) return;
        System.arraycopy(next, 0, gamepad, 0, next.length);
        if (usb.send("PAD " + buttonMask + " " + hat + " " + leftX + " " + leftY + " " + rightX + " " + rightY)) return;
        if (isConnected()) hid.sendReport(device, GAMEPAD_ID, next);
    }
    void releaseGamepad() { gamepadState(0,8,0,0,0,0); }
    void sendText(String value, Consumer<Integer> callback) {
        if (usb.isConnected()) {
            String encoded=Base64.encodeToString(value.getBytes(StandardCharsets.UTF_8),Base64.NO_WRAP);
            usb.sendText(encoded, accepted -> callback.accept(accepted ? TEXT_USB : TEXT_NO_CONNECTION));
            return;
        }
        if (!isConnected()) { callback.accept(TEXT_NO_CONNECTION); return; }
        ArrayDeque<int[]> next=new ArrayDeque<>();
        for (int i=0;i<value.length();i++) {
            int[] key=asciiKey(value.charAt(i));
            if (key==null) { callback.accept(TEXT_NEEDS_USB); return; }
            next.add(key);
        }
        textKeys.addAll(next);
        if (!typingText) typeNextKey();
        callback.accept(TEXT_BLUETOOTH);
    }
    private void typeNextKey() {
        int[] key=textKeys.poll();
        if (key==null || !isConnected()) {typingText=false; textKeys.clear(); return;}
        typingText=true;
        keyboard(key[0],key[1]);
        mainHandler.postDelayed(() -> {
            keyboard(0,0);
            mainHandler.postDelayed(this::typeNextKey,18);
        },35);
    }
    private int[] asciiKey(char c) {
        if (c>='a' && c<='z') return new int[]{0,c-'a'+4};
        if (c>='A' && c<='Z') return new int[]{2,c-'A'+4};
        if (c>='1' && c<='9') return new int[]{0,c-'1'+0x1e};
        if (c=='0') return new int[]{0,0x27};
        if (c==' ' || c=='\n' || c=='\t') return new int[]{0,c==' ' ? 0x2c : c=='\n' ? 0x28 : 0x2b};
        String plain="-=[]\\;'`,./";
        int[] codes={0x2d,0x2e,0x2f,0x30,0x31,0x33,0x34,0x35,0x36,0x37,0x38};
        int at=plain.indexOf(c);
        if (at>=0) return new int[]{0,codes[at]};
        String shifted="!@#$%^&*()_+{}|:\"~<>?";
        at=shifted.indexOf(c);
        if (at>=0) {
            int code=at<9 ? 0x1e+at : at==9 ? 0x27 : codes[at-10];
            return new int[]{2,code};
        }
        return null;
    }
    private void mouse(int x, int y, int wheel) {
        mouseBluetooth(x, y, wheel);
    }
    private void mouseBluetooth(int x, int y, int wheel) {
        if (isConnected()) hid.sendReport(device, MOUSE_ID, new byte[] {(byte)buttons,(byte)x,(byte)y,(byte)wheel});
    }
    private void keyboard(int modifiers, int key) {
        if (isConnected()) hid.sendReport(device, KEYBOARD_ID, new byte[] {(byte)modifiers,0,(byte)key,0,0,0,0,0});
    }
}
