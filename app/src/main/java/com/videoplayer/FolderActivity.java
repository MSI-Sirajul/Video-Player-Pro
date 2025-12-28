package com.videoplayer;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;

public class FolderActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private TextView tvTitle, tvEmpty;
    private ImageView btnBack;
    
    private ArrayList<VideoItem> folderVideoList = new ArrayList<>();
    private VideoAdapter videoAdapter;
    private VideoDatabaseHelper dbHelper;
    private SettingsManager settingsManager;
    private String folderName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_folder);

        // ইনটেন্ট থেকে ফোল্ডারের নাম নেওয়া
        folderName = getIntent().getStringExtra("folderName");
        
        dbHelper = new VideoDatabaseHelper(this);
        settingsManager = new SettingsManager(this);

        initViews();
        loadVideos();
    }

    private void initViews() {
        recyclerView = findViewById(R.id.recycler_view_folder_videos);
        tvTitle = findViewById(R.id.tv_folder_title);
        tvEmpty = findViewById(R.id.tv_empty);
        btnBack = findViewById(R.id.btn_back);

        if (folderName != null) {
            tvTitle.setText(folderName);
        }

        btnBack.setOnClickListener(v -> finish());
    }

    private void loadVideos() {
        // ডাটাবেস থেকে নির্দিষ্ট ফোল্ডারের ভিডিও আনা
        folderVideoList = dbHelper.getVideosByFolder(folderName);

        if (folderVideoList.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            tvEmpty.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);

            // সেটিংস অনুযায়ী ভিউ মোড সেট করা (Grid/List)
            int viewMode = settingsManager.getViewMode();
            if (viewMode == SettingsManager.VIEW_GRID) {
                recyclerView.setLayoutManager(new GridLayoutManager(this, 2));
            } else {
                recyclerView.setLayoutManager(new LinearLayoutManager(this));
            }

            videoAdapter = new VideoAdapter(this, folderVideoList, (pos) -> {
                // ভিডিও প্লে করার লজিক
                Intent intent = new Intent(FolderActivity.this, PlayerActivity.class);
                intent.putExtra("position", pos);
                // প্লেয়ারকে এই ফোল্ডারের লিস্ট পাঠানো হচ্ছে
                PlayerActivity.videoList = folderVideoList; 
                startActivity(intent);
            });
            recyclerView.setAdapter(videoAdapter);
        }
    }
}