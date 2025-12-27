package com.mattintech.simplelogupload.model;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Model class for QR code server configuration
 */
public class QrServerConfig {
    private final String server;
    private final int port;
    private final String key;

    private QrServerConfig(String server, int port, String key) {
        this.server = server;
        this.port = port;
        this.key = key;
    }

    public String getServer() {
        return server;
    }

    public int getPort() {
        return port;
    }

    public String getKey() {
        return key;
    }

    /**
     * Parse QR code JSON data
     *
     * @param qrData The QR code string data
     * @return QrServerConfig object
     * @throws QrConfigException if parsing fails or data is invalid
     */
    public static QrServerConfig fromQrData(String qrData) throws QrConfigException {
        if (qrData == null || qrData.trim().isEmpty()) {
            throw new QrConfigException("QR code data is empty");
        }

        try {
            JSONObject json = new JSONObject(qrData);

            // Extract fields
            if (!json.has("server")) {
                throw new QrConfigException("Missing 'server' field in QR code");
            }
            if (!json.has("port")) {
                throw new QrConfigException("Missing 'port' field in QR code");
            }
            if (!json.has("key")) {
                throw new QrConfigException("Missing 'key' field in QR code");
            }

            String server = json.getString("server");
            int port = json.getInt("port");
            String key = json.getString("key");

            // Validate
            if (server.trim().isEmpty()) {
                throw new QrConfigException("Server address cannot be empty");
            }
            if (port <= 0 || port > 65535) {
                throw new QrConfigException("Port must be between 1 and 65535");
            }
            if (key.trim().isEmpty()) {
                throw new QrConfigException("Client key cannot be empty");
            }

            return new QrServerConfig(server.trim(), port, key.trim());

        } catch (JSONException e) {
            throw new QrConfigException("Invalid QR code format: " + e.getMessage());
        }
    }

    /**
     * Custom exception for QR configuration errors
     */
    public static class QrConfigException extends Exception {
        public QrConfigException(String message) {
            super(message);
        }
    }
}
