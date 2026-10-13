package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val RamsesColorScheme = darkColorScheme(
    primary = RoyalGold,
    onPrimary = PharaonicMidnight,
    primaryContainer = RoyalGoldDark,
    onPrimaryContainer = TextPrimary,
    secondary = EgyptianCyan,
    onSecondary = PharaonicMidnight,
    secondaryContainer = PharaonicLapis,
    onSecondaryContainer = EgyptianTurquoise,
    tertiary = RoyalGoldLight,
    onTertiary = PharaonicMidnight,
    background = PharaonicMidnight,
    onBackground = TextPrimary,
    surface = SurfaceCard,
    onSurface = TextPrimary,
    surfaceVariant = PharaonicLapis,
    onSurfaceVariant = TextSecondary,
    outline = PharaonicGlassBorder,
    outlineVariant = RamsesBorder
)

@Composable
fun RamsesTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = RamsesColorScheme,
        typography = Typography,
        content = content
    )
}
