package com.mobiletrackpad.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.bluetooth.BluetoothDevice;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.WindowInsets;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

public final class MainActivity extends Activity {
    private static final String FEEDBACK_EMAIL = "eleven.zhi@outlook.com";
    private static final String FEEDBACK_WECHAT = "kaka20491010";
    private static final int REQUEST_BLUETOOTH = 100;
    private static final int REQUEST_SAVE_MAC_BRIDGE = 301, REQUEST_SAVE_WINDOWS_BRIDGE = 302;
    private static final int MODE_HOME = -1, MODE_TOUCHPAD = 0, MODE_GAMEPAD = 1, MODE_KEYBOARD = 2;
    private static final int MODE_MICROPHONE = MediaFeatureView.MICROPHONE;
    private static final int MODE_SPEAKER = MediaFeatureView.SPEAKER;
    private static final int MODE_CAMERA = MediaFeatureView.CAMERA, MODE_CAMERA_GUIDE = 6, MODE_SPEAKER_GUIDE = 7;
    private static final int MODE_TOUCHPAD_GUIDE = 8, MODE_KEYBOARD_GUIDE = 9;
    private static final int MODE_GAMEPAD_GUIDE = 10, MODE_MICROPHONE_GUIDE = 11;
    private static final int MODE_DISPLAY = 12, MODE_DISPLAY_GUIDE = 13;
    private HidController hid;
    private TrackpadView trackpad;
    private GamepadView gamepad;
    private VirtualKeyboardView keyboard;
    private TextInputView textInput;
    private MediaFeatureView microphoneView, speakerView, cameraView;
    private CameraGuideView cameraGuideView;
    private SpeakerGuideView speakerGuideView;
    private FeatureGuideView touchpadGuideView, keyboardGuideView, gamepadGuideView, microphoneGuideView;
    private DisplayView displayView;
    private DisplayGuideView displayGuideView;
    private MediaBridge mediaBridge;
    private EditText portraitEditor;
    private LinearLayout home, functionPage;
    private FrameLayout rootView, displayPage;
    private LinearLayout headerBar, trackpadContainer, mouseButtons, portraitInputRow;
    private View orientationButton, cameraRotateButton, cameraSwitchButton;
    private TextView settingsButton, textButton, helpButton, portraitKeyboardButton, leftMouseButton, portraitInputStatus;
    private TextView displayAspectButton;
    private boolean windows, portraitTouchpad, textMode, portraitImeVisible, leftButtonHeld, bound;
    private boolean longPressRightEnabled;
    private float touchpadSensitivity;
    private int selectedMode = MODE_HOME;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            hid = ((TrackpadService.LocalBinder) binder).service().controller();
            buildUi();
            if (hid.hasPermission()) hid.autoConnect();
            else if (Build.VERSION.SDK_INT >= 31)
                requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, REQUEST_BLUETOOTH);
        }
        @Override public void onServiceDisconnected(ComponentName name) { hid = null; }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(16,20,26));
        getWindow().setNavigationBarColor(Color.rgb(16,20,26));
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(0);
        if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
        windows = getPreferences(MODE_PRIVATE).getBoolean("windows", true);
        portraitTouchpad = getPreferences(MODE_PRIVATE).getBoolean("portrait_touchpad_v2", true);
        longPressRightEnabled = getPreferences(MODE_PRIVATE).getBoolean("long_press_right", true);
        touchpadSensitivity = getPreferences(MODE_PRIVATE).getFloat("touchpad_sensitivity", 1.25f);
        applyOrientation();
        Intent service = new Intent(this, TrackpadService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(service);
        else startService(service);
        bound = bindService(service, connection, Context.BIND_AUTO_CREATE);
    }

    private int dp(float x) { return (int)(x*getResources().getDisplayMetrics().density+0.5f); }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        rootView = root;
        root.setBackgroundColor(Color.rgb(16,20,26));
        if (Build.VERSION.SDK_INT >= 30) {
            root.setOnApplyWindowInsetsListener((view,insets) -> {
                Insets safe=insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                Insets ime=insets.getInsets(WindowInsets.Type.ime());
                if (selectedMode == MODE_DISPLAY) view.setPadding(0,0,0,0);
                else view.setPadding(safe.left,safe.top,safe.right,Math.max(safe.bottom,ime.bottom));
                return insets;
            });
        }
        setContentView(root);
        if (Build.VERSION.SDK_INT >= 30) root.requestApplyInsets();

        ScrollView homeScroll = new ScrollView(this);
        homeScroll.setFillViewport(true);
        homeScroll.setVerticalScrollBarEnabled(false);
        root.addView(homeScroll,new FrameLayout.LayoutParams(-1,-1));
        home = new LinearLayout(this);
        home.setOrientation(LinearLayout.VERTICAL);
        home.setGravity(Gravity.CENTER_HORIZONTAL);
        home.setPadding(dp(20),dp(26),dp(20),dp(20));
        homeScroll.addView(home,new ScrollView.LayoutParams(-1,-2));
        TextView brand = new TextView(this);
        brand.setText("懒人外设");
        brand.setTextSize(31);
        brand.setTypeface(null,Typeface.BOLD);
        brand.setTextColor(Color.WHITE);
        home.addView(brand,new LinearLayout.LayoutParams(-1,-2));
        TextView tagline = new TextView(this);
        tagline.setText("把手机变成你的桌面装备");
        tagline.setTextColor(Color.rgb(148,170,204));
        tagline.setTextSize(14);
        LinearLayout.LayoutParams taglineParams = new LinearLayout.LayoutParams(-1,-2);
        taglineParams.bottomMargin = dp(18);
        home.addView(tagline,taglineParams);
        homeButton("⌁", "触摸板", "手势操控与拖拽", MODE_TOUCHPAD);
        homeButton("⌨", "键盘", "快捷输入与组合键", MODE_KEYBOARD);
        homeButton("✦", "游戏手柄", "双摇杆与按键", MODE_GAMEPAD);
        homeButton("◉", "麦克风", "实时声音反馈", MODE_MICROPHONE);
        homeButton("♫", "音箱", "双音箱展示", MODE_SPEAKER);
        homeButton("▣", "摄像头", "前后镜头预览", MODE_CAMERA);
        homeButton("▤", "显示器", "手机作为电脑扩展屏幕", MODE_DISPLAY);
        TextView feedbackEntry = new TextView(this);
        feedbackEntry.setText("意见反馈");
        feedbackEntry.setTextSize(15);
        feedbackEntry.setTextColor(Color.rgb(158,177,201));
        feedbackEntry.setGravity(Gravity.CENTER);
        feedbackEntry.setContentDescription("打开意见反馈");
        LinearLayout.LayoutParams feedbackParams = new LinearLayout.LayoutParams(-1,dp(52));
        feedbackParams.topMargin = dp(18);
        home.addView(feedbackEntry,feedbackParams);
        feedbackEntry.setOnClickListener(v -> showFeedback());

        functionPage = new LinearLayout(this);
        functionPage.setOrientation(LinearLayout.VERTICAL);
        functionPage.setMotionEventSplittingEnabled(true);
        root.addView(functionPage,new FrameLayout.LayoutParams(-1,-1));
        headerBar = new LinearLayout(this);
        headerBar.setGravity(Gravity.CENTER_VERTICAL);
        headerBar.setMotionEventSplittingEnabled(true);
        headerBar.setPadding(dp(8),dp(5),dp(8),dp(5));
        functionPage.addView(headerBar,new LinearLayout.LayoutParams(-1,dp(54)));
        TextView back = headerButton("←", "返回主界面", headerBar, dp(48));
        back.setTextSize(25);
        back.setOnClickListener(v -> navigateBack());
        View spacer = new View(this);
        headerBar.addView(spacer,new LinearLayout.LayoutParams(0,1,1));
        ImageView rotateIcon = new ImageView(this);
        // build-local.sh compiles Java before aapt2 generates R; resolve this bundled vector by name.
        rotateIcon.setImageResource(getResources().getIdentifier("ic_refresh_cw", "drawable", getPackageName()));
        rotateIcon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        rotateIcon.setPadding(dp(8),dp(8),dp(8),dp(8));
        orientationButton = rotateIcon;
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(42),dp(42));
        iconParams.setMargins(dp(4),0,0,0);
        headerBar.addView(orientationButton,iconParams);
        orientationButton.setBackground(buttonBackground(false));
        orientationButton.setOnClickListener(v -> {
            portraitTouchpad = !portraitTouchpad;
            getPreferences(MODE_PRIVATE).edit().putBoolean("portrait_touchpad_v2",portraitTouchpad).apply();
            updateOrientationButton();
            applyOrientation();
        });
        settingsButton = headerButton("⚙", "触摸板设置", headerBar, dp(42));
        settingsButton.setOnClickListener(v -> showSettings());
        portraitKeyboardButton = headerButton("⌨", "打开手机键盘", headerBar, dp(42));
        portraitKeyboardButton.setOnClickListener(v -> {
            if (portraitImeVisible) hidePortraitIme(); else showPortraitIme();
        });
        textButton = headerButton("文字", "文字输入", headerBar, dp(56));
        textButton.setTextSize(14);
        textButton.setOnClickListener(v -> setTextMode(true));
        cameraRotateButton = cameraHeaderButton("ic_camera_rotate", "画面旋转 90°");
        cameraRotateButton.setOnClickListener(v -> cameraView.rotateCameraOutput());
        cameraSwitchButton = cameraHeaderButton("ic_camera_switch", "切换前后镜头");
        cameraSwitchButton.setOnClickListener(v -> cameraView.switchCamera());
        helpButton = headerButton("指南", "打开使用指南", headerBar, dp(48));
        helpButton.setTextSize(13);
        helpButton.setOnClickListener(v -> showGuide());

        FrameLayout surface = new FrameLayout(this);
        functionPage.addView(surface,new LinearLayout.LayoutParams(-1,0,1));
        trackpad = new TrackpadView(this,hid);
        trackpad.setWindows(windows);
        trackpad.setLongPressRightEnabled(longPressRightEnabled);
        trackpad.setSensitivity(touchpadSensitivity);
        trackpadContainer = new LinearLayout(this);
        trackpadContainer.setOrientation(LinearLayout.VERTICAL);
        trackpadContainer.setMotionEventSplittingEnabled(true);
        buildPortraitInputRow();
        mouseButtons = new LinearLayout(this);
        mouseButtons.setPadding(dp(2),0,dp(2),dp(5));
        trackpadContainer.addView(mouseButtons,new LinearLayout.LayoutParams(-1,dp(55)));
        leftMouseButton = mouseButton("左键",mouseButtons);
        leftMouseButton.setOnTouchListener((view,event) -> {
            int action=event.getActionMasked();
            if (action==MotionEvent.ACTION_DOWN) {
                leftButtonHeld=true;
                trackpad.setExternalLeftHeld(true);
                hid.leftDown();
                leftMouseButton.setBackground(buttonBackground(true));
                return true;
            }
            if (action==MotionEvent.ACTION_UP || action==MotionEvent.ACTION_CANCEL) {
                releaseLeftButton();
                if (action==MotionEvent.ACTION_UP) view.performClick();
                return true;
            }
            return true;
        });
        TextView rightMouseButton = mouseButton("右键",mouseButtons);
        rightMouseButton.setOnClickListener(v -> hid.click(true));
        trackpadContainer.addView(trackpad,new LinearLayout.LayoutParams(-1,0,1));
        addSurface(surface,trackpadContainer);
        gamepad = new GamepadView(this,hid);
        addSurface(surface,gamepad);
        keyboard = new VirtualKeyboardView(this,hid);
        addSurface(surface,keyboard);
        textInput = new TextInputView(this,hid);
        addSurface(surface,textInput);
        mediaBridge = new MediaBridge();
        microphoneView = new MediaFeatureView(this,MODE_MICROPHONE,mediaBridge);
        addSurface(surface,microphoneView);
        speakerView = new MediaFeatureView(this,MODE_SPEAKER,mediaBridge);
        addSurface(surface,speakerView);
        cameraView = new MediaFeatureView(this,MODE_CAMERA,mediaBridge);
        addSurface(surface,cameraView);
        cameraGuideView = new CameraGuideView(this,() -> setMode(MODE_CAMERA),
            () -> requestSaveBridgePackage(true), () -> requestSaveBridgePackage(false));
        addSurface(surface,cameraGuideView);
        speakerGuideView = new SpeakerGuideView(this,() -> setMode(MODE_SPEAKER),
            () -> requestSaveBridgePackage(true), () -> requestSaveBridgePackage(false));
        addSurface(surface,speakerGuideView);
        touchpadGuideView = createFeatureGuide(surface, FeatureGuideView.TOUCHPAD);
        keyboardGuideView = createFeatureGuide(surface, FeatureGuideView.KEYBOARD);
        gamepadGuideView = createFeatureGuide(surface, FeatureGuideView.GAMEPAD);
        microphoneGuideView = createFeatureGuide(surface, FeatureGuideView.MICROPHONE);
        displayGuideView = new DisplayGuideView(this, () -> setMode(MODE_DISPLAY),
            () -> requestSaveBridgePackage(true), () -> requestSaveBridgePackage(false));
        addSurface(surface, displayGuideView);

        displayPage = new FrameLayout(this);
        root.addView(displayPage, new FrameLayout.LayoutParams(-1, -1));
        displayView = new DisplayView(this);
        displayPage.addView(displayView, new FrameLayout.LayoutParams(-1, -1));
        floatingDisplayButton("←", "返回主界面", Gravity.TOP | Gravity.LEFT, () -> navigateBack());
        floatingDisplayButton("指南", "打开显示器指南", Gravity.TOP | Gravity.RIGHT,
            () -> setMode(MODE_DISPLAY_GUIDE));
        displayAspectButton = floatingDisplayButton("等比", "按原比例显示", Gravity.BOTTOM | Gravity.RIGHT,
            () -> {
                boolean fill = displayView.toggleAspectMode();
                displayAspectButton.setText(fill ? "等比" : "铺满");
                displayAspectButton.setContentDescription(fill ? "按原比例显示" : "铺满全屏显示");
            });
        setMode(MODE_HOME);
    }

    private TextView floatingDisplayButton(String label, String description, int gravity, Runnable action) {
        TextView button = new TextView(this);
        button.setText(label);
        button.setTextSize(label.equals("←") ? 24 : 13);
        button.setTextColor(Color.WHITE);
        button.setGravity(Gravity.CENTER);
        button.setContentDescription(description);
        button.setBackground(buttonBackground(false));
        button.setAlpha(0.82f);
        button.setElevation(dp(4));
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(dp(label.equals("←") ? 46 : 52), dp(42), gravity);
        params.setMargins(dp(14), dp(12), dp(14), dp(12));
        displayPage.addView(button, params);
        button.setOnClickListener(v -> action.run());
        return button;
    }

    private FeatureGuideView createFeatureGuide(FrameLayout surface, int feature) {
        int mode = feature == FeatureGuideView.TOUCHPAD ? MODE_TOUCHPAD
            : feature == FeatureGuideView.KEYBOARD ? MODE_KEYBOARD
            : feature == FeatureGuideView.GAMEPAD ? MODE_GAMEPAD : MODE_MICROPHONE;
        FeatureGuideView guide = new FeatureGuideView(this, feature, () -> hid.connectionSummary(),
            () -> setMode(mode), () -> showConnections(), () -> requestSaveBridgePackage(true),
            () -> requestSaveBridgePackage(false));
        addSurface(surface, guide);
        return guide;
    }

    private void homeButton(String symbol,String label,String detail,int mode) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(18),0,dp(18),0);
        card.setBackground(UiKit.card(this,Color.rgb(39,58,88),Color.rgb(27,37,56)));
        card.setElevation(dp(4));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1,dp(78));
        params.setMargins(0,0,0,dp(10));
        home.addView(card,params);
        TextView icon = new TextView(this);
        icon.setText(symbol);
        icon.setTextSize(27);
        icon.setTextColor(Color.rgb(109,180,255));
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(UiKit.pill(this,Color.rgb(31,67,112)));
        card.addView(icon,new LinearLayout.LayoutParams(dp(48),dp(48)));
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams labelsParams = new LinearLayout.LayoutParams(0,-2,1);
        labelsParams.leftMargin = dp(16);
        card.addView(labels,labelsParams);
        TextView title = new TextView(this);
        title.setText(label);
        title.setTextSize(18);
        title.setTypeface(null,Typeface.BOLD);
        title.setTextColor(Color.WHITE);
        labels.addView(title);
        TextView subtitle = new TextView(this);
        subtitle.setText(detail);
        subtitle.setTextSize(12);
        subtitle.setTextColor(Color.rgb(163,184,213));
        labels.addView(subtitle);
        TextView arrow = new TextView(this);
        arrow.setText("›");
        arrow.setTextSize(28);
        arrow.setTextColor(Color.rgb(124,148,181));
        card.addView(arrow);
        card.setContentDescription(label + "，" + detail);
        card.setOnClickListener(v -> setMode(mode));
    }

    private TextView headerButton(String label,String description,LinearLayout header,int width) {
        TextView button = new TextView(this);
        button.setText(label);
        button.setTextSize(20);
        button.setTextColor(Color.WHITE);
        button.setGravity(Gravity.CENTER);
        button.setContentDescription(description);
        button.setBackground(buttonBackground(false));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width,dp(42));
        params.setMargins(dp(4),0,0,0);
        header.addView(button,params);
        return button;
    }

    private TextView mouseButton(String label,LinearLayout row) {
        TextView button=new TextView(this);
        button.setText(label);
        button.setTextSize(15);
        button.setTypeface(null,Typeface.BOLD);
        button.setTextColor(Color.WHITE);
        button.setGravity(Gravity.CENTER);
        button.setBackground(buttonBackground(false));
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,dp(48),1);
        params.setMargins(dp(4),0,dp(4),0);
        row.addView(button,params);
        return button;
    }

    private void buildPortraitInputRow() {
        portraitInputRow=new LinearLayout(this);
        portraitInputRow.setOrientation(LinearLayout.VERTICAL);
        portraitInputRow.setPadding(dp(4),0,dp(4),dp(4));
        trackpadContainer.addView(portraitInputRow,new LinearLayout.LayoutParams(-1,dp(86)));
        LinearLayout inputLine=new LinearLayout(this);
        inputLine.setGravity(Gravity.CENTER_VERTICAL);
        portraitInputRow.addView(inputLine,new LinearLayout.LayoutParams(-1,dp(55)));
        portraitEditor=new EditText(this);
        portraitEditor.setSingleLine(true);
        portraitEditor.setTextSize(15);
        portraitEditor.setTextColor(Color.WHITE);
        portraitEditor.setHintTextColor(Color.rgb(154,165,180));
        portraitEditor.setHint("输入文字或使用语音键");
        portraitEditor.setPadding(dp(10),0,dp(10),0);
        portraitEditor.setBackground(buttonBackground(false));
        portraitEditor.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEND);
        inputLine.addView(portraitEditor,new LinearLayout.LayoutParams(0,dp(48),1));
        TextView send=new TextView(this);
        send.setText("发送");
        send.setTextColor(Color.WHITE);
        send.setTextSize(15);
        send.setTypeface(null,Typeface.BOLD);
        send.setGravity(Gravity.CENTER);
        send.setBackground(buttonBackground(true));
        LinearLayout.LayoutParams sendParams=new LinearLayout.LayoutParams(dp(68),dp(48));
        sendParams.leftMargin=dp(6);
        inputLine.addView(send,sendParams);
        send.setOnClickListener(v -> sendPortraitText());
        portraitEditor.setOnEditorActionListener((v,action,event) -> {
            if (action==android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                sendPortraitText();
                return true;
            }
            return false;
        });
        portraitInputStatus=new TextView(this);
        portraitInputStatus.setText("输入后点击发送；中文需要 USB Bridge");
        portraitInputStatus.setTextSize(11);
        portraitInputStatus.setTextColor(Color.rgb(158,177,201));
        portraitInputRow.addView(portraitInputStatus,new LinearLayout.LayoutParams(-1,dp(26)));
        portraitInputRow.setVisibility(View.GONE);
    }

    private void sendPortraitText() {
        String value=portraitEditor.getText().toString();
        if (value.isEmpty()) { portraitInputStatus.setText("请先输入文字"); return; }
        portraitInputStatus.setText("正在发送…");
        hid.sendText(value, result -> {
            if (result==HidController.TEXT_USB)
                portraitInputStatus.setText("电脑端已接收，请检查当前光标位置");
            else if (result==HidController.TEXT_BLUETOOTH)
                portraitInputStatus.setText("蓝牙按键正在发送，请检查电脑光标位置");
            else if (result==HidController.TEXT_NEEDS_USB)
                portraitInputStatus.setText("中文无法通过蓝牙 HID 发送；请连接 USB Bridge");
            else portraitInputStatus.setText("发送失败：电脑未连接或 Bridge 未响应");
        });
    }

    private GradientDrawable buttonBackground(boolean selected) {
        return selected
            ? UiKit.card(this,Color.rgb(49,112,221),Color.rgb(27,76,167))
            : UiKit.pill(this,Color.rgb(35,47,66));
    }

    private ImageView cameraHeaderButton(String drawable, String description) {
        ImageView button = new ImageView(this);
        button.setImageResource(getResources().getIdentifier(drawable, "drawable", getPackageName()));
        button.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        button.setContentDescription(description);
        button.setBackground(buttonBackground(false));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(44), dp(42));
        params.leftMargin = dp(4);
        headerBar.addView(button, params);
        return button;
    }

    private void addSurface(FrameLayout surface,View view) {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-1,-1);
        params.setMargins(dp(8),0,dp(8),dp(8));
        surface.addView(view,params);
    }

    private void setMode(int mode) {
        if (trackpad == null || gamepad == null || keyboard == null || textInput == null || cameraView == null) return;
        if (mode == MODE_CAMERA && !getPreferences(MODE_PRIVATE).getBoolean("camera_guide_seen_v1",false)) {
            getPreferences(MODE_PRIVATE).edit().putBoolean("camera_guide_seen_v1",true).apply();
            mode = MODE_CAMERA_GUIDE;
        }
        if (mode == MODE_SPEAKER && !getPreferences(MODE_PRIVATE).getBoolean("speaker_guide_seen_v1",false)) {
            getPreferences(MODE_PRIVATE).edit().putBoolean("speaker_guide_seen_v1",true).apply();
            mode = MODE_SPEAKER_GUIDE;
        }
        if (mode == MODE_MICROPHONE && !getPreferences(MODE_PRIVATE).getBoolean("microphone_guide_seen_v1",false)) {
            getPreferences(MODE_PRIVATE).edit().putBoolean("microphone_guide_seen_v1",true).apply();
            mode = MODE_MICROPHONE_GUIDE;
        }
        if (mode == MODE_DISPLAY && !getPreferences(MODE_PRIVATE).getBoolean("display_guide_seen_v1",false)) {
            getPreferences(MODE_PRIVATE).edit().putBoolean("display_guide_seen_v1",true).apply();
            mode = MODE_DISPLAY_GUIDE;
        }
        microphoneView.deactivate();
        speakerView.deactivate();
        cameraView.deactivate();
        displayView.deactivate();
        hidePortraitIme();
        releaseLeftButton();
        textInput.hideKeyboard();
        textMode=false;
        trackpad.cancelGesture();
        gamepad.releaseAll();
        keyboard.clearModifiers();
        selectedMode=mode;
        ((View)home.getParent()).setVisibility(mode == MODE_HOME ? View.VISIBLE : View.GONE);
        functionPage.setVisibility(mode == MODE_HOME || mode == MODE_DISPLAY ? View.GONE : View.VISIBLE);
        displayPage.setVisibility(mode == MODE_DISPLAY ? View.VISIBLE : View.GONE);
        trackpadContainer.setVisibility(mode == MODE_TOUCHPAD ? View.VISIBLE : View.GONE);
        keyboard.setVisibility(mode == MODE_KEYBOARD ? View.VISIBLE : View.GONE);
        gamepad.setVisibility(mode == MODE_GAMEPAD ? View.VISIBLE : View.GONE);
        textInput.setVisibility(View.GONE);
        microphoneView.setVisibility(mode == MODE_MICROPHONE ? View.VISIBLE : View.GONE);
        speakerView.setVisibility(mode == MODE_SPEAKER ? View.VISIBLE : View.GONE);
        cameraView.setVisibility(mode == MODE_CAMERA ? View.VISIBLE : View.GONE);
        cameraGuideView.setVisibility(mode == MODE_CAMERA_GUIDE ? View.VISIBLE : View.GONE);
        if (mode == MODE_CAMERA_GUIDE) cameraGuideView.post(() -> cameraGuideView.scrollTo(0,0));
        speakerGuideView.setVisibility(mode == MODE_SPEAKER_GUIDE ? View.VISIBLE : View.GONE);
        if (mode == MODE_SPEAKER_GUIDE) speakerGuideView.post(() -> speakerGuideView.scrollTo(0,0));
        touchpadGuideView.setVisibility(mode == MODE_TOUCHPAD_GUIDE ? View.VISIBLE : View.GONE);
        keyboardGuideView.setVisibility(mode == MODE_KEYBOARD_GUIDE ? View.VISIBLE : View.GONE);
        gamepadGuideView.setVisibility(mode == MODE_GAMEPAD_GUIDE ? View.VISIBLE : View.GONE);
        microphoneGuideView.setVisibility(mode == MODE_MICROPHONE_GUIDE ? View.VISIBLE : View.GONE);
        if (mode == MODE_TOUCHPAD_GUIDE) touchpadGuideView.refresh();
        if (mode == MODE_KEYBOARD_GUIDE) keyboardGuideView.refresh();
        if (mode == MODE_GAMEPAD_GUIDE) gamepadGuideView.refresh();
        if (mode == MODE_MICROPHONE_GUIDE) microphoneGuideView.refresh();
        displayView.setVisibility(mode == MODE_DISPLAY ? View.VISIBLE : View.GONE);
        displayGuideView.setVisibility(mode == MODE_DISPLAY_GUIDE ? View.VISIBLE : View.GONE);
        if (mode == MODE_DISPLAY_GUIDE) displayGuideView.post(() -> displayGuideView.scrollTo(0, 0));
        boolean onPad = mode == MODE_TOUCHPAD;
        orientationButton.setVisibility(onPad ? View.VISIBLE : View.GONE);
        cameraRotateButton.setVisibility(mode == MODE_CAMERA ? View.VISIBLE : View.GONE);
        cameraSwitchButton.setVisibility(mode == MODE_CAMERA ? View.VISIBLE : View.GONE);
        settingsButton.setVisibility(onPad ? View.VISIBLE : View.GONE);
        textButton.setVisibility(onPad ? View.VISIBLE : View.GONE);
        helpButton.setVisibility(mode == MODE_TOUCHPAD || mode == MODE_KEYBOARD || mode == MODE_GAMEPAD
            || mode == MODE_MICROPHONE || mode == MODE_CAMERA || mode == MODE_SPEAKER ? View.VISIBLE : View.GONE);
        updateTouchpadControls();
        updateOrientationButton();
        applyOrientation();
        updateDisplaySystemUi();
        if (mode == MODE_MICROPHONE || mode == MODE_CAMERA || mode == MODE_SPEAKER) activateMedia(mode);
        if (mode == MODE_DISPLAY) {
            displayView.activate();
            displayAspectButton.setText("等比");
            displayAspectButton.setContentDescription("按原比例显示");
        }
    }

    private void activateMedia(int mode) {
        if (mode != selectedMode) return;
        if (mode == MODE_MICROPHONE) microphoneView.activate();
        if (mode == MODE_SPEAKER) speakerView.activate();
        if (mode == MODE_CAMERA) cameraView.activate();
    }

    private void setTextMode(boolean enabled) {
        if (selectedMode != MODE_TOUCHPAD) return;
        if (enabled) hidePortraitIme();
        textMode=enabled;
        trackpad.cancelGesture();
        trackpadContainer.setVisibility(enabled ? View.GONE : View.VISIBLE);
        textInput.setVisibility(enabled ? View.VISIBLE : View.GONE);
        orientationButton.setVisibility(enabled ? View.GONE : View.VISIBLE);
        settingsButton.setVisibility(enabled ? View.GONE : View.VISIBLE);
        textButton.setVisibility(enabled ? View.GONE : View.VISIBLE);
        portraitKeyboardButton.setVisibility(View.GONE);
        helpButton.setVisibility(enabled ? View.GONE : View.VISIBLE);
        if (enabled) {
            textInput.refreshConnection();
            textInput.showKeyboard();
        } else {
            textInput.hideKeyboard();
            updateTouchpadControls();
        }
    }

    private void navigateBack() {
        if (textMode) setTextMode(false);
        else if (selectedMode == MODE_CAMERA_GUIDE) setMode(MODE_CAMERA);
        else if (selectedMode == MODE_SPEAKER_GUIDE) setMode(MODE_SPEAKER);
        else if (selectedMode == MODE_TOUCHPAD_GUIDE) setMode(MODE_TOUCHPAD);
        else if (selectedMode == MODE_KEYBOARD_GUIDE) setMode(MODE_KEYBOARD);
        else if (selectedMode == MODE_GAMEPAD_GUIDE) setMode(MODE_GAMEPAD);
        else if (selectedMode == MODE_MICROPHONE_GUIDE) setMode(MODE_MICROPHONE);
        else if (selectedMode == MODE_DISPLAY_GUIDE) setMode(MODE_DISPLAY);
        else if (selectedMode != MODE_HOME) setMode(MODE_HOME);
        else super.onBackPressed();
    }

    private void updateOrientationButton() {
        if (orientationButton == null) return;
        orientationButton.setContentDescription(portraitTouchpad ? "切换横屏" : "切换竖屏");
    }

    private void applyOrientation() {
        int target=selectedMode == MODE_HOME || selectedMode == MODE_MICROPHONE || selectedMode == MODE_SPEAKER
            || selectedMode == MODE_CAMERA || selectedMode == MODE_CAMERA_GUIDE || selectedMode == MODE_SPEAKER_GUIDE
            || selectedMode == MODE_MICROPHONE_GUIDE || selectedMode == MODE_DISPLAY_GUIDE
            || ((selectedMode == MODE_TOUCHPAD || selectedMode == MODE_TOUCHPAD_GUIDE) && portraitTouchpad)
            ? ActivityInfo.SCREEN_ORIENTATION_PORTRAIT : ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
        if (getRequestedOrientation() != target) setRequestedOrientation(target);
    }

    private void updateDisplaySystemUi() {
        View decor = getWindow().getDecorView();
        boolean fullScreen = selectedMode == MODE_DISPLAY;
        if (Build.VERSION.SDK_INT >= 30) {
            android.view.WindowInsetsController controller = decor.getWindowInsetsController();
            if (controller != null) {
                if (fullScreen) {
                    controller.setSystemBarsBehavior(android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                    controller.hide(WindowInsets.Type.systemBars());
                } else controller.show(WindowInsets.Type.systemBars());
            }
        } else {
            decor.setSystemUiVisibility(fullScreen
                ? View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                : 0);
        }
        if (rootView != null && Build.VERSION.SDK_INT >= 30) rootView.requestApplyInsets();
    }

    private void releaseLeftButton() {
        if (!leftButtonHeld) return;
        leftButtonHeld=false;
        trackpad.setExternalLeftHeld(false);
        hid.leftUp();
        leftMouseButton.setBackground(buttonBackground(false));
    }

    private boolean isPortrait() {
        return getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
    }

    private void updateTouchpadControls() {
        if (mouseButtons == null || portraitKeyboardButton == null) return;
        boolean onPad=selectedMode == MODE_TOUCHPAD && !textMode;
        boolean compact=onPad && !isPortrait();
        if (mouseButtons.getParent() != (compact ? headerBar : trackpadContainer)) releaseLeftButton();
        LinearLayout.LayoutParams headerParams=(LinearLayout.LayoutParams)headerBar.getLayoutParams();
        int headerHeight=dp(compact ? 44 : 54);
        if (headerParams.height != headerHeight) {
            headerParams.height=headerHeight;
            headerBar.setLayoutParams(headerParams);
            headerBar.setPadding(dp(8),dp(compact ? 3 : 5),dp(8),dp(compact ? 3 : 5));
            for (int i=0;i<headerBar.getChildCount();i++) {
                View child=headerBar.getChildAt(i);
                LinearLayout.LayoutParams params=(LinearLayout.LayoutParams)child.getLayoutParams();
                if (params.weight == 1 && params.width == 0) continue;
                params.height=dp(compact ? 36 : 42);
                child.setLayoutParams(params);
            }
        }
        boolean inHeader=mouseButtons.getParent() == headerBar;
        if (compact && !inHeader) {
            ((ViewGroup)mouseButtons.getParent()).removeView(mouseButtons);
            headerBar.addView(mouseButtons,1,new LinearLayout.LayoutParams(dp(128),dp(36)));
        } else if (!compact && inHeader) {
            headerBar.removeView(mouseButtons);
            trackpadContainer.addView(mouseButtons,1,new LinearLayout.LayoutParams(-1,dp(55)));
        }
        mouseButtons.setPadding(dp(compact ? 0 : 2),0,dp(compact ? 0 : 2),dp(compact ? 0 : 5));
        for (int i=0;i<mouseButtons.getChildCount();i++) {
            TextView button=(TextView)mouseButtons.getChildAt(i);
            LinearLayout.LayoutParams params=(LinearLayout.LayoutParams)button.getLayoutParams();
            params.height=dp(compact ? 34 : 48);
            button.setLayoutParams(params);
            button.setTextSize(compact ? 13 : 15);
        }
        mouseButtons.setVisibility(onPad && windows ? View.VISIBLE : View.GONE);
        portraitKeyboardButton.setVisibility(onPad && isPortrait() ? View.VISIBLE : View.GONE);
        if ((!onPad || !isPortrait()) && portraitImeVisible) hidePortraitIme();
    }

    private void showPortraitIme() {
        if (selectedMode != MODE_TOUCHPAD || !isPortrait() || textMode) return;
        portraitImeVisible=true;
        portraitInputRow.setVisibility(View.VISIBLE);
        portraitKeyboardButton.setBackground(buttonBackground(true));
        portraitKeyboardButton.setContentDescription("关闭手机键盘");
        portraitEditor.requestFocus();
        portraitEditor.post(() -> {
            InputMethodManager imm=(InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(portraitEditor,InputMethodManager.SHOW_IMPLICIT);
        });
    }

    private void hidePortraitIme() {
        if (!portraitImeVisible || portraitEditor == null) return;
        portraitImeVisible=false;
        InputMethodManager imm=(InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(portraitEditor.getWindowToken(),0);
        portraitEditor.clearFocus();
        portraitInputRow.setVisibility(View.GONE);
        portraitKeyboardButton.setBackground(buttonBackground(false));
        portraitKeyboardButton.setContentDescription("打开手机键盘");
    }

    private void showSettings() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(22), dp(8), dp(22), dp(12));

        TextView systemLabel = new TextView(this);
        systemLabel.setText("电脑系统");
        systemLabel.setTextSize(15);
        content.addView(systemLabel);
        RadioGroup systems = new RadioGroup(this);
        RadioButton windowsOption = new RadioButton(this);
        windowsOption.setText("Windows 模式");
        windowsOption.setId(View.generateViewId());
        systems.addView(windowsOption);
        RadioButton macOption = new RadioButton(this);
        macOption.setText("Mac 模式");
        macOption.setId(View.generateViewId());
        systems.addView(macOption);
        systems.check(windows ? windowsOption.getId() : macOption.getId());
        content.addView(systems);
        systems.setOnCheckedChangeListener((group, checkedId) -> {
            windows = checkedId == windowsOption.getId();
            getPreferences(MODE_PRIVATE).edit().putBoolean("windows", windows).apply();
            trackpad.setWindows(windows);
            if (!windows) releaseLeftButton();
            updateTouchpadControls();
        });

        Switch longPressSwitch = new Switch(this);
        longPressSwitch.setText("长按触发右键");
        longPressSwitch.setTextSize(15);
        longPressSwitch.setChecked(longPressRightEnabled);
        LinearLayout.LayoutParams switchParams = new LinearLayout.LayoutParams(-1, -2);
        switchParams.topMargin = dp(12);
        content.addView(longPressSwitch, switchParams);
        longPressSwitch.setOnCheckedChangeListener((button, checked) -> {
            longPressRightEnabled = checked;
            trackpad.setLongPressRightEnabled(checked);
            getPreferences(MODE_PRIVATE).edit().putBoolean("long_press_right", checked).apply();
        });

        TextView sensitivityLabel = new TextView(this);
        sensitivityLabel.setTextSize(15);
        sensitivityLabel.setText(String.format(java.util.Locale.getDefault(), "指针灵敏度  %.2f×", touchpadSensitivity));
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(-1, -2);
        labelParams.topMargin = dp(20);
        content.addView(sensitivityLabel, labelParams);
        SeekBar sensitivitySlider = new SeekBar(this);
        sensitivitySlider.setMax(200);
        sensitivitySlider.setProgress(Math.round((touchpadSensitivity - 0.5f) * 100));
        content.addView(sensitivitySlider, new LinearLayout.LayoutParams(-1, -2));
        sensitivitySlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (!fromUser) return;
                touchpadSensitivity = (50 + progress) / 100f;
                sensitivityLabel.setText(String.format(java.util.Locale.getDefault(),
                    "指针灵敏度  %.2f×", touchpadSensitivity));
                trackpad.setSensitivity(touchpadSensitivity);
                getPreferences(MODE_PRIVATE).edit().putFloat("touchpad_sensitivity", touchpadSensitivity).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
        new AlertDialog.Builder(this).setTitle("触摸板设置").setView(content)
            .setPositiveButton("完成", null).show();
    }

    private void showFeedback() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(22),dp(8),dp(22),0);
        TextView instructions = new TextView(this);
        instructions.setText("写下问题或建议，点“邮件发送”后会打开手机邮件应用。\n\n邮箱：" + FEEDBACK_EMAIL + "\n微信：" + FEEDBACK_WECHAT + "（可点下方按钮复制）");
        instructions.setTextSize(14);
        instructions.setTextColor(Color.rgb(39,45,56));
        instructions.setTextIsSelectable(true);
        content.addView(instructions,new LinearLayout.LayoutParams(-1,-2));
        EditText message = new EditText(this);
        message.setHint("请描述遇到的问题或你的建议…");
        message.setMinLines(4);
        message.setMaxLines(8);
        message.setGravity(Gravity.TOP | Gravity.START);
        message.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(-1,-2);
        messageParams.topMargin = dp(14);
        content.addView(message,messageParams);
        new AlertDialog.Builder(this).setTitle("意见反馈")
            .setView(content)
            .setPositiveButton("邮件发送",(dialog,which) -> openFeedbackEmail(message.getText().toString()))
            .setNeutralButton("复制微信号",(dialog,which) -> {
                ClipboardManager clipboard=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
                if (clipboard != null) {
                    clipboard.setPrimaryClip(ClipData.newPlainText("懒人外设微信",FEEDBACK_WECHAT));
                    Toast.makeText(this,"微信号已复制",Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("关闭",null)
            .show();
    }

    private void openFeedbackEmail(String message) {
        String subject="懒人外设反馈";
        String body=message.trim();
        Uri recipient=Uri.parse("mailto:" + FEEDBACK_EMAIL + "?subject=" + Uri.encode(subject) + "&body=" + Uri.encode(body));
        Intent email=new Intent(Intent.ACTION_SENDTO,recipient);
        email.putExtra(Intent.EXTRA_SUBJECT,subject);
        email.putExtra(Intent.EXTRA_TEXT,body);
        try {
            startActivity(email);
        } catch (ActivityNotFoundException error) {
            ClipboardManager clipboard=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null && !body.isEmpty())
                clipboard.setPrimaryClip(ClipData.newPlainText("懒人外设反馈",body));
            Toast.makeText(this,"未找到邮件应用；反馈内容已复制，可通过微信发送",Toast.LENGTH_LONG).show();
        }
    }

    private void showGuide() {
        if (selectedMode == MODE_DISPLAY) { setMode(MODE_DISPLAY_GUIDE); return; }
        if (selectedMode == MODE_CAMERA) { setMode(MODE_CAMERA_GUIDE); return; }
        if (selectedMode == MODE_SPEAKER) { setMode(MODE_SPEAKER_GUIDE); return; }
        if (selectedMode == MODE_TOUCHPAD) setMode(MODE_TOUCHPAD_GUIDE);
        else if (selectedMode == MODE_KEYBOARD) setMode(MODE_KEYBOARD_GUIDE);
        else if (selectedMode == MODE_GAMEPAD) setMode(MODE_GAMEPAD_GUIDE);
        else if (selectedMode == MODE_MICROPHONE) setMode(MODE_MICROPHONE_GUIDE);
    }

    private void showConnections() {
        if (!hid.hasPermission()) {
            if (Build.VERSION.SDK_INT >= 31)
                requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, REQUEST_BLUETOOTH);
            return;
        }
        if (!hid.isBluetoothReady()) {
            new AlertDialog.Builder(this).setMessage("请先在系统设置中开启蓝牙。")
                .setPositiveButton("蓝牙设置",(d,w) -> openBluetoothSettings())
                .setNegativeButton("关闭",null).show();
            return;
        }
        List<BluetoothDevice> devices=hid.bondedDevices();
        String[] names=new String[devices.size()];
        for (int i=0;i<devices.size();i++) {
            String name=devices.get(i).getName();
            names[i]=(name == null ? "未命名设备" : name) + " · " + devices.get(i).getAddress();
        }
        AlertDialog.Builder dialog=new AlertDialog.Builder(this).setTitle("选择已配对电脑")
            .setMessage(devices.isEmpty() ? "没有已配对设备。请先在系统蓝牙设置中配对电脑。" : null)
            .setPositiveButton("蓝牙设置",(d,w) -> openBluetoothSettings())
            .setNegativeButton("关闭",null);
        if (!devices.isEmpty()) dialog.setItems(names,(d,which) -> hid.connect(devices.get(which)));
        dialog.show();
    }

    private void openBluetoothSettings() { startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)); }

    private void requestSaveBridgePackage(boolean mac) {
        String name = "懒人外设-" + (mac ? "macOS" : "Windows") + "-全功能-USB-Bridge.zip";
        Intent save = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        save.addCategory(Intent.CATEGORY_OPENABLE);
        save.setType("application/zip");
        save.putExtra(Intent.EXTRA_TITLE, name);
        try {
            startActivityForResult(save, mac ? REQUEST_SAVE_MAC_BRIDGE : REQUEST_SAVE_WINDOWS_BRIDGE);
        } catch (ActivityNotFoundException error) {
            Toast.makeText(this,"手机没有可用的文件保存程序",Toast.LENGTH_LONG).show();
        }
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if (requestCode != REQUEST_SAVE_MAC_BRIDGE && requestCode != REQUEST_SAVE_WINDOWS_BRIDGE) return;
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        String asset = requestCode == REQUEST_SAVE_MAC_BRIDGE ? "bridge-macos.zip" : "bridge-windows.zip";
        try (InputStream source = getAssets().open(asset);
             OutputStream target = getContentResolver().openOutputStream(data.getData(),"w")) {
            if (target == null) throw new IOException("无法打开目标文件");
            byte[] buffer = new byte[8192];
            int count;
            while ((count = source.read(buffer)) != -1) target.write(buffer,0,count);
            target.flush();
            Toast.makeText(this,"桥接包已保存到手机；请将 ZIP 传到电脑后解压",Toast.LENGTH_LONG).show();
        } catch (IOException | SecurityException error) {
            Toast.makeText(this,"保存失败：" + error.getMessage(),Toast.LENGTH_LONG).show();
        }
    }

    @Override public void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);
        if (trackpad != null) trackpad.cancelGesture();
        if (gamepad != null) gamepad.releaseAll();
        updateTouchpadControls();
        updateDisplaySystemUi();
    }

    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] results) {
        super.onRequestPermissionsResult(requestCode,permissions,results);
        if (requestCode == REQUEST_BLUETOOTH && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED)
            if (hid != null) hid.start();
        if (requestCode == MediaFeatureView.REQUEST_MICROPHONE && microphoneView != null)
            microphoneView.onPermissionResult(results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED);
        if (requestCode == MediaFeatureView.REQUEST_CAMERA && cameraView != null)
            cameraView.onPermissionResult(results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED);
    }

    @Override protected void onResume() {
        super.onResume();
        updateDisplaySystemUi();
        if (hid != null) hid.autoConnect();
        if (selectedMode == MODE_MICROPHONE && microphoneView != null) microphoneView.activate();
        if (selectedMode == MODE_SPEAKER && speakerView != null) speakerView.activate();
        if (selectedMode == MODE_CAMERA && cameraView != null) cameraView.activate();
        if (selectedMode == MODE_DISPLAY && displayView != null) displayView.activate();
    }
    @Override protected void onPause() {
        if (microphoneView != null) microphoneView.deactivate();
        if (speakerView != null) speakerView.deactivate();
        if (cameraView != null) cameraView.deactivate();
        if (displayView != null) displayView.deactivate();
        if (trackpad != null) trackpad.cancelGesture();
        if (gamepad != null) gamepad.releaseAll();
        if (leftButtonHeld) releaseLeftButton();
        super.onPause();
    }
    @Override public void onBackPressed() { navigateBack(); }
    @Override protected void onDestroy() {
        if (microphoneView != null) microphoneView.dispose();
        if (speakerView != null) speakerView.dispose();
        if (cameraView != null) cameraView.dispose();
        if (displayView != null) displayView.dispose();
        if (mediaBridge != null) mediaBridge.stop();
        if (bound) unbindService(connection);
        super.onDestroy();
    }

}
