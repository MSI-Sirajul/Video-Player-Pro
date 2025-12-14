package com.msi.videoplayer;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class VideoScanner {

    public List<VideoModel> getAllVideos(Context context) {
        List<VideoModel> videoList = new ArrayList<>();
        
        Uri uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
        
        String[] projection = {
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DATA, // Path
                MediaStore.Video.Media.TITLE,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.DURATION,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.DATE_ADDED,
                MediaStore.Video.Media.RESOLUTION
        };

        // Sort by Date Added (Newest first)
        String sortOrder = MediaStore.Video.Media.DATE_ADDED + " DESC";

        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(uri, projection, null, null, sortOrder);

            if (cursor != null) {
                int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID);
                int pathColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA);
                int titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE);
                int nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME);
                int durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION);
                int sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE);
                int dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED);
                int resColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.RESOLUTION);

                while (cursor.moveToNext()) {
                    String id = cursor.getString(idColumn);
                    String path = cursor.getString(pathColumn);
                    String title = cursor.getString(titleColumn);
                    String fileName = cursor.getString(nameColumn);
                    long durationMs = cursor.getLong(durationColumn);
                    long sizeBytes = cursor.getLong(sizeColumn);
                    long dateAdded = cursor.getLong(dateColumn);
                    String resolution = cursor.getString(resColumn);

                    // Skip invalid or 0 second videos
                    if (path == null || durationMs <= 0) continue;

                    // Format Duration
                    String durationFormatted = TimeUtils.formatDuration(durationMs);
                    
                    // Format Size (MB/GB)
                    String sizeFormatted = formatSize(sizeBytes);

                    // Extract Folder Name from Path
                    File file = new File(path);
                    String folderName = "Unknown";
                    if (file.getParentFile() != null) {
                        folderName = file.getParentFile().getName();
                    }
                    
                    // Handle null resolution
                    if (resolution == null) resolution = "HD";

                    VideoModel video = new VideoModel(
                            id, path, title, fileName, 
                            durationFormatted, durationMs, resolution, 
                            sizeFormatted, folderName, dateAdded
                    );
                    
                    videoList.add(video);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        
        return videoList;
    }

    // Helper to format file size
    private String formatSize(long sizeBytes) {
        long kb = 1024;
        long mb = kb * 1024;
        long gb = mb * 1024;

        if (sizeBytes >= gb) {
            return String.format(java.util.Locale.US, "%.2f GB", (float) sizeBytes / gb);
        } else if (sizeBytes >= mb) {
            return String.format(java.util.Locale.US, "%.2f MB", (float) sizeBytes / mb);
        } else {
            return String.format(java.util.Locale.US, "%d KB", sizeBytes / kb);
        }
    }
}