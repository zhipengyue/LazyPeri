package com.mobiletrackpad.app;

import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import android.os.Handler;
import android.os.Looper;

/** Local TCP endpoint exposed to a desktop companion through adb forward over USB. */
final class UsbBridge {
    static final int PORT = 27183;
    private final Runnable onConnected;
    private volatile boolean running = true;
    private volatile Socket client;
    private volatile OutputStream output;
    private ServerSocket server;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<Integer,Consumer<Boolean>> pending = new HashMap<>();
    private int nextRequestId;

    UsbBridge(Runnable onConnected) {
        this.onConnected = onConnected;
        Thread thread = new Thread(this::acceptLoop, "UsbTrackpadBridge");
        thread.setDaemon(true);
        thread.start();
    }

    boolean isConnected() { return output != null; }

    private void acceptLoop() {
        try (ServerSocket socket = new ServerSocket(PORT, 1, InetAddress.getByName("127.0.0.1"))) {
            server = socket;
            if (!running) return;
            while (running) {
                Socket peer = socket.accept();
                peer.setTcpNoDelay(true);
                synchronized (this) {
                    closeClient();
                    client = peer;
                    output = peer.getOutputStream();
                }
                onConnected.run();
                try {
                    BufferedReader input = new BufferedReader(new InputStreamReader(peer.getInputStream(), StandardCharsets.US_ASCII));
                    String line;
                    while (running && (line = input.readLine()) != null) {
                        String[] parts=line.split(" ");
                        if (parts.length == 2 && (parts[0].equals("ACK") || parts[0].equals("ERR"))) {
                            try { finish(Integer.parseInt(parts[1]),parts[0].equals("ACK")); }
                            catch (NumberFormatException ignored) { }
                        }
                    }
                } catch (IOException ignored) {
                } finally {
                    synchronized (this) {
                        if (client == peer) closeClient();
                    }
                }
            }
        } catch (IOException ignored) {
            // Port unavailable: Bluetooth remains usable.
        }
    }

    synchronized boolean send(String event) {
        if (output == null) return false;
        try {
            output.write((event + "\n").getBytes(StandardCharsets.US_ASCII));
            output.flush();
            return true;
        } catch (IOException error) {
            closeClient();
            return false;
        }
    }

    synchronized void sendText(String encoded, Consumer<Boolean> callback) {
        int id=++nextRequestId;
        pending.put(id,callback);
        if (!send("TEXT " + id + " " + encoded)) { finish(id,false); return; }
        handler.postDelayed(() -> finish(id,false),5000);
    }

    private synchronized void finish(int id, boolean success) {
        Consumer<Boolean> callback=pending.remove(id);
        if (callback!=null) handler.post(() -> callback.accept(success));
    }

    synchronized void stop() {
        running = false;
        closeClient();
        try { if (server != null) server.close(); } catch (IOException ignored) { }
    }

    private void closeClient() {
        output = null;
        try { if (client != null) client.close(); } catch (IOException ignored) { }
        client = null;
        for (Integer id: new java.util.ArrayList<>(pending.keySet())) finish(id,false);
    }
}
