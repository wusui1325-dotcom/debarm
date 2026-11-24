package com.wusper.debarm.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Ink = Color(0xFF12100D)
val Paper = Color(0xFF1C1914)
val PaperHigh = Color(0xFF2A261F)
val Hairline = Color(0x33E8C07A)
val Gold = Color(0xFFE8C07A)
val GoldDim = Color(0xFFB8965A)
val Cream = Color(0xFFF6F1E8)
val Muted = Color(0xFFA89F91)
val Coral = Color(0xFFE07A6A)
val Moss = Color(0xFF8FBF88)

private val Scheme: ColorScheme = darkColorScheme(
    primary = Gold,
    onPrimary = Ink,
    secondary = GoldDim,
    onSecondary = Ink,
    background = Ink,
    onBackground = Cream,
    surface = Paper,
    onSurface = Cream,
    surfaceVariant = PaperHigh,
    onSurfaceVariant = Muted,
    error = Coral,
    onError = Ink,
    outline = Color(0xFF4A443A),
    outlineVariant = Hairline,
)

private val Type = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Medium,
        fontSize = 36.sp,
        letterSpacing = (-0.6).sp,
        lineHeight = 42.sp,
        color = Cream,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        letterSpacing = (-0.2).sp,
        color = Cream,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        color = Cream,
    ),
    bodyLarge = TextStyle(fontSize = 16.sp, color = Cream, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, color = Cream, lineHeight = 21.sp),
    bodySmall = TextStyle(fontSize = 13.sp, color = Muted, lineHeight = 18.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, letterSpacing = 0.1.sp, color = Cream),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 0.4.sp, color = Muted),
)

@Composable
fun DebArmTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, typography = Type, content = content)
}
