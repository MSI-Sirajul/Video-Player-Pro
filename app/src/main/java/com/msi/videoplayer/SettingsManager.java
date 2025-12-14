package com.msi.videoplayer;

import android.content.Context;
import android.content.SharedPreferences;

public class SettingsManager {
    private static final String PREF_NAME = "VideoPlayerPrefs";

    // Preference Keys
    private static final String KEY_DARK_THEME = "is_dark_theme";
    private static final String KEY_SORT_OPTION = "sort_option";
    private static final String KEY_VIEW_TYPE = "view_type";

    // Sorting Constants
    public static final int SORT_BY_DATE = 0;     // Default
    public static final int SORT_BY_NAME = 1;     // A-Z
    public static final int SORT_BY_SIZE = 2;
    public static final int SORT_BY_DURATION = 3;

    // View Type Constants
    public static final int VIEW_TYPE_LIST = 0;   // Default
    public static final int VIEW_TYPE_CARD = 1;
    public static final int VIEW_TYPE_GRID = 2;

    private SharedPreferences prefs;
    private SharedPreferences.Editor editor;

    public SettingsManager(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        editor = prefs.edit();
    }

    // --- Theme Settings ---
    public void setDarkTheme(boolean isDark) {
        editor.putBoolean(KEY_DARK_THEME, isDark);
        editor.apply();
    }

    public boolean isDarkTheme() {
        return prefs.getBoolean(KEY_DARK_THEME, false); // Default is Light
    }

    // --- Sorting Settings ---
    public void setSortOption(int sortOption) {
        editor.putInt(KEY_SORT_OPTION, sortOption);
        editor.apply();
    }

    public int getSortOption() {
        return prefs.getInt(KEY_SORT_OPTION, SORT_BY_DATE); // Default is Date
    }

    // --- View Type Settings ---
    public void setViewType(int viewType) {
        editor.putInt(KEY_VIEW_TYPE, viewType);
        editor.apply();
    }

    public int getViewType() {
        return prefs.getInt(KEY_VIEW_TYPE, VIEW_TYPE_LIST); // Default is List
    }
}