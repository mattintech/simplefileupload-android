package com.mattintech.simplelogupload.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * Light color scheme using Google Blue and Green from app icon
 */
private val LightColorScheme = lightColorScheme(
    primary = GoogleBlue,
    onPrimary = White,
    primaryContainer = GoogleBlueLight,
    onPrimaryContainer = Gray900,
    secondary = GoogleGreen,
    onSecondary = White,
    secondaryContainer = GoogleGreenLight,
    onSecondaryContainer = Gray900,
    tertiary = GoogleBlueDark,
    onTertiary = White,
    tertiaryContainer = GoogleBlueLight,
    onTertiaryContainer = Gray900,
    error = ErrorRed,
    onError = White,
    errorContainer = ErrorRedLight,
    onErrorContainer = Gray900,
    background = White,
    onBackground = Gray900,
    surface = White,
    onSurface = Gray900,
    surfaceVariant = Gray100,
    onSurfaceVariant = Gray700,
    outline = Gray200,
    outlineVariant = Gray100,
    scrim = Black
)

/**
 * Dark color scheme using Google Blue and Green from app icon
 */
private val DarkColorScheme = darkColorScheme(
    primary = GoogleBlueLight,
    onPrimary = Gray900,
    primaryContainer = GoogleBlueDark,
    onPrimaryContainer = Gray100,
    secondary = GoogleGreen,
    onSecondary = Gray900,
    secondaryContainer = GoogleGreenDark,
    onSecondaryContainer = Gray100,
    tertiary = GoogleBlue,
    onTertiary = Gray900,
    tertiaryContainer = GoogleBlueDark,
    onTertiaryContainer = Gray100,
    error = ErrorRedLight,
    onError = Gray900,
    errorContainer = ErrorRed,
    onErrorContainer = Gray100,
    background = Gray900,
    onBackground = Gray100,
    surface = Gray900,
    onSurface = Gray100,
    surfaceVariant = Gray700,
    onSurfaceVariant = Gray200,
    outline = Gray700,
    outlineVariant = Gray700,
    scrim = Black
)

/**
 * SimpleLogUpload theme with Google Blue/Green branding
 *
 * @param darkTheme Whether to use dark theme (follows system by default)
 * @param content Composable content to apply theme to
 */
@Composable
fun SimpleLogUploadTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
