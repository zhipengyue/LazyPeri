package com.mobiletrackpad.app;

import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
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
    private final IntConsumer onRumble;
    private volatile boolean running = true;
    private volatile Socket client;
    private volatile OutputStream output;
    private volatile ServerSocket server;
    private final ExecutorService writer = Executors.newSingleThreadExecutor(
        task -> new Thread(task, "UsbTrackpadWriter"));
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<Integer,Consumer<Boolean>> pending = new HashMap<>();
    private int nextRequestId;

    UsbBridge(Runnable onConnected, IntConsumer onRumble) {
        this.onConnected = onConnected;
        this.onRumble = onRumble;
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
                    if (!running) {
                        peer.close();
                        return;
                    }
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
                        } else if (parts.length == 3 && parts[0].equals("RUMBLE")) {
                            try {
                                int intensity = Math.max(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
                                handler.post(() -> { if (client == peer) onRumble.accept(intensity); });
                            } catch (NumberFormatException ignored) { }
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
        OutputStream destination = output;
        if (!running || destination == null) return false;
        // Touch handlers run on the UI thread. Send reports in order on a worker.
        writer.execute(() -> writeEvent(destination, event));
        return true;
    }

    private void writeEvent(OutputStream destination, String event) {
        if (output != destination) return;
        try {
            destination.write((event + "\n").getBytes(StandardCharsets.US_ASCII));
            destination.flush();
        } catch (IOException error) {
            synchronized (this) {
                if (output == destination) closeClient();
            }
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
        writer.shutdownNow();
        try { if (server != null) server.close(); } catch (IOException ignored) { }
    }

    private void closeClient() {
        output = null;
        try { if (client != null) client.close(); } catch (IOException ignored) { }
        client = null;
        handler.post(() -> onRumble.accept(0));
        for (Integer id: new java.util.ArrayList<>(pending.keySet())) finish(id,false);
    }
}
