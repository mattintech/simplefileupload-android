package com.mattintech.simplelogupload.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.mattintech.simplelogupload.ui.screens.OnboardingScreen
import com.mattintech.simplelogupload.ui.screens.WelcomeScreen
import com.mattintech.simplelogupload.ui.screens.ServerSetupScreen
import com.mattintech.simplelogupload.ui.screens.MainScreen
import com.mattintech.simplelogupload.ui.screens.UploadHistoryScreen
import com.mattintech.simplelogupload.ui.screens.SettingsScreen

/**
 * Main navigation graph for the app
 *
 * @param navController Navigation controller
 * @param startDestination Initial screen to show
 */
@Composable
fun AppNavigation(
    navController: NavHostController,
    startDestination: String
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        // Onboarding carousel - shown on very first launch with permissions and server setup
        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onOnboardingComplete = {
                    navController.navigate(Screen.Main.route) {
                        // Clear backstack so user can't go back to onboarding
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }

        // Welcome screen - shown on first launch
        composable(Screen.Welcome.route) {
            WelcomeScreen(
                onNavigateToSetup = {
                    navController.navigate(Screen.ServerSetup.route)
                }
            )
        }

        // Server setup screen - configure server connection
        composable(Screen.ServerSetup.route) {
            ServerSetupScreen(
                onSetupComplete = {
                    navController.navigate(Screen.Main.route) {
                        // Clear backstack so user can't go back to setup
                        popUpTo(Screen.Welcome.route) { inclusive = true }
                    }
                },
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        // Main screen - primary upload functionality
        composable(Screen.Main.route) {
            MainScreen(
                onNavigateToHistory = {
                    navController.navigate(Screen.UploadHistory.route)
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                }
            )
        }

        // Upload history screen
        composable(Screen.UploadHistory.route) {
            UploadHistoryScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        // Settings screen
        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
