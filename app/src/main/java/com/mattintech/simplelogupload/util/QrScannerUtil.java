package com.mattintech.simplelogupload.util;

import com.journeyapps.barcodescanner.ScanOptions;
import com.mattintech.simplelogupload.model.QrServerConfig;

/**
 * Utility class for QR code scanning
 */
public class QrScannerUtil {

    /**
     * Callback interface for QR scan results
     */
    public interface QrScanCallback {
        void onQrScanned(QrServerConfig config);
        void onQrScanCancelled();
        void onQrScanError(String errorMessage);
    }

    /**
     * Create scan options for QR code scanning
     *
     * @return ScanOptions configured for QR codes
     */
    public static ScanOptions createScanOptions() {
        ScanOptions options = new ScanOptions();
        options.setDesiredBarcodeFormats(ScanOptions.QR_CODE);
        options.setPrompt("Scan server configuration QR code");
        options.setBeepEnabled(true);
        options.setBarcodeImageEnabled(false);
        options.setOrientationLocked(true);
        return options;
    }

    /**
     * Process scan result
     *
     * @param contents The scanned QR code contents
     * @param callback The callback to handle the result
     */
    public static void processScanResult(String contents, QrScanCallback callback) {
        if (contents == null) {
            callback.onQrScanCancelled();
            return;
        }

        try {
            QrServerConfig config = QrServerConfig.fromQrData(contents);
            callback.onQrScanned(config);
        } catch (QrServerConfig.QrConfigException e) {
            callback.onQrScanError(e.getMessage());
        }
    }
}
