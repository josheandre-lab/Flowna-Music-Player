package com.flowna.musicplayer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay

@Composable
fun PremiumAudioSpectrum(
    isActive: Boolean,
    modifier: Modifier = Modifier,
    accent: Color,
    glow: Color
) {
    val levels = remember {
        mutableStateListOf<Float>().apply {
            repeat(30) { index ->
                add(0.18f + ((index % 5) * 0.035f))
            }
        }
    }

    LaunchedEffect(isActive) {
        if (!isActive) {
            levels.indices.forEach { index ->
                levels[index] = 0.14f + ((index % 4) * 0.025f)
            }
            return@LaunchedEffect
        }

        var frame = 0
        while (isActive) {
            levels.indices.forEach { index ->
                val slowWave = ((sin((frame + index * 1.55f) / 7.5f) + 1.0) / 2.0).toFloat()
                val fastWave = ((cos((frame * 1.6f + index * 2.25f) / 5.2f) + 1.0) / 2.0).toFloat()
                val lift = if (index % 7 == 0) 0.14f else 0f
                levels[index] = (0.16f + slowWave * 0.46f + fastWave * 0.22f + lift)
                    .coerceIn(0.14f, 0.94f)
            }
            frame++
            delay(72L)
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(42.dp)
    ) {
        val count = levels.size
        val barWidth = (size.width / (count * 1.85f)).coerceAtLeast(3f)
        val gap = if (count > 1) {
            (size.width - (barWidth * count)) / (count - 1)
        } else {
            0f
        }

        drawLine(
            color = glow.copy(alpha = 0.28f),
            start = Offset(0f, size.height / 2f),
            end = Offset(size.width, size.height / 2f),
            strokeWidth = 1.dp.toPx()
        )

        levels.forEachIndexed { index, level ->
            val left = index * (barWidth + gap)
            val barHeight = (size.height * level).coerceAtLeast(size.height * 0.16f)
            val top = (size.height - barHeight) / 2f
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        glow.copy(alpha = 0.42f),
                        accent.copy(alpha = 0.92f),
                        glow.copy(alpha = 0.42f)
                    )
                ),
                topLeft = Offset(left, top),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth, barWidth)
            )
        }
    }
}
