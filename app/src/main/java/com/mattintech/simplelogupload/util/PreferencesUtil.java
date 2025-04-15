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
    
    // Default values
    private static final String DEFAULT_SERVER_ADDRESS = "172.16.100.167";
    private static final int DEFAULT_SERVER_PORT = 7777;
    private static final String DEFAULT_CLIENT_KEY = "default-secret-key";
    
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
     * Get the complete server URL (address + port + path)
     * 
     * @param context The application context
     * @return The complete server URL
     */
    public static String getServerUrl(Context context) {
        String address = getServerAddress(context);
        int port = getServerPort(context);
        return "http://" + address + ":" + port + "/upload";
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
        editor.apply();
    }
}
