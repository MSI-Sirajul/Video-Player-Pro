package com.video.player.pro;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class FolderAdapter extends RecyclerView.Adapter<FolderAdapter.FolderViewHolder> {

    private Context context;
    private List<FolderModel> folderList;
    private OnFolderClickListener listener;
    
    // View Mode Support
    private int currentViewMode = 0; // 0=List, 1=Grid

    public interface OnFolderClickListener {
        void onFolderClick(FolderModel folder);
    }

    public FolderAdapter(Context context, List<FolderModel> folderList, OnFolderClickListener listener) {
        this.context = context;
        this.folderList = folderList;
        this.listener = listener;
    }
    
    public void setViewMode(int mode) {
        this.currentViewMode = mode;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public FolderViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Grid মোডে আমরা একই লেআউট ব্যবহার করছি, কিন্তু মেইন অ্যাক্টিভিটি থেকে LayoutManager চেঞ্জ হবে
        // আপনি চাইলে item_folder_grid.xml আলাদা বানাতে পারেন, তবে আপাতত একই লেআউট কাজ করবে
        View view = LayoutInflater.from(context).inflate(R.layout.item_folder, parent, false);
        return new FolderViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FolderViewHolder holder, int position) {
        FolderModel folder = folderList.get(position);
        holder.name.setText(folder.getBucketName());
        holder.count.setText(folder.getVideoCount() + " Videos");
        
        // আইকন কালার টিন্ট (থিম অনুযায়ী)
        // holder.icon.setColorFilter(...); // প্রয়োজন হলে

        holder.itemView.setOnClickListener(v -> listener.onFolderClick(folder));
    }

    @Override
    public int getItemCount() {
        return folderList.size();
    }

    public static class FolderViewHolder extends RecyclerView.ViewHolder {
        TextView name, count;
        ImageView icon;
        public FolderViewHolder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.folderName);
            count = itemView.findViewById(R.id.videoCount);
            // icon = itemView.findViewById(R.id.folderIcon); // যদি item_folder.xml এ আইডি থাকে
        }
    }
}