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

val Ink = Color(0xFF14120F)
val Paper = Color(0xFF1E1B16)
val PaperHigh = Color(0xFF2A261F)
val Gold = Color(0xFFE8C07A)
val GoldDim = Color(0xFFB8965A)
val Cream = Color(0xFFF4EFE6)
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
)

private val Type = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Medium,
        fontSize = 34.sp,
        letterSpacing = (-0.5).sp,
        color = Cream,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        color = Cream,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        color = Cream,
    ),
    bodyLarge = TextStyle(fontSize = 16.sp, color = Cream, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, color = Cream, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 13.sp, color = Muted, lineHeight = 18.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, color = Cream),
)

@Composable
fun DebArmTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, typography = Type, content = content)
}
