package com.msi.videoplayer;

import android.app.Activity;
import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

public class GestureController {

    private Activity activity;
    private AudioManager audioManager;
    private MediaPlayer mediaPlayer;

    // UI Elements
    private View gestureLayout;
    private ImageView iconView;
    private TextView textView;

    // Logic Variables
    private float startY, startX;
    private int startVolume, maxVolume;
    private float startBrightness;
    private long startSeekPos;
    private int screenWidth, screenHeight;

    // Threshold to distinguish between Tap and Scroll
    private static final int TOUCH_THRESHOLD = 50;
    
    // Modes
    private enum Mode { NONE, VOLUME, BRIGHTNESS, SEEK }
    private Mode currentMode = Mode.NONE;

    // Callback Interface for Single Tap
    public interface OnGestureAction {
        void onSingleTap();
    }
    private OnGestureAction actionListener;

    public void setOnGestureAction(OnGestureAction listener) {
        this.actionListener = listener;
    }

    public GestureController(Activity activity, MediaPlayer player, View overlay, ImageView icon, TextView text) {
        this.activity = activity;
        this.mediaPlayer = player;
        this.gestureLayout = overlay;
        this.iconView = icon;
        this.textView = text;

        audioManager = (AudioManager) activity.getSystemService(Context.AUDIO_SERVICE);
        maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);

        // Get Screen Metrics
        screenWidth = activity.getResources().getDisplayMetrics().widthPixels;
        screenHeight = activity.getResources().getDisplayMetrics().heightPixels;
    }

    public void onTouch(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                startY = event.getY();
                startX = event.getX();
                startVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                
                // Get current brightness
                WindowManager.LayoutParams lp = activity.getWindow().getAttributes();
                startBrightness = lp.screenBrightness;
                if (startBrightness < 0) startBrightness = 0.5f; // Default system brightness fallback

                if (mediaPlayer != null) {
                    startSeekPos = mediaPlayer.getCurrentPosition();
                }
                
                currentMode = Mode.NONE;
                break;

            case MotionEvent.ACTION_MOVE:
                float deltaY = startY - event.getY(); // Up is positive
                float deltaX = event.getX() - startX; // Right is positive

                // Determine Mode if not set
                if (currentMode == Mode.NONE) {
                    // Check if movement is significant enough to be a gesture
                    if (Math.abs(deltaX) > TOUCH_THRESHOLD || Math.abs(deltaY) > TOUCH_THRESHOLD) {
                        if (Math.abs(deltaX) > Math.abs(deltaY)) {
                            currentMode = Mode.SEEK;
                        } else {
                            // Left side = Brightness, Right side = Volume
                            if (startX < screenWidth / 2) {
                                currentMode = Mode.BRIGHTNESS;
                            } else {
                                currentMode = Mode.VOLUME;
                            }
                        }
                    }
                }

                if (currentMode == Mode.VOLUME) {
                    changeVolume(deltaY);
                } else if (currentMode == Mode.BRIGHTNESS) {
                    changeBrightness(deltaY);
                } else if (currentMode == Mode.SEEK) {
                    changeSeek(deltaX);
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                gestureLayout.setVisibility(View.GONE);

                if (currentMode == Mode.SEEK && mediaPlayer != null) {
                    mediaPlayer.seekTo((int) targetSeekPos);
                } else if (currentMode == Mode.NONE) {
                    // No significant movement detected -> treat as TAP
                    if (actionListener != null) {
                        actionListener.onSingleTap();
                    }
                }
                break;
        }
    }

    private void changeVolume(float deltaY) {
        gestureLayout.setVisibility(View.VISIBLE);
        iconView.setImageResource(R.drawable.ic_volume);

        float percent = deltaY / screenHeight;
        int change = (int) (percent * maxVolume * 3); // Sensitivity multiplier
        int newVol = Math.max(0, Math.min(maxVolume, startVolume + change));

        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0);
        
        int percentVal = (newVol * 100) / maxVolume;
        textView.setText(percentVal + "%");
    }

    private void changeBrightness(float deltaY) {
        gestureLayout.setVisibility(View.VISIBLE);
        iconView.setImageResource(R.drawable.ic_brightness);

        float percent = deltaY / screenHeight;
        float newBright = Math.max(0.01f, Math.min(1.0f, startBrightness + percent * 2));

        WindowManager.LayoutParams lp = activity.getWindow().getAttributes();
        lp.screenBrightness = newBright;
        activity.getWindow().setAttributes(lp);

        textView.setText((int) (newBright * 100) + "%");
    }

    private long targetSeekPos = 0;

    private void changeSeek(float deltaX) {
        if (mediaPlayer == null) return;

        gestureLayout.setVisibility(View.VISIBLE);

        // 90 seconds seek for full width swipe
        long totalDuration = mediaPlayer.getDuration();
        int seekTime = (int) ((deltaX / screenWidth) * 90000); 

        targetSeekPos = Math.max(0, Math.min(totalDuration, startSeekPos + seekTime));

        String timeText = TimeUtils.formatDuration(targetSeekPos);
        
        // Icon update
        if (seekTime > 0) iconView.setImageResource(R.drawable.ic_next);
        else iconView.setImageResource(R.drawable.ic_prev);

        textView.setText(timeText);
    }
}