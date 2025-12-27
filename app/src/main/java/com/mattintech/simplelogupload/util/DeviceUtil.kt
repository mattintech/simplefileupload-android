package com.mattintech.simplelogupload.util

import android.os.Build

/**
 * Utility object for device-specific feature detection
 */
object DeviceUtil {
    /**
     * Check if the current device is a Samsung device
     *
     * @return true if device manufacturer is Samsung, false otherwise
     */
    fun isSamsungDevice(): Boolean {
        return Build.MANUFACTURER.equals("samsung", ignoreCase = true)
    }

    /**
     * Check if dumpstate features should be shown on this device
     * Currently only Samsung devices have dumpstate file support
     *
     * @return true if dumpstate features should be visible
     */
    fun shouldShowDumpstateFeatures(): Boolean {
        return isSamsungDevice()
    }

    /**
     * Get device manufacturer name
     *
     * @return Device manufacturer (e.g., "Samsung", "Google", "Xiaomi")
     */
    fun getManufacturer(): String {
        return Build.MANUFACTURER
    }

    /**
     * Get device model name
     *
     * @return Device model (e.g., "SM-G991U", "Pixel 7")
     */
    fun getModel(): String {
        return Build.MODEL
    }
}
