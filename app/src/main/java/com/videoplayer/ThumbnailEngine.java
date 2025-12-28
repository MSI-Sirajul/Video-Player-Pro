package com.videoplayer;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class ThumbnailEngine {

    // ভিডিও পাথ থেকে থাম্বনেইল জেনারেট করে ক্যাশ ডিরেক্টরিতে সেভ করে পাথ রিটার্ন করবে
    public static String getThumbnailPath(Context context, String videoPath, String videoId) {
        // ক্যাশ ফোল্ডার পাথ
        File cacheDir = new File(context.getCacheDir(), "video_thumbnails");
        if (!cacheDir.exists()) cacheDir.mkdirs();

        // ইউনিক ফাইলের নাম (Video ID দিয়ে)
        File thumbFile = new File(cacheDir, "thumb_" + videoId + ".jpg");

        // যদি অলরেডি জেনারেট করা থাকে, তবে সেই পাথ রিটার্ন করো (Instant Load)
        if (thumbFile.exists()) {
            return thumbFile.getAbsolutePath();
        }

        // যদি না থাকে, নতুন জেনারেট করো
        Bitmap bitmap = null;
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(videoPath);
            // ১ সেকেন্ডের ফ্রেম নেওয়া
            bitmap = retriever.getFrameAtTime(1000000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // FIX: IOException এর বদলে Exception ব্যবহার করা হয়েছে
            try { 
                retriever.release(); 
            } catch (Exception e) { 
                e.printStackTrace(); 
            }
        }

        if (bitmap != null) {
            try (FileOutputStream out = new FileOutputStream(thumbFile)) {
                // ইমেজ কম্প্রেস করে সেভ করা (Quality 60%)
                bitmap.compress(Bitmap.CompressFormat.JPEG, 60, out);
                return thumbFile.getAbsolutePath();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // যদি ফেইল করে, নাল রিটার্ন করবে
        return null;
    }
}