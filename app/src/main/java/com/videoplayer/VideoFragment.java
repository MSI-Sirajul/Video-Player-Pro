package com.videoplayer;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;

public class VideoFragment extends Fragment {

    private RecyclerView recyclerView;
    private TextView tvEmpty;
    private VideoAdapter videoAdapter;
    private ArrayList<VideoItem> videoList = new ArrayList<>();
    private VideoDatabaseHelper dbHelper;
    private SettingsManager settingsManager;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_video, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        recyclerView = view.findViewById(R.id.recycler_view_videos);
        tvEmpty = view.findViewById(R.id.tv_empty);
        
        dbHelper = new VideoDatabaseHelper(getContext());
        settingsManager = new SettingsManager(getContext());

        loadVideos();
    }

    @Override
    public void onResume() {
        super.onResume();
        // সেটিংস বা ডাটাবেস চেঞ্জ হলে অটোমেটিক আপডেট হবে
        loadVideos();
    }

    // পাবলিক মেথড: MainActivity থেকে কল করার জন্য
    public void loadVideos() {
        if (getContext() == null) return;

        // ১. সর্টিং
        int sortType = settingsManager.getSortType();
        videoList = dbHelper.getAllVideos(sortType);

        if (videoList.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            tvEmpty.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);

            // ২. ভিউ মোড (Grid/List)
            int viewMode = settingsManager.getViewMode();
            if (viewMode == SettingsManager.VIEW_GRID) {
                recyclerView.setLayoutManager(new GridLayoutManager(getContext(), 2));
            } else {
                recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
            }

            // ৩. অ্যাডাপ্টার সেটআপ
            videoAdapter = new VideoAdapter(getContext(), videoList, (pos) -> {
                Intent intent = new Intent(getContext(), PlayerActivity.class);
                intent.putExtra("position", pos);
                PlayerActivity.videoList = videoList; 
                startActivity(intent);
            });
            recyclerView.setAdapter(videoAdapter);
        }
    }
}