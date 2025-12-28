package com.videoplayer;

import android.Manifest;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.RadioGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.viewpager2.widget.ViewPager2;
import com.airbnb.lottie.LottieAnimationView;
import com.bumptech.glide.Glide;

import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private ViewPager2 viewPager;
    private MainPagerAdapter pagerAdapter;
    private ImageView dot1, dot2;

    private TextView tvTitle;
    private EditText searchBar;
    private LottieAnimationView lottieLoading;
    
    // Mini Player
    private RelativeLayout miniPlayerLayout;
    private ImageView miniPlay, miniNext, miniPrev, miniClose, miniArt; 
    private TextView miniTitle;
    
    private VideoDatabaseHelper dbHelper;
    private SettingsManager settingsManager;
    private ExecutorService executorService = Executors.newSingleThreadExecutor();

    private PlaybackService playbackService;
    private boolean isBound = false;

    private static final String PERMISSION_READ_MEDIA_VIDEO = "android.permission.READ_MEDIA_VIDEO";
    private static final String PERMISSION_READ_EXTERNAL_STORAGE = "android.permission.READ_EXTERNAL_STORAGE";

    // Listener
    private Player.Listener playerListener = new Player.Listener() {
        @Override
        public void onIsPlayingChanged(boolean isPlaying) { updateMiniPlayer(); }
        @Override
        public void onMediaItemTransition(MediaItem mediaItem, int reason) { updateMiniPlayer(); }
        @Override
        public void onPlaybackStateChanged(int state) {
            if (state == Player.STATE_ENDED) miniPlayerLayout.setVisibility(View.GONE);
            updateMiniPlayer();
        }
    };

    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            PlaybackService.LocalBinder binder = (PlaybackService.LocalBinder) service;
            playbackService = binder.getService();
            isBound = true;
            playbackService.player.addListener(playerListener);
            updateMiniPlayer();
        }
        @Override
        public void onServiceDisconnected(ComponentName name) { isBound = false; }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        settingsManager = new SettingsManager(this);
        applyTheme(); 
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        dbHelper = new VideoDatabaseHelper(this);
        initViews();
        checkPermissions();
        
        // Default Home
        int defaultHome = settingsManager.getDefaultHome();
        viewPager.setCurrentItem(defaultHome, false);
    }
    
    @Override
    protected void onStart() {
        super.onStart();
        Intent intent = new Intent(this, PlaybackService.class);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if(isBound && playbackService != null) updateMiniPlayer();
        // Resume হলে ফ্রাগমেন্ট রিফ্রেশ
        refreshFragments();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isBound && playbackService != null) {
            playbackService.player.removeListener(playerListener);
            unbindService(serviceConnection);
            isBound = false;
        }
    }

    private void initViews() {
        viewPager = findViewById(R.id.view_pager);
        dot1 = findViewById(R.id.dot_1);
        dot2 = findViewById(R.id.dot_2);
        
        // Adapter একবারই সেট হবে
        pagerAdapter = new MainPagerAdapter(this);
        viewPager.setAdapter(pagerAdapter);
        
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                updateDots(position);
                if(position == 0) tvTitle.setText("All Videos");
                else tvTitle.setText("Folders");
            }
        });

        tvTitle = findViewById(R.id.tv_app_title);
        searchBar = findViewById(R.id.et_search);
        lottieLoading = findViewById(R.id.lottie_loading);
        
        miniPlayerLayout = findViewById(R.id.miniPlayerLayout);
        miniArt = findViewById(R.id.mini_art);
        miniPlay = findViewById(R.id.mini_play);
        miniNext = findViewById(R.id.mini_next);
        miniPrev = findViewById(R.id.mini_prev);
        miniClose = findViewById(R.id.mini_close); 
        miniTitle = findViewById(R.id.mini_title);

        setupMiniPlayerControls();

        ImageView btnSettings = findViewById(R.id.btn_settings);
        if(btnSettings != null) {
            btnSettings.setOnClickListener(v -> showSettingsPopup(v));
        }

        searchBar.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {}
        });
    }
    
    private void updateDots(int position) {
        if (position == 0) {
            dot1.setImageResource(R.drawable.tab_indicator_selected);
            dot2.setImageResource(R.drawable.tab_indicator_default);
        } else {
            dot1.setImageResource(R.drawable.tab_indicator_default);
            dot2.setImageResource(R.drawable.tab_indicator_selected);
        }
    }

    // --- FIX: REFRESH FRAGMENTS METHOD (CRASH SOLVER) ---
    private void refreshFragments() {
        for (Fragment fragment : getSupportFragmentManager().getFragments()) {
            if (fragment instanceof VideoFragment) {
                ((VideoFragment) fragment).loadVideos();
            } else if (fragment instanceof FolderFragment) {
                ((FolderFragment) fragment).loadFolders();
            }
        }
    }

    // --- SETTINGS MENU ---
    private void showSettingsPopup(View anchorView) {
        View popupView = getLayoutInflater().inflate(R.layout.popup_settings, null);
        PopupWindow popupWindow = new PopupWindow(popupView, LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT, true);
        popupWindow.setElevation(10);
        popupWindow.showAsDropDown(anchorView, 0, 0); 

        popupView.findViewById(R.id.menu_sort).setOnClickListener(v -> {
            popupWindow.dismiss();
            showSortingDialog();
        });
        popupView.findViewById(R.id.menu_view_mode).setOnClickListener(v -> {
            popupWindow.dismiss();
            toggleViewMode();
        });
        popupView.findViewById(R.id.menu_default_home).setOnClickListener(v -> {
            popupWindow.dismiss();
            showHomeDialog();
        });
        popupView.findViewById(R.id.menu_filter).setOnClickListener(v -> {
            popupWindow.dismiss();
            showFilterDialog();
        });
        popupView.findViewById(R.id.menu_theme).setOnClickListener(v -> {
            popupWindow.dismiss();
            toggleTheme();
        });
        popupView.findViewById(R.id.menu_share).setOnClickListener(v -> {
            popupWindow.dismiss();
            shareApp();
        });
        popupView.findViewById(R.id.menu_info).setOnClickListener(v -> {
            popupWindow.dismiss();
            showAppInfoDialog();
        });
    }

    // --- DIALOGS (Fixed logic to call refreshFragments) ---
    private void showSortingDialog() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_sorting);
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));

        RadioGroup radioGroup = dialog.findViewById(R.id.radio_group_sort);
        int currentSort = settingsManager.getSortType();
        
        if (currentSort == SettingsManager.SORT_NAME_AZ) radioGroup.check(R.id.sort_name);
        else if (currentSort == SettingsManager.SORT_DATE_NEW) radioGroup.check(R.id.sort_date);
        else if (currentSort == SettingsManager.SORT_SIZE_LARGE) radioGroup.check(R.id.sort_size);
        else if (currentSort == SettingsManager.SORT_DURATION) radioGroup.check(R.id.sort_length);

        radioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            int newSortType = SettingsManager.SORT_DATE_NEW;
            if (checkedId == R.id.sort_name) newSortType = SettingsManager.SORT_NAME_AZ;
            else if (checkedId == R.id.sort_date) newSortType = SettingsManager.SORT_DATE_NEW;
            else if (checkedId == R.id.sort_size) newSortType = SettingsManager.SORT_SIZE_LARGE;
            else if (checkedId == R.id.sort_length) newSortType = SettingsManager.SORT_DURATION;
            
            settingsManager.setSortType(newSortType);
            refreshFragments(); // FIX: Refresh instead of reset adapter
            dialog.dismiss();
        });
        dialog.show();
    }

    private void toggleViewMode() {
        int currentMode = settingsManager.getViewMode();
        int newMode = (currentMode == SettingsManager.VIEW_LIST) ? SettingsManager.VIEW_GRID : SettingsManager.VIEW_LIST;
        settingsManager.setViewMode(newMode);
        
        refreshFragments(); // FIX
        Toast.makeText(this, newMode == SettingsManager.VIEW_GRID ? "Grid View" : "List View", Toast.LENGTH_SHORT).show();
    }
    
    private void showHomeDialog() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_default_home);
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
        
        RadioGroup radioGroup = dialog.findViewById(R.id.radio_group_home);
        int currentHome = settingsManager.getDefaultHome();
        if (currentHome == 0) radioGroup.check(R.id.home_all_videos);
        else radioGroup.check(R.id.home_folders);
        
        radioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.home_all_videos) settingsManager.setDefaultHome(0);
            else settingsManager.setDefaultHome(1);
            Toast.makeText(this, "Default Home Set", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });
        dialog.show();
    }
    
    private void showFilterDialog() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_filters);
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
        
        CheckBox checkHidden = dialog.findViewById(R.id.check_show_hidden);
        CheckBox checkShort = dialog.findViewById(R.id.check_hide_short);
        Button btnApply = dialog.findViewById(R.id.btn_apply_filter);
        
        checkHidden.setChecked(settingsManager.getShowHidden());
        checkShort.setChecked(settingsManager.getHideShortVideos());
        
        btnApply.setOnClickListener(v -> {
            settingsManager.setShowHidden(checkHidden.isChecked());
            settingsManager.setHideShortVideos(checkShort.isChecked());
            
            showLoading(true);
            scanStorageInBackground();
            dialog.dismiss();
        });
        dialog.show();
    }
    
    private void toggleTheme() {
        int currentTheme = settingsManager.getAppTheme();
        int newTheme = (currentTheme == SettingsManager.THEME_LIGHT) ? SettingsManager.THEME_DARK : SettingsManager.THEME_LIGHT;
        settingsManager.setAppTheme(newTheme);
        recreate();
    }
    
    private void applyTheme() {
        int theme = settingsManager.getAppTheme();
        if (theme == SettingsManager.THEME_DARK) AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        else if (theme == SettingsManager.THEME_LIGHT) AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        else AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    }
    
    private void shareApp() {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        String shareBody = "Check out this amazing Video Player: https://play.google.com/store/apps/details?id=" + getPackageName();
        intent.putExtra(Intent.EXTRA_SUBJECT, "Video Player");
        intent.putExtra(Intent.EXTRA_TEXT, shareBody);
        startActivity(Intent.createChooser(intent, "Share via"));
    }
    
    private void showAppInfoDialog() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_app_info);
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
        
        dialog.findViewById(R.id.btn_close_info).setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void setupMiniPlayerControls() {
        miniPlay.setOnClickListener(v -> {
            if(playbackService != null && playbackService.player != null) {
                if(playbackService.player.isPlaying()) playbackService.player.pause();
                else playbackService.player.play();
            }
        });
        miniNext.setOnClickListener(v -> {
            if(playbackService != null && playbackService.player != null && playbackService.player.hasNextMediaItem()) {
                playbackService.player.seekToNextMediaItem();
            }
        });
        miniPrev.setOnClickListener(v -> {
            if(playbackService != null && playbackService.player != null && playbackService.player.hasPreviousMediaItem()) {
                playbackService.player.seekToPreviousMediaItem();
            }
        });
        miniClose.setOnClickListener(v -> {
            if(playbackService != null) {
                playbackService.player.pause();
                playbackService.stopBackgroundPlay();
                playbackService.isBackgroundPlayEnabled = false; 
            }
            miniPlayerLayout.setVisibility(View.GONE);
        });
        miniPlayerLayout.setOnClickListener(v -> {
            if (playbackService != null && playbackService.player.getMediaItemCount() > 0) {
                Intent intent = new Intent(MainActivity.this, PlayerActivity.class);
                intent.putExtra("position", -1); 
                startActivity(intent);
            }
        });
    }   

    private void updateMiniPlayer() {
        if (playbackService != null && playbackService.player != null 
                && playbackService.player.getMediaItemCount() > 0
                && playbackService.isBackgroundPlayEnabled) { 
            miniPlayerLayout.setVisibility(View.VISIBLE);
            MediaItem item = playbackService.player.getCurrentMediaItem();
            if (item != null) {
                if (item.mediaMetadata.title != null) miniTitle.setText(item.mediaMetadata.title);
                if (item.mediaId != null) Glide.with(this).load(item.mediaId).centerCrop().placeholder(R.drawable.exo_icon_play).into(miniArt);
            }
            if (playbackService.player.isPlaying()) miniPlay.setImageResource(R.drawable.exo_icon_pause);
            else miniPlay.setImageResource(R.drawable.exo_icon_play);
        } else {
            miniPlayerLayout.setVisibility(View.GONE);
        }
    }  
     
    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, PERMISSION_READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{PERMISSION_READ_MEDIA_VIDEO}, 101);
            } else {
                startAppLogic();
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, PERMISSION_READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{PERMISSION_READ_EXTERNAL_STORAGE}, 101);
            } else {
                startAppLogic();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 101 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startAppLogic();
        } else {
            Toast.makeText(this, "Permission Required", Toast.LENGTH_SHORT).show();
        }
    }

    private void startAppLogic() {
        if (dbHelper.hasData()) {
            showLoading(false);
            scanStorageInBackground();
        } else {
            showLoading(true);
            scanStorageInBackground();
        }
    }

    private void scanStorageInBackground() {
        executorService.execute(() -> {
            ArrayList<VideoItem> scannedVideos = new ArrayList<>();
            Uri uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
            String[] projection = {
                    MediaStore.Video.Media._ID,
                    MediaStore.Video.Media.TITLE,
                    MediaStore.Video.Media.DATA,
                    MediaStore.Video.Media.DURATION,
                    MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
                    MediaStore.Video.Media.SIZE,
                    MediaStore.Video.Media.DATE_ADDED
            };

            Cursor cursor = getContentResolver().query(uri, projection, null, null, MediaStore.Video.Media.DATE_ADDED + " DESC");
            boolean hideShort = settingsManager.getHideShortVideos();

            if (cursor != null) {
                while (cursor.moveToNext()) {
                    String id = cursor.getString(0);
                    String title = cursor.getString(1);
                    String path = cursor.getString(2);
                    String durationStr = cursor.getString(3);
                    String folder = cursor.getString(4);
                    String size = cursor.getString(5);
                    long date = cursor.getLong(6);
                    
                    if (path != null && new File(path).exists()) {
                        long durationMillis = durationStr != null ? Long.parseLong(durationStr) : 0;
                        if (hideShort && durationMillis < 60000) continue; 

                        String formattedDuration = formatTime(durationMillis);
                        scannedVideos.add(new VideoItem(id, title, path, formattedDuration, folder, null, size, date));
                    }
                }
                cursor.close();
            }

            if (!scannedVideos.isEmpty()) {
                dbHelper.addVideos(scannedVideos);
            }

            runOnUiThread(() -> {
                showLoading(false);
                refreshFragments(); // FIX: Refresh Fragments
            });
        });
    }
    public void openFolder(String folderName) {
        // সরাসরি প্লেয়ার না খুলে, FolderActivity তে ফোল্ডারের নাম পাঠানো হচ্ছে
        Intent intent = new Intent(MainActivity.this, FolderActivity.class);
        intent.putExtra("folderName", folderName);
        startActivity(intent);
    }
    private void showLoading(boolean isLoading) {
        if (isLoading) {
            lottieLoading.setVisibility(View.VISIBLE);
            viewPager.setVisibility(View.GONE);
        } else {
            lottieLoading.setVisibility(View.GONE);
            viewPager.setVisibility(View.VISIBLE);
        }
    }

    private String formatTime(long millis) {
        long seconds = millis / 1000;
        long s = seconds % 60;
        long m = (seconds / 60) % 60;
        long h = (seconds / (60 * 60)) % 24;
        return h > 0 ? String.format("%02d:%02d:%02d", h, m, s) : String.format("%02d:%02d", m, s);
    }

    @Override
    public void onBackPressed() {
        if (viewPager != null && viewPager.getCurrentItem() != 0) {
            viewPager.setCurrentItem(0); 
        } else {
            super.onBackPressed();
        }
    }
}