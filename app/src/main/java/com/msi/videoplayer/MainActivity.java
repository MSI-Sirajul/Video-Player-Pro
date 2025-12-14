package com.msi.videoplayer;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MainActivity extends Activity {

    // --- UI Components ---
    private GridView gridView;
    private TextView txtEmptyState, appTitle;
    private EditText etSearch;
    private ImageView btnViewType, btnMenu;
    
    // Bottom Nav Icons & Tabs
    private ImageView iconHistory, iconVideos, iconFolders, iconFavorites;
    private FrameLayout tabHistory, tabVideos, tabFolders, tabFavorites;
    private LinearLayout bottomNav;
    private LinearLayout topContainer; // For theme coloring

    // --- Logic & Data ---
    private VideoAdapter adapter;
    private List<VideoModel> masterVideoList = new ArrayList<>(); // Source of truth
    private List<VideoModel> displayList = new ArrayList<>();     // Filtered/Sorted list
    
    private SettingsManager settingsManager;
    private DatabaseHelper dbHelper;
    private VideoScanner videoScanner;

    // State Variables
    private int currentTab = TAB_VIDEOS;
    private static final int TAB_HISTORY = 0;
    private static final int TAB_VIDEOS = 1;
    private static final int TAB_FOLDERS = 2;
    private static final int TAB_FAVORITES = 3;

    private static final int PERMISSION_REQUEST_CODE = 101;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // 1. Theme Setup
        settingsManager = new SettingsManager(this);
        if (settingsManager.isDarkTheme()) {
            setTheme(R.style.AppTheme_Dark);
        } else {
            setTheme(R.style.AppTheme_Light);
        }

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 2. Initialize Helpers
        dbHelper = new DatabaseHelper(this);
        videoScanner = new VideoScanner();

        // 3. Init UI & Listeners
        initViews();
        setupListeners();
        
        // 4. Apply Initial View State (List/Card/Grid)
        updateViewTypeUI();
        applyThemeColors(); // Manual fixes for non-attribute views

        // 5. Check Permissions & Load Data
        checkAndRequestPermission();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh data when returning from Player (History/Favorites might change)
        if (currentTab == TAB_HISTORY || currentTab == TAB_FAVORITES) {
            loadVideos();
        }
    }

    private void initViews() {
        gridView = findViewById(R.id.videos_grid);
        txtEmptyState = findViewById(R.id.txt_empty_state);
        appTitle = findViewById(R.id.app_title);
        etSearch = findViewById(R.id.et_search);
        
        btnViewType = findViewById(R.id.btn_view_type);
        btnMenu = findViewById(R.id.btn_menu);
        
        // Bottom Nav
        bottomNav = findViewById(R.id.bottom_nav);
        topContainer = findViewById(R.id.top_container);
        
        tabHistory = findViewById(R.id.tab_history);
        tabVideos = findViewById(R.id.tab_videos);
        tabFolders = findViewById(R.id.tab_folders);
        tabFavorites = findViewById(R.id.tab_favorites);
        
        iconHistory = findViewById(R.id.icon_history);
        iconVideos = findViewById(R.id.icon_videos);
        iconFolders = findViewById(R.id.icon_folders);
        iconFavorites = findViewById(R.id.icon_favorites);
    }

    private void setupListeners() {
        // --- 1. View Type Toggle ---
        btnViewType.setOnClickListener(v -> {
            int currentType = settingsManager.getViewType();
            int newType = (currentType + 1) % 3; // Cycle 0 -> 1 -> 2 -> 0
            
            settingsManager.setViewType(newType);
            updateViewTypeUI();
            
            // Re-set adapter to refresh layout inflation
            if (adapter != null) {
                adapter.setViewType(newType);
                gridView.setAdapter(adapter); // Force refresh
            }
        });

        // --- 2. Menu (Sorting & Theme) ---
        btnMenu.setOnClickListener(this::showPopupMenu);

        // --- 3. Search Logic ---
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterList(s.toString());
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });

        // --- 4. Bottom Navigation ---
        tabHistory.setOnClickListener(v -> switchTab(TAB_HISTORY));
        tabVideos.setOnClickListener(v -> switchTab(TAB_VIDEOS));
        tabFolders.setOnClickListener(v -> switchTab(TAB_FOLDERS));
        tabFavorites.setOnClickListener(v -> switchTab(TAB_FAVORITES));

        // --- 5. Grid Item Click ---
        gridView.setOnItemClickListener((parent, view, position, id) -> {
            VideoModel video = displayList.get(position);
            
            // Add to History DB
            dbHelper.addToHistory(video);
            
            Intent intent = new Intent(MainActivity.this, PlayerActivity.class);
            intent.putExtra("video_path", video.getPath());
            intent.putExtra("video_title", video.getTitle());
            startActivity(intent);
        });
    }

    // --- DATA LOADING LOGIC ---
    private void loadVideos() {
        new Thread(() -> {
            List<VideoModel> fetchedList = new ArrayList<>();

            switch (currentTab) {
                case TAB_HISTORY:
                    fetchedList = dbHelper.getAllHistory();
                    break;
                case TAB_FAVORITES:
                    fetchedList = dbHelper.getAllFavorites();
                    break;
                case TAB_FOLDERS:
                    // For now, let's just show all videos sorted by folder?
                    // Or placeholder. Let's keep it safe: Show All but toast
                    fetchedList = videoScanner.getAllVideos(this); 
                    break;
                case TAB_VIDEOS:
                default:
                    fetchedList = videoScanner.getAllVideos(this);
                    break;
            }

            // Apply Sort
            sortList(fetchedList);

            // Update UI
            final List<VideoModel> finalList = fetchedList;
            runOnUiThread(() -> {
                masterVideoList.clear();
                masterVideoList.addAll(finalList);
                displayList = new ArrayList<>(masterVideoList); // Init display list
                
                if (displayList.isEmpty()) {
                    txtEmptyState.setVisibility(View.VISIBLE);
                    txtEmptyState.setText(getEmptyText());
                    gridView.setVisibility(View.GONE);
                } else {
                    txtEmptyState.setVisibility(View.GONE);
                    gridView.setVisibility(View.VISIBLE);
                }

                if (adapter == null) {
                    adapter = new VideoAdapter(this, displayList, settingsManager.getViewType());
                    gridView.setAdapter(adapter);
                } else {
                    adapter.updateList(displayList);
                }
                
                // Re-apply filter if search text exists
                if (etSearch.getText().length() > 0) {
                    filterList(etSearch.getText().toString());
                }
            });
        }).start();
    }
    
    private String getEmptyText() {
        switch (currentTab) {
            case TAB_HISTORY: return "No history yet.";
            case TAB_FAVORITES: return "No favorites added.";
            default: return "No videos found.";
        }
    }

    // --- SORTING LOGIC ---
    private void sortList(List<VideoModel> list) {
        int sortOption = settingsManager.getSortOption();
        Collections.sort(list, new Comparator<VideoModel>() {
            @Override
            public int compare(VideoModel o1, VideoModel o2) {
                switch (sortOption) {
                    case SettingsManager.SORT_BY_NAME:
                        return o1.getTitle().compareToIgnoreCase(o2.getTitle());
                    case SettingsManager.SORT_BY_SIZE:
                        // Need raw size comparison, but VideoModel stores formatted string?
                        // Ideally VideoModel should store long size. 
                        // For now let's fallback to Name or Date if size raw missing.
                        // Assuming Scanner logic updated in future steps or basic String compare
                        return o1.getTitle().compareTo(o2.getTitle()); 
                    case SettingsManager.SORT_BY_DURATION:
                         return Long.compare(o2.getDurationMs(), o1.getDurationMs()); // Longest first
                    case SettingsManager.SORT_BY_DATE:
                    default:
                        return Long.compare(o2.getDateAdded(), o1.getDateAdded()); // Newest first
                }
            }
        });
    }

    private void filterList(String query) {
        if (query.isEmpty()) {
            displayList = new ArrayList<>(masterVideoList);
        } else {
            List<VideoModel> filtered = new ArrayList<>();
            for (VideoModel video : masterVideoList) {
                if (video.getTitle().toLowerCase().contains(query.toLowerCase())) {
                    filtered.add(video);
                }
            }
            displayList = filtered;
        }
        if (adapter != null) adapter.updateList(displayList);
    }

    // --- UI HELPERS ---
    private void switchTab(int tabIndex) {
        if (currentTab == tabIndex) return;
        currentTab = tabIndex;
        
        // 1. Update Icons
        float activeAlpha = 1.0f;
        float inactiveAlpha = 0.5f;
        
        iconHistory.setAlpha(inactiveAlpha);
        iconVideos.setAlpha(inactiveAlpha);
        iconFolders.setAlpha(inactiveAlpha);
        iconFavorites.setAlpha(inactiveAlpha);
        
        // Reset tints (Optional: set to primary color if active)
        // Simplest: just use alpha for now based on theme
        
        switch (tabIndex) {
            case TAB_HISTORY: iconHistory.setAlpha(activeAlpha); break;
            case TAB_VIDEOS: iconVideos.setAlpha(activeAlpha); break;
            case TAB_FOLDERS: 
                iconFolders.setAlpha(activeAlpha); 
                Toast.makeText(this, "Folder View Coming Soon", Toast.LENGTH_SHORT).show();
                break;
            case TAB_FAVORITES: iconFavorites.setAlpha(activeAlpha); break;
        }

        // 2. Load Data
        loadVideos();
    }

    private void updateViewTypeUI() {
        int type = settingsManager.getViewType();
        switch (type) {
            case SettingsManager.VIEW_TYPE_LIST:
                btnViewType.setImageResource(R.drawable.ic_list_view);
                gridView.setNumColumns(1);
                break;
            case SettingsManager.VIEW_TYPE_CARD:
                btnViewType.setImageResource(R.drawable.ic_card_view);
                gridView.setNumColumns(1);
                break;
            case SettingsManager.VIEW_TYPE_GRID:
                btnViewType.setImageResource(R.drawable.ic_grid_view);
                gridView.setNumColumns(2);
                break;
        }
    }

    private void showPopupMenu(View view) {
        PopupMenu popup = new PopupMenu(this, view);
        
        // Add items programmatically (No XML needed for simple menu)
        popup.getMenu().add(0, 1, 0, "Sort by Date");
        popup.getMenu().add(0, 2, 0, "Sort by Name (A-Z)");
        popup.getMenu().add(0, 3, 0, "Sort by Duration");
        popup.getMenu().add(0, 4, 0, "Toggle Theme");
        popup.getMenu().add(0, 5, 0, "About");

        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1: updateSort(SettingsManager.SORT_BY_DATE); return true;
                case 2: updateSort(SettingsManager.SORT_BY_NAME); return true;
                case 3: updateSort(SettingsManager.SORT_BY_DURATION); return true;
                case 4: 
                    settingsManager.setDarkTheme(!settingsManager.isDarkTheme());
                    recreate();
                    return true;
                case 5:
                    Toast.makeText(this, "Video Player Pro v1.0\nCreated by MSI", Toast.LENGTH_LONG).show();
                    return true;
            }
            return false;
        });
        popup.show();
    }

    private void updateSort(int option) {
        settingsManager.setSortOption(option);
        loadVideos(); // Reload and sort
    }

    // --- PERMISSION ---
    private void checkAndRequestPermission() {
        if (android.os.Build.VERSION.SDK_INT >= 33) { 
            if (checkSelfPermission("android.permission.READ_MEDIA_VIDEO") != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{"android.permission.READ_MEDIA_VIDEO"}, PERMISSION_REQUEST_CODE);
            } else {
                loadVideos();
            }
        } else if (android.os.Build.VERSION.SDK_INT >= 23) { 
            if (checkSelfPermission("android.permission.READ_EXTERNAL_STORAGE") != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{"android.permission.READ_EXTERNAL_STORAGE"}, PERMISSION_REQUEST_CODE);
            } else {
                loadVideos();
            }
        } else {
            loadVideos();
        }
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == PERMISSION_REQUEST_CODE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            loadVideos();
        } else {
            Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show();
        }
    }

    // --- THEME COLORS ---
    private void applyThemeColors() {
        boolean isDark = settingsManager.isDarkTheme();
        int colorBg = isDark ? 0xFF121212 : 0xFFFFFFFF; // Dark Surface vs Light Surface
        int colorText = isDark ? 0xFFEEEEEE : 0xFF212121;
        
        // Tint Nav Icons
        int iconColor = isDark ? 0xFFFFFFFF : 0xFF000000;
        iconHistory.setColorFilter(iconColor);
        iconVideos.setColorFilter(iconColor);
        iconFolders.setColorFilter(iconColor);
        iconFavorites.setColorFilter(iconColor);
        
        // Search & View Buttons
        btnViewType.setColorFilter(colorText);
        btnMenu.setColorFilter(colorText);
    }
}