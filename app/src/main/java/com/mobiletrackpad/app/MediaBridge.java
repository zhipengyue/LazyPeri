package com.mobiletrackpad.app;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

/** Three loopback-only data channels reached from a desktop through adb forward. */
final class MediaBridge {
    static final int MIC_PORT = 27184, SPEAKER_PORT = 27185, CAMERA_PORT = 27186;
    private volatile boolean running = true;
    private volatile boolean speakerEnabled;
    private volatile Socket micSocket, cameraSocket, speakerSocket;
    private volatile ServerSocket micServer, speakerServer, cameraServer;
    private volatile OutputStream micOutput, cameraOutput;

    MediaBridge() {
        listen(MIC_PORT, 0);
        listen(SPEAKER_PORT, 1);
        listen(CAMERA_PORT, 2);
    }

    private void listen(int port, int channel) {
        Thread thread = new Thread(() -> {
            try (ServerSocket server = new ServerSocket(port, 1, InetAddress.getByName("127.0.0.1"))) {
                if (channel == 0) micServer = server;
                else if (channel == 1) speakerServer = server;
                else cameraServer = server;
                while (running) {
                    Socket peer = server.accept();
                    peer.setTcpNoDelay(true);
                    if (channel == 0) micSocket = peer;
                    else if (channel == 1) speakerSocket = peer;
                    else cameraSocket = peer;
                    try (Socket session = peer) {
                        if (channel == 1) receiveSpeaker(session.getInputStream());
                        else {
                            if (channel == 0) micOutput = session.getOutputStream();
                            else cameraOutput = session.getOutputStream();
                            // Keep the socket open until the desktop process disconnects.
                            InputStream input = session.getInputStream();
                            while (running && input.read() != -1) { }
                        }
                    } catch (IOException ignored) {
                    } finally {
                        if (channel == 0 && micSocket == peer) { micOutput = null; micSocket = null; }
                        if (channel == 1 && speakerSocket == peer) speakerSocket = null;
                        if (channel == 2 && cameraSocket == peer) { cameraOutput = null; cameraSocket = null; }
                    }
                }
            } catch (IOException ignored) { }
        }, "MediaBridge-" + port);
        thread.setDaemon(true);
        thread.start();
    }

    synchronized boolean sendMic(byte[] pcm) {
        return write(micOutput, micSocket, pcm);
    }

    synchronized boolean sendCamera(byte[] jpeg) {
        if (cameraOutput == null) return false;
        int size = jpeg.length;
        byte[] header = {(byte)(size >>> 24), (byte)(size >>> 16), (byte)(size >>> 8), (byte)size};
        if (!write(cameraOutput, cameraSocket, header)) return false;
        return write(cameraOutput, cameraSocket, jpeg);
    }

    private boolean write(OutputStream output, Socket socket, byte[] data) {
        if (output == null) return false;
        try { output.write(data); return true; }
        catch (IOException error) {
            try { if (socket != null) socket.close(); } catch (IOException ignored) { }
            return false;
        }
    }

    void setSpeakerEnabled(boolean enabled) { speakerEnabled = enabled; }
    boolean micConnected() { return micSocket != null && !micSocket.isClosed(); }
    boolean speakerConnected() { return speakerSocket != null && !speakerSocket.isClosed(); }
    boolean cameraConnected() { return cameraSocket != null && !cameraSocket.isClosed(); }

    private void receiveSpeaker(InputStream input) throws IOException {
        AudioTrack track = null;
        byte[] chunk = new byte[8192];
        byte[] aligned = new byte[8196];
        int carry = 0;
        try {
            while (running) {
                int count = input.read(chunk);
                if (count < 0) break;
                if (!speakerEnabled) {
                    if (track != null) { track.stop(); track.release(); track = null; }
                    carry = 0;
                    continue;
                }
                if (track == null) {
                    int minimum = AudioTrack.getMinBufferSize(48000, AudioFormat.CHANNEL_OUT_STEREO,
                        AudioFormat.ENCODING_PCM_16BIT);
                    if (minimum <= 0) continue;
                    track = new AudioTrack.Builder()
                        .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build())
                        .setAudioFormat(new AudioFormat.Builder().setSampleRate(48000)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build())
                        .setBufferSizeInBytes(Math.max(minimum, 16384)).build();
                    track.play();
                }
                System.arraycopy(chunk, 0, aligned, carry, count);
                int available = carry + count;
                int complete = available / 4 * 4;
                int written = 0;
                while (written < complete) {
                    int amount = track.write(aligned, written, complete - written);
                    if (amount <= 0) break;
                    written += amount;
                }
                carry = available - complete;
                if (carry > 0) System.arraycopy(aligned, complete, aligned, 0, carry);
            }
        } catch (IllegalStateException ignored) {
        } finally {
            if (track != null) { try { track.stop(); } catch (IllegalStateException ignored) { } track.release(); }
        }
    }

    void stop() {
        running = false;
        for (Socket socket : new Socket[]{micSocket, speakerSocket, cameraSocket})
            try { if (socket != null) socket.close(); } catch (IOException ignored) { }
        for (ServerSocket server : new ServerSocket[]{micServer, speakerServer, cameraServer})
            try { if (server != null) server.close(); } catch (IOException ignored) { }
    }
}
