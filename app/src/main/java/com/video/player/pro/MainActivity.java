package com.video.player.pro;

import android.Manifest;
import android.content.ContentUris;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 101;
    
    // UI Elements
    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private TextView emptyStateText;
    private DrawerLayout drawerLayout;
    
    // Toolbar & Tabs
    private LinearLayout defaultToolbar, searchToolbar;
    private EditText searchInput;
    private ImageView btnViewMode, btnSearch, btnCloseSearch, btnMenu;
    private LinearLayout tabVideos, tabFolders;
    private ImageView iconVideo, iconFolder;
    private TextView textVideo, textFolder;
    private TextView versionText;

    // --- MINI PLAYER UI ---
    private FrameLayout miniPlayerContainer;
    private VideoView miniVideoView;
    private ImageButton btnCloseMini, btnMiniPlayPause, btnMiniFullscreen;
    private TextView miniTitle;
    private VideoModel currentVideo;

    // Data
    private List<VideoModel> allVideos = new ArrayList<>();
    private List<FolderModel> folderList = new ArrayList<>();
    private VideoAdapter videoAdapter;
    private FolderAdapter folderAdapter;
    
    // States
    private boolean isFolderTab = false;
    private boolean isInsideFolder = false;
    private int viewMode = 0; 

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initializeViews();
        setupListeners();
        setAppVersion();
        
        // Setup Video Adapter with Click Listener
        videoAdapter = new VideoAdapter(this, allVideos, video -> {
            playMiniVideo(video); // প্লেয়ার চালু হবে
        });

        folderAdapter = new FolderAdapter(this, folderList, folder -> openFolder(folder));
        
        recyclerView.setAdapter(videoAdapter);
        updateViewMode(); 

        checkAndRequestPermissions();
    }

    private void initializeViews() {
        drawerLayout = findViewById(R.id.drawerLayout);
        recyclerView = findViewById(R.id.recyclerView);
        progressBar = findViewById(R.id.progressBar);
        emptyStateText = findViewById(R.id.emptyStateText);
        
        defaultToolbar = findViewById(R.id.defaultToolbar);
        searchToolbar = findViewById(R.id.searchToolbar);
        searchInput = findViewById(R.id.searchInput);
        
        btnMenu = findViewById(R.id.btnMenu);
        btnViewMode = findViewById(R.id.btnViewMode);
        btnSearch = findViewById(R.id.btnSearch);
        btnCloseSearch = findViewById(R.id.btnCloseSearch);
        
        tabVideos = findViewById(R.id.tabVideos);
        tabFolders = findViewById(R.id.tabFolders);
        iconVideo = findViewById(R.id.iconVideo);
        iconFolder = findViewById(R.id.iconFolder);
        textVideo = findViewById(R.id.textVideo);
        textFolder = findViewById(R.id.textFolder);
        
        View headerView = findViewById(R.id.sideDrawer); 
        versionText = headerView.findViewById(R.id.versionText);

        // Mini Player
        miniPlayerContainer = findViewById(R.id.miniPlayerContainer);
        miniVideoView = findViewById(R.id.miniVideoView);
        btnCloseMini = findViewById(R.id.btnCloseMini);
        btnMiniPlayPause = findViewById(R.id.btnMiniPlayPause);
        btnMiniFullscreen = findViewById(R.id.btnMiniFullscreen);
        miniTitle = findViewById(R.id.miniTitle);
    }

    // --- MINI PLAYER LOGIC ---
    private void playMiniVideo(VideoModel video) {
        currentVideo = video;
        miniPlayerContainer.setVisibility(View.VISIBLE);
        miniTitle.setText(video.getTitle());
        
        miniVideoView.setVideoURI(video.getContentUri());
        
        miniVideoView.setOnPreparedListener(mp -> {
            mp.setLooping(true); // লুপ বা অটো নেক্সট লজিক দিতে পারেন
            miniVideoView.start();
            btnMiniPlayPause.setImageResource(R.drawable.ic_pause_player);
        });
    }

    private void setupListeners() {
        // ... Drawer, Search, Tabs Logic (Same as before) ...
        btnMenu.setOnClickListener(v -> {
            if (isInsideFolder) onBackPressed();
            else drawerLayout.openDrawer(GravityCompat.START);
        });
        
        findViewById(R.id.menuAbout).setOnClickListener(v -> {
            drawerLayout.closeDrawer(GravityCompat.START);
            new Handler().postDelayed(() -> startActivity(new Intent(this, AboutActivity.class)), 250);
        });

        // Search
        btnSearch.setOnClickListener(v -> { defaultToolbar.setVisibility(View.GONE); searchToolbar.setVisibility(View.VISIBLE); });
        btnCloseSearch.setOnClickListener(v -> closeSearch());
        searchInput.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) { filterData(s.toString()); }
            public void afterTextChanged(Editable s) {}
        });

        // View Mode
        btnViewMode.setOnClickListener(v -> {
            viewMode = (viewMode + 1) % 3;
            updateViewModeIcon();
            updateViewMode();
        });

        // Tabs
        tabVideos.setOnClickListener(v -> { if (isInsideFolder) onBackPressed(); switchTab(false); });
        tabFolders.setOnClickListener(v -> { if (isInsideFolder) onBackPressed(); switchTab(true); });

        // --- MINI PLAYER CONTROLS ---
        btnMiniPlayPause.setOnClickListener(v -> {
            if (miniVideoView.isPlaying()) {
                miniVideoView.pause();
                btnMiniPlayPause.setImageResource(R.drawable.ic_play_player);
            } else {
                miniVideoView.start();
                btnMiniPlayPause.setImageResource(R.drawable.ic_pause_player);
            }
        });

        btnCloseMini.setOnClickListener(v -> {
            miniVideoView.stopPlayback();
            miniPlayerContainer.setVisibility(View.GONE);
        });

        btnMiniFullscreen.setOnClickListener(v -> {
            if (currentVideo != null) {
                // পজিশন নিয়ে ফুল স্ক্রিনে যাওয়া
                int pos = miniVideoView.getCurrentPosition();
                miniVideoView.pause(); 
                
                Intent intent = new Intent(MainActivity.this, PlayerActivity.class);
                intent.putExtra("videoUri", currentVideo.getContentUri().toString());
                intent.putExtra("videoTitle", currentVideo.getTitle());
                intent.putExtra("startPosition", pos); // পজিশন পাস
                startActivity(intent);
            }
        });
    }

    // ... Other Helper Methods (setAppVersion, updateViewMode, etc.) are SAME as before ...
    // Just putting back minimal versions to save space, but you use full logic from previous step
    
    private void setAppVersion() {
         try {
            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            versionText.setText("Version " + pInfo.versionName);
        } catch (PackageManager.NameNotFoundException e) {
            versionText.setText("Version 1.0");
        }
    }
    
    private void closeSearch() {
        searchToolbar.setVisibility(View.GONE);
        defaultToolbar.setVisibility(View.VISIBLE);
        searchInput.setText("");
        if (!isFolderTab) videoAdapter.updateList(allVideos);
    }
    
    private void updateViewModeIcon() {
        if (viewMode == 0) btnViewMode.setImageResource(R.drawable.ic_sort);
        else if (viewMode == 1) btnViewMode.setImageResource(R.drawable.ic_grid); // Assuming grid icon exists
        else btnViewMode.setImageResource(R.drawable.ic_scale); // Or card icon
    }

    private void updateViewMode() {
        int spanCount = (viewMode == 1) ? 2 : 1; 
        if (isFolderTab && !isInsideFolder) {
            if (viewMode == 1) recyclerView.setLayoutManager(new GridLayoutManager(this, 2));
            else recyclerView.setLayoutManager(new LinearLayoutManager(this));
            folderAdapter.setViewMode(viewMode);
            recyclerView.setAdapter(folderAdapter);
        } else {
            if (viewMode == 1) recyclerView.setLayoutManager(new GridLayoutManager(this, 2));
            else recyclerView.setLayoutManager(new LinearLayoutManager(this));
            videoAdapter.setViewMode(viewMode);
            recyclerView.setAdapter(videoAdapter);
        }
    }
    
    private void switchTab(boolean toFolder) {
        if (isFolderTab == toFolder && !isInsideFolder) return;
        isFolderTab = toFolder;
        isInsideFolder = false;
        btnMenu.setImageResource(R.drawable.ic_menu);
        int active = ContextCompat.getColor(this, R.color.light_primary); 
        int inactive = ContextCompat.getColor(this, android.R.color.darker_gray);
        if (isFolderTab) {
            iconFolder.setColorFilter(active); textFolder.setTextColor(active);
            iconVideo.setColorFilter(inactive); textVideo.setTextColor(inactive);
            updateViewMode(); 
        } else {
            iconVideo.setColorFilter(active); textVideo.setTextColor(active);
            iconFolder.setColorFilter(inactive); textFolder.setTextColor(inactive);
            videoAdapter.updateList(allVideos);
            updateViewMode();
        }
    }
    
    private void openFolder(FolderModel folder) {
        isInsideFolder = true;
        btnMenu.setImageResource(R.drawable.ic_back);
        List<VideoModel> folderVideos = new ArrayList<>();
        for (VideoModel v : allVideos) {
            if (v.getPath().contains(folder.getBucketName())) folderVideos.add(v);
        }
        videoAdapter.updateList(folderVideos);
        if (viewMode == 1) recyclerView.setLayoutManager(new GridLayoutManager(this, 2));
        else recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(videoAdapter);
    }
    
    private void filterData(String query) {
        if (isFolderTab && !isInsideFolder) return;
        List<VideoModel> filtered = new ArrayList<>();
        for (VideoModel item : allVideos) {
            if (item.getTitle().toLowerCase().contains(query.toLowerCase())) filtered.add(item);
        }
        videoAdapter.updateList(filtered);
    }
    
    @Override
    public void onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) drawerLayout.closeDrawer(GravityCompat.START);
        else if (searchToolbar.getVisibility() == View.VISIBLE) closeSearch();
        else if (isInsideFolder) {
            isInsideFolder = false;
            btnMenu.setImageResource(R.drawable.ic_menu);
            updateViewMode();
        } else if (isFolderTab) switchTab(false);
        else if (miniPlayerContainer.getVisibility() == View.VISIBLE) {
            // মিনি প্লেয়ার বন্ধ করতে চাইলে
             miniVideoView.stopPlayback();
             miniPlayerContainer.setVisibility(View.GONE);
        }
        else super.onBackPressed();
    }
    
    // Permission & Data Loading (Same as before)
    private void checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, "android.permission.READ_MEDIA_VIDEO") != PackageManager.PERMISSION_GRANTED) 
                ActivityCompat.requestPermissions(this, new String[]{"android.permission.READ_MEDIA_VIDEO"}, PERMISSION_REQUEST_CODE);
            else loadVideos();
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) 
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, PERMISSION_REQUEST_CODE);
            else loadVideos();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) loadVideos();
        else Toast.makeText(this, "Permission Required", Toast.LENGTH_SHORT).show();
    }

    private void loadVideos() {
        progressBar.setVisibility(View.VISIBLE);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            List<VideoModel> tempVideos = new ArrayList<>();
            List<FolderModel> tempFolders = new ArrayList<>();
            List<String> folderNames = new ArrayList<>();
            Uri collection = (Build.VERSION.SDK_INT >= 29) ? MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL) : MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
            String[] projection = { MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME, MediaStore.Video.Media.DURATION, MediaStore.Video.Media.DATA, MediaStore.Video.Media.SIZE, MediaStore.Video.Media.BUCKET_DISPLAY_NAME, MediaStore.Video.Media.BUCKET_ID };
            try (Cursor cursor = getContentResolver().query(collection, projection, null, null, MediaStore.Video.Media.DATE_ADDED + " DESC")) {
                if (cursor != null) {
                    int idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID);
                    int nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME);
                    int durCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION);
                    int pathCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA);
                    int sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE);
                    int bucketNameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME);
                    int bucketIdCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_ID);
                    while (cursor.moveToNext()) {
                        long id = cursor.getLong(idCol);
                        String name = cursor.getString(nameCol);
                        int duration = cursor.getInt(durCol);
                        String path = cursor.getString(pathCol);
                        long sizeBytes = cursor.getLong(sizeCol);
                        String size = String.format(Locale.US, "%.1f MB", sizeBytes / (1024.0 * 1024.0));
                        String bucketName = cursor.getString(bucketNameCol);
                        String bucketId = cursor.getString(bucketIdCol);
                        Uri contentUri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id);
                        if(duration > 0) {
                            VideoModel video = new VideoModel(String.valueOf(id), name, formatDuration(duration), path, size, contentUri);
                            tempVideos.add(video);
                            if (!folderNames.contains(bucketName)) {
                                folderNames.add(bucketName);
                                tempFolders.add(new FolderModel(bucketId, bucketName, path));
                            } else {
                                for (FolderModel f : tempFolders) { if (f.getBucketName().equals(bucketName)) { f.incrementVideoCount(); break; } }
                            }
                        }
                    }
                }
            }
            runOnUiThread(() -> {
                progressBar.setVisibility(View.GONE);
                allVideos.clear();
                allVideos.addAll(tempVideos);
                folderList.clear();
                folderList.addAll(tempFolders);
                if (isFolderTab) updateViewMode(); else videoAdapter.updateList(allVideos);
                if (allVideos.isEmpty()) emptyStateText.setVisibility(View.VISIBLE); else emptyStateText.setVisibility(View.GONE);
            });
        });
    }

    private String formatDuration(int durationMs) {
        int seconds = (durationMs / 1000) % 60;
        int minutes = (durationMs / (1000 * 60)) % 60;
        int hours = (durationMs / (1000 * 60 * 60));
        if (hours > 0) return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds);
        else return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
    }
}