package com.flowna.musicplayer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val FlownaColors = darkColorScheme(
    primary = Color(0xFF9A66FF),
    onPrimary = Color(0xFFF7F3FF),
    primaryContainer = Color(0xFF251A3A),
    onPrimaryContainer = Color(0xFFE9DDFF),
    secondary = Color(0xFF6EA5FF),
    onSecondary = Color(0xFFF4F8FF),
    secondaryContainer = Color(0xFF182640),
    onSecondaryContainer = Color(0xFFDCE8FF),
    tertiary = Color(0xFF66E7A5),
    onTertiary = Color(0xFF062416),
    tertiaryContainer = Color(0xFF143222),
    onTertiaryContainer = Color(0xFFD3FBE5),
    background = Color(0xFF0A0712),
    onBackground = Color(0xFFF5F1FF),
    surface = Color(0xFF171222),
    onSurface = Color(0xFFF6F2FF),
    surfaceVariant = Color(0xFF211A31),
    onSurfaceVariant = Color(0xFFB7B0CC),
    error = Color(0xFFFF7B82),
    onError = Color(0xFF300108),
    outline = Color(0xFF4E4563),
    outlineVariant = Color(0xFF312942),
    scrim = Color(0xFF05030A)
)

val FlownaTypography = Typography(
    headlineLarge = TextStyle(
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 30.sp
    ),
    headlineMedium = TextStyle(
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 28.sp
    ),
    titleLarge = TextStyle(
        fontSize = 20.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 26.sp
    ),
    titleMedium = TextStyle(
        fontSize = 17.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 23.sp
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
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = FlownaColors,
        typography = FlownaTypography,
        content = content
    )
}
