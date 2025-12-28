package com.videoplayer;

import android.content.Context;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import java.io.File;
import java.util.ArrayList;

public class VideoAdapter extends RecyclerView.Adapter<VideoAdapter.VideoViewHolder> {

    private Context context;
    private ArrayList<VideoItem> videoList;
    private ArrayList<VideoItem> sourceList; // সার্চের জন্য ব্যাকআপ
    private OnItemClickListener listener;
    private int currentViewMode = SettingsManager.VIEW_LIST; // ডিফল্ট লিস্ট ভিউ
    private int lastPosition = -1; // এনিমেশনের জন্য

    public interface OnItemClickListener {
        void onClick(int position);
    }

    // Constructor Updated
    public VideoAdapter(Context context, ArrayList<VideoItem> videoList, OnItemClickListener listener) {
        this.context = context;
        this.videoList = videoList;
        this.sourceList = new ArrayList<>(videoList);
        this.listener = listener;
        
        // বর্তমান সেটিংস চেক করে ভিউ মোড সেট করা
        SettingsManager settingsManager = new SettingsManager(context);
        this.currentViewMode = settingsManager.getViewMode();
    }

    // ভিউ মোড আপডেট করার মেথড (MainActivity থেকে কল হবে)
    public void updateViewMode(int viewMode) {
        this.currentViewMode = viewMode;
        notifyDataSetChanged(); // পুরো লিস্ট রিফ্রেশ করবে নতুন লেআউট দিয়ে
    }

    @NonNull
    @Override
    public VideoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view;
        // কন্ডিশন অনুযায়ী লেআউট ইনফ্লেট করা
        if (currentViewMode == SettingsManager.VIEW_GRID) {
            view = LayoutInflater.from(context).inflate(R.layout.item_video_grid, parent, false);
        } else {
            view = LayoutInflater.from(context).inflate(R.layout.item_video, parent, false);
        }
        return new VideoViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VideoViewHolder holder, int position) {
        VideoItem video = videoList.get(position);

        // ১. টাইটেল সেট করা
        holder.tvTitle.setText(video.getTitle());
        
        // ২. ডিউরেশন সেট করা
        holder.tvDuration.setText(video.getDuration());

        // ৩. সাইজ সেট করা (শুধুমাত্র গ্রিড ভিউতে tvSize আছে, তাই নাল চেক জরুরি)
        if (holder.tvSize != null) {
            try {
                long sizeBytes = Long.parseLong(video.getSize());
                holder.tvSize.setText(Formatter.formatFileSize(context, sizeBytes));
            } catch (Exception e) {
                holder.tvSize.setText(video.getSize());
            }
        }

        // ৪. থাম্বনেইল লোডিং (Glide) - অপটিমাইজড
        Object imageSource;
        // যদি আমাদের জেনারেট করা থাম্বনেইল থাকে তবে সেটি ব্যবহার করো, নাহলে ভিডিও পাথ
        if (video.getThumbPath() != null && new File(video.getThumbPath()).exists()) {
            imageSource = video.getThumbPath();
        } else {
            imageSource = video.getPath();
        }

        Glide.with(context)
                .load(imageSource)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .centerCrop()
                .placeholder(R.color.secondary_bg)
                .error(android.R.drawable.stat_notify_error)
                .into(holder.imgThumbnail);

        // ৫. ক্লিক লিসেনার
        holder.itemView.setOnClickListener(v -> listener.onClick(position));

        // ৬. মেনু ক্লিক
        if (holder.imgMenu != null) {
            holder.imgMenu.setOnClickListener(v -> {
                // TODO: Open Popup Menu (Delete, Share, Properties)
                // পরবর্তী আপডেটে এটি যুক্ত হবে
            });
        }

        // ৭. এনিমেশন
        setAnimation(holder.itemView, position);
    }

    private void setAnimation(View viewToAnimate, int position) {
        if (position > lastPosition) {
            // গ্রিড হলে ফেইড ইন, লিস্ট হলে স্লাইড আপ
            int animRes = (currentViewMode == SettingsManager.VIEW_GRID) ? android.R.anim.fade_in : android.R.anim.slide_in_left;
            Animation animation = AnimationUtils.loadAnimation(context, animRes);
            animation.setDuration(300);
            viewToAnimate.startAnimation(animation);
            lastPosition = position;
        }
    }

    @Override
    public int getItemCount() {
        return videoList.size();
    }
    
    @Override
    public void onViewDetachedFromWindow(@NonNull VideoViewHolder holder) {
        holder.itemView.clearAnimation();
    }

    // সার্চ ফিল্টারিং মেথড
    public void filter(String text) {
        videoList.clear();
        if (text.isEmpty()) {
            videoList.addAll(sourceList);
        } else {
            text = text.toLowerCase();
            for (VideoItem item : sourceList) {
                if (item.getTitle().toLowerCase().contains(text)) {
                    videoList.add(item);
                }
            }
        }
        notifyDataSetChanged();
    }
    
    // ডাটা রিফ্রেশ করার মেথড
    public void updateList(ArrayList<VideoItem> newList) {
        videoList = new ArrayList<>(newList);
        sourceList = new ArrayList<>(newList);
        notifyDataSetChanged();
    }

    // ViewHolder Class
    public class VideoViewHolder extends RecyclerView.ViewHolder {
        ImageView imgThumbnail, imgMenu;
        TextView tvTitle, tvDuration, tvSize; // tvSize নতুন যুক্ত হয়েছে

        public VideoViewHolder(@NonNull View itemView) {
            super(itemView);
            // কমন ভিউগুলো
            imgThumbnail = itemView.findViewById(R.id.img_thumbnail);
            imgMenu = itemView.findViewById(R.id.img_menu_more);
            tvTitle = itemView.findViewById(R.id.tv_title);
            tvDuration = itemView.findViewById(R.id.tv_duration);
            
            // গ্রিড ভিউতে এক্সট্রা ভিউ থাকতে পারে (tvSize)
            // লিস্ট ভিউতে এটি নেই, তাই null হতে পারে। Bind করার সময় চেক করতে হবে।
            tvSize = itemView.findViewById(R.id.tv_size);
        }
    }
}