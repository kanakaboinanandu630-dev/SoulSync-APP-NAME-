package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

// Firestore security rules whitelist ['rose_quartz', 'midnight_amethyst', 'velvet_obsidian']
enum class AppThemePalette(val key: String, val label: String) {
    SKY_SYNC("rose_quartz", "Sky Sync"),
    MIDNIGHT_AMETHYST("midnight_amethyst", "Lavender"),
    VELVET_OBSIDIAN("velvet_obsidian", "Rose Blush");

    companion object {
        fun fromKey(raw: String?): AppThemePalette =
            entries.firstOrNull { it.key == raw } ?: SKY_SYNC
    }
}

val ForeverUsShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun ForeverUsTheme(
    palette: AppThemePalette = AppThemePalette.SKY_SYNC,
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when (palette) {
        AppThemePalette.SKY_SYNC -> if (darkTheme) SkySyncDarkColorScheme else SkySyncLightColorScheme
        AppThemePalette.MIDNIGHT_AMETHYST -> if (darkTheme) MidnightAmethystDarkColorScheme else MidnightAmethystLightColorScheme
        AppThemePalette.VELVET_OBSIDIAN -> if (darkTheme) VelvetObsidianDarkColorScheme else VelvetObsidianLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = ForeverUsTypography,
        shapes = ForeverUsShapes,
        content = content
    )
}
