package com.video.player.pro;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.ThumbnailUtils;
import android.os.Build;
import android.os.CancellationSignal;
import android.provider.MediaStore;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class VideoAdapter extends RecyclerView.Adapter<VideoAdapter.VideoViewHolder> {

    private Context context;
    private List<VideoModel> videoList;
    private VideoClickListener listener; // INTERFACE
    private ExecutorService executorService = Executors.newFixedThreadPool(4);
    
    private int currentViewMode = 0; // 0=List, 1=Grid, 2=Card

    // 1. Interface Definition
    public interface VideoClickListener {
        void onVideoClick(VideoModel video);
    }

    // 2. Updated Constructor
    public VideoAdapter(Context context, List<VideoModel> videoList, VideoClickListener listener) {
        this.context = context;
        this.videoList = videoList;
        this.listener = listener;
    }

    public void updateList(List<VideoModel> newList) {
        this.videoList = new ArrayList<>(newList);
        notifyDataSetChanged();
    }
    
    public void setViewMode(int viewMode) {
        this.currentViewMode = viewMode;
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        if (currentViewMode == 0) return 0; // List
        else return 1; // Grid/Card
    }

    @NonNull
    @Override
    public VideoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view;
        if (viewType == 1) { 
            view = LayoutInflater.from(context).inflate(R.layout.item_video_card, parent, false);
        } else {
            view = LayoutInflater.from(context).inflate(R.layout.item_video_list, parent, false);
        }
        return new VideoViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VideoViewHolder holder, int position) {
        VideoModel video = videoList.get(position);

        holder.title.setText(video.getTitle());
        holder.duration.setText(video.getDuration());
        if (holder.size != null) holder.size.setText(video.getSize());

        holder.thumbnail.setImageResource(android.R.drawable.ic_menu_gallery);

        executorService.execute(() -> {
            Bitmap bitmap = null;
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    bitmap = context.getContentResolver().loadThumbnail(
                            video.getContentUri(), new Size(640, 480), new CancellationSignal());
                } else {
                    bitmap = ThumbnailUtils.createVideoThumbnail(
                            video.getPath(), MediaStore.Images.Thumbnails.MINI_KIND);
                }
            } catch (IOException e) { e.printStackTrace(); }

            if (bitmap != null) {
                Bitmap finalBitmap = bitmap;
                holder.itemView.post(() -> holder.thumbnail.setImageBitmap(finalBitmap));
            }
        });

        // 3. Interface Callback
        holder.itemView.setOnClickListener(v -> {
            // Update Playlist helper for Next/Prev logic
            VideoPlaylist.setPlaylist(videoList, position);
            listener.onVideoClick(video); 
        });
    }

    @Override
    public int getItemCount() {
        return videoList.size();
    }

    public static class VideoViewHolder extends RecyclerView.ViewHolder {
        ImageView thumbnail;
        TextView title, duration, size;

        public VideoViewHolder(@NonNull View itemView) {
            super(itemView);
            thumbnail = itemView.findViewById(R.id.thumbnail);
            title = itemView.findViewById(R.id.videoTitle);
            duration = itemView.findViewById(R.id.videoDuration);
            size = itemView.findViewById(R.id.videoSize);
        }
    }
}