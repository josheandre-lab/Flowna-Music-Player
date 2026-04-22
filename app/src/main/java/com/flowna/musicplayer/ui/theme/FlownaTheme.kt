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
    primary = Color(0xFF9A67F7),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEADFFF),
    onPrimaryContainer = Color(0xFF3E246E),
    secondary = Color(0xFF9F88D8),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF1E9FF),
    onSecondaryContainer = Color(0xFF443268),
    tertiary = Color(0xFF69B982),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE0F4E5),
    onTertiaryContainer = Color(0xFF224A2D),
    background = Color(0xFFF7F2FB),
    onBackground = Color(0xFF2F2542),
    surface = Color(0xFFFFFBFF),
    onSurface = Color(0xFF2F2542),
    surfaceVariant = Color(0xFFF2EAF8),
    onSurfaceVariant = Color(0xFF6C607D),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    outline = Color(0xFFD5C8E6),
    outlineVariant = Color(0xFFE7DDF3),
    scrim = Color(0xFF1E1628)
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
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = FlownaColors,
        typography = FlownaTypography,
        content = content
    )
}
