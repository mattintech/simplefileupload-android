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
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Adapter for displaying dumpstate files in a RecyclerView with multi-selection capability
 */
public class DumpstateAdapter extends RecyclerView.Adapter<DumpstateAdapter.ViewHolder> {
    
    private final List<File> files;
    private final Context context;
    private final Set<Integer> selectedPositions = new HashSet<>();
    private OnSelectionChangedListener selectionChangedListener;
    
    /**
     * Constructor
     *
     * @param context The context
     * @param files List of dumpstate files
     */
    public DumpstateAdapter(Context context, List<File> files) {
        this.context = context;
        this.files = files;
    }
    
    /**
     * Set a listener for selection changes
     *
     * @param listener The listener
     */
    public void setOnSelectionChangedListener(OnSelectionChangedListener listener) {
        this.selectionChangedListener = listener;
    }
    
    /**
     * Get the list of selected files
     *
     * @return List of selected files
     */
    public List<File> getSelectedFiles() {
        List<File> selectedFiles = new ArrayList<>();
        for (int position : selectedPositions) {
            if (position >= 0 && position < files.size()) {
                selectedFiles.add(files.get(position));
            }
        }
        return selectedFiles;
    }
    
    /**
     * Check if an item is selected
     *
     * @param position The position to check
     * @return true if selected, false otherwise
     */
    public boolean isSelected(int position) {
        return selectedPositions.contains(position);
    }
    
    /**
     * Select or deselect an item
     *
     * @param position The position to select/deselect
     * @param selected Whether to select (true) or deselect (false)
     */
    public void setSelected(int position, boolean selected) {
        if (selected) {
            selectedPositions.add(position);
        } else {
            selectedPositions.remove(position);
        }
        notifyItemChanged(position);
        
        if (selectionChangedListener != null) {
            selectionChangedListener.onSelectionChanged(selectedPositions.size());
        }
    }
    
    /**
     * Toggle selection state of an item
     *
     * @param position The position to toggle
     */
    public void toggleSelection(int position) {
        setSelected(position, !isSelected(position));
    }
    
    /**
     * Select all items
     */
    public void selectAll() {
        selectedPositions.clear();
        for (int i = 0; i < files.size(); i++) {
            selectedPositions.add(i);
        }
        notifyDataSetChanged();
        
        if (selectionChangedListener != null) {
            selectionChangedListener.onSelectionChanged(selectedPositions.size());
        }
    }
    
    /**
     * Clear all selections
     */
    public void clearSelections() {
        selectedPositions.clear();
        notifyDataSetChanged();
        
        if (selectionChangedListener != null) {
            selectionChangedListener.onSelectionChanged(0);
        }
    }
    
    /**
     * Get the number of selected items
     *
     * @return The count of selected items
     */
    public int getSelectedCount() {
        return selectedPositions.size();
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
        holder.checkBox.setChecked(isSelected(position));
        
        // Set click listeners
        holder.checkBox.setOnClickListener(v -> toggleSelection(holder.getAbsoluteAdapterPosition()));
        
        holder.itemView.setOnClickListener(v -> toggleSelection(holder.getAbsoluteAdapterPosition()));
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
     * Interface for handling selection changes
     */
    public interface OnSelectionChangedListener {
        void onSelectionChanged(int selectedCount);
    }
}
