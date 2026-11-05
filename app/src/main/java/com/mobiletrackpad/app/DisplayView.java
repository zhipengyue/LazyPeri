package com.mobiletrackpad.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;

/** Shows one desktop display streamed through adb forward to a loopback socket. */
final class DisplayView extends FrameLayout {
    static final int PORT = 27187;
    private static final int MAX_FRAME_BYTES = 2_000_000;
    private final ImageView image;
    private final TextView status;
    private final AtomicBoolean pendingFrame = new AtomicBoolean();
    private volatile boolean active;
    private volatile boolean running = true;
    private volatile int generation;
    private volatile Socket peer;
    private volatile ServerSocket server;

    DisplayView(Context context) {
        super(context);
        setBackgroundColor(Color.BLACK);
        image = new ImageView(context);
        image.setScaleType(ImageView.ScaleType.FIT_XY);
        image.setBackgroundColor(Color.BLACK);
        addView(image, new FrameLayout.LayoutParams(-1, -1));

        status = new TextView(context);
        status.setText("等待电脑端显示器桥接程序；请先在电脑创建扩展屏幕。");
        status.setTextColor(Color.WHITE);
        status.setTextSize(14);
        status.setGravity(Gravity.CENTER);
        status.setPadding(dp(16), dp(12), dp(16), dp(12));
        status.setBackground(UiKit.pill(context, Color.rgb(28, 39, 58)));
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        params.setMargins(dp(12), 0, dp(12), dp(12));
        addView(status, params);

        Thread listener = new Thread(this::listen, "DisplayBridge-" + PORT);
        listener.setDaemon(true);
        listener.start();
    }

    void activate() {
        active = true;
        image.setScaleType(ImageView.ScaleType.FIT_XY);
        status.setText("等待电脑端显示器桥接程序；请先在电脑创建扩展屏幕。");
        status.setVisibility(View.VISIBLE);
    }

    boolean toggleAspectMode() {
        boolean fill = image.getScaleType() != ImageView.ScaleType.FIT_XY;
        image.setScaleType(fill ? ImageView.ScaleType.FIT_XY : ImageView.ScaleType.FIT_CENTER);
        return fill;
    }

    void deactivate() {
        active = false;
        generation++;
        close(peer);
        image.setImageDrawable(null);
        status.setVisibility(View.VISIBLE);
    }

    void dispose() {
        deactivate();
        running = false;
        close(server);
    }

    private void listen() {
        try (ServerSocket listener = new ServerSocket(PORT, 1, InetAddress.getByName("127.0.0.1"))) {
            server = listener;
            while (running) {
                try (Socket connection = listener.accept()) {
                    peer = connection;
                    connection.setTcpNoDelay(true);
                    if (!active) continue;
                    int session = generation;
                    receive(connection.getInputStream(), session);
                } catch (IOException ignored) {
                    // Closing the page or USB cable ends the current stream.
                } finally {
                    peer = null;
                    if (active && running) post(() -> {
                        if (active) {
                            status.setText("连接已断开；等待电脑端重新连接。");
                            status.setVisibility(View.VISIBLE);
                        }
                    });
                }
            }
        } catch (IOException error) {
            post(() -> status.setText("无法启动显示器通道：" + error.getMessage()));
        }
    }

    private void receive(InputStream input, int session) throws IOException {
        byte[] header = new byte[4];
        while (running && active && session == generation) {
            readFully(input, header);
            int size = ((header[0] & 255) << 24) | ((header[1] & 255) << 16)
                | ((header[2] & 255) << 8) | (header[3] & 255);
            if (size < 100 || size > MAX_FRAME_BYTES) throw new IOException("显示器画面长度异常");
            byte[] encoded = new byte[size];
            readFully(input, encoded);
            if (!pendingFrame.compareAndSet(false, true)) continue;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            Bitmap frame = BitmapFactory.decodeByteArray(encoded, 0, size, options);
            if (frame == null) { pendingFrame.set(false); continue; }
            if (!post(() -> {
                try {
                    if (active && session == generation) {
                        image.setImageBitmap(frame);
                        status.setVisibility(View.GONE);
                    }
                } finally { pendingFrame.set(false); }
            })) pendingFrame.set(false);
        }
    }

    private void readFully(InputStream input, byte[] data) throws IOException {
        int offset = 0;
        while (offset < data.length) {
            int count = input.read(data, offset, data.length - offset);
            if (count < 0) throw new EOFException("电脑已断开连接");
            offset += count;
        }
    }

    private void close(Socket socket) {
        try { if (socket != null) socket.close(); } catch (IOException ignored) { }
    }

    private void close(ServerSocket socket) {
        try { if (socket != null) socket.close(); } catch (IOException ignored) { }
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
