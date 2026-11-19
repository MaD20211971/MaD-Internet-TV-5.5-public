package com.example.androidtvlight.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R

val OutfitFontFamily = FontFamily(
    Font(R.font.outfit, FontWeight.Normal),
    Font(R.font.outfit, FontWeight.SemiBold),
    Font(R.font.outfit, FontWeight.Bold)
)

val PlusJakartaFontFamily = FontFamily(
    Font(R.font.plus_jakarta_sans, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans, FontWeight.Bold)
)

fun buildTvTypography(scaleMultiplier: Float = 1.0f): Typography {
    return Typography(
        displayLarge = TextStyle(
            fontFamily = OutfitFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = (38 * scaleMultiplier).sp,
            lineHeight = (46 * scaleMultiplier).sp,
            letterSpacing = (-0.5).sp
        ),
        displayMedium = TextStyle(
            fontFamily = OutfitFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = (30 * scaleMultiplier).sp,
            lineHeight = (38 * scaleMultiplier).sp
        ),
        headlineLarge = TextStyle(
            fontFamily = OutfitFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = (26 * scaleMultiplier).sp,
            lineHeight = (34 * scaleMultiplier).sp
        ),
        headlineMedium = TextStyle(
            fontFamily = OutfitFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = (22 * scaleMultiplier).sp,
            lineHeight = (28 * scaleMultiplier).sp
        ),
        titleLarge = TextStyle(
            fontFamily = OutfitFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = (20 * scaleMultiplier).sp,
            lineHeight = (26 * scaleMultiplier).sp,
            letterSpacing = 0.15.sp
        ),
        titleMedium = TextStyle(
            fontFamily = PlusJakartaFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = (17 * scaleMultiplier).sp,
            lineHeight = (24 * scaleMultiplier).sp,
            letterSpacing = 0.15.sp
        ),
        bodyLarge = TextStyle(
            fontFamily = PlusJakartaFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = (16 * scaleMultiplier).sp,
            lineHeight = (24 * scaleMultiplier).sp,
            letterSpacing = 0.25.sp
        ),
        bodyMedium = TextStyle(
            fontFamily = PlusJakartaFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = (14 * scaleMultiplier).sp,
            lineHeight = (21 * scaleMultiplier).sp,
            letterSpacing = 0.2.sp
        ),
        labelLarge = TextStyle(
            fontFamily = PlusJakartaFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = (15 * scaleMultiplier).sp,
            lineHeight = (20 * scaleMultiplier).sp,
            letterSpacing = 0.4.sp
        ),
        labelMedium = TextStyle(
            fontFamily = PlusJakartaFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = (13 * scaleMultiplier).sp,
            lineHeight = (18 * scaleMultiplier).sp,
            letterSpacing = 0.4.sp
        )
    )
}
