package com.videoplayer;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.database.Cursor;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.util.Rational;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.graphics.Bitmap;
import android.view.TextureView;
import android.os.Environment;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.List;
import java.util.Locale;

public class PlayerActivity extends AppCompatActivity {
    public static ArrayList<VideoItem> videoList;
    private int position = -1;
    private PlaybackService playbackService;
    private boolean isBound = false;
    private android.view.animation.Animation slideInTop, slideInBottom, slideOutTop, slideOutBottom;
    private DrawerLayout drawerLayout;
    private PlayerView playerView;
    private RelativeLayout controllerOverlay;
    private LinearLayout layoutTop, layoutGestureInfo;
    private RelativeLayout layoutBottom;
    private TextView tvTitle, tvCurrentTime, tvTotalTime, tvGesturePerc;
    private ImageView btnPlayPause, btnNext, btnPrev, btnLock, btnRotate, btnScale, btnBack, btnPlaylist, btnBgPlay, imgGestureIcon;
    private SeekBar seekBar;
    private RecyclerView recyclerQueue;
    private QueueAdapter queueAdapter;
    private ConstraintLayout playerRootContainer;
    private ImageView btnSpeed, btnScreenshot, btnAudioBoost;
    private int maxUnifiedVolume = 150;
    private int currentUnifiedVolume = 0;
    private android.app.Dialog brightnessDialog;
    private SeekBar brightnessSeekBar;
    private TextView brightnessText;
    private int currentBrightnessLevel = 50;
    private int startBrightnessInt = 0;
    private android.app.Dialog audioBoosterDialog;
    private SeekBar boosterSeekBar;
    private TextView boosterBubbleText;
    private ImageView boosterMuteIcon;
    private boolean isMuted = false;
    private float lastVolumeLevel = 1.0f;
    private boolean isLocked = false;
    private boolean isControlsVisible = true;
    private boolean isBgPlayEnabled = false;
    private int resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT;
    private GestureDetector gestureDetector;
    private ScaleGestureDetector scaleGestureDetector;
    private AudioManager audioManager;
    private int screenWidth, screenHeight;
    private static final int EDGE_INSET_DP = 20;
    private float edgeInsetPx;
    private static final int MODE_NONE = 0;
    private static final int MODE_SEEK = 1;
    private static final int MODE_VOLUME = 2;
    private static final int MODE_BRIGHTNESS = 3;
    private static final int MODE_ZOOM = 4;
    private int currentGestureMode = MODE_NONE;
    private int startVolume;
    private long startSeekPosition;
    private boolean isTouchInsideSafeZone = false;
    private float scaleFactor = 1.0f;
    private static final int VOLUME_DIVIDER = 60;
    private static final int BRIGHTNESS_DIVIDER = 2;
    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable hideControlsRunnable = this::hideControls;
    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            PlaybackService.LocalBinder binder = (PlaybackService.LocalBinder) service;
            playbackService = binder.getService();
            isBound = true;
            isBgPlayEnabled = playbackService.isBackgroundPlayEnabled;
            if (isBgPlayEnabled) {
                btnBgPlay.setColorFilter(ContextCompat.getColor(PlayerActivity.this, R.color.colorAccent));
            } else {
                btnBgPlay.setColorFilter(ContextCompat.getColor(PlayerActivity.this, R.color.white));
            }
            initializePlayer();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isBound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);
        hideSystemUI();
        if (getIntent().getAction() != null && getIntent().getAction().equals(Intent.ACTION_VIEW)) {
            handleIncomingIntent();
        } else {
            position = getIntent().getIntExtra("position", -1);
        }
        if (position == -1 && (videoList == null || videoList.isEmpty())) {
            finish();
            return;
        }
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        syncInitialVolume();
        syncInitialBrightness();
        screenWidth = getResources().getDisplayMetrics().widthPixels;
        screenHeight = getResources().getDisplayMetrics().heightPixels;
        initViews();
        edgeInsetPx = EDGE_INSET_DP * getResources().getDisplayMetrics().density;
        setupGestures();
        Intent intent = new Intent(this, PlaybackService.class);
        startService(intent);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    private void syncInitialVolume() {
        int max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        if (max > 0) {
            currentUnifiedVolume = (int) (((float) current / max) * 100);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent.getAction() != null && intent.getAction().equals(Intent.ACTION_VIEW)) {
            handleIncomingIntent();
            if (playbackService != null && playbackService.player != null) {
                playbackService.player.stop();
                playbackService.player.clearMediaItems();
                MediaItem mediaItem = MediaItem.fromUri(videoList.get(position).getPath());
                playbackService.player.setMediaItem(mediaItem);
                playbackService.player.prepare();
                playbackService.player.play();
                tvTitle.setText(videoList.get(position).getTitle());
                if (isBgPlayEnabled) {
                    playbackService.startBackgroundPlay(videoList.get(position).getTitle());
                }
            }
        }
    }

    private void handleIncomingIntent() {
        Uri videoUri = getIntent().getData();
        if (videoUri != null) {
            videoList = new ArrayList<>();
            String title = "External Video";
            try {
                if (videoUri.getScheme().equals("content")) {
                    Cursor cursor = getContentResolver().query(videoUri, null, null, null, null);
                    if (cursor != null && cursor.moveToFirst()) {
                        int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                        if (nameIndex != -1) title = cursor.getString(nameIndex);
                        cursor.close();
                    }
                } else {
                    title = videoUri.getLastPathSegment();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            VideoItem externalVideo = new VideoItem(
                "0", 
                title, 
                videoUri.toString(), 
                "00:00", 
                "External", 
                null, 
                "0", 
                0L
            );
            videoList.add(externalVideo);
            position = 0;
        }
    }

    private void hideSystemUI() {
        WindowInsetsControllerCompat windowInsetsController = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (windowInsetsController != null) {
            windowInsetsController.hide(WindowInsetsCompat.Type.systemBars());
            windowInsetsController.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        }
    }

    private void initViews() {
        drawerLayout = findViewById(R.id.drawer_layout);
        playerView = findViewById(R.id.player_view);
        controllerOverlay = findViewById(R.id.controller_overlay);
        layoutTop = findViewById(R.id.layout_top);
        layoutBottom = findViewById(R.id.layout_bottom);
        layoutGestureInfo = findViewById(R.id.layout_gesture_info);
        tvTitle = findViewById(R.id.tv_video_title);
        tvCurrentTime = findViewById(R.id.tv_current_time);
        tvTotalTime = findViewById(R.id.tv_total_time);
        tvGesturePerc = findViewById(R.id.tv_gesture_perc);
        btnPlayPause = findViewById(R.id.btn_play_pause);
        btnNext = findViewById(R.id.btn_next);
        btnPrev = findViewById(R.id.btn_prev);
        btnLock = findViewById(R.id.btn_lock);
        btnRotate = findViewById(R.id.btn_rotate);
        btnScale = findViewById(R.id.btn_scale);
        btnBack = findViewById(R.id.btn_back);
        btnPlaylist = findViewById(R.id.btn_playlist);
        btnBgPlay = findViewById(R.id.btn_bg_play);
        imgGestureIcon = findViewById(R.id.img_gesture_icon);
        btnSpeed = findViewById(R.id.btn_speed);
        btnScreenshot = findViewById(R.id.btn_screenshot);
        btnAudioBoost = findViewById(R.id.btn_audio_boost);
        seekBar = findViewById(R.id.seekbar);
        recyclerQueue = findViewById(R.id.recycler_queue);
        playerRootContainer = findViewById(R.id.player_root_container);
        slideInTop = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.slide_in_top);
        slideInBottom = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.slide_in_bottom);
        slideOutTop = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.slide_out_top);
        slideOutBottom = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.slide_out_bottom);
        initListeners();
    }

    private void initializePlayer() {
        if (playbackService == null) return;
        playerView.setPlayer(playbackService.player);
        resetResizeMode();
        if (position != -1) {
            boolean isSameVideo = false;
            if (playbackService.player.getMediaItemCount() > 0) {
                MediaItem currentItem = playbackService.player.getCurrentMediaItem();
                if (currentItem != null && currentItem.mediaId.equals(videoList.get(position).getPath())) {
                    isSameVideo = true;
                }
            }
            if (!isSameVideo) {
                playbackService.player.stop();
                playbackService.player.clearMediaItems();
                List<MediaItem> mediaItems = new ArrayList<>();
                for (VideoItem video : videoList) {
                    MediaItem item = new MediaItem.Builder()
                            .setUri(video.getPath())
                            .setMediaId(video.getPath())
                            .setMediaMetadata(new androidx.media3.common.MediaMetadata.Builder()
                                    .setTitle(video.getTitle())
                                    .build())
                            .build();
                    mediaItems.add(item);
                }
                playbackService.player.setMediaItems(mediaItems, position, 0);
                playbackService.player.prepare();
                playbackService.player.play();
            }
            tvTitle.setText(videoList.get(position).getTitle());
        } else {
            if (playbackService.player.getCurrentMediaItem() != null) {
                tvTitle.setText(playbackService.player.getCurrentMediaItem().mediaMetadata.title);
            }
        }
        setupSideBar();
        playbackService.player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_READY) {
                    updatePlayerUI();
                }
            }

            @Override
            public void onIsPlayingChanged(boolean playing) {
                btnPlayPause.setImageResource(playing ? R.drawable.exo_icon_pause : R.drawable.exo_icon_play);
                if (playing) startProgressUpdater();
                else handler.removeCallbacks(updateProgressRunnable);
            }

            @Override
            public void onMediaItemTransition(androidx.media3.common.MediaItem mediaItem, int reason) {
                if (mediaItem != null && mediaItem.mediaMetadata.title != null) {
                    tvTitle.setText(mediaItem.mediaMetadata.title);
                    resetResizeMode();
                    if (videoList != null) {
                        for (int i = 0; i < videoList.size(); i++) {
                            if (videoList.get(i).getPath().equals(mediaItem.mediaId)) {
                                position = i;
                                break;
                            }
                        }
                    }
                    if (queueAdapter != null) {
                        queueAdapter.updateCurrentPosition(position);
                        if (recyclerQueue != null) {
                            recyclerQueue.scrollToPosition(position);
                        }
                    }
                }
            }
        });
        updatePlayerUI();
        if (playbackService.player.isPlaying()) {
            btnPlayPause.setImageResource(R.drawable.exo_icon_pause);
            startProgressUpdater();
        } else {
            btnPlayPause.setImageResource(R.drawable.exo_icon_play);
        }
    }

    private void updatePlayerUI() {
        if (playbackService != null && playbackService.player != null) {
            long duration = playbackService.player.getDuration();
            long current = playbackService.player.getCurrentPosition();
            if (duration > 0 && duration != androidx.media3.common.C.TIME_UNSET) {
                tvTotalTime.setText(formatTime(duration));
                seekBar.setMax((int) duration);
            }
            tvCurrentTime.setText(formatTime(current));
            seekBar.setProgress((int) current);
        }
    }

    private void setupSideBar() {
        recyclerQueue.setLayoutManager(new LinearLayoutManager(this));
        queueAdapter = new QueueAdapter(this, videoList, pos -> {
            if (playbackService != null && playbackService.player != null) {
                playbackService.player.seekTo(pos, 0);
                playbackService.player.play();
                position = pos;
                tvTitle.setText(videoList.get(position).getTitle());
                queueAdapter.updateCurrentPosition(pos);
            }
            drawerLayout.closeDrawer(GravityCompat.END);
        });
        recyclerQueue.setAdapter(queueAdapter);
        if (position != -1) {
            queueAdapter.updateCurrentPosition(position);
            recyclerQueue.scrollToPosition(position);
        }
    }

    private void initListeners() {
        btnBack.setOnClickListener(v -> onBackPressed());
        btnAudioBoost.setOnClickListener(v -> showAudioBoosterDialog(false));
        btnScale.setOnClickListener(v -> toggleResizeMode());
        btnPlayPause.setOnClickListener(v -> {
            if (playbackService.player.isPlaying()) playbackService.player.pause();
            else playbackService.player.play();
        });
        btnNext.setOnClickListener(v -> playNext());
        btnPrev.setOnClickListener(v -> playPrev());
        btnSpeed.setOnClickListener(v -> showSpeedDialog());
        btnScreenshot.setOnClickListener(v -> takeScreenshot());
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    playbackService.player.seekTo(progress);
                    tvCurrentTime.setText(formatTime(progress));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        btnLock.setOnClickListener(v -> toggleLock());
        btnRotate.setOnClickListener(v -> {
            int orientation = getResources().getConfiguration().orientation;
            setRequestedOrientation(orientation == Configuration.ORIENTATION_PORTRAIT ?
                    ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        });
        btnPlaylist.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.END));
        btnBgPlay.setOnClickListener(v -> toggleBackgroundPlay());
    }

    private void setupGestures() {
        scaleGestureDetector = new ScaleGestureDetector(this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                currentGestureMode = MODE_ZOOM;
                scaleFactor *= detector.getScaleFactor();
                scaleFactor = Math.max(0.25f, Math.min(scaleFactor, 4.0f));
                playerView.setScaleX(scaleFactor);
                playerView.setScaleY(scaleFactor);
                int zoomPercent = (int) (scaleFactor * 100);
                showGestureInfo(R.drawable.exo_icon_fullscreen_enter, zoomPercent + "%");
                return true;
            }

            @Override
            public boolean onScaleBegin(ScaleGestureDetector detector) {
                currentGestureMode = MODE_ZOOM;
                return super.onScaleBegin(detector);
            }
        });
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                if (isControlsVisible) {
                    hideControls();
                } else {
                    showControls();
                }
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                if (isLocked) return false;
                int currentWidth = playerRootContainer.getWidth();
                if (e.getX() > currentWidth / 2) {
                    playbackService.player.seekTo(playbackService.player.getCurrentPosition() + 10000);
                    showGestureInfo(R.drawable.exo_ic_forward, "+10s");
                } else {
                    playbackService.player.seekTo(playbackService.player.getCurrentPosition() - 10000);
                    showGestureInfo(R.drawable.exo_ic_rewind, "-10s");
                }
                return true;
            }

            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                if (isLocked) return false;
                if (!isTouchInsideSafeZone) return false;
                if (e2.getPointerCount() > 1 || currentGestureMode == MODE_ZOOM) return false;
                int currentWidth = playerRootContainer.getWidth();
                int currentHeight = playerRootContainer.getHeight();
                float totalDeltaX = e2.getX() - e1.getX();
                float totalDeltaY = e1.getY() - e2.getY();
                if (currentGestureMode == MODE_NONE) {
                    if (Math.abs(totalDeltaX) > Math.abs(totalDeltaY)) {
                        currentGestureMode = MODE_SEEK;
                    } else {
                        if (e1.getX() > currentWidth / 2) {
                            currentGestureMode = MODE_VOLUME;
                        } else {
                            currentGestureMode = MODE_BRIGHTNESS;
                        }
                    }
                }
                switch (currentGestureMode) {
                    case MODE_SEEK:
                        long maxSeekPerScreen = 60000;
                        float percentMovedX = totalDeltaX / currentWidth;
                        long seekChange = (long) (percentMovedX * maxSeekPerScreen);
                        long targetPos = startSeekPosition + seekChange;
                        long duration = playbackService.player.getDuration();
                        targetPos = Math.max(0, Math.min(targetPos, duration));
                        playbackService.player.seekTo(targetPos);
                        int icon = totalDeltaX > 0 ? R.drawable.exo_icon_fastforward : R.drawable.exo_icon_rewind;
                        String timeDiff = (seekChange > 0 ? "+" : "") + (seekChange / 1000) + "s";
                        showGestureInfo(icon, formatTime(targetPos) + " (" + timeDiff + ")");
                        break;
                    case MODE_VOLUME:
                        float percentMovedVol = totalDeltaY / currentHeight;
                        int deltaVol = (int) (maxUnifiedVolume * percentMovedVol);
                        int newVol = startVolume + deltaVol;
                        setUnifiedVolume(newVol, true, true);
                        break;
                    case MODE_BRIGHTNESS:
                        float percentMovedBright = totalDeltaY / currentHeight;
                        int deltaBright = (int) (100 * percentMovedBright * 1.5f);
                        int newBright = startBrightnessInt + deltaBright;
                        setBrightness(newBright);
                        break;
                }
                return true;
            }
        });
        playerRootContainer.setOnTouchListener((v, event) -> {
            scaleGestureDetector.onTouchEvent(event);
            boolean isZooming = scaleGestureDetector.isInProgress();
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                currentGestureMode = MODE_NONE;
                float x = event.getX();
                float y = event.getY();
                int currentWidth = v.getWidth();
                int currentHeight = v.getHeight();
                if (x > edgeInsetPx && x < (currentWidth - edgeInsetPx) &&
                        y > edgeInsetPx && y < (currentHeight - edgeInsetPx)) {
                    isTouchInsideSafeZone = true;
                    startVolume = currentUnifiedVolume;
                    startBrightnessInt = currentBrightnessLevel;
                    if (playbackService != null && playbackService.player != null) {
                        startSeekPosition = playbackService.player.getCurrentPosition();
                    }
                } else {
                    isTouchInsideSafeZone = false;
                }
            }
            if (!isZooming && event.getPointerCount() == 1) {
                gestureDetector.onTouchEvent(event);
            }
            if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                layoutGestureInfo.setVisibility(View.GONE);
                isTouchInsideSafeZone = false;
                if (currentGestureMode == MODE_VOLUME && audioBoosterDialog != null) {
                    audioBoosterDialog.dismiss();
                }
                if (currentGestureMode == MODE_BRIGHTNESS && brightnessDialog != null) {
                    brightnessDialog.dismiss();
                }
                currentGestureMode = MODE_NONE;
            }
            return true;
        });
    }

    private void adjustVolume(float deltaY) {
        int maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        float percentChange = (deltaY / screenHeight) * 2.0f;
        float volChange = maxVol * percentChange;
        int newVol = currentVol + (int) volChange;
        newVol = Math.max(0, Math.min(newVol, maxVol));
        if (newVol != currentVol) {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0);
            int perc = (int) (((float) newVol / maxVol) * 100);
            showGestureInfo(R.drawable.ic_volume, perc + "%");
        }
    }

    private void adjustBrightness(float deltaY) {
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        float change = (deltaY / screenHeight) * 2.0f;
        float newBrightness = lp.screenBrightness + change;
        newBrightness = Math.max(0.01f, Math.min(newBrightness, 1.0f));
        lp.screenBrightness = newBrightness;
        getWindow().setAttributes(lp);
        int perc = (int) (newBrightness * 100);
        showGestureInfo(R.drawable.ic_brightness, perc + "%");
    }

    private void showGestureInfo(int iconRes, String text) {
        layoutGestureInfo.setVisibility(View.VISIBLE);
        imgGestureIcon.setImageResource(iconRes);
        tvGesturePerc.setText(text);
    }

    private void toggleBackgroundPlay() {
        if (!isBgPlayEnabled) {
            if (Build.VERSION.SDK_INT >= 33) {
                if (ContextCompat.checkSelfPermission(this, "android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this, new String[]{"android.permission.POST_NOTIFICATIONS"}, 102);
                    return;
                }
            }
            enableBgPlay();
        } else {
            disableBgPlay();
        }
    }

    private void takeScreenshot() {
        View videoSurfaceView = playerView.getVideoSurfaceView();
        if (videoSurfaceView instanceof TextureView) {
            Bitmap bitmap = ((TextureView) videoSurfaceView).getBitmap();
            if (bitmap != null) {
                saveBitmap(bitmap);
            } else {
                Toast.makeText(this, "Failed to capture screenshot", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "Screenshot not supported in SurfaceView mode", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveBitmap(Bitmap bitmap) {
        String videoName = "Unknown";
        String time = "00-00";
        if (videoList != null && position != -1) {
            videoName = videoList.get(position).getTitle().replace(" ", "_");
        }
        if (playbackService != null) {
            long currentPos = playbackService.player.getCurrentPosition();
            long minutes = (currentPos / 1000) / 60;
            long seconds = (currentPos / 1000) % 60;
            time = String.format(Locale.getDefault(), "%02d-%02d", minutes, seconds);
        }
        String fileName = videoName + "_" + time + ".jpg";
        File storageDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "VideoPlayer");
        if (!storageDir.exists()) storageDir.mkdirs();
        File imageFile = new File(storageDir, fileName);
        try {
            FileOutputStream fos = new FileOutputStream(imageFile);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos);
            fos.flush();
            fos.close();
            sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(imageFile)));
            Toast.makeText(this, "Screenshot Saved to Gallery", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Error Saving: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void enableBgPlay() {
        isBgPlayEnabled = true;
        btnBgPlay.setColorFilter(ContextCompat.getColor(this, R.color.colorAccent));
        if (playbackService != null) {
            playbackService.isBackgroundPlayEnabled = true;
            String videoTitle = "Unknown Video";
            if (position != -1 && videoList != null && position < videoList.size()) {
                videoTitle = videoList.get(position).getTitle();
            } else if (playbackService.player.getCurrentMediaItem() != null
                    && playbackService.player.getCurrentMediaItem().mediaMetadata.title != null) {
                videoTitle = playbackService.player.getCurrentMediaItem().mediaMetadata.title.toString();
            }
            playbackService.startBackgroundPlay(videoTitle);
        }
        Toast.makeText(this, "Background Play ON", Toast.LENGTH_SHORT).show();
    }

    private void disableBgPlay() {
        isBgPlayEnabled = false;
        btnBgPlay.setColorFilter(ContextCompat.getColor(this, R.color.white));
        if (playbackService != null) {
            playbackService.isBackgroundPlayEnabled = false;
            playbackService.stopBackgroundPlay();
        }
        Toast.makeText(this, "Background Play OFF", Toast.LENGTH_SHORT).show();
    }

    private void playNext() {
        if (position < videoList.size() - 1) changeVideo(position + 1);
    }

    private void playPrev() {
        if (position > 0) changeVideo(position - 1);
    }

    private void changeVideo(int newPos) {
        position = newPos;
        MediaItem mediaItem = MediaItem.fromUri(videoList.get(position).getPath());
        playbackService.player.setMediaItem(mediaItem);
        playbackService.player.prepare();
        playbackService.player.play();
        tvTitle.setText(videoList.get(position).getTitle());
    }

    private void toggleLock() {
        isLocked = !isLocked;
        if (isLocked) {
            btnLock.setImageResource(R.drawable.ic_lock);
            btnLock.setColorFilter(ContextCompat.getColor(this, R.color.colorAccent));
            Toast.makeText(this, "Locked", Toast.LENGTH_SHORT).show();
            hideControls();
        } else {
            btnLock.setImageResource(R.drawable.ic_unlock);
            btnLock.setColorFilter(ContextCompat.getColor(this, R.color.white));
            Toast.makeText(this, "Unlocked", Toast.LENGTH_SHORT).show();
            showControls();
        }
    }

    private void showControls() {
        if (isLocked) {
            layoutTop.setVisibility(View.GONE);
            btnRotate.setVisibility(View.GONE);
            layoutBottom.setVisibility(View.VISIBLE);
            layoutBottom.startAnimation(slideInBottom);
            findViewById(R.id.layout_seekbar).setVisibility(View.GONE);
            findViewById(R.id.btn_prev).setVisibility(View.GONE);
            findViewById(R.id.btn_play_pause).setVisibility(View.GONE);
            findViewById(R.id.btn_next).setVisibility(View.GONE);
            findViewById(R.id.btn_scale).setVisibility(View.GONE);
            btnLock.setVisibility(View.VISIBLE);
        } else {
            layoutTop.setVisibility(View.VISIBLE);
            layoutBottom.setVisibility(View.VISIBLE);
            btnRotate.setVisibility(View.VISIBLE);
            findViewById(R.id.layout_seekbar).setVisibility(View.VISIBLE);
            findViewById(R.id.btn_prev).setVisibility(View.VISIBLE);
            findViewById(R.id.btn_play_pause).setVisibility(View.VISIBLE);
            findViewById(R.id.btn_next).setVisibility(View.VISIBLE);
            findViewById(R.id.btn_scale).setVisibility(View.VISIBLE);
            btnLock.setVisibility(View.VISIBLE);
            layoutTop.startAnimation(slideInTop);
            layoutBottom.startAnimation(slideInBottom);
        }
        isControlsVisible = true;
        hideSystemUI();
        handler.removeCallbacks(hideControlsRunnable);
        handler.postDelayed(hideControlsRunnable, 4000);
    }

    private void hideControls() {
        if (isControlsVisible) {
            if (isLocked) {
                layoutBottom.startAnimation(slideOutBottom);
                layoutBottom.setVisibility(View.GONE);
                layoutTop.setVisibility(View.GONE);
                btnRotate.setVisibility(View.GONE);
            } else {
                layoutTop.startAnimation(slideOutTop);
                layoutBottom.startAnimation(slideOutBottom);
                layoutTop.setVisibility(View.GONE);
                layoutBottom.setVisibility(View.GONE);
                btnRotate.setVisibility(View.GONE);
            }
            isControlsVisible = false;
            hideSystemUI();
        }
    }

    private Runnable updateProgressRunnable = new Runnable() {
        @Override
        public void run() {
            if (playbackService != null && playbackService.player.isPlaying()) {
                long current = playbackService.player.getCurrentPosition();
                tvCurrentTime.setText(formatTime(current));
                seekBar.setProgress((int) current);
            }
            handler.postDelayed(this, 1000);
        }
    };

    private void showSpeedDialog() {
        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_speed_sidebar);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
            WindowManager.LayoutParams params = dialog.getWindow().getAttributes();
            params.gravity = android.view.Gravity.END;
            params.width = WindowManager.LayoutParams.WRAP_CONTENT;
            params.height = WindowManager.LayoutParams.MATCH_PARENT;
            params.windowAnimations = android.R.style.Animation_Dialog;
            dialog.getWindow().setAttributes(params);
        }
        LinearLayout container = dialog.findViewById(R.id.speed_container);
        String[] speeds = {"0.5x", "0.75x", "1.0x", "1.25x", "1.5x", "2.0x"};
        float[] values = {0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f};
        float currentSpeed = playbackService.player.getPlaybackParameters().speed;
        for (int i = 0; i < speeds.length; i++) {
            TextView textView = new TextView(this);
            textView.setText(speeds[i]);
            textView.setTextSize(14);
            textView.setGravity(android.view.Gravity.CENTER);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(0, 20, 0, 20);
            params.gravity = android.view.Gravity.CENTER_HORIZONTAL;
            textView.setLayoutParams(params);
            textView.setPadding(40, 15, 40, 15);
            final int index = i;
            if (Math.abs(currentSpeed - values[i]) < 0.01) {
                textView.setTextColor(ContextCompat.getColor(this, R.color.white));
                textView.setTypeface(null, android.graphics.Typeface.BOLD);
                textView.setBackgroundResource(R.drawable.bg_speed_selected);
                textView.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.colorAccent)));
            } else {
                textView.setTextColor(android.graphics.Color.LTGRAY);
                textView.setBackground(null);
            }
            textView.setOnClickListener(v -> {
                if (playbackService != null && playbackService.player != null) {
                    playbackService.player.setPlaybackParameters(new androidx.media3.common.PlaybackParameters(values[index]));
                }
                dialog.dismiss();
            });
            container.addView(textView);
        }
        dialog.show();
    }

    private void showAudioBoosterDialog(boolean onLeft) {
        if (playbackService == null || playbackService.player == null) return;
        if (audioBoosterDialog == null) {
            audioBoosterDialog = new android.app.Dialog(this);
            audioBoosterDialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
            audioBoosterDialog.setContentView(R.layout.dialog_audio_booster);
            boosterSeekBar = audioBoosterDialog.findViewById(R.id.seekbar_boost);
            boosterBubbleText = audioBoosterDialog.findViewById(R.id.tv_bubble_value);
            boosterMuteIcon = audioBoosterDialog.findViewById(R.id.btn_mute);
            setupDialogListeners();
            audioBoosterDialog.setOnKeyListener((dialog, keyCode, event) -> {
                if (keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP || keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN) {
                    if (event.getAction() == android.view.KeyEvent.ACTION_DOWN) {
                        int step = 5;
                        int newVol;
                        if (keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP) {
                            newVol = currentUnifiedVolume + step;
                        } else {
                            newVol = currentUnifiedVolume - step;
                        }
                        setUnifiedVolume(newVol, true, false);
                    }
                    return true;
                }
                return false;
            });
        }
        if (audioBoosterDialog.getWindow() != null) {
            audioBoosterDialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
            WindowManager.LayoutParams params = audioBoosterDialog.getWindow().getAttributes();
            params.gravity = android.view.Gravity.CENTER_VERTICAL | (onLeft ? android.view.Gravity.START : android.view.Gravity.END);
            params.x = 30;
            params.flags &= ~WindowManager.LayoutParams.FLAG_DIM_BEHIND;
            params.windowAnimations = 0;
            audioBoosterDialog.getWindow().setAttributes(params);
        }
        updateDialogUI(currentUnifiedVolume);
        if (!audioBoosterDialog.isShowing()) {
            audioBoosterDialog.show();
        }
    }

    private void updateDialogUI(int volume) {
        if (boosterSeekBar != null) boosterSeekBar.setMax(maxUnifiedVolume);
        if (boosterSeekBar != null) boosterSeekBar.setProgress(volume);
        if (boosterBubbleText != null) boosterBubbleText.setText(String.valueOf(volume));
        if (boosterMuteIcon != null) {
            if (volume == 0) boosterMuteIcon.setImageResource(R.drawable.ic_mute);
            else boosterMuteIcon.setImageResource(R.drawable.ic_audio_boost);
        }
    }

    private void setupDialogListeners() {
        if (boosterSeekBar == null) return;
        boosterSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    setUnifiedVolume(progress, false, false);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        if (boosterMuteIcon != null) {
            boosterMuteIcon.setOnClickListener(v -> {
                if (currentUnifiedVolume > 0) {
                    lastVolumeLevel = currentUnifiedVolume;
                    setUnifiedVolume(0, true, false);
                } else {
                    setUnifiedVolume((int) lastVolumeLevel, true, false);
                }
            });
        }
    }

    private void startProgressUpdater() {
        handler.removeCallbacks(updateProgressRunnable);
        handler.post(updateProgressRunnable);
    }

    private String formatTime(long millis) {
        StringBuilder sb = new StringBuilder();
        Formatter formatter = new Formatter(sb, Locale.getDefault());
        long totalSeconds = millis / 1000;
        long seconds = totalSeconds % 60;
        long minutes = (totalSeconds / 60) % 60;
        long hours = totalSeconds / 3600;
        sb.setLength(0);
        if (hours > 0) return formatter.format("%d:%02d:%02d", hours, minutes, seconds).toString();
        else return formatter.format("%02d:%02d", minutes, seconds).toString();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 102 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            enableBgPlay();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUI();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (!isBgPlayEnabled && playbackService != null && playbackService.player.isPlaying()) {
            playbackService.player.pause();
        }
    }

    @Override
    public void onBackPressed() {
        if (isBgPlayEnabled && playbackService != null && playbackService.player.isPlaying()) {
            super.onBackPressed();
        } else {
            if (playbackService != null) {
                playbackService.stopBackgroundPlay();
                playbackService.isBackgroundPlayEnabled = false;
            }
            Intent intent = new Intent(this, PlaybackService.class);
            stopService(intent);
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isBound && playbackService != null) {
            unbindService(serviceConnection);
            isBound = false;
        }
        boolean isPlaying = (playbackService != null && playbackService.player != null && playbackService.player.isPlaying());
        if (!isBgPlayEnabled || !isPlaying) {
            Intent intent = new Intent(this, PlaybackService.class);
            stopService(intent);
        }
        handler.removeCallbacks(updateProgressRunnable);
    }

    private void setUnifiedVolume(int targetVolume, boolean showDialog, boolean isGesture) {
        targetVolume = Math.max(0, Math.min(targetVolume, maxUnifiedVolume));
        currentUnifiedVolume = targetVolume;
        int maxSystemVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        if (targetVolume <= 100) {
            int sysVol = (int) ((targetVolume / 100.0f) * maxSystemVol);
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, sysVol, 0);
            if (playbackService != null && playbackService.player != null) {
                playbackService.player.setVolume(1.0f);
            }
        } else {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxSystemVol, 0);
            float boostVol = targetVolume / 100.0f;
            if (playbackService != null && playbackService.player != null) {
                playbackService.player.setVolume(boostVol);
            }
        }
        if (showDialog || isGesture) {
            showAudioBoosterDialog(isGesture);
        }
        if (audioBoosterDialog != null && audioBoosterDialog.isShowing()) {
            updateDialogUI(targetVolume);
        }
    }

    @Override
    public boolean dispatchKeyEvent(android.view.KeyEvent event) {
        int keyCode = event.getKeyCode();
        int action = event.getAction();
        if (keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP || keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (action == android.view.KeyEvent.ACTION_DOWN) {
                int step = 5;
                int newVol;
                if (keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP) {
                    newVol = currentUnifiedVolume + step;
                } else {
                    newVol = currentUnifiedVolume - step;
                }
                setUnifiedVolume(newVol, true, false);
            }
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    private void syncInitialBrightness() {
        try {
            int sysBright = android.provider.Settings.System.getInt(
                    getContentResolver(),
                    android.provider.Settings.System.SCREEN_BRIGHTNESS);
            currentBrightnessLevel = (int) ((sysBright / 255.0f) * 100);
        } catch (Exception e) {
            currentBrightnessLevel = 50;
        }
    }

    private void setBrightness(int value) {
        value = Math.max(0, Math.min(value, 100));
        currentBrightnessLevel = value;
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        if (value <= 0) lp.screenBrightness = 0.01f;
        else lp.screenBrightness = value / 100.0f;
        getWindow().setAttributes(lp);
        if (brightnessDialog == null || !brightnessDialog.isShowing()) {
            showBrightnessDialog();
        }
        if (brightnessSeekBar != null) brightnessSeekBar.setProgress(value);
        if (brightnessText != null) brightnessText.setText(String.valueOf(value));
    }

    private void showBrightnessDialog() {
        if (brightnessDialog == null) {
            brightnessDialog = new android.app.Dialog(this);
            brightnessDialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
            brightnessDialog.setContentView(R.layout.dialog_brightness_control);
            brightnessSeekBar = brightnessDialog.findViewById(R.id.seekbar_brightness);
            brightnessText = brightnessDialog.findViewById(R.id.tv_brightness_value);
            brightnessSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser) setBrightness(progress);
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                }
            });
        }
        if (brightnessDialog.getWindow() != null) {
            brightnessDialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
            WindowManager.LayoutParams params = brightnessDialog.getWindow().getAttributes();
            params.gravity = android.view.Gravity.CENTER_VERTICAL | android.view.Gravity.END;
            params.x = 30;
            params.flags &= ~WindowManager.LayoutParams.FLAG_DIM_BEHIND;
            params.windowAnimations = 0;
            brightnessDialog.getWindow().setAttributes(params);
        }
        if (!brightnessDialog.isShowing()) brightnessDialog.show();
    }

    private void toggleResizeMode() {
        if (resizeMode == AspectRatioFrameLayout.RESIZE_MODE_FIT) {
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM;
            playerView.setResizeMode(resizeMode);
            btnScale.setImageResource(R.drawable.ic_scale_fill);
            Toast.makeText(this, "Zoom / Fill", Toast.LENGTH_SHORT).show();
        } else if (resizeMode == AspectRatioFrameLayout.RESIZE_MODE_ZOOM) {
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL;
            playerView.setResizeMode(resizeMode);
            btnScale.setImageResource(R.drawable.ic_scale_strech);
            Toast.makeText(this, "Stretch", Toast.LENGTH_SHORT).show();
        } else {
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT;
            playerView.setResizeMode(resizeMode);
            btnScale.setImageResource(R.drawable.ic_scale_fit);
            Toast.makeText(this, "Fit to Screen", Toast.LENGTH_SHORT).show();
        }
    }

    private void resetResizeMode() {
        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT;
        playerView.setResizeMode(resizeMode);
        btnScale.setImageResource(R.drawable.ic_scale_fit);
    }
}