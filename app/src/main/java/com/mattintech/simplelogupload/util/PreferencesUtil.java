package com.mattintech.simplelogupload.util;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Utility class for handling application preferences
 */
public class PreferencesUtil {
    private static final String PREFERENCES_NAME = "SimpleLogUploadPrefs";
    
    // Preference keys
    public static final String KEY_SERVER_ADDRESS = "server_address";
    public static final String KEY_SERVER_PORT = "server_port";
    public static final String KEY_CLIENT_KEY = "client_key";
    public static final String KEY_SETUP_COMPLETED = "setup_completed";
    public static final String KEY_ONBOARDING_COMPLETED = "onboarding_completed";
    public static final String KEY_DEFAULT_MAX_DOWNLOADS = "default_max_downloads";
    public static final String KEY_DEFAULT_EXPIRY_HOURS = "default_expiry_hours";
    public static final String KEY_ASK_BEFORE_UPLOAD = "ask_before_upload";

    // Default values
    private static final String DEFAULT_SERVER_ADDRESS = "";
    private static final int DEFAULT_SERVER_PORT = 0;
    private static final String DEFAULT_CLIENT_KEY = "";
    private static final boolean DEFAULT_SETUP_COMPLETED = false;
    private static final boolean DEFAULT_ONBOARDING_COMPLETED = false;
    private static final int DEFAULT_MAX_DOWNLOADS = 5;
    private static final int DEFAULT_EXPIRY_HOURS = 24;
    private static final boolean DEFAULT_ASK_BEFORE_UPLOAD = true;
    
    /**
     * Get the shared preferences instance
     * 
     * @param context The application context
     * @return The SharedPreferences instance
     */
    private static SharedPreferences getPreferences(Context context) {
        return context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
    }
    
    /**
     * Get the server address from preferences
     * 
     * @param context The application context
     * @return The server address
     */
    public static String getServerAddress(Context context) {
        return getPreferences(context).getString(KEY_SERVER_ADDRESS, DEFAULT_SERVER_ADDRESS);
    }
    
    /**
     * Set the server address in preferences
     * 
     * @param context The application context
     * @param serverAddress The server address to save
     */
    public static void setServerAddress(Context context, String serverAddress) {
        getPreferences(context).edit().putString(KEY_SERVER_ADDRESS, serverAddress).apply();
    }
    
    /**
     * Get the server port from preferences
     * 
     * @param context The application context
     * @return The server port
     */
    public static int getServerPort(Context context) {
        return getPreferences(context).getInt(KEY_SERVER_PORT, DEFAULT_SERVER_PORT);
    }
    
    /**
     * Set the server port in preferences
     * 
     * @param context The application context
     * @param serverPort The server port to save
     */
    public static void setServerPort(Context context, int serverPort) {
        getPreferences(context).edit().putInt(KEY_SERVER_PORT, serverPort).apply();
    }
    
    /**
     * Get the client key from preferences
     * 
     * @param context The application context
     * @return The client key
     */
    public static String getClientKey(Context context) {
        return getPreferences(context).getString(KEY_CLIENT_KEY, DEFAULT_CLIENT_KEY);
    }
    
    /**
     * Set the client key in preferences
     * 
     * @param context The application context
     * @param clientKey The client key to save
     */
    public static void setClientKey(Context context, String clientKey) {
        getPreferences(context).edit().putString(KEY_CLIENT_KEY, clientKey).apply();
    }
    
    /**
     * Get the server base URL (address + port) without any path
     * 
     * @param context The application context
     * @return The server base URL
     */
    public static String getServerBaseUrl(Context context) {
        String address = getServerAddress(context);
        int port = getServerPort(context);
        // Use HTTPS for port 443 (standard HTTPS port)
        String protocol = (port == 443) ? "https://" : "http://";
        return protocol + address + ":" + port;
    }
    
    /**
     * Get the complete server URL (address + port + path)
     * 
     * @param context The application context
     * @return The complete server URL
     */
    public static String getServerUrl(Context context) {
        return getServerBaseUrl(context) + "/upload";
    }
    
