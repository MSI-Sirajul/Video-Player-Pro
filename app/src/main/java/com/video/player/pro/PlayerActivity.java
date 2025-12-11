package com.video.player.pro;

import android.app.AlertDialog;
import android.app.PictureInPictureParams;
import android.content.ContentValues;
import android.content.Context;
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
import android.provider.MediaStore;
import android.util.Rational;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.PixelCopy;
import android.view.ScaleGestureDetector;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;

import java.io.OutputStream;
import java.util.Locale;

public class PlayerActivity extends AppCompatActivity {

    // Views
    private VideoView videoView;
    private FrameLayout videoContainer;
    private ConstraintLayout controlsLayout;
    
    // Gesture Feedback
    private LinearLayout gestureLayout;
    private TextView gestureText;
    private android.widget.ImageView gestureIcon;
    
    // Buttons
    private ImageButton btnBack, btnScreenshot, btnPip, btnScale, btnMute;
    private ImageButton btnPlayPause, btnPrev, btnNext, btnLock, btnRotate, btnUnlockOverlay;
    private TextView btnSpeed;
    private TextView txtTitle, txtCurrent, txtTotal;
    private SeekBar seekBar;

    // Logic Variables
    private MediaPlayer mediaPlayer;
    private boolean isPlaying = true;
    private boolean isLandscape = false;
    private boolean isLocked = false; // Track Lock State
    private boolean isMuted = false;
    private int screenMode = 0; 
    
    private Handler handler = new Handler();
    private Runnable updateSeekBarRunnable;
    private Handler hideHandler = new Handler();
    private Runnable hideRunnable;

