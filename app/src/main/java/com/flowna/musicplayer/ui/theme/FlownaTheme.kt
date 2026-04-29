package com.flowna.musicplayer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Lavender50 = Color(0xFFFFF7F4)
val Lavender100 = Color(0xFFFFF0EC)
val Lavender200 = Color(0xFFFFD6CC)
val Lavender300 = Color(0xFFFFB49F)
val Lavender400 = Color(0xFFFF8A6E)
val Lavender500 = Color(0xFFFF795E)
val Lavender600 = Color(0xFFFF6B4A)
val Lavender700 = Color(0xFFE65232)
val Lavender800 = Color(0xFFBF3F25)
val Lavender900 = Color(0xFF8F2D1B)

val FlownaBackground = Color(0xFFF7F4F0)
val FlownaSurface = Color(0xFFFFFFFF)
val FlownaBorder = Color(0xFFEDE7DF)
val FlownaTextPrimary = Color(0xFF1A1A2E)
val FlownaTextSecondary = Color(0xFF6B7280)
val FlownaTextMuted = Color(0xFFA0ADB8)
val FlownaSuccess = Color(0xFF00C896)
val FlownaWarning = Color(0xFFF59E0B)
val FlownaError = Color(0xFFEF4444)

private val FlownaColors = lightColorScheme(
    primary = Lavender600,
    onPrimary = FlownaSurface,
    primaryContainer = Lavender100,
    onPrimaryContainer = Lavender800,
    secondary = Lavender400,
    onSecondary = FlownaSurface,
    secondaryContainer = Lavender50,
    onSecondaryContainer = FlownaTextPrimary,
    tertiary = FlownaSuccess,
    onTertiary = FlownaSurface,
    tertiaryContainer = Color(0xFFE6FAF5),
    onTertiaryContainer = Color(0xFF007A5C),
    background = FlownaBackground,
    onBackground = FlownaTextPrimary,
    surface = FlownaSurface,
    onSurface = FlownaTextPrimary,
    surfaceVariant = Lavender50,
    onSurfaceVariant = FlownaTextSecondary,
    error = FlownaError,
    onError = FlownaSurface,
    outline = FlownaBorder,
    outlineVariant = Lavender200,
    scrim = Color(0x331A1A2E)
)

val FlownaTypography = Typography(
    displayLarge = TextStyle(
        fontSize = 30.sp,
        fontWeight = FontWeight.ExtraBold,
        lineHeight = 34.sp,
        letterSpacing = (-0.8).sp
    ),
    headlineLarge = TextStyle(
        fontSize = 28.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 34.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineMedium = TextStyle(
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 30.sp,
        letterSpacing = (-0.3).sp
    ),
    titleLarge = TextStyle(
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 26.sp
    ),
    titleMedium = TextStyle(
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 21.sp
    ),
    bodyLarge = TextStyle(
        fontSize = 15.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 20.sp
    ),
    labelLarge = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    ),
    labelMedium = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 16.sp
    ),
    labelSmall = TextStyle(
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 14.sp
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
