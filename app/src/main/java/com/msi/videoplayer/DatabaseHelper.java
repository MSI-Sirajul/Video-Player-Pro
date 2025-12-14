package com.msi.videoplayer;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "VideoPlayer.db";
    private static final int DATABASE_VERSION = 1;

    // Table Names
    private static final String TABLE_HISTORY = "history";
    private static final String TABLE_FAVORITES = "favorites";

    // Common Column Names
    private static final String COL_ID = "id";
    private static final String COL_PATH = "path";
    private static final String COL_TITLE = "title";
    private static final String COL_DURATION = "duration";
    private static final String COL_DURATION_MS = "duration_ms";
    private static final String COL_RESOLUTION = "resolution";
    private static final String COL_SIZE = "size";
    private static final String COL_DATE_ADDED = "date_added";
    private static final String COL_TIMESTAMP = "timestamp"; // For sorting history

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // Create History Table
        String createHistoryTable = "CREATE TABLE " + TABLE_HISTORY + " (" +
                COL_PATH + " TEXT PRIMARY KEY, " + // Path is unique
                COL_ID + " TEXT, " +
                COL_TITLE + " TEXT, " +
                COL_DURATION + " TEXT, " +
                COL_DURATION_MS + " INTEGER, " +
                COL_RESOLUTION + " TEXT, " +
                COL_SIZE + " TEXT, " +
                COL_DATE_ADDED + " INTEGER, " +
                COL_TIMESTAMP + " INTEGER)";
        db.execSQL(createHistoryTable);

        // Create Favorites Table
        String createFavoritesTable = "CREATE TABLE " + TABLE_FAVORITES + " (" +
                COL_PATH + " TEXT PRIMARY KEY, " +
                COL_ID + " TEXT, " +
                COL_TITLE + " TEXT, " +
                COL_DURATION + " TEXT, " +
                COL_DURATION_MS + " INTEGER, " +
                COL_RESOLUTION + " TEXT, " +
                COL_SIZE + " TEXT, " +
                COL_DATE_ADDED + " INTEGER)";
        db.execSQL(createFavoritesTable);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_HISTORY);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_FAVORITES);
        onCreate(db);
    }

    // --- HISTORY METHODS ---

    public void addToHistory(VideoModel video) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();

        values.put(COL_PATH, video.getPath());
        values.put(COL_ID, video.getId());
        values.put(COL_TITLE, video.getTitle());
        values.put(COL_DURATION, video.getDuration());
        values.put(COL_DURATION_MS, video.getDurationMs());
        values.put(COL_RESOLUTION, video.getResolution());
        values.put(COL_SIZE, video.getSize());
        values.put(COL_DATE_ADDED, video.getDateAdded());
        values.put(COL_TIMESTAMP, System.currentTimeMillis()); // Current time for sorting

        // Insert or Replace (Update timestamp if already exists)
        db.insertWithOnConflict(TABLE_HISTORY, null, values, SQLiteDatabase.CONFLICT_REPLACE);
        db.close();
    }

    public List<VideoModel> getAllHistory() {
        List<VideoModel> historyList = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();

        // Sort by Timestamp DESC (Newest played first)
        Cursor cursor = db.query(TABLE_HISTORY, null, null, null, null, null, COL_TIMESTAMP + " DESC");

        if (cursor != null && cursor.moveToFirst()) {
            do {
                VideoModel video = extractVideoFromCursor(cursor);
                historyList.add(video);
            } while (cursor.moveToNext());
            cursor.close();
        }
        db.close();
        return historyList;
    }

    public void clearHistory() {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_HISTORY, null, null);
        db.close();
    }

    // --- FAVORITES METHODS ---

    public void toggleFavorite(VideoModel video) {
        if (isFavorite(video.getPath())) {
            removeFavorite(video.getPath());
        } else {
            addFavorite(video);
        }
    }

    public void addFavorite(VideoModel video) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();

        values.put(COL_PATH, video.getPath());
        values.put(COL_ID, video.getId());
        values.put(COL_TITLE, video.getTitle());
        values.put(COL_DURATION, video.getDuration());
        values.put(COL_DURATION_MS, video.getDurationMs());
        values.put(COL_RESOLUTION, video.getResolution());
        values.put(COL_SIZE, video.getSize());
        values.put(COL_DATE_ADDED, video.getDateAdded());

        db.insertWithOnConflict(TABLE_FAVORITES, null, values, SQLiteDatabase.CONFLICT_REPLACE);
        db.close();
    }

    public void removeFavorite(String path) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_FAVORITES, COL_PATH + "=?", new String[]{path});
        db.close();
    }

    public boolean isFavorite(String path) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_FAVORITES, new String[]{COL_PATH}, COL_PATH + "=?", new String[]{path}, null, null, null);
        boolean exists = (cursor.getCount() > 0);
        cursor.close();
        db.close();
        return exists;
    }

    public List<VideoModel> getAllFavorites() {
        List<VideoModel> favList = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();

        Cursor cursor = db.query(TABLE_FAVORITES, null, null, null, null, null, COL_DATE_ADDED + " DESC");

        if (cursor != null && cursor.moveToFirst()) {
            do {
                VideoModel video = extractVideoFromCursor(cursor);
                favList.add(video);
            } while (cursor.moveToNext());
            cursor.close();
        }
        db.close();
        return favList;
    }

    // Helper Method to convert Cursor data to VideoModel
    private VideoModel extractVideoFromCursor(Cursor cursor) {
        String id = cursor.getString(cursor.getColumnIndexOrThrow(COL_ID));
        String path = cursor.getString(cursor.getColumnIndexOrThrow(COL_PATH));
        String title = cursor.getString(cursor.getColumnIndexOrThrow(COL_TITLE));
        String duration = cursor.getString(cursor.getColumnIndexOrThrow(COL_DURATION));
        long durationMs = cursor.getLong(cursor.getColumnIndexOrThrow(COL_DURATION_MS));
        String resolution = cursor.getString(cursor.getColumnIndexOrThrow(COL_RESOLUTION));
        String size = cursor.getString(cursor.getColumnIndexOrThrow(COL_SIZE));
        long dateAdded = cursor.getLong(cursor.getColumnIndexOrThrow(COL_DATE_ADDED));
        
        // Extract filename from path since we don't store it separately
        String fileName = title; 
        try {
            java.io.File file = new java.io.File(path);
            fileName = file.getName();
        } catch (Exception e) {}

        // Extract folder name from path
        String folderName = "Unknown";
        try {
            java.io.File file = new java.io.File(path);
            if (file.getParentFile() != null) {
                folderName = file.getParentFile().getName();
            }
        } catch (Exception e) {}

        return new VideoModel(id, path, title, fileName, duration, durationMs, resolution, size, folderName, dateAdded);
    }
}