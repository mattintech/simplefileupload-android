package com.mattintech.simplelogupload.controller;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.webkit.MimeTypeMap;

import com.mattintech.simplelogupload.constant.AppConstants;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Controller for handling share intents
 */
public class ShareIntentHandler {
    private static final String TAG = AppConstants.APP_TAG + "ShareIntentHandler";

    /**
     * Check if an intent is a share intent
     * 
     * @param intent The intent to check
     * @return true if it's a share intent, false otherwise
     */
    public static boolean isShareIntent(Intent intent) {
        if (intent == null) {
            return false;
        }
        
        String action = intent.getAction();
        return Intent.ACTION_SEND.equals(action) || Intent.ACTION_SEND_MULTIPLE.equals(action);
    }
    
    /**
     * Extract shared URIs from an intent
     * 
     * @param intent The share intent
     * @return List of URIs or an empty list if none found
     */
    public static List<Uri> extractSharedUris(Intent intent) {
        List<Uri> uris = new ArrayList<>();
        
        if (intent == null) {
            return uris;
        }
        
        String action = intent.getAction();
        String type = intent.getType();
        
        if (Intent.ACTION_SEND.equals(action) && type != null) {
            Uri uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (uri != null) {
                uris.add(uri);
            }
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(action) && type != null) {
            ArrayList<Uri> intentUris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            if (intentUris != null) {
                uris.addAll(intentUris);
            }
        }
        
        return uris;
    }
    
    /**
     * Process a shared URI and prepare it for upload
     * 
     * @param context The context
     * @param uri The URI to process
     * @return CompletableFuture containing the prepared file
     */
    public static CompletableFuture<File> processSharedUri(Context context, Uri uri) {
        if (context == null || uri == null) {
            CompletableFuture<File> future = new CompletableFuture<>();
            future.completeExceptionally(new IllegalArgumentException("Invalid context or URI"));
            return future;
        }
        
        String filename = getFileName(context, uri);
        
        return FileController.saveContentUriToFileAsync(context, uri, filename)
                .thenCompose(FileController::prepareFileForUploadAsync);
    }
    
    /**
     * Get the filename from a URI
     * 
     * @param context The context
     * @param uri The URI
     * @return The filename or a default name if none can be determined
     */
    private static String getFileName(Context context, Uri uri) {
        String result = null;
        
        if ("content".equals(uri.getScheme())) {
            ContentResolver contentResolver = context.getContentResolver();
            android.database.Cursor cursor = contentResolver.query(uri, null, null, null, null);
            
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex("_display_name");
                    if (nameIndex != -1) {
                        result = cursor.getString(nameIndex);
                    }
                }
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }
        
        if (result == null) {
            // If no name found, use the last path segment with an extension based on mime type
            result = uri.getLastPathSegment();
            
            // If still null, generate a default name
            if (result == null) {
                String mimeType = context.getContentResolver().getType(uri);
                String extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
                result = "shared_file" + (extension != null ? "." + extension : "");
            }
        }
        
        return result;
    }
}
