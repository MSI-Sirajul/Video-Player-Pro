package com.videoplayer;

public class VideoItem {
    String id, title, path, duration, folderName, thumbPath;
    String size;      // New for Sorting
    long dateAdded;   // New for Sorting

    // Constructor Updated
    public VideoItem(String id, String title, String path, String duration, String folderName, String thumbPath, String size, long dateAdded) {
        this.id = id;
        this.title = title;
        this.path = path;
        this.duration = duration;
        this.folderName = folderName;
        this.thumbPath = thumbPath;
        this.size = size;
        this.dateAdded = dateAdded;
    }

    // Getters
    public String getPath() { return path; }
    public String getTitle() { return title; }
    public String getDuration() { return duration; }
    public String getThumbPath() { return thumbPath; }
    public String getSize() { return size; }
    public long getDateAdded() { return dateAdded; }
}