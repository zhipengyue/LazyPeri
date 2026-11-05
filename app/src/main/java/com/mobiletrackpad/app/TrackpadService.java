package com.mobiletrackpad.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;

/** Owns HID registration so locking the screen does not destroy the connection. */
public final class TrackpadService extends Service {
    private static final String CHANNEL = "trackpad_connection";
    private static final int NOTIFICATION_ID = 1201;
    static final String ACTION_STOP = "com.mobiletrackpad.app.STOP";

    final class LocalBinder extends Binder {
        TrackpadService service() { return TrackpadService.this; }
    }

    private final IBinder binder = new LocalBinder();
    private HidController controller;

    HidController controller() { return controller; }

    @Override public void onCreate() {
        super.onCreate();
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(CHANNEL, "触摸板连接", NotificationManager.IMPORTANCE_LOW));
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent openIntent = PendingIntent.getActivity(this, 0, open,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent stop = new Intent(this, TrackpadService.class).setAction(ACTION_STOP);
        PendingIntent stopIntent = PendingIntent.getService(this, 1, stop,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setContentTitle("懒人外设")
            .setContentText("触摸板连接保持中")
            .setContentIntent(openIntent)
            .addAction(new Notification.Action.Builder(null, "断开", stopIntent).build())
            .setOngoing(true)
            .build();
        if (Build.VERSION.SDK_INT >= 29)
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
        else startForeground(NOTIFICATION_ID, notification);
        controller = new HidController(this, (message, connected) -> { });
        if (controller.hasPermission()) controller.start();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        return START_NOT_STICKY;
    }

    @Override public IBinder onBind(Intent intent) { return binder; }

    @Override public void onTaskRemoved(Intent rootIntent) { stopSelf(); }

    @Override public void onDestroy() {
        if (controller != null) controller.stop();
        super.onDestroy();
    }
}
