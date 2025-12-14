package com.msi.videoplayer;

import android.app.Activity;
import android.app.PictureInPictureParams;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Rational;
import android.view.MotionEvent;
import android.view.PixelCopy;
import android.view.ScaleGestureDetector;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class PlayerActivity extends Activity implements SurfaceHolder.Callback, MediaPlayer.OnPreparedListener, MediaPlayer.OnCompletionListener, MediaPlayer.OnBufferingUpdateListener {

    // --- UI Variables ---
    private SurfaceView surfaceView;
    private RelativeLayout rootLayout;
    private RelativeLayout controllerRoot;
    
    // Layouts to hide/show
    private LinearLayout layoutTop, layoutBottom;

    // Top Right Icons
    private ImageView btnMute, btnScreenshot, btnPip, btnSubtitle, btnBgPlay, btnAspectRatio, btnMore;

    // Bottom Controls
    private ImageView btnLock, btnFavorite;
    private ImageView btnPrev, btnPlayPause, btnNext;
    private ImageView btnSpeed, btnRotate, btnBack;

    // Text & Seekbar
    private TextView txtTitle, txtCurrentTime, txtTotalTime, txtSubtitle;
    private SeekBar seekBar;

    // Gesture UI
    private View gestureOverlay;
    private ImageView gestureIcon;
    private TextView gestureText;

    // --- Logic Variables ---
    private MediaPlayer mediaPlayer;
    private SurfaceHolder surfaceHolder;
    private GestureController gestureController;
    private ScaleGestureDetector scaleGestureDetector;
    private DatabaseHelper dbHelper;
    private Handler handler = new Handler();

    private String videoPath, videoTitle;
    private boolean isControlShowing = true;
    private boolean isLocked = false;
    private boolean isBgPlayEnabled = false;
    private boolean isMuted = false;
    private float currentSpeed = 1.0f;
    private float mScaleFactor = 1.0f; // For Zoom

    // Data for Favorites
    private VideoModel currentVideo; 
    
    // Subtitle Data
    private List<SubtitleUtils.SubtitleItem> subtitleList = new ArrayList<>();
    private boolean hasSubtitle = false;

    // Aspect Ratio Modes
    private int currentAspectRatioMode = 0;
    private static final int MODE_FIT = 0;
    private static final int MODE_FILL = 1;
    private static final int MODE_STRETCH = 2;
    private static final int MODE_16_9 = 3;

    private Runnable updateProgressAction = new Runnable() {
        @Override
        public void run() {
            if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                long current = mediaPlayer.getCurrentPosition();
                
                // Update Subtitle
                if (hasSubtitle) {
                    boolean found = false;
                    for (SubtitleUtils.SubtitleItem item : subtitleList) {
                        if (current >= item.startTime && current <= item.endTime) {
                            txtSubtitle.setText(item.text);
                            found = true;
                            break;
                        }
                    }
                    if (!found) txtSubtitle.setText("");
                }

                txtCurrentTime.setText(TimeUtils.formatDuration(current));
                seekBar.setProgress((int) current);
                handler.postDelayed(this, 200);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_player);

        dbHelper = new DatabaseHelper(this);

        Intent intent = getIntent();
        if (intent != null) {
            videoPath = intent.getStringExtra("video_path");
            videoTitle = intent.getStringExtra("video_title");
            
            // Create a temp model for DB operations
            currentVideo = new VideoModel("0", videoPath, videoTitle, videoTitle, "", 0, "", "", "", System.currentTimeMillis());
        }

        initViews();
        setupSurface();
        setupControls();
        
        // Initialize Scale Detector for Pinch Zoom
        scaleGestureDetector = new ScaleGestureDetector(this, new ScaleListener());
    }

    private void initViews() {
        rootLayout = findViewById(R.id.controller_root); // Note: ID in XML root is controller_root, ensuring parent is captured
        surfaceView = findViewById(R.id.surface_view);
        controllerRoot = findViewById(R.id.controller_root);

        // Areas
        layoutTop = findViewById(R.id.layout_top);
        layoutBottom = findViewById(R.id.layout_bottom);

        // Top Buttons
        btnBack = findViewById(R.id.btn_back);
        btnMute = findViewById(R.id.btn_volume_mute);
        btnScreenshot = findViewById(R.id.btn_screenshot);
        btnPip = findViewById(R.id.btn_pip);
        btnSubtitle = findViewById(R.id.btn_subtitle);
        btnBgPlay = findViewById(R.id.btn_bg_play);
        btnAspectRatio = findViewById(R.id.btn_aspect_ratio);
        btnMore = findViewById(R.id.btn_more);

        // Bottom Buttons
        btnLock = findViewById(R.id.btn_lock);
        btnFavorite = findViewById(R.id.btn_favorite);
        btnPrev = findViewById(R.id.btn_prev);
        btnPlayPause = findViewById(R.id.btn_play_pause);
        btnNext = findViewById(R.id.btn_next);
        btnSpeed = findViewById(R.id.btn_speed);
        btnRotate = findViewById(R.id.btn_rotate);

        // Text & Seekbar
        txtTitle = findViewById(R.id.txt_video_title);
        txtCurrentTime = findViewById(R.id.txt_current_time);
        txtTotalTime = findViewById(R.id.txt_total_time);
        seekBar = findViewById(R.id.seekbar_video);
        txtSubtitle = findViewById(R.id.txt_subtitle);

        // Gesture Overlay
        gestureOverlay = findViewById(R.id.gesture_layout);
        gestureIcon = findViewById(R.id.img_gesture_icon);
        gestureText = findViewById(R.id.txt_gesture_text);

        txtTitle.setText(videoTitle);
        
        // Check Favorite State
        if (dbHelper.isFavorite(videoPath)) {
            btnFavorite.setImageResource(R.drawable.ic_heart_filled);
            btnFavorite.setColorFilter(0xFFFF0000); // Red
        }
    }

    private void setupSurface() {
        surfaceHolder = surfaceView.getHolder();
        surfaceHolder.addCallback(this);
    }

    private void setupControls() {
        // --- BASIC CONTROLS ---
        btnPlayPause.setOnClickListener(v -> togglePlayPause());
        btnBack.setOnClickListener(v -> finish());
        
        // --- FAVORITE LOGIC ---
        btnFavorite.setOnClickListener(v -> {
            dbHelper.toggleFavorite(currentVideo);
            if (dbHelper.isFavorite(videoPath)) {
                btnFavorite.setImageResource(R.drawable.ic_heart_filled);
                btnFavorite.setColorFilter(0xFFFF0000); // Red
                Toast.makeText(this, "Added to Favorites", Toast.LENGTH_SHORT).show();
            } else {
                btnFavorite.setImageResource(R.drawable.ic_heart_empty);
                btnFavorite.clearColorFilter();
                Toast.makeText(this, "Removed from Favorites", Toast.LENGTH_SHORT).show();
            }
        });

        // --- MUTE TOGGLE ---
        btnMute.setOnClickListener(v -> {
            if (mediaPlayer == null) return;
            isMuted = !isMuted;
            if (isMuted) {
                mediaPlayer.setVolume(0, 0);
                btnMute.setImageResource(R.drawable.ic_volume_off);
            } else {
                mediaPlayer.setVolume(1, 1);
                btnMute.setImageResource(R.drawable.ic_volume);
            }
        });

        // --- SPEED CONTROL ---
        btnSpeed.setOnClickListener(v -> showSpeedMenu(v));

        // --- PIP MODE ---
        btnPip.setOnClickListener(v -> enterPipMode());

        // --- BACKGROUND PLAY ---
        btnBgPlay.setOnClickListener(v -> {
            isBgPlayEnabled = !isBgPlayEnabled;
            if (isBgPlayEnabled) {
                btnBgPlay.setColorFilter(0xFF00E5FF); // Neon Active
                Toast.makeText(this, "Background Play ON", Toast.LENGTH_SHORT).show();
            } else {
                btnBgPlay.clearColorFilter();
                Toast.makeText(this, "Background Play OFF", Toast.LENGTH_SHORT).show();
            }
        });

        // --- SCREENSHOT ---
        btnScreenshot.setOnClickListener(v -> takeScreenshot());

        // --- SEEKBAR ---
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) txtCurrentTime.setText(TimeUtils.formatDuration(progress));
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) { handler.removeCallbacks(updateProgressAction); }
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (mediaPlayer != null) {
                    mediaPlayer.seekTo(seekBar.getProgress());
                    if (mediaPlayer.isPlaying()) handler.post(updateProgressAction);
                }
            }
        });

        // --- ASPECT RATIO ---
        btnAspectRatio.setOnClickListener(v -> toggleAspectRatio());

        // --- ROTATE ---
        btnRotate.setOnClickListener(v -> {
            int orientation = getResources().getConfiguration().orientation;
            if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            } else {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
            }
        });

        // --- LOCK MODE ---
        btnLock.setOnClickListener(v -> toggleLockMode());
        
        // --- SUBTITLE TOGGLE ---
        btnSubtitle.setOnClickListener(v -> {
            if(!hasSubtitle) {
                Toast.makeText(this, "No Subtitle Found", Toast.LENGTH_SHORT).show();
                return;
            }
            if(txtSubtitle.getVisibility() == View.VISIBLE) {
                 txtSubtitle.setVisibility(View.GONE);
                 btnSubtitle.setAlpha(0.5f);
            } else {
                 txtSubtitle.setVisibility(View.VISIBLE);
                 btnSubtitle.setAlpha(1.0f);
            }
        });
        
        // --- TOUCH & GESTURES ---
        // We use dispatchTouchEvent or a wrapper layout to handle both Pinch and Swipe
        // Using a listener on SurfaceView
        
        surfaceView.setOnTouchListener((v, event) -> {
            if (isLocked) {
                if (event.getAction() == MotionEvent.ACTION_UP) showLockUIOnly();
                return true;
            }
            
            // 1. Handle Pinch Zoom
            scaleGestureDetector.onTouchEvent(event);
            
            // 2. Handle Swipe/Tap (Only if not zooming)
            if (!scaleGestureDetector.isInProgress() && gestureController != null) {
                 gestureController.onTouch(event);
            }
            return true;
        });
    }

    // --- ZOOM LOGIC (ScaleListener) ---
    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            mScaleFactor *= detector.getScaleFactor();
            mScaleFactor = Math.max(0.25f, Math.min(mScaleFactor, 4.0f)); // Min 25%, Max 400%
            
            surfaceView.setScaleX(mScaleFactor);
            surfaceView.setScaleY(mScaleFactor);
            return true;
        }
    }

    // --- PIP MODE LOGIC ---
    private void enterPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Rational aspectRatio = new Rational(surfaceView.getWidth(), surfaceView.getHeight());
            PictureInPictureParams params = new PictureInPictureParams.Builder()
                    .setAspectRatio(aspectRatio)
                    .build();
            enterPictureInPictureMode(params);
        } else {
            Toast.makeText(this, "PiP not supported on this device", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
        if (isInPictureInPictureMode) {
            hideAllControls();
        } else {
            showAllControls();
        }
    }

    // --- SCREENSHOT LOGIC ---
    private void takeScreenshot() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                // Create a bitmap with the size of the surface view
                Bitmap bitmap = Bitmap.createBitmap(surfaceView.getWidth(), surfaceView.getHeight(), Bitmap.Config.ARGB_8888);
                
                // Use PixelCopy to capture SurfaceView content
                PixelCopy.request(surfaceView, bitmap, copyResult -> {
                    if (copyResult == PixelCopy.SUCCESS) {
                        saveBitmap(bitmap);
                    } else {
                        runOnUiThread(() -> Toast.makeText(this, "Screenshot Failed", Toast.LENGTH_SHORT).show());
                    }
                }, new Handler(Looper.getMainLooper()));
                
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
             Toast.makeText(this, "Screenshot requires Android 7.0+", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveBitmap(Bitmap bitmap) {
        String filename = "SS_" + System.currentTimeMillis() + ".jpg";
        File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "VideoPlayerPro");
        if (!dir.exists()) dir.mkdirs();
        File file = new File(dir, filename);
        
        try (FileOutputStream out = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);
            runOnUiThread(() -> Toast.makeText(this, "Saved: " + file.getAbsolutePath(), Toast.LENGTH_SHORT).show());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // --- SPEED MENU ---
    private void showSpeedMenu(View v) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            Toast.makeText(this, "Speed control requires Android 6.0+", Toast.LENGTH_SHORT).show();
            return;
        }
        
        PopupMenu popup = new PopupMenu(this, v);
        popup.getMenu().add(0, 1, 0, "0.5x");
        popup.getMenu().add(0, 2, 0, "1.0x (Normal)");
        popup.getMenu().add(0, 3, 0, "1.5x");
        popup.getMenu().add(0, 4, 0, "2.0x");
        
        popup.setOnMenuItemClickListener(item -> {
            float speed = 1.0f;
            switch (item.getItemId()) {
                case 1: speed = 0.5f; break;
                case 2: speed = 1.0f; break;
                case 3: speed = 1.5f; break;
                case 4: speed = 2.0f; break;
            }
            if (mediaPlayer != null) {
                try {
                    mediaPlayer.setPlaybackParams(mediaPlayer.getPlaybackParams().setSpeed(speed));
                    currentSpeed = speed;
                    Toast.makeText(this, "Speed: " + speed + "x", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {}
            }
            return true;
        });
        popup.show();
    }

    // --- STANDARD PLAYER LOGIC ---

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        loadSubtitles();
        try {
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDisplay(surfaceHolder);
            mediaPlayer.setDataSource(this, Uri.parse(videoPath));
            mediaPlayer.setOnPreparedListener(this);
            mediaPlayer.setOnCompletionListener(this);
            mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mediaPlayer.prepareAsync();
        } catch (IOException e) {
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    private void loadSubtitles() {
         String subPath = videoPath.substring(0, videoPath.lastIndexOf(".")) + ".srt";
         File srtFile = new File(subPath);
         if(srtFile.exists()) {
             new Thread(() -> {
                 subtitleList = SubtitleUtils.parseSrt(subPath);
                 hasSubtitle = !subtitleList.isEmpty();
                 runOnUiThread(() -> {
                     if(hasSubtitle) {
                         Toast.makeText(this, "Subtitle Detected", Toast.LENGTH_SHORT).show();
                         btnSubtitle.setAlpha(1.0f);
                     } else {
                         btnSubtitle.setAlpha(0.3f);
                     }
                 });
             }).start();
         } else {
             btnSubtitle.setAlpha(0.3f);
         }
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        seekBar.setMax(mp.getDuration());
        txtTotalTime.setText(TimeUtils.formatDuration(mp.getDuration()));
        mp.start();
        btnPlayPause.setImageResource(R.drawable.ic_pause);
        handler.post(updateProgressAction);
        adjustAspectRatio(mp.getVideoWidth(), mp.getVideoHeight());

        // Init Gestures
        gestureController = new GestureController(this, mp, gestureOverlay, gestureIcon, gestureText);
        gestureController.setOnGestureAction(this::toggleControls);
    }
    
    private void togglePlayPause() {
        if(mediaPlayer == null) return;
        if(mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            btnPlayPause.setImageResource(R.drawable.ic_play);
            handler.removeCallbacks(updateProgressAction);
        } else {
            mediaPlayer.start();
            btnPlayPause.setImageResource(R.drawable.ic_pause);
            handler.post(updateProgressAction);
        }
    }

    private void adjustAspectRatio(int videoWidth, int videoHeight) {
        if (videoWidth == 0 || videoHeight == 0) return;
        View parent = (View) surfaceView.getParent();
        int screenWidth = parent.getWidth();
        int screenHeight = parent.getHeight();
        
        android.view.ViewGroup.LayoutParams params = surfaceView.getLayoutParams();
        double videoRatio = (double) videoWidth / videoHeight;
        double screenRatio = (double) screenWidth / screenHeight;

        if (currentAspectRatioMode == MODE_16_9) videoRatio = 16.0 / 9.0;

        switch (currentAspectRatioMode) {
            case MODE_STRETCH:
                params.width = screenWidth;
                params.height = screenHeight;
                break;
            case MODE_FILL:
                if (videoRatio > screenRatio) {
                    params.height = screenHeight;
                    params.width = (int) (screenHeight * videoRatio);
                } else {
                    params.width = screenWidth;
                    params.height = (int) (screenWidth / videoRatio);
                }
                break;
            case MODE_FIT:
            case MODE_16_9:
            default:
                if (videoRatio > screenRatio) {
                    params.width = screenWidth;
                    params.height = (int) (screenWidth / videoRatio);
                } else {
                    params.width = (int) (screenHeight * videoRatio);
                    params.height = screenHeight;
                }
                break;
        }
        surfaceView.setLayoutParams(params);
    }
    
    private void toggleAspectRatio() {
        currentAspectRatioMode = (currentAspectRatioMode + 1) % 4;
        if (mediaPlayer != null) adjustAspectRatio(mediaPlayer.getVideoWidth(), mediaPlayer.getVideoHeight());
        String[] modes = {"Fit", "Fill", "Stretch", "16:9"};
        Toast.makeText(this, modes[currentAspectRatioMode], Toast.LENGTH_SHORT).show();
    }

    private void toggleLockMode() {
        isLocked = !isLocked;
        if (isLocked) {
            btnLock.setImageResource(R.drawable.ic_lock);
            btnLock.setColorFilter(0xFF00E5FF);
            hideAllControls();
            Toast.makeText(this, "Locked", Toast.LENGTH_SHORT).show();
        } else {
            btnLock.setImageResource(R.drawable.ic_unlock);
            btnLock.clearColorFilter();
            showAllControls();
            Toast.makeText(this, "Unlocked", Toast.LENGTH_SHORT).show();
        }
    }

    private void toggleControls() {
        if (isLocked) return;
        if (isControlShowing) {
            hideAllControls();
        } else {
            showAllControls();
        }
    }

    private void hideAllControls() {
        layoutTop.setVisibility(View.GONE);
        layoutBottom.setVisibility(View.GONE);
        // btnLock remains visible via manual logic or simple visibility check in showLockUI
        // Here we just hide the containers
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        isControlShowing = false;
    }

    private void showAllControls() {
        layoutTop.setVisibility(View.VISIBLE);
        layoutBottom.setVisibility(View.VISIBLE);
        btnLock.setVisibility(View.VISIBLE);
        isControlShowing = true;
    }
    
    private void showLockUIOnly() {
        if(btnLock.getVisibility() == View.VISIBLE) {
            btnLock.setVisibility(View.GONE);
        } else {
            btnLock.setVisibility(View.VISIBLE);
            handler.postDelayed(() -> { if(isLocked) btnLock.setVisibility(View.GONE); }, 2000);
        }
    }

    // --- LIFECYCLE (BACKGROUND PLAY) ---
    @Override
    protected void onPause() {
        super.onPause();
        if (isBgPlayEnabled) {
            // Do not pause player, but stop UI updates
            // handler.removeCallbacks(updateProgressAction); 
            // Optionally show notification
            Toast.makeText(this, "Playing in Background...", Toast.LENGTH_LONG).show();
        } else {
            if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                mediaPlayer.pause();
                btnPlayPause.setImageResource(R.drawable.ic_play);
            }
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        handler.removeCallbacksAndMessages(null);
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        btnPlayPause.setImageResource(R.drawable.ic_play);
        handler.removeCallbacks(updateProgressAction);
    }

    @Override
    public void onBufferingUpdate(MediaPlayer mp, int percent) {
        if (seekBar != null) seekBar.setSecondaryProgress((seekBar.getMax() * percent) / 100);
    }
    
    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}
    
    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        // If not bg play, release
        if(!isBgPlayEnabled && mediaPlayer != null) {
             mediaPlayer.release();
             mediaPlayer = null;
        }
    }
}