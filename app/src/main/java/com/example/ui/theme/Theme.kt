package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val MaDColorScheme = darkColorScheme(
    primary = GoldPrimary,
    onPrimary = Color(0xFF04101E),
    primaryContainer = DarkSurfaceElevated,
    onPrimaryContainer = GoldLight,
    secondary = AccentCyan,
    onSecondary = Color(0xFF04101E),
    secondaryContainer = DarkSurface,
    onSecondaryContainer = TextPrimary,
    tertiary = AccentEmerald,
    onTertiary = Color(0xFF04101E),
    background = ObsidianBlack,
    onBackground = TextPrimary,
    surface = DarkNavyCard,
    onSurface = TextPrimary,
    surfaceVariant = DarkSurface,
    onSurfaceVariant = TextSecondary,
    outline = BorderSubtle,
    outlineVariant = BorderGlow
)

val MaDShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = MaDColorScheme,
        typography = Typography,
        shapes = MaDShapes,
        content = content
    )
}

@Composable
fun MaDInternetTVTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = MaDColorScheme,
        typography = Typography,
        shapes = MaDShapes,
        content = content
    )
}
