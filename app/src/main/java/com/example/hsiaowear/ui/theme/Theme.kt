package com.example.hsiaowear.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.hsiaowear.viewmodel.SettingsViewModel

@Composable
fun HsiaoWearTheme(
    content: @Composable () -> Unit
) {
    val viewModel: SettingsViewModel = hiltViewModel()
    val themeSettings by viewModel.themeSettings.collectAsState()

    val isDark = when (themeSettings.themeMode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }

    val fontScale = when (themeSettings.fontScale) {
        0 -> 0.85f
        1 -> 0.95f
        2 -> 1.0f
        3 -> 1.1f
        4 -> 1.2f
        else -> 1.0f
    }

    val colorScheme = if (isDark) darkScheme() else lightScheme()
    val typography = AppTypography(fontScale)
    val spacing = AppSpacing()
    val shapes = AppShape()
    val elevations = AppElevation()

    CompositionLocalProvider(
        LocalAppSpacing provides spacing,
        LocalAppShape provides shapes,
        LocalAppElevation provides elevations
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            content = content
        )
    }
}

private fun darkScheme() = darkColorScheme(
    primary = FunctionalBlue,
    onPrimary = White,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    error = FunctionalRed,
    onError = White,
    outline = DarkOnSurfaceVariant.copy(alpha = 0.3f),
    outlineVariant = DarkOnSurfaceVariant.copy(alpha = 0.15f),
    scrim = Black.copy(alpha = 0.6f)
)

private fun lightScheme() = lightColorScheme(
    primary = FunctionalBlue,
    onPrimary = White,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    error = FunctionalRed,
    onError = White,
    outline = LightOnSurfaceVariant.copy(alpha = 0.4f),
    outlineVariant = LightSurfaceVariant.copy(alpha = 0.2f),
    scrim = Black.copy(alpha = 0.6f)
)
