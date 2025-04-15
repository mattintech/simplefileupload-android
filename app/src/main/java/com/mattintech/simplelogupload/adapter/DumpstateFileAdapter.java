package com.mattintech.simplelogupload.adapter;

import android.content.Context;
import android.text.format.DateFormat;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.mattintech.simplelogupload.R;

import java.io.File;
import java.util.Date;
import java.util.List;

/**
 * Adapter for displaying dumpstate files in a RecyclerView
 */
public class DumpstateFileAdapter extends RecyclerView.Adapter<DumpstateFileAdapter.ViewHolder> {
    
    private final List<File> files;
    private final Context context;
    private int selectedPosition = -1;
    private OnItemSelectedListener listener;
    
    /**
     * Constructor
     *
     * @param context The context
     * @param files List of dumpstate files
     */
    public DumpstateFileAdapter(Context context, List<File> files) {
        this.context = context;
        this.files = files;
    }
    
    /**
     * Set a listener for item selection
     *
     * @param listener The listener
     */
    public void setOnItemSelectedListener(OnItemSelectedListener listener) {
        this.listener = listener;
    }
    
    /**
     * Get the currently selected file
     *
     * @return The selected file or null if none selected
     */
    public File getSelectedFile() {
        if (selectedPosition >= 0 && selectedPosition < files.size()) {
            return files.get(selectedPosition);
        }
        return null;
    }
    
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_dumpstate_file, parent, false);
        return new ViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        File file = files.get(position);
        
        // Set file name
        holder.fileNameText.setText(file.getName());
        
        // Add tooltip for long filenames
        holder.fileNameText.setOnLongClickListener(v -> {
            Toast.makeText(context, file.getName(), Toast.LENGTH_LONG).show();
            return true;
        });
        
        // Format and set file date
        Date lastModified = new Date(file.lastModified());
        String formattedDate = DateFormat.format("MMM dd, yyyy hh:mm a", lastModified).toString();
        holder.fileDateText.setText(context.getString(R.string.dumpstate_date_format, formattedDate));
        
        // Format and set file size
        String fileSize = Formatter.formatFileSize(context, file.length());
        holder.fileSizeText.setText(context.getString(R.string.dumpstate_size_format, fileSize));
        
        // Set checkbox state
        holder.checkBox.setChecked(position == selectedPosition);
        
        // Set click listener for the whole item
        holder.itemView.setOnClickListener(v -> {
            int previousSelected = selectedPosition;
            selectedPosition = holder.getAdapterPosition();
            
            // Update previous selected item
            if (previousSelected >= 0) {
                notifyItemChanged(previousSelected);
            }
            
            // Update newly selected item
            notifyItemChanged(selectedPosition);
            
            // Notify listener
            if (listener != null) {
                listener.onItemSelected(file);
            }
        });
    }
    
    @Override
    public int getItemCount() {
        return files.size();
    }
    
    /**
     * ViewHolder for dumpstate file items
     */
    static class ViewHolder extends RecyclerView.ViewHolder {
        final CheckBox checkBox;
        final TextView fileNameText;
        final TextView fileDateText;
        final TextView fileSizeText;
        
        ViewHolder(View itemView) {
            super(itemView);
            checkBox = itemView.findViewById(R.id.checkBox);
            fileNameText = itemView.findViewById(R.id.fileNameText);
            fileDateText = itemView.findViewById(R.id.fileDateText);
            fileSizeText = itemView.findViewById(R.id.fileSizeText);
        }
    }
    
    /**
     * Interface for handling item selection
     */
    public interface OnItemSelectedListener {
        void onItemSelected(File file);
    }
}