    /**
     * Reset all settings to default values
     *
     * @param context The application context
     */
    public static void resetToDefaults(Context context) {
        SharedPreferences.Editor editor = getPreferences(context).edit();
        editor.putString(KEY_SERVER_ADDRESS, DEFAULT_SERVER_ADDRESS);
        editor.putInt(KEY_SERVER_PORT, DEFAULT_SERVER_PORT);
        editor.putString(KEY_CLIENT_KEY, DEFAULT_CLIENT_KEY);
        editor.putBoolean(KEY_SETUP_COMPLETED, DEFAULT_SETUP_COMPLETED);
        editor.putBoolean(KEY_ONBOARDING_COMPLETED, DEFAULT_ONBOARDING_COMPLETED);
        editor.putInt(KEY_DEFAULT_MAX_DOWNLOADS, DEFAULT_MAX_DOWNLOADS);
        editor.putInt(KEY_DEFAULT_EXPIRY_HOURS, DEFAULT_EXPIRY_HOURS);
        editor.putBoolean(KEY_ASK_BEFORE_UPLOAD, DEFAULT_ASK_BEFORE_UPLOAD);
        editor.apply();
    }
    
    /**
     * Check if setup has been completed
     * 
     * @param context The application context
     * @return True if setup has been completed, false otherwise
     */
    public static boolean isSetupCompleted(Context context) {
        return getPreferences(context).getBoolean(KEY_SETUP_COMPLETED, DEFAULT_SETUP_COMPLETED);
    }
    
    /**
     * Set the setup completed status
     *
     * @param context The application context
     * @param completed True if setup has been completed, false otherwise
     */
    public static void setSetupCompleted(Context context, boolean completed) {
        getPreferences(context).edit().putBoolean(KEY_SETUP_COMPLETED, completed).apply();
    }

    /**
     * Check if onboarding has been completed
     *
     * @param context The application context
     * @return True if onboarding has been completed, false otherwise
     */
    public static boolean isOnboardingCompleted(Context context) {
        return getPreferences(context).getBoolean(KEY_ONBOARDING_COMPLETED, DEFAULT_ONBOARDING_COMPLETED);
    }

    /**
     * Set the onboarding completed status
     *
     * @param context The application context
     * @param completed True if onboarding has been completed, false otherwise
     */
    public static void setOnboardingCompleted(Context context, boolean completed) {
        getPreferences(context).edit().putBoolean(KEY_ONBOARDING_COMPLETED, completed).apply();
    }

    /**
     * Get the default max downloads from preferences
     *
     * @param context The application context
     * @return The default max downloads
     */
    public static int getDefaultMaxDownloads(Context context) {
        return getPreferences(context).getInt(KEY_DEFAULT_MAX_DOWNLOADS, DEFAULT_MAX_DOWNLOADS);
    }

    /**
     * Set the default max downloads in preferences
     *
     * @param context The application context
     * @param maxDownloads The default max downloads to save
     */
    public static void setDefaultMaxDownloads(Context context, int maxDownloads) {
        getPreferences(context).edit().putInt(KEY_DEFAULT_MAX_DOWNLOADS, maxDownloads).apply();
    }

    /**
     * Get the default expiry hours from preferences
     *
     * @param context The application context
     * @return The default expiry hours
     */
    public static int getDefaultExpiryHours(Context context) {
        return getPreferences(context).getInt(KEY_DEFAULT_EXPIRY_HOURS, DEFAULT_EXPIRY_HOURS);
    }

    /**
     * Set the default expiry hours in preferences
     *
     * @param context The application context
     * @param expiryHours The default expiry hours to save
     */
    public static void setDefaultExpiryHours(Context context, int expiryHours) {
        getPreferences(context).edit().putInt(KEY_DEFAULT_EXPIRY_HOURS, expiryHours).apply();
    }

    /**
     * Get the ask before upload preference
     *
     * @param context The application context
     * @return True if should ask before upload, false otherwise
     */
    public static boolean getAskBeforeUpload(Context context) {
        return getPreferences(context).getBoolean(KEY_ASK_BEFORE_UPLOAD, DEFAULT_ASK_BEFORE_UPLOAD);
    }

    /**
     * Set the ask before upload preference
     *
     * @param context The application context
     * @param askBeforeUpload True if should ask before upload, false otherwise
     */
    public static void setAskBeforeUpload(Context context, boolean askBeforeUpload) {
        getPreferences(context).edit().putBoolean(KEY_ASK_BEFORE_UPLOAD, askBeforeUpload).apply();
    }

    /**
     * Check if all required server settings are configured
     *
     * @param context The application context
     * @return True if all required settings are configured, false otherwise
     */
    public static boolean areServerSettingsConfigured(Context context) {
        SharedPreferences prefs = getPreferences(context);
        String address = prefs.getString(KEY_SERVER_ADDRESS, DEFAULT_SERVER_ADDRESS);
        int port = prefs.getInt(KEY_SERVER_PORT, DEFAULT_SERVER_PORT);
        String clientKey = prefs.getString(KEY_CLIENT_KEY, DEFAULT_CLIENT_KEY);

        return !address.isEmpty() && port > 0 && !clientKey.isEmpty();
    }
}
