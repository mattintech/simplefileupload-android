package com.mattintech.simplelogupload.view;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.mattintech.simplelogupload.R;
import com.mattintech.simplelogupload.adapter.DumpstateFileAdapter;

import java.io.File;
import java.util.Arrays;
import java.util.List;

/**
 * Dialog for selecting a dumpstate file from multiple options
 */
public class DumpstateSelectionDialog extends Dialog {
    
    private final List<File> dumpstateFiles;
    private final OnDumpstateSelectedListener listener;
    
    private TextView titleText;
    private RecyclerView recyclerView;
    private TextView emptyView;
    private Button selectButton;
    private Button cancelButton;
    
    private DumpstateFileAdapter adapter;
    
    /**
     * Interface for handling dumpstate selection
     */
    public interface OnDumpstateSelectedListener {
        void onDumpstateSelected(File dumpstateFile);
    }
    
    /**
     * Constructor
     *
     * @param context The context
     * @param dumpstateFiles List of dumpstate files
     * @param listener Listener for selection events
     */
    public DumpstateSelectionDialog(@NonNull Context context, 
                                   File[] dumpstateFiles,
                                   OnDumpstateSelectedListener listener) {
        super(context);
        this.dumpstateFiles = Arrays.asList(dumpstateFiles);
        this.listener = listener;
    }
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_dumpstate_selection);
        
        // Initialize views
        titleText = findViewById(R.id.titleText);
        recyclerView = findViewById(R.id.dumpstateRecyclerView);
        emptyView = findViewById(R.id.emptyView);
        selectButton = findViewById(R.id.selectButton);
        cancelButton = findViewById(R.id.cancelButton);
        
        // Update title with file count
        titleText.setText(getContext().getString(R.string.dumpstate_selection_subtitle, dumpstateFiles.size()));
        
        // Set up RecyclerView
        setupRecyclerView();
        
        // Set up buttons
        cancelButton.setOnClickListener(v -> dismiss());
        selectButton.setOnClickListener(v -> {
            File selectedFile = adapter.getSelectedFile();
            if (selectedFile != null && listener != null) {
                listener.onDumpstateSelected(selectedFile);
                dismiss();
            }
        });
        
        // Show empty view if no files
        if (dumpstateFiles.isEmpty()) {
            recyclerView.setVisibility(View.GONE);
            emptyView.setVisibility(View.VISIBLE);
        } else {
            recyclerView.setVisibility(View.VISIBLE);
            emptyView.setVisibility(View.GONE);
        }
    }
    
    /**
     * Set up the RecyclerView with the adapter
     */
    private void setupRecyclerView() {
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new DumpstateFileAdapter(getContext(), dumpstateFiles);
        recyclerView.setAdapter(adapter);
        
        // Listen for item selection to enable/disable select button
        adapter.setOnItemSelectedListener(file -> {
            selectButton.setEnabled(true);
        });
    }
}
