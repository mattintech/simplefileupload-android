package com.mattintech.simplelogupload.navigation

/**
 * Sealed class representing all navigation destinations in the app
 */
sealed class Screen(val route: String) {
    /**
     * Onboarding carousel with app intro and permissions
     */
    data object Onboarding : Screen("onboarding")

    /**
     * Welcome/onboarding screen shown on first launch
     */
    data object Welcome : Screen("welcome")

    /**
     * Server setup screen for configuring server connection
     */
    data object ServerSetup : Screen("server_setup")

    /**
     * Main screen with upload functionality and bottom navigation
     */
    data object Main : Screen("main")

    /**
     * Upload history screen showing past uploads
     */
    data object UploadHistory : Screen("upload_history")

    /**
     * Settings screen for app configuration
     */
    data object Settings : Screen("settings")
}
