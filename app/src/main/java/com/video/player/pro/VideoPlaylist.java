package com.video.player.pro;

import java.util.ArrayList;
import java.util.List;

public class VideoPlaylist {
    public static List<VideoModel> currentVideoList = new ArrayList<>();
    public static int currentPosition = 0;

    // লিস্ট ক্লিয়ার এবং সেট করার মেথড
    public static void setPlaylist(List<VideoModel> list, int position) {
        currentVideoList.clear();
        currentVideoList.addAll(list);
        currentPosition = position;
    }

    // পরবর্তী ভিডিও পাওয়ার মেথড
    public static VideoModel getNextVideo() {
        if (currentVideoList.isEmpty()) return null;
        if (currentPosition < currentVideoList.size() - 1) {
            currentPosition++;
            return currentVideoList.get(currentPosition);
        }
        return null; // লিস্টের শেষে পৌঁছে গেছে
    }

    // আগের ভিডিও পাওয়ার মেথড
    public static VideoModel getPrevVideo() {
        if (currentVideoList.isEmpty()) return null;
        if (currentPosition > 0) {
            currentPosition--;
            return currentVideoList.get(currentPosition);
        }
        return null; // লিস্টের শুরুতে আছে
    }
    
    // বর্তমান ভিডিও
    public static VideoModel getCurrentVideo() {
        if (currentVideoList.isEmpty() || currentPosition < 0 || currentPosition >= currentVideoList.size()) {
            return null;
        }
        return currentVideoList.get(currentPosition);
    }
}