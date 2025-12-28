package com.videoplayer;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import java.io.File;
import java.util.ArrayList;

public class QueueAdapter extends RecyclerView.Adapter<QueueAdapter.QueueViewHolder> {

    private Context context;
    private ArrayList<VideoItem> videoList;
    private OnItemClickListener listener;
    private int currentPlayingPosition = -1; // হাইলাইট করার জন্য

    public interface OnItemClickListener {
        void onClick(int position);
    }

    public QueueAdapter(Context context, ArrayList<VideoItem> videoList, OnItemClickListener listener) {
        this.context = context;
        this.videoList = videoList;
        this.listener = listener;
    }
    
    // হাইলাইট আপডেট করার মেথড
    public void updateCurrentPosition(int pos) {
        this.currentPlayingPosition = pos;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public QueueViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // কম্প্যাক্ট লেআউট ব্যবহার করা হচ্ছে
        View view = LayoutInflater.from(context).inflate(R.layout.item_queue_compact, parent, false);
        return new QueueViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull QueueViewHolder holder, int position) {
        VideoItem video = videoList.get(position);

        holder.tvTitle.setText(video.getTitle());
        holder.tvDuration.setText(video.getDuration());
        
        // কারেন্ট ভিডিও হাইলাইট করা
        if (position == currentPlayingPosition) {
            holder.tvTitle.setTextColor(ContextCompat.getColor(context, R.color.colorAccent));
            holder.tvStatus.setVisibility(View.VISIBLE);
        } else {
            holder.tvTitle.setTextColor(ContextCompat.getColor(context, R.color.white));
            holder.tvStatus.setVisibility(View.GONE);
        }

        // থাম্বনেইল লোড
        Object imageSource;
        if (video.getThumbPath() != null && new File(video.getThumbPath()).exists()) {
            imageSource = video.getThumbPath();
        } else {
            imageSource = video.getPath();
        }

        Glide.with(context)
                .load(imageSource)
                .centerCrop()
                .placeholder(android.R.color.darker_gray)
                .into(holder.imgThumbnail);

        holder.itemView.setOnClickListener(v -> listener.onClick(position));
    }

    @Override
    public int getItemCount() {
        return videoList.size();
    }

    public class QueueViewHolder extends RecyclerView.ViewHolder {
        ImageView imgThumbnail;
        TextView tvTitle, tvDuration, tvStatus;

        public QueueViewHolder(@NonNull View itemView) {
            super(itemView);
            imgThumbnail = itemView.findViewById(R.id.img_thumbnail);
            tvTitle = itemView.findViewById(R.id.tv_title);
            tvDuration = itemView.findViewById(R.id.tv_duration);
            tvStatus = itemView.findViewById(R.id.tv_status);
        }
    }
}