package com.mobiletrackpad.app;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.RectF;
import android.graphics.ImageFormat;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

/** Audio controls and a camera preview drawn from the same frames sent to the desktop bridge. */
@SuppressWarnings("deprecation")
final class MediaFeatureView extends FrameLayout implements SurfaceHolder.Callback {
    static final int MICROPHONE = 3, SPEAKER = 4, CAMERA = 5;
    static final int REQUEST_MICROPHONE = 201, REQUEST_CAMERA = 202;

    private final Activity activity;
    private final int kind;
    private final MediaBridge bridge;
    private final TextView status;
    private final Visual visual;
    private SurfaceView surface;
    private CameraFrameView cameraFrame;
    private Camera camera;
    private volatile boolean active;
    private boolean recording;
    private AudioRecord recorder;
    private int cameraFacing = Camera.CameraInfo.CAMERA_FACING_FRONT;
    private volatile int cameraRotationQuarters;
    private long lastFrameTime;
    private volatile int cameraGeneration;
    private final ExecutorService cameraSender;
    private final AtomicBoolean framePending = new AtomicBoolean();
    private final Runnable connectionUpdater = new Runnable() {
        @Override public void run() {
            if (!active) return;
            if (kind == MICROPHONE && recording)
                status.setText(bridge.micConnected()
                    ? "已连接电脑端麦克风桥；请在电脑应用中选择虚拟音频线的录音端。"
                    : "正在检测声音；等待电脑端麦克风桥接程序。");
            if (kind == SPEAKER)
                status.setText(bridge.speakerConnected()
                    ? "已连接电脑端音箱桥；正在播放从虚拟音频线接收的声音。"
                    : "等待电脑端音箱桥；请在电脑选择虚拟音频线作为声音输出。");
            if (kind == CAMERA && camera != null)
                status.setText((cameraFacing == Camera.CameraInfo.CAMERA_FACING_FRONT ? "前置镜头" : "后置镜头")
                    + (bridge.cameraConnected() ? " · 已连接电脑虚拟摄像头。" : " · 等待电脑端摄像头桥接程序。"));
            postDelayed(this, 1000);
        }
    };

    MediaFeatureView(Activity activity, int kind, MediaBridge bridge) {
        super(activity);
        this.activity = activity;
        this.kind = kind;
        this.bridge = bridge;
        cameraSender = kind == CAMERA ? Executors.newSingleThreadExecutor() : null;
        if (kind == CAMERA) cameraRotationQuarters = activity.getPreferences(Context.MODE_PRIVATE)
            .getInt("camera_rotation_quarters", 0) & 3;
        setBackgroundColor(Color.rgb(15, 20, 31));
        int padding = dp(22);
        if (kind == CAMERA) {
            surface = new SurfaceView(activity);
            surface.setBackgroundColor(Color.BLACK);
            surface.getHolder().addCallback(this);
            addView(surface, new FrameLayout.LayoutParams(-1, -1));
            cameraFrame = new CameraFrameView(activity);
            addView(cameraFrame, new FrameLayout.LayoutParams(-1, -1));
            TextView switchButton = new TextView(activity);
            switchButton.setText("↻  切换镜头");
            switchButton.setTextColor(Color.WHITE);
            switchButton.setTextSize(14);
            switchButton.setGravity(Gravity.CENTER);
            switchButton.setBackground(UiKit.pill(activity, Color.rgb(33, 45, 67)));
            FrameLayout.LayoutParams switchParams = new FrameLayout.LayoutParams(dp(116), dp(44), Gravity.TOP | Gravity.RIGHT);
            switchParams.setMargins(0, dp(12), dp(12), 0);
            addView(switchButton, switchParams);
            switchButton.setOnClickListener(v -> switchCamera());
            TextView rotateButton = new TextView(activity);
            rotateButton.setText("↻  画面旋转 90°");
            rotateButton.setTextColor(Color.WHITE);
            rotateButton.setTextSize(13);
            rotateButton.setGravity(Gravity.CENTER);
            rotateButton.setBackground(UiKit.pill(activity, Color.rgb(33, 45, 67)));
            FrameLayout.LayoutParams rotateParams = new FrameLayout.LayoutParams(dp(176), dp(44), Gravity.TOP | Gravity.RIGHT);
            rotateParams.setMargins(0, dp(64), dp(12), 0);
            addView(rotateButton, rotateParams);
            rotateButton.setOnClickListener(v -> rotateCameraOutput());
            visual = null;
        } else {
            visual = new Visual(activity);
            addView(visual, new FrameLayout.LayoutParams(-1, -1));
        }

        LinearLayout information = new LinearLayout(activity);
        information.setOrientation(LinearLayout.VERTICAL);
        information.setPadding(padding, dp(14), padding, dp(16));
        information.setBackground(UiKit.pill(activity, Color.rgb(25, 34, 50)));
        TextView title = new TextView(activity);
        title.setText(kind == MICROPHONE ? "麦克风" : kind == SPEAKER ? "双音箱" : "摄像头预览");
        title.setTextColor(Color.WHITE);
        title.setTextSize(20);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        information.addView(title);
        status = new TextView(activity);
        status.setTextColor(Color.rgb(180, 193, 214));
        status.setTextSize(13);
        status.setPadding(0, dp(8), 0, 0);
        information.addView(status);
        FrameLayout.LayoutParams infoParams = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        infoParams.setMargins(dp(12), 0, dp(12), dp(12));
        addView(information, infoParams);
        if (kind == SPEAKER) status.setText("需要 USB 音视频 Bridge 和电脑端虚拟音频线；蓝牙 HID 不传声音。");
        else if (kind == MICROPHONE) status.setText("需要 USB 音视频 Bridge 和电脑端虚拟音频线；蓝牙 HID 不传声音。");
        else status.setText("可预览手机镜头；可使用系统 USB 网络摄像头，或电脑端 OBS 虚拟摄像头与 USB 音视频 Bridge。");
    }

    private int dp(float value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }

    void activate() {
        active = true;
        if (kind == MICROPHONE) {
            if (activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                status.setText("需要麦克风权限，才能显示实时音量。");
                activity.requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_MICROPHONE);
            } else startMeter();
        } else if (kind == CAMERA) {
            if (activity.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                status.setText("需要摄像头权限，才能显示预览画面。");
                activity.requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA);
            } else startCamera();
        } else if (kind == SPEAKER) {
            bridge.setSpeakerEnabled(true);
            status.setText(bridge.speakerConnected()
                ? "电脑音频已连接，正在从电脑接收声音。"
                : "等待电脑端音频桥接程序。请在电脑选择虚拟音频设备作为输出。");
        }
        removeCallbacks(connectionUpdater);
        if (kind == SPEAKER || kind == MICROPHONE && recording || kind == CAMERA)
            post(connectionUpdater);
    }

    void deactivate() {
        active = false;
        removeCallbacks(connectionUpdater);
        stopMeter();
        stopCamera();
        if (kind == SPEAKER) bridge.setSpeakerEnabled(false);
        if (visual != null) visual.setLevel(0);
    }

    void dispose() {
        deactivate();
        if (cameraSender != null) cameraSender.shutdownNow();
    }

    void onPermissionResult(boolean granted) {
        if (!active) return;
        if (granted) activate();
        else status.setText(kind == MICROPHONE ? "麦克风权限未授予，无法显示声音反馈。" : "摄像头权限未授予，无法显示预览画面。");
    }

    private void startMeter() {
        if (recording || !active) return;
        int minimum = AudioRecord.getMinBufferSize(48000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if (minimum <= 0) { status.setText("此设备无法打开麦克风。"); return; }
        try {
            recorder = new AudioRecord(MediaRecorder.AudioSource.MIC, 48000, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, Math.max(minimum, 4096));
            if (recorder.getState() != AudioRecord.STATE_INITIALIZED) {
                recorder.release(); recorder = null;
                status.setText("麦克风初始化失败。");
                return;
            }
            recorder.startRecording();
            recording = true;
            status.setText("正在检测声音；等待电脑端麦克风桥接程序。");
            AudioRecord input = recorder;
            Thread thread = new Thread(() -> {
                short[] samples = new short[1024];
                try {
                    while (recording) {
                        int count = input.read(samples, 0, samples.length);
                        if (count <= 0) continue;
                        if (bridge.micConnected()) {
                            byte[] pcm = new byte[count * 2];
                            for (int i = 0; i < count; i++) {
                                pcm[i * 2] = (byte) samples[i];
                                pcm[i * 2 + 1] = (byte) (samples[i] >>> 8);
                            }
                            bridge.sendMic(pcm);
                        }
                        double energy = 0;
                        for (int i = 0; i < count; i++) energy += (double) samples[i] * samples[i];
                        float level = (float) Math.min(1, Math.sqrt(energy / count) / 7500.0);
                        post(() -> { if (active && visual != null) visual.setLevel(level); });
                    }
                } catch (IllegalStateException ignored) {
                    // The recording can stop while a read is in progress.
                } finally { input.release(); }
            }, "MicrophoneLevelMeter");
            thread.setDaemon(true);
            thread.start();
        } catch (RuntimeException error) {
            if (recorder != null) {
                recorder.release();
                recorder = null;
            }
            status.setText("无法启动麦克风：" + error.getClass().getSimpleName());
        }
    }

    private void stopMeter() {
        recording = false;
        AudioRecord input = recorder;
        recorder = null;
        if (input != null) {
            try { input.stop(); } catch (IllegalStateException ignored) { }
        }
    }

    private void switchCamera() {
        cameraFacing = cameraFacing == Camera.CameraInfo.CAMERA_FACING_FRONT
            ? Camera.CameraInfo.CAMERA_FACING_BACK : Camera.CameraInfo.CAMERA_FACING_FRONT;
        stopCamera();
        startCamera();
    }

    private void rotateCameraOutput() {
        cameraRotationQuarters = (cameraRotationQuarters + 1) & 3;
        activity.getPreferences(Context.MODE_PRIVATE).edit()
            .putInt("camera_rotation_quarters", cameraRotationQuarters).apply();
        status.setText("手机和电脑画面已旋转 " + (cameraRotationQuarters * 90) + "°；无需重启桥接程序。");
    }

    private void startCamera() {
        if (!active || kind != CAMERA || camera != null || !surface.getHolder().getSurface().isValid()
            || activity.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return;
        int chosen = -1;
        Camera.CameraInfo info = new Camera.CameraInfo();
        for (int i = 0; i < Camera.getNumberOfCameras(); i++) {
            Camera.getCameraInfo(i, info);
            if (info.facing == cameraFacing) { chosen = i; break; }
        }
        if (chosen < 0) { status.setText("此设备没有所选镜头。"); return; }
        try {
            camera = Camera.open(chosen);
            int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
            int degrees = rotation == 1 ? 90 : rotation == 2 ? 180 : rotation == 3 ? 270 : 0;
            Camera.getCameraInfo(chosen, info);
            int orientation = info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT
                ? (360 - (info.orientation + degrees) % 360) % 360
                : (info.orientation - degrees + 360) % 360;
            // The front preview is mirrored by Android, but the USB stream is mirrored by
            // the desktop bridge. Its raw sensor frames need the uncompensated angle.
            int streamOrientation = info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT
                ? (info.orientation + degrees) % 360 : orientation;
            camera.setDisplayOrientation(orientation);
            Camera.Parameters parameters = camera.getParameters();
            Camera.Size chosenSize = null;
            for (Camera.Size size : parameters.getSupportedPreviewSizes())
                if (chosenSize == null || Math.abs(size.width - 640) + Math.abs(size.height - 480)
                    < Math.abs(chosenSize.width - 640) + Math.abs(chosenSize.height - 480)) chosenSize = size;
            if (chosenSize != null) parameters.setPreviewSize(chosenSize.width, chosenSize.height);
            parameters.setPreviewFormat(ImageFormat.NV21);
            camera.setParameters(parameters);
            camera.setPreviewDisplay(surface.getHolder());
            Camera.Size previewSize = camera.getParameters().getPreviewSize();
            int generation = cameraGeneration;
            camera.setPreviewCallback((data, source) -> {
                if (!active || generation != cameraGeneration || System.currentTimeMillis() - lastFrameTime < 100
                    || !framePending.compareAndSet(false, true)) return;
                lastFrameTime = System.currentTimeMillis();
                byte[] snapshot = data.clone();
                boolean front = cameraFacing == Camera.CameraInfo.CAMERA_FACING_FRONT;
                int frameRotation = ((streamOrientation / 90) + cameraRotationQuarters) & 3;
                try { cameraSender.execute(() -> {
                    try {
                        YuvImage frame = new YuvImage(snapshot, ImageFormat.NV21, previewSize.width, previewSize.height, null);
                        ByteArrayOutputStream encoded = new ByteArrayOutputStream(65536);
                        encoded.write(frameRotation | (front ? 4 : 0));
                        frame.compressToJpeg(new android.graphics.Rect(0, 0, previewSize.width, previewSize.height), 65, encoded);
                        byte[] payload = encoded.toByteArray();
                        Bitmap bitmap = BitmapFactory.decodeByteArray(payload, 1, payload.length - 1);
                        if (bitmap != null) post(() -> {
                            if (active && generation == cameraGeneration) cameraFrame.show(bitmap, frameRotation, front);
                        });
                        if (generation == cameraGeneration && bridge.cameraConnected()) bridge.sendCamera(payload);
                    } catch (RuntimeException ignored) {
                    } finally { framePending.set(false); }
                }); } catch (RejectedExecutionException ignored) { framePending.set(false); }
            });
            camera.startPreview();
            status.setText((cameraFacing == Camera.CameraInfo.CAMERA_FACING_FRONT ? "前置镜头" : "后置镜头")
                + " · 等待电脑端摄像头桥接程序；部分机型也可使用系统 USB 网络摄像头模式。");
        } catch (Exception error) {
            stopCamera();
            status.setText("无法打开摄像头：" + error.getClass().getSimpleName());
        }
    }

    private void stopCamera() {
        cameraGeneration++;
        if (cameraFrame != null) cameraFrame.clear();
        Camera old = camera;
        camera = null;
        if (old != null) {
            try { old.stopPreview(); } catch (RuntimeException ignored) { }
            old.release();
        }
    }

    @Override public void surfaceCreated(SurfaceHolder holder) { startCamera(); }
    @Override public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        if (camera != null) { stopCamera(); startCamera(); }
    }
    @Override public void surfaceDestroyed(SurfaceHolder holder) { stopCamera(); }

    private final class CameraFrameView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private Bitmap bitmap;
        private int rotation;
        private boolean mirror;

        CameraFrameView(Context context) { super(context); }

        void show(Bitmap frame, int quarters, boolean front) {
            bitmap = frame;
            rotation = quarters;
            mirror = front;
            invalidate();
        }

        void clear() {
            bitmap = null;
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.drawColor(Color.rgb(10, 15, 25));
            if (bitmap == null) return;
            Matrix transform = new Matrix();
            transform.postRotate(rotation * 90f);
            if (mirror) transform.postScale(-1f, 1f);
            RectF bounds = new RectF(0, 0, bitmap.getWidth(), bitmap.getHeight());
            transform.mapRect(bounds);
            transform.postTranslate(-bounds.left, -bounds.top);
            float scale = Math.min(getWidth() / bounds.width(), getHeight() / bounds.height());
            transform.postScale(scale, scale);
            transform.postTranslate((getWidth() - bounds.width() * scale) / 2f,
                (getHeight() - bounds.height() * scale) / 2f);
            canvas.drawBitmap(bitmap, transform, paint);
        }
    }

    private final class Visual extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float level;
        Visual(Context context) { super(context); setLayerType(View.LAYER_TYPE_SOFTWARE, null); }
        void setLevel(float value) { level = value; invalidate(); }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float scale = Math.min(getWidth() / 380f, getHeight() / 600f);
            float cx = getWidth() / 2f, cy = getHeight() * 0.44f;
            canvas.save(); canvas.translate(cx, cy); canvas.scale(scale, scale);
            if (kind == MICROPHONE) drawMicrophone(canvas);
            else drawSpeakers(canvas);
            canvas.restore();
        }
        private void drawMicrophone(Canvas canvas) {
            int blue = Color.rgb(55, 151, 255), gray = Color.rgb(100, 111, 128);
            boolean speaking = level > 0.055f;
            paint.setColor(speaking ? blue : gray);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(7);
            paint.setShadowLayer(speaking ? 24 + level * 45 : 0, 0, 0, blue);
            canvas.drawRoundRect(new RectF(-48, -118, 48, 54), 48, 48, paint);
            canvas.drawArc(new RectF(-76, -70, 76, 92), 0, 180, false, paint);
            canvas.drawLine(0, 92, 0, 136, paint);
            canvas.drawLine(-35, 136, 35, 136, paint);
            paint.clearShadowLayer();
            paint.setStrokeWidth(2);
            paint.setColor(speaking ? Color.argb(140, 55, 151, 255) : Color.rgb(49, 59, 77));
            canvas.drawCircle(0, -17, 114 + level * 22, paint);
            canvas.drawCircle(0, -17, 145 + level * 30, paint);
        }
        private void drawSpeakers(Canvas canvas) {
            drawSpeaker(canvas, -89);
            drawSpeaker(canvas, 89);
        }
        private void drawSpeaker(Canvas canvas, float x) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(34, 47, 68));
            paint.setShadowLayer(20, 0, 12, Color.argb(110, 0, 0, 0));
            canvas.drawRoundRect(new RectF(x - 63, -125, x + 63, 145), 22, 22, paint);
            paint.clearShadowLayer();
            paint.setColor(Color.rgb(15, 22, 34));
            canvas.drawCircle(x, -53, 39, paint);
            canvas.drawCircle(x, 63, 55, paint);
            paint.setColor(Color.rgb(55, 151, 255));
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(5);
            canvas.drawCircle(x, -53, 24, paint);
            canvas.drawCircle(x, 63, 36, paint);
            paint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(x, -53, 6, paint);
            canvas.drawCircle(x, 63, 11, paint);
        }
    }
}
