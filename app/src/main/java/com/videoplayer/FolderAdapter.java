package com.videoplayer;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;

public class FolderAdapter extends RecyclerView.Adapter<FolderAdapter.FolderViewHolder> {

    private Context context;
    private ArrayList<String> folderList;
    private OnFolderClickListener listener;

    public interface OnFolderClickListener {
        void onFolderClick(String folderName);
    }

    public FolderAdapter(Context context, ArrayList<String> folderList, OnFolderClickListener listener) {
        this.context = context;
        this.folderList = folderList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public FolderViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_folder, parent, false);
        return new FolderViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FolderViewHolder holder, int position) {
        String folderName = folderList.get(position);
        holder.tvFolderName.setText(folderName);
        
        holder.itemView.setOnClickListener(v -> listener.onFolderClick(folderName));
    }

    @Override
    public int getItemCount() {
        return folderList.size();
    }

    public class FolderViewHolder extends RecyclerView.ViewHolder {
        TextView tvFolderName;
        ImageView imgFolderIcon;

        public FolderViewHolder(@NonNull View itemView) {
            super(itemView);
            tvFolderName = itemView.findViewById(R.id.tv_folder_name);
            imgFolderIcon = itemView.findViewById(R.id.img_folder_icon);
        }
    }
}