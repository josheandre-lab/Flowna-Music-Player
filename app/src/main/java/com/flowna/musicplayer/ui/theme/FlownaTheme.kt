package com.flowna.musicplayer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val FlownaColors = lightColorScheme(
    primary = Color(0xFFA74FFF),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFF2E5FF),
    onPrimaryContainer = Color(0xFF54219B),
    secondary = Color(0xFFC3A3FF),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF7F0FF),
    onSecondaryContainer = Color(0xFF4C3370),
    tertiary = Color(0xFF72C18D),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE1F5E8),
    onTertiaryContainer = Color(0xFF224B30),
    background = Color(0xFFF4F1F7),
    onBackground = Color(0xFF1D3744),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1D3744),
    surfaceVariant = Color(0xFFF3EDF7),
    onSurfaceVariant = Color(0xFF6B6371),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    outline = Color(0xFFD7CDE2),
    outlineVariant = Color(0xFFE9E1F1),
    scrim = Color(0x331D3744)
)

val FlownaTypography = Typography(
    headlineLarge = TextStyle(
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 28.sp
    ),
    headlineMedium = TextStyle(
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 26.sp
    ),
    titleLarge = TextStyle(
        fontSize = 18.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 24.sp
    ),
    titleMedium = TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 22.sp
    ),
    bodyLarge = TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 23.sp
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 20.sp
    ),
    labelLarge = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 20.sp
    ),
    labelMedium = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 16.sp
    )
)

@Composable
fun FlownaTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = FlownaColors,
        typography = FlownaTypography,
        content = content
    )
}
