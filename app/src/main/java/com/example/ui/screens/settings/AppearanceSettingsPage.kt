package com.example.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.BrightnessAuto
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.AppThemeMode

private data class ThemeVisualSpec(
    val mode: AppThemeMode,
    val bg: Color,
    val accent: Color,
    val secondaryBg: Color? = null
)

@Composable
fun AppearanceSettingsPage(
    selectedTheme: AppThemeMode,
    onSelectTheme: (AppThemeMode) -> Unit,
    onBack: () -> Unit
) {
    var systemMenuExpanded by remember { mutableStateOf(false) }

    val systemOptionLabel = stringResource(R.string.theme_option_system)
    val darkOptionLabel = stringResource(R.string.theme_option_dark)
    val lightOptionLabel = stringResource(R.string.theme_option_light)
    val autoCarbonBadge = stringResource(R.string.theme_badge_auto_carbon)
    val darkCarbonBadge = stringResource(R.string.theme_badge_dark_carbon)
    val lightCarbonBadge = stringResource(R.string.theme_badge_light_carbon)

    val systemModes = remember {
        listOf(
            AppThemeMode.SYSTEM,
            AppThemeMode.CARBON,
            AppThemeMode.LIGHT_CARBON
        )
    }

    fun labelForSystemMode(mode: AppThemeMode): String = when (mode) {
        AppThemeMode.CARBON -> darkOptionLabel
        AppThemeMode.LIGHT_CARBON -> lightOptionLabel
        else -> systemOptionLabel
    }

    fun badgeForSystemMode(mode: AppThemeMode): String? = when (mode) {
        AppThemeMode.SYSTEM -> autoCarbonBadge
        AppThemeMode.CARBON -> darkCarbonBadge
        AppThemeMode.LIGHT_CARBON -> lightCarbonBadge
        else -> null
    }

    val collapsedSystemLabel = when (selectedTheme) {
        AppThemeMode.CARBON -> darkOptionLabel
        AppThemeMode.LIGHT_CARBON -> lightOptionLabel
        else -> systemOptionLabel
    }

    val collapsedSystemBadge = if (selectedTheme in systemModes) {
        badgeForSystemMode(selectedTheme)
    } else {
        null
    }

    val darkThemes = listOf(
        ThemeVisualSpec(AppThemeMode.CRIMSON_NIGHT, Color(0xFF1B0F12), Color(0xFFFF8894)),
        ThemeVisualSpec(AppThemeMode.DARK, Color(0xFF181A1E), Color(0xFFFFB3B8)),
        ThemeVisualSpec(AppThemeMode.OCEAN_DARK, Color(0xFF09131C), Color(0xFF5EEAD4)),
        ThemeVisualSpec(AppThemeMode.FOREST_DARK, Color(0xFF0B1510), Color(0xFF86EFAC)),
        ThemeVisualSpec(AppThemeMode.AMETHYST_DARK, Color(0xFF160F22), Color(0xFFD8B4FE)),
        ThemeVisualSpec(AppThemeMode.AMBER_DARK, Color(0xFF1C160D), Color(0xFFFCD34D))
    )

    val lightThemes = listOf(
        ThemeVisualSpec(AppThemeMode.LIGHT, Color(0xFFFAF7F5), Color(0xFFA3182D)),
        ThemeVisualSpec(AppThemeMode.NORDIC_LIGHT, Color(0xFFF8FAFC), Color(0xFF1D4ED8)),
        ThemeVisualSpec(AppThemeMode.SAKURA_LIGHT, Color(0xFFFFF8FB), Color(0xFF9D174D)),
        ThemeVisualSpec(AppThemeMode.SAGE_LIGHT, Color(0xFFF5FAF6), Color(0xFF15803D)),
        ThemeVisualSpec(AppThemeMode.AMBER_LIGHT, Color(0xFFFFFBF4), Color(0xFFB45309)),
        ThemeVisualSpec(AppThemeMode.LAVENDER_LIGHT, Color(0xFFFAF8FF), Color(0xFF6D28D9))
    )

    SettingsSubPageScaffold(
        title = stringResource(R.string.settings_appearance),
        onBack = onBack
    ) {
        // 1. System (tap System to expand System / Dark / Light)
        SettingsSectionCard(
            title = stringResource(R.string.settings_theme_system_section),
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.BrightnessAuto,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        ) {
            CollapsibleDropdownSelector(
                selectedLabel = collapsedSystemLabel,
                selectedBadge = collapsedSystemBadge,
                expanded = systemMenuExpanded,
                onToggleExpanded = { systemMenuExpanded = !systemMenuExpanded },
                items = systemModes,
                isItemSelected = { it == selectedTheme },
                itemLabel = ::labelForSystemMode,
                itemBadge = ::badgeForSystemMode,
                onSelectItem = { mode ->
                    onSelectTheme(mode)
                    systemMenuExpanded = false
                },
                toggleTestTag = "dropdown_system_theme_toggle",
                itemTestTag = { _, mode -> "theme_swatch_${mode.name}" }
            )
        }

        // 2. 6 Dark Themes
        SettingsSectionCard(
            title = stringResource(R.string.settings_theme_dark_section),
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.DarkMode,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        ) {
            darkThemes.chunked(2).forEach { rowSpecs ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    rowSpecs.forEach { spec ->
                        ThemeOptionTile(
                            spec = spec,
                            selected = selectedTheme == spec.mode,
                            onClick = { onSelectTheme(spec.mode) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // 3. 6 Light Themes
        SettingsSectionCard(
            title = stringResource(R.string.settings_theme_light_section),
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.LightMode,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        ) {
            lightThemes.chunked(2).forEach { rowSpecs ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    rowSpecs.forEach { spec ->
                        ThemeOptionTile(
                            spec = spec,
                            selected = selectedTheme == spec.mode,
                            onClick = { onSelectTheme(spec.mode) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeOptionTile(
    spec: ThemeVisualSpec,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .border(if (selected) 2.dp else 1.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag("theme_swatch_${spec.mode.name}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val circleModifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .let { base ->
                    if (spec.secondaryBg != null) {
                        base.background(
                            Brush.linearGradient(listOf(spec.bg, spec.secondaryBg))
                        )
                    } else {
                        base.background(spec.bg)
                    }
                }
                .border(1.dp, Color.Gray.copy(alpha = 0.4f), CircleShape)

            Box(
                modifier = circleModifier,
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(spec.accent)
                )
            }

            Text(
                text = spec.mode.label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            if (selected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
