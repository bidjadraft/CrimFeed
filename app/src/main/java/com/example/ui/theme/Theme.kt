package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.example.data.model.AppThemeMode

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun CrimsonFeedTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        AppThemeMode.SYSTEM -> systemDark
        else -> !themeMode.isLight
    }

    val colorScheme = when (themeMode) {
        AppThemeMode.SYSTEM -> if (systemDark) CarbonColorScheme else LightCarbonColorScheme
        AppThemeMode.CARBON -> CarbonColorScheme
        AppThemeMode.LIGHT_CARBON -> LightCarbonColorScheme
        AppThemeMode.CRIMSON_NIGHT -> CrimsonNightColorScheme
        AppThemeMode.DARK -> ClassicDarkColorScheme
        AppThemeMode.OCEAN_DARK -> OceanDarkColorScheme
        AppThemeMode.FOREST_DARK -> ForestDarkColorScheme
        AppThemeMode.AMETHYST_DARK -> AmethystDarkColorScheme
        AppThemeMode.AMBER_DARK -> AmberDarkColorScheme
        AppThemeMode.LIGHT -> EditorialLightColorScheme
        AppThemeMode.NORDIC_LIGHT -> NordicLightColorScheme
        AppThemeMode.SAKURA_LIGHT -> SakuraLightColorScheme
        AppThemeMode.SAGE_LIGHT -> SageLightColorScheme
        AppThemeMode.AMBER_LIGHT -> AmberLightColorScheme
        AppThemeMode.LAVENDER_LIGHT -> LavenderLightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !isDark
                    isAppearanceLightNavigationBars = !isDark
                }
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
