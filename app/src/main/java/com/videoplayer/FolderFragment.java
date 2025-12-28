package com.videoplayer;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;

public class FolderFragment extends Fragment {

    private RecyclerView recyclerView;
    private TextView tvEmpty;
    private FolderAdapter folderAdapter;
    private ArrayList<VideoItem> allVideos = new ArrayList<>();
    private ArrayList<String> folderList = new ArrayList<>();
    private VideoDatabaseHelper dbHelper;
    private SettingsManager settingsManager;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_folder, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        recyclerView = view.findViewById(R.id.recycler_view_folders);
        tvEmpty = view.findViewById(R.id.tv_empty_folder);
        
        dbHelper = new VideoDatabaseHelper(getContext());
        settingsManager = new SettingsManager(getContext());

        loadFolders();
    }
    
    @Override
    public void onResume() {
        super.onResume();
        loadFolders();
    }

    // পাবলিক মেথড: MainActivity থেকে রিফ্রেশ করার জন্য
    public void loadFolders() {
        if (getContext() == null) return;

        int sortType = settingsManager.getSortType();
        allVideos = dbHelper.getAllVideos(sortType);

        if (allVideos.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
            return;
        }
        
        tvEmpty.setVisibility(View.GONE);
        recyclerView.setVisibility(View.VISIBLE);

        HashSet<String> folders = new HashSet<>();
        for (VideoItem item : allVideos) {
            if(item.folderName != null) folders.add(item.folderName);
        }
        folderList = new ArrayList<>(folders);
        Collections.sort(folderList); 

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        
        folderAdapter = new FolderAdapter(getContext(), folderList, folderName -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).openFolder(folderName);
            }
        });
        recyclerView.setAdapter(folderAdapter);
    }
}