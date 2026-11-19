package com.example.androidtvlight.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val StandardDarkBlueColorScheme = darkColorScheme(
    primary = TvElectricCyan,
    onPrimary = TvOnPrimaryDark,
    primaryContainer = TvSurfaceElevated,
    onPrimaryContainer = TvTextPrimary,
    secondary = TvGoldenFocus,
    onSecondary = TvOnPrimaryDark,
    secondaryContainer = TvSurfaceCard,
    onSecondaryContainer = TvTextPrimary,
    tertiary = TvEmeraldLive,
    onTertiary = TvOnPrimaryDark,
    background = TvDarkBlueBackground,
    onBackground = TvTextPrimary,
    surface = TvSurfaceNavy,
    onSurface = TvTextPrimary,
    surfaceVariant = TvSurfaceCard,
    onSurfaceVariant = TvTextSecondary,
    outline = TvBrightSky,
    outlineVariant = Color(0xFF2B4C7E)
)

private val UltraContrastDarkBlueColorScheme = darkColorScheme(
    primary = Color(0xFF7DD3FC),
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFF153462),
    onPrimaryContainer = Color(0xFFFFFFFF),
    secondary = Color(0xFFFDE047),
    onSecondary = Color(0xFF000000),
    secondaryContainer = Color(0xFF0D2142),
    onSecondaryContainer = Color(0xFFFFFFFF),
    tertiary = TvEmeraldLive,
    onTertiary = Color(0xFF000000),
    background = TvUltraDarkBackground,
    onBackground = Color(0xFFFFFFFF),
    surface = TvUltraCard,
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF132F5C),
    onSurfaceVariant = Color(0xFFE2E8F0),
    outline = Color(0xFFFDE047),
    outlineVariant = Color(0xFF38BDF8)
)

@Composable
fun AndroidTvLightTheme(
    highContrast: Boolean = false,
    largeText: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (highContrast) {
        UltraContrastDarkBlueColorScheme
    } else {
        StandardDarkBlueColorScheme
    }
    val scale = if (largeText) 1.16f else 1.0f

    MaterialTheme(
        colorScheme = colorScheme,
        typography = buildTvTypography(scale),
        content = content
    )
}
