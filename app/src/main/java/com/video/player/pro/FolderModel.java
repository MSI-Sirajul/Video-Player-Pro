package com.video.player.pro;

public class FolderModel {
    private String bucketId;
    private String bucketName;
    private int videoCount;
    private String firstVideoPath; // থাম্বনেইল দেখানোর জন্য ফোল্ডারের প্রথম ভিডিওর পাথ

    public FolderModel(String bucketId, String bucketName, String firstVideoPath) {
        this.bucketId = bucketId;
        this.bucketName = bucketName;
        this.firstVideoPath = firstVideoPath;
        this.videoCount = 1; // ইনিশিয়াল কাউন্ট
    }

    public String getBucketId() { return bucketId; }
    public String getBucketName() { return bucketName; }
    public String getFirstVideoPath() { return firstVideoPath; }
    
    public int getVideoCount() { return videoCount; }
    public void incrementVideoCount() { this.videoCount++; }
}