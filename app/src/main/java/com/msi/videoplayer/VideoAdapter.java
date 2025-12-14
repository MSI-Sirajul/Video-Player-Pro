package com.msi.videoplayer;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.ThumbnailUtils;
import android.os.AsyncTask;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import java.lang.ref.WeakReference;
import java.util.List;

public class VideoAdapter extends BaseAdapter {

    private Context context;
    private List<VideoModel> videoList;
    private int currentViewType; // 0=List, 1=Card, 2=Grid

    public VideoAdapter(Context context, List<VideoModel> videoList, int viewType) {
        this.context = context;
        this.videoList = videoList;
        this.currentViewType = viewType;
    }

    public void setViewType(int viewType) {
        this.currentViewType = viewType;
        notifyDataSetChanged();
    }
    
    public void updateList(List<VideoModel> newList) {
        this.videoList = newList;
        notifyDataSetChanged();
    }

    @Override
    public int getCount() { return videoList.size(); }

    @Override
    public Object getItem(int position) { return videoList.get(position); }

    @Override
    public long getItemId(int position) { return position; }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;

        if (convertView == null) {
            int layoutId;
            switch (currentViewType) {
                case SettingsManager.VIEW_TYPE_CARD:
                    layoutId = R.layout.item_video_card;
                    break;
                case SettingsManager.VIEW_TYPE_GRID:
                    layoutId = R.layout.item_video; // Old Grid layout
                    break;
                case SettingsManager.VIEW_TYPE_LIST:
                default:
                    layoutId = R.layout.item_video_list;
                    break;
            }
            
            convertView = LayoutInflater.from(context).inflate(layoutId, parent, false);
            holder = new ViewHolder();
            holder.thumbnail = convertView.findViewById(R.id.img_thumbnail);
            holder.title = convertView.findViewById(R.id.txt_title);
            holder.duration = convertView.findViewById(R.id.txt_duration);
            holder.resolution = convertView.findViewById(R.id.txt_resolution);
            holder.size = convertView.findViewById(R.id.txt_size);
            // btn_item_more might not exist in grid view (check before use)
            // holder.moreBtn = convertView.findViewById(R.id.btn_item_more); 
            
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        VideoModel video = videoList.get(position);

        if(holder.title != null) holder.title.setText(video.getTitle());
        if(holder.duration != null) holder.duration.setText(video.getDuration());
        
        // Size Text Logic
        if(holder.size != null) {
             if (currentViewType == SettingsManager.VIEW_TYPE_CARD) {
                 holder.size.setText(video.getResolution() + " • " + video.getSize());
             } else {
                 holder.size.setText(video.getSize());
             }
        }
        
        if(holder.resolution != null && currentViewType != SettingsManager.VIEW_TYPE_CARD) {
             holder.resolution.setText(video.getResolution());
        }

        // Reset & Load Thumbnail
        if(holder.thumbnail != null) {
            holder.thumbnail.setImageResource(R.drawable.ic_video_placeholder);
            new ThumbnailTask(holder.thumbnail).execute(video.getPath());
        }

        return convertView;
    }

    static class ViewHolder {
        ImageView thumbnail;
        TextView title, duration, resolution, size;
    }

    private static class ThumbnailTask extends AsyncTask<String, Void, Bitmap> {
        private final WeakReference<ImageView> imageViewReference;

        public ThumbnailTask(ImageView imageView) {
            imageViewReference = new WeakReference<>(imageView);
        }

        @Override
        protected Bitmap doInBackground(String... params) {
            return ThumbnailUtils.createVideoThumbnail(params[0], MediaStore.Images.Thumbnails.MINI_KIND);
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            if (imageViewReference != null && bitmap != null) {
                ImageView imageView = imageViewReference.get();
                if (imageView != null) {
                    imageView.setImageBitmap(bitmap);
                }
            }
        }
    }
}