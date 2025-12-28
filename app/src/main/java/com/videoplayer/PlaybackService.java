package com.videoplayer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerNotificationManager;

public class PlaybackService extends Service {

    private final IBinder binder = new LocalBinder();
    public ExoPlayer player;
    public boolean isBackgroundPlayEnabled = false;
    private PlayerNotificationManager notificationManager;
    private static final String CHANNEL_ID = "playback_channel";
    private static final int NOTIFICATION_ID = 111;

    public class LocalBinder extends Binder {
        PlaybackService getService() {
            return PlaybackService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        player = new ExoPlayer.Builder(this).build();
        
        // CRASH FIX: সার্ভিস তৈরি হওয়ার সাথে সাথেই নোটিফিকেশন চ্যানেল তৈরি করতে হবে
        createNotificationChannel();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Background Playback",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Media Control");
            channel.setShowBadge(false);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    public void startBackgroundPlay(String videoTitle) {
        if (notificationManager == null) {
            notificationManager = new PlayerNotificationManager.Builder(this, NOTIFICATION_ID, CHANNEL_ID)
                    .setMediaDescriptionAdapter(new PlayerNotificationManager.MediaDescriptionAdapter() {
                        @Override
                        public CharSequence getCurrentContentTitle(Player player) {
                            return videoTitle;
                        }

                        @Nullable
                        @Override
                        public PendingIntent createCurrentContentIntent(Player player) {
                            Intent intent = new Intent(PlaybackService.this, PlayerActivity.class);
                            intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                            return PendingIntent.getActivity(PlaybackService.this, 0, intent, PendingIntent.FLAG_IMMUTABLE);
                        }

                        @Nullable
                        @Override
                        public CharSequence getCurrentContentText(Player player) {
                            return "Playing in background";
                        }

                        @Nullable
                        @Override
                        public Bitmap getCurrentLargeIcon(Player player, PlayerNotificationManager.BitmapCallback callback) {
                            return null;
                        }
                    })
                    .setNotificationListener(new PlayerNotificationManager.NotificationListener() {
                        @Override
                        public void onNotificationPosted(int notificationId, Notification notification, boolean ongoing) {
                            if (ongoing) {
                                try {
                                    startForeground(notificationId, notification);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                        }

                        @Override
                        public void onNotificationCancelled(int notificationId, boolean dismissedByUser) {
                            stopForeground(true);
                            stopSelf();
                        }
                    })
                    .setSmallIconResourceId(R.drawable.exo_icon_play) // নিশ্চিত করুন এই আইকনটি আছে
                    .build();

            notificationManager.setPlayer(player);
        }
    }

    public void stopBackgroundPlay() {
        if (notificationManager != null) {
            notificationManager.setPlayer(null);
            notificationManager = null;
        }
        stopForeground(true);
        stopSelf();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (player != null) {
            player.release();
            player = null;
        }
        super.onDestroy();
    }
}