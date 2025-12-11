package com.video.player.pro;

import android.net.Uri;

public class VideoModel {
    private String id;
    private String title;
    private String duration;
    private String path; // New: For file operations
    private String size; // New: For details
    private Uri contentUri;

    public VideoModel(String id, String title, String duration, String path, String size, Uri contentUri) {
        this.id = id;
        this.title = title;
        this.duration = duration;
        this.path = path;
        this.size = size;
        this.contentUri = contentUri;
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getDuration() { return duration; }
    public String getPath() { return path; }
    public String getSize() { return size; }
    public Uri getContentUri() { return contentUri; }
}