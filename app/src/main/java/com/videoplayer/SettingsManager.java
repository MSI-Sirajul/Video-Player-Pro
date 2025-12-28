package com.videoplayer;

import android.content.Context;
import android.content.SharedPreferences;

public class SettingsManager {

    private SharedPreferences preferences;
    private SharedPreferences.Editor editor;
    private static final String PREF_NAME = "VideoPlayerSettings";

    // Keys
    private static final String KEY_SORT_TYPE = "sort_type";
    private static final String KEY_VIEW_MODE = "view_mode";
    private static final String KEY_DEFAULT_HOME = "default_home"; 
    private static final String KEY_THEME = "app_theme"; 
    
    private static final String KEY_SHOW_HIDDEN = "show_hidden_nomedia";
    private static final String KEY_HIDE_SHORT = "hide_short_videos"; 

    // Constants
    public static final int VIEW_LIST = 0;
    public static final int VIEW_GRID = 1;

    public static final int THEME_SYSTEM = 0;
    public static final int THEME_LIGHT = 1;
    public static final int THEME_DARK = 2;

    // --- Sorting Constants (FIXED: Added missing ones) ---
    public static final int SORT_NAME_AZ = 0;
    public static final int SORT_NAME_ZA = 1; // Added
    public static final int SORT_DATE_NEW = 2;
    public static final int SORT_DATE_OLD = 3; // Added
    public static final int SORT_SIZE_LARGE = 4;
    public static final int SORT_SIZE_SMALL = 5; // Added
    public static final int SORT_DURATION = 6;

    public SettingsManager(Context context) {
        preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        editor = preferences.edit();
    }

    // --- Methods ---
    public void setSortType(int type) {
        editor.putInt(KEY_SORT_TYPE, type).apply();
    }
    public int getSortType() {
        return preferences.getInt(KEY_SORT_TYPE, SORT_DATE_NEW);
    }

    public void setViewMode(int mode) {
        editor.putInt(KEY_VIEW_MODE, mode).apply();
    }
    public int getViewMode() {
        return preferences.getInt(KEY_VIEW_MODE, VIEW_LIST);
    }

    public void setDefaultHome(int tabIndex) {
        editor.putInt(KEY_DEFAULT_HOME, tabIndex).apply();
    }
    public int getDefaultHome() {
        return preferences.getInt(KEY_DEFAULT_HOME, 0);
    }

    public void setAppTheme(int themeMode) {
        editor.putInt(KEY_THEME, themeMode).apply();
    }
    public int getAppTheme() {
        return preferences.getInt(KEY_THEME, THEME_SYSTEM);
    }

    public void setShowHidden(boolean show) {
        editor.putBoolean(KEY_SHOW_HIDDEN, show).apply();
    }
    public boolean getShowHidden() {
        return preferences.getBoolean(KEY_SHOW_HIDDEN, false);
    }

    public void setHideShortVideos(boolean hide) {
        editor.putBoolean(KEY_HIDE_SHORT, hide).apply();
    }
    public boolean getHideShortVideos() {
        return preferences.getBoolean(KEY_HIDE_SHORT, true);
    }
}