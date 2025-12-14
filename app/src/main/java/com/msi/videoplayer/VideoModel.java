package com.msi.videoplayer;

import java.io.Serializable;

public class VideoModel implements Serializable {
    private String id;
    private String path;
    private String title;
    private String fileName;
    private String duration; // Formatted string like "05:30"
    private long durationMs; // Raw duration in millis
    private String resolution;
    private String size;
    private String folderName;
    private long dateAdded;

    public VideoModel(String id, String path, String title, String fileName, String duration, long durationMs, String resolution, String size, String folderName, long dateAdded) {
        this.id = id;
        this.path = path;
        this.title = title;
        this.fileName = fileName;
        this.duration = duration;
        this.durationMs = durationMs;
        this.resolution = resolution;
        this.size = size;
        this.folderName = folderName;
        this.dateAdded = dateAdded;
    }

    public String getId() { return id; }
    public String getPath() { return path; }
    public String getTitle() { return title; }
    public String getFileName() { return fileName; }
    public String getDuration() { return duration; }
    public long getDurationMs() { return durationMs; }
    public String getResolution() { return resolution; }
    public String getSize() { return size; }
    public String getFolderName() { return folderName; }
    public long getDateAdded() { return dateAdded; }
}