    // Gestures
    private ScaleGestureDetector scaleGestureDetector;
    private GestureDetector gestureDetector;
    private float mScaleFactor = 1.0f;
    private AudioManager audioManager;
    private int deviceWidth;
    private int deviceHeight;
    private int seekModeStartPos = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_player);

        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        deviceWidth = getResources().getDisplayMetrics().widthPixels;
        deviceHeight = getResources().getDisplayMetrics().heightPixels;

        initializeViews();
        setupGestures();
        setupListeners();
        
        // Load Video
        String uriString = getIntent().getStringExtra("videoUri");
        String title = getIntent().getStringExtra("videoTitle");
        if (uriString != null) playVideo(Uri.parse(uriString), title);
    }

    private void initializeViews() {
        videoContainer = findViewById(R.id.videoContainer);
        videoView = findViewById(R.id.videoView);
        controlsLayout = findViewById(R.id.controlsLayout);
        
        gestureLayout = findViewById(R.id.gestureLayout);
        gestureText = findViewById(R.id.gestureText);
        gestureIcon = findViewById(R.id.gestureIcon);
        
        // Buttons
        btnBack = findViewById(R.id.btnBack);
        btnScreenshot = findViewById(R.id.btnScreenshot);
        btnPip = findViewById(R.id.btnPip);
        btnScale = findViewById(R.id.btnScale);
        btnMute = findViewById(R.id.btnMute);
        
        btnPlayPause = findViewById(R.id.btnPlayPause);
        btnPrev = findViewById(R.id.btnPrev);
        btnNext = findViewById(R.id.btnNext);
        
        btnLock = findViewById(R.id.btnLock); // The lock icon in control bar
        btnUnlockOverlay = findViewById(R.id.btnUnlockOverlay); // The floating unlock icon
        
        btnSpeed = findViewById(R.id.btnSpeed);
        btnRotate = findViewById(R.id.btnRotate);
        
        txtTitle = findViewById(R.id.txtVideoTitle);
        txtCurrent = findViewById(R.id.txtCurrentTime);
        txtTotal = findViewById(R.id.txtTotalTime);
        seekBar = findViewById(R.id.seekBar);

        // --- UPDATED HIDE RUNNABLE ---
        hideRunnable = () -> {
            // ১. মেইন কন্ট্রোল প্যানেল হাইড হবে
            controlsLayout.setVisibility(View.GONE);
            
            // ২. যদি লক করা থাকে, তবে ফ্লোটিং আনলক বাটনটিও হাইড হবে (ক্লিন ভিউ এর জন্য)
            // টাচ করলে এটি আবার আসবে
            if (isLocked) {
                btnUnlockOverlay.setVisibility(View.GONE);
            }
        };
    }

    private void playVideo(Uri uri, String title) {
        txtTitle.setText(title);
        videoView.setVideoURI(uri);
        
        // রিসিভ করুন
        int startPos = getIntent().getIntExtra("startPosition", 0);
        
        videoView.setOnPreparedListener(mp -> {
            this.mediaPlayer = mp;
            resizeVideo();
            seekBar.setMax(videoView.getDuration());
            txtTotal.setText(formatTime(videoView.getDuration()));
            
            // সিক করুন (যদি পজিশন থাকে)
            if (startPos > 0) {
                videoView.seekTo(startPos);
                // পজিশন একবার ব্যবহারের পর রিসেট করতে পারেন, তবে ইন্টেন্ট রি-ইউজ না করলে দরকার নেই
            }
            
            videoView.start();
            isPlaying = true;
            btnPlayPause.setImageResource(R.drawable.ic_pause_player);
            
            updateSeekBar();
            autoHideControls();
        });

        videoView.setOnCompletionListener(mp -> playNextVideo());
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> finish());
        
        // Top Actions
        btnScreenshot.setOnClickListener(v -> takeScreenshot());
        btnPip.setOnClickListener(v -> enterPipMode());
        btnScale.setOnClickListener(v -> toggleScale());
        btnMute.setOnClickListener(v -> toggleMute());

        // Center Actions
        btnPlayPause.setOnClickListener(v -> togglePlayPause());
        btnNext.setOnClickListener(v -> playNextVideo());
        btnPrev.setOnClickListener(v -> playPrevVideo());
        
        // Bottom Actions
        btnLock.setOnClickListener(v -> toggleLockMode());     // To Lock
        btnUnlockOverlay.setOnClickListener(v -> toggleLockMode()); // To Unlock
        
        btnRotate.setOnClickListener(v -> toggleOrientation());
        btnSpeed.setOnClickListener(v -> showSpeedDialog());

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    videoView.seekTo(progress);
                    txtCurrent.setText(formatTime(progress));
                }
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) { handler.removeCallbacks(hideRunnable); }
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) { if(isPlaying) autoHideControls(); }
        });
    }

    // --- LOCK / UNLOCK LOGIC ---
    private void toggleLockMode() {
        isLocked = !isLocked;
        if (isLocked) {
            // Lock Activated
            controlsLayout.setVisibility(View.GONE); // Hide main controls
            btnUnlockOverlay.setVisibility(View.VISIBLE); // Show floating unlock button
            Toast.makeText(this, "Screen Locked", Toast.LENGTH_SHORT).show();
            
            // Hide the unlock button after 3 seconds
            handler.removeCallbacks(hideRunnable);
            hideHandler.postDelayed(() -> btnUnlockOverlay.setVisibility(View.GONE), 3000);
        } else {
            // Unlock Activated
            btnUnlockOverlay.setVisibility(View.GONE); // Hide floating button
            controlsLayout.setVisibility(View.VISIBLE); // Show main controls
            Toast.makeText(this, "Screen Unlocked", Toast.LENGTH_SHORT).show();
            autoHideControls();
        }
    }

    // --- GESTURES ---
    private void setupGestures() {
        scaleGestureDetector = new ScaleGestureDetector(this, new ScaleListener());
        gestureDetector = new GestureDetector(this, new GestureListener());

        videoContainer.setOnTouchListener((v, event) -> {
            // LOCKED STATE LOGIC
            if (isLocked) {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    // Show Unlock Button momentarily
                    btnUnlockOverlay.setVisibility(View.VISIBLE);
                    hideHandler.removeCallbacks(hideRunnable);
                    hideHandler.postDelayed(() -> btnUnlockOverlay.setVisibility(View.GONE), 3000);
                }
                return true; // Consume touch, disable other gestures
            }
            
            // UNLOCKED STATE LOGIC
            scaleGestureDetector.onTouchEvent(event);
            if (!scaleGestureDetector.isInProgress()) gestureDetector.onTouchEvent(event);
            if (event.getAction() == MotionEvent.ACTION_UP) {
                gestureLayout.setVisibility(View.GONE);
                seekModeStartPos = -1;
            }
            return true;
        });
    }

    private class GestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            if (controlsLayout.getVisibility() == View.VISIBLE) {
                controlsLayout.setVisibility(View.GONE);
                handler.removeCallbacks(hideRunnable);
            } else {
                controlsLayout.setVisibility(View.VISIBLE);
                autoHideControls();
            }
            return true;
        }

        @Override
        public boolean onDoubleTap(MotionEvent e) {
            int current = videoView.getCurrentPosition();
            if (e.getX() > deviceWidth / 2) {
                videoView.seekTo(current + 10000);
                showGestureFeedback(R.drawable.ic_next_player, "+10s");
            } else {
                videoView.seekTo(Math.max(current - 10000, 0));
                showGestureFeedback(R.drawable.ic_prev_player, "-10s");
            }
            return true;
        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            if (e1 == null || e2 == null) return false;
            float deltaX = e2.getX() - e1.getX();
            float deltaY = e2.getY() - e1.getY();

            if (Math.abs(deltaX) > Math.abs(deltaY)) { 
                // Seek
                if (Math.abs(deltaX) > 50) {
                    if (seekModeStartPos == -1) seekModeStartPos = videoView.getCurrentPosition();
                    int maxSeek = 60000; 
                    int change = (int) ((deltaX / deviceWidth) * maxSeek);
                    int target = Math.max(0, Math.min(videoView.getDuration(), seekModeStartPos + change));
                    videoView.seekTo(target);
                    showGestureFeedback(R.drawable.ic_next_player, formatTime(target));
                }
            } else { 
                // Volume/Bright
                if (Math.abs(deltaY) > 20) {
                    if (e1.getX() > deviceWidth / 2) changeVolume(distanceY);
                    else changeBrightness(distanceY);
                }
            }
            return true;
        }
    }

    // --- OTHER HELPERS ---
    private void enterPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Rational aspectRatio = new Rational(videoView.getWidth(), videoView.getHeight());
            enterPictureInPictureMode(new PictureInPictureParams.Builder().setAspectRatio(aspectRatio).build());
        } else {
            Toast.makeText(this, "PiP not supported", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void toggleScale() {
        screenMode = (screenMode + 1) % 3;
        resizeVideo();
        String modeText = (screenMode == 0) ? "Fit" : (screenMode == 1) ? "Fill" : "Stretch";
        showGestureFeedback(R.drawable.ic_scale, modeText);
    }
    
    private void toggleMute() {
        if (mediaPlayer == null) return;
        isMuted = !isMuted;
        if (isMuted) {
            mediaPlayer.setVolume(0f, 0f);
            btnMute.setImageResource(R.drawable.ic_volume_on); // Icon should indicate muted state or toggle
        } else {
            mediaPlayer.setVolume(1f, 1f);
            btnMute.setImageResource(R.drawable.ic_volume_on);
        }
    }
    
    private void changeVolume(float distanceY) {
        int max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        float delta = distanceY / (deviceHeight / 30);
        int newVol = Math.max(0, Math.min(max, current + (int)delta));
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0);
        showGestureFeedback(R.drawable.ic_volume_on, (newVol * 100 / max) + "%");
    }

    private void changeBrightness(float distanceY) {
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        float current = lp.screenBrightness < 0 ? 0.5f : lp.screenBrightness;
        float delta = distanceY / (deviceHeight * 2);
        float newBright = Math.max(0.01f, Math.min(1.0f, current + delta));
        lp.screenBrightness = newBright;
        getWindow().setAttributes(lp);
        showGestureFeedback(R.drawable.ic_scale, (int)(newBright * 100) + "%");
    }
    
    private void showSpeedDialog() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            Toast.makeText(this, "Speed req Android 6+", Toast.LENGTH_SHORT).show();
            return;
        }
        View view = getLayoutInflater().inflate(R.layout.dialog_speed, null);
        AlertDialog dialog = new AlertDialog.Builder(this).setView(view).create();
        View.OnClickListener listener = v -> {
            float speed = Float.parseFloat(v.getTag().toString());
            try {
                if (mediaPlayer != null) {
                    mediaPlayer.setPlaybackParams(mediaPlayer.getPlaybackParams().setSpeed(speed));
                    btnSpeed.setText(speed + "x");
                }
            } catch (Exception e) { e.printStackTrace(); }
            dialog.dismiss();
        };
        view.findViewById(R.id.speed05).setOnClickListener(listener);
        view.findViewById(R.id.speed075).setOnClickListener(listener);
        view.findViewById(R.id.speed10).setOnClickListener(listener);
        view.findViewById(R.id.speed125).setOnClickListener(listener);
        view.findViewById(R.id.speed15).setOnClickListener(listener);
        view.findViewById(R.id.speed20).setOnClickListener(listener);
        dialog.show();
    }
    
    private void takeScreenshot() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Bitmap bitmap = Bitmap.createBitmap(videoView.getWidth(), videoView.getHeight(), Bitmap.Config.ARGB_8888);
            try {
                PixelCopy.request((SurfaceView) videoView, bitmap, (copyResult) -> {
                    if (copyResult == PixelCopy.SUCCESS) saveImage(bitmap);
                }, new Handler());
            } catch (Exception e) { Toast.makeText(this, "Failed", Toast.LENGTH_SHORT).show(); }
        }
    }
    
    private void saveImage(Bitmap bitmap) {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, "snap_" + System.currentTimeMillis() + ".jpg");
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/VideoPlayerPro");
        Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        try (OutputStream out = getContentResolver().openOutputStream(uri)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);
            new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show());
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void resizeVideo() {
        if (mediaPlayer == null) return;
        int videoWidth = mediaPlayer.getVideoWidth();
        int videoHeight = mediaPlayer.getVideoHeight();
        float videoProportion = (float) videoWidth / (float) videoHeight;
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        float screenProportion = (float) screenWidth / (float) screenHeight;
        android.view.ViewGroup.LayoutParams lp = videoView.getLayoutParams();

        if (screenMode == 0) { // FIT
            if (videoProportion > screenProportion) {
                lp.width = screenWidth;
                lp.height = (int) ((float) screenWidth / videoProportion);
            } else {
                lp.width = (int) (videoProportion * (float) screenHeight);
                lp.height = screenHeight;
            }
        } else if (screenMode == 1) { // FILL
            if (videoProportion > screenProportion) {
                lp.width = (int) (videoProportion * (float) screenHeight);
                lp.height = screenHeight;
            } else {
                lp.width = screenWidth;
                lp.height = (int) ((float) screenWidth / videoProportion);
            }
        } else { // STRETCH
            lp.width = screenWidth;
            lp.height = screenHeight;
        }
        videoView.setLayoutParams(lp);
    }
    
    private void togglePlayPause() {
        if (videoView.isPlaying()) {
            videoView.pause();
            btnPlayPause.setImageResource(R.drawable.ic_play_player);
            isPlaying = false;
            handler.removeCallbacks(hideRunnable);
        } else {
            videoView.start();
            btnPlayPause.setImageResource(R.drawable.ic_pause_player);
            isPlaying = true;
            autoHideControls();
            updateSeekBar();
        }
    }

    private void toggleOrientation() {
        if (isLandscape) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            isLandscape = false;
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            isLandscape = true;
        }
    }
    
    private void playNextVideo() {
        VideoModel next = VideoPlaylist.getNextVideo();
        if (next != null) playVideo(next.getContentUri(), next.getTitle());
    }

    private void playPrevVideo() {
        VideoModel prev = VideoPlaylist.getPrevVideo();
        if (prev != null) playVideo(prev.getContentUri(), prev.getTitle());
    }
    
    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            mScaleFactor *= detector.getScaleFactor();
            mScaleFactor = Math.max(0.25f, Math.min(mScaleFactor, 4.0f));
            videoView.setScaleX(mScaleFactor);
            videoView.setScaleY(mScaleFactor);
            showGestureFeedback(R.drawable.ic_scale, (int)(mScaleFactor * 100) + "%");
            return true;
        }
    }

    private void showGestureFeedback(int icon, String text) {
        gestureLayout.setVisibility(View.VISIBLE);
        gestureIcon.setImageResource(icon);
        gestureText.setText(text);
    }

    private void updateSeekBar() {
        if (videoView.isPlaying()) {
            int current = videoView.getCurrentPosition();
            seekBar.setProgress(current);
            txtCurrent.setText(formatTime(current));
            updateSeekBarRunnable = this::updateSeekBar;
            handler.postDelayed(updateSeekBarRunnable, 1000);
        }
    }

    private void autoHideControls() {
        handler.removeCallbacks(hideRunnable);
        handler.postDelayed(hideRunnable, 4000);
    }

    private String formatTime(int ms) {
        int seconds = (ms / 1000) % 60;
        int minutes = (ms / (1000 * 60)) % 60;
        int hours = (ms / (1000 * 60 * 60));
        return hours > 0 ? String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
                         : String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
    }
    
    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
        if (isInPictureInPictureMode) {
            controlsLayout.setVisibility(View.GONE);
            btnUnlockOverlay.setVisibility(View.GONE);
        } else {
            controlsLayout.setVisibility(View.VISIBLE);
        }
    }
    
    @Override
    protected void onUserLeaveHint() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isPlaying) {
             Rational aspectRatio = new Rational(videoView.getWidth(), videoView.getHeight());
             enterPictureInPictureMode(new PictureInPictureParams.Builder().setAspectRatio(aspectRatio).build());
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(updateSeekBarRunnable);
        handler.removeCallbacks(hideRunnable);
    }
}