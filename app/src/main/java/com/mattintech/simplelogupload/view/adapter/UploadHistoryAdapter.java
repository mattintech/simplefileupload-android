package com.mattintech.simplelogupload.view.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.mattintech.simplelogupload.R;
import com.mattintech.simplelogupload.model.UploadHistory;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Adapter for displaying upload history items
 */
public class UploadHistoryAdapter extends RecyclerView.Adapter<UploadHistoryAdapter.HistoryViewHolder> {
    
    private List<UploadHistory> history = new ArrayList<>();
    private final OnHistoryItemClickListener listener;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault());
    
    /**
     * Interface for history item click events
     */
    public interface OnHistoryItemClickListener {
        void onCopyCodeClick(UploadHistory history);
        void onDeleteClick(UploadHistory history);
    }
    
    /**
     * Constructor
     * 
     * @param listener The click listener
     */
    public UploadHistoryAdapter(OnHistoryItemClickListener listener) {
        this.listener = listener;
    }
    
    /**
     * Update the history data
     * 
     * @param history The new history data
     */
    public void setHistory(List<UploadHistory> history) {
        this.history = history;
        notifyDataSetChanged();
    }
    
    @NonNull
    @Override
    public HistoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_upload_history, parent, false);
        return new HistoryViewHolder(itemView);
    }
    
    @Override
    public void onBindViewHolder(@NonNull HistoryViewHolder holder, int position) {
        UploadHistory current = history.get(position);
        
        holder.fileNameTextView.setText(current.getFileName());
        holder.uploadCodeTextView.setText(current.getUploadCode());
        
        // Format date
        String formattedDate = dateFormat.format(new Date(current.getUploadTime()));
        holder.uploadTimeTextView.setText(formattedDate);
        
        // Set details text
        String details = String.format(Locale.getDefault(),
                "Max downloads: %d • Expires in %d hours",
                current.getMaxDownloads(),
                current.getExpiryHours());
        holder.detailsTextView.setText(details);
        
        // Set click listeners
        holder.copyButton.setOnClickListener(v -> {
            if (listener != null) {
                listener.onCopyCodeClick(current);
            }
        });
        
        holder.deleteButton.setOnClickListener(v -> {
            if (listener != null) {
                listener.onDeleteClick(current);
            }
        });
    }
    
    @Override
    public int getItemCount() {
        return history.size();
    }
    
    /**
     * ViewHolder for history items
     */
    static class HistoryViewHolder extends RecyclerView.ViewHolder {
        private final TextView fileNameTextView;
        private final TextView uploadCodeTextView;
        private final TextView uploadTimeTextView;
        private final TextView detailsTextView;
        private final Button copyButton;
        private final Button deleteButton;
        
        HistoryViewHolder(View itemView) {
            super(itemView);
            fileNameTextView = itemView.findViewById(R.id.fileNameText);
            uploadCodeTextView = itemView.findViewById(R.id.uploadCodeText);
            uploadTimeTextView = itemView.findViewById(R.id.uploadTimeText);
            detailsTextView = itemView.findViewById(R.id.detailsText);
            copyButton = itemView.findViewById(R.id.copyButton);
            deleteButton = itemView.findViewById(R.id.deleteButton);
        }
    }
}
