package com.flowna.musicplayer.ui.components

import android.media.audiofx.Visualizer
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.delay

@Composable
fun ReactiveWaveform(
    audioSessionId: Int,
    isActive: Boolean,
    modifier: Modifier = Modifier,
    accent: Color,
    glow: Color
) {
    val levels = remember { mutableStateListOf<Float>().apply { repeat(24) { add(0.18f) } } }
    val useFallback = remember { androidx.compose.runtime.mutableStateOf(audioSessionId <= 0) }

    DisposableEffect(audioSessionId, isActive) {
        if (!isActive || audioSessionId <= 0) {
            useFallback.value = true
            onDispose { }
        } else {
            val visualizer = runCatching {
                Visualizer(audioSessionId).apply {
                    captureSize = Visualizer.getCaptureSizeRange()[1]
                    setDataCaptureListener(
                        object : Visualizer.OnDataCaptureListener {
                            override fun onWaveFormDataCapture(
                                visualizer: Visualizer?,
                                waveform: ByteArray?,
                                samplingRate: Int
                            ) {
                                waveform?.let {
                                    updateLevelsFromWaveform(levels, it)
                                }
                            }

                            override fun onFftDataCapture(
                                visualizer: Visualizer?,
                                fft: ByteArray?,
                                samplingRate: Int
                            ) = Unit
                        },
                        Visualizer.getMaxCaptureRate() / 3,
                        true,
                        false
                    )
                    enabled = true
                }
            }.onFailure {
                useFallback.value = true
            }.getOrNull()

            if (visualizer != null) {
                useFallback.value = false
            }

            onDispose {
                runCatching {
                    visualizer?.enabled = false
                    visualizer?.release()
                }
            }
        }
    }

    LaunchedEffect(isActive, useFallback.value) {
        if (!isActive || !useFallback.value) return@LaunchedEffect
        var frame = 0
        while (isActive && useFallback.value) {
            for (index in levels.indices) {
                val wave = ((sin((frame + index * 0.75) / 4.2) + 1f) / 2f).toFloat()
                levels[index] = 0.16f + (wave * 0.72f)
            }
            frame++
            delay(90L)
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(58.dp)
    ) {
        val barWidth = size.width / (levels.size * 1.6f)
        val gap = barWidth * 0.6f
        levels.forEachIndexed { index, level ->
            val left = index * (barWidth + gap)
            val barHeight = (size.height * level).coerceAtLeast(size.height * 0.15f)
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(glow.copy(alpha = 0.22f), accent.copy(alpha = 0.84f))
                ),
                topLeft = androidx.compose.ui.geometry.Offset(left, size.height - barHeight),
                size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth, barWidth)
            )
        }
    }
}

private fun updateLevelsFromWaveform(levels: SnapshotStateList<Float>, waveform: ByteArray) {
    if (waveform.isEmpty()) return
    val chunkSize = (waveform.size / levels.size).coerceAtLeast(1)
    for (index in levels.indices) {
        val start = index * chunkSize
        val end = minOf(start + chunkSize, waveform.size)
        if (start >= end) continue
        var sum = 0f
        for (sampleIndex in start until end) {
            sum += kotlin.math.abs(waveform[sampleIndex].toInt()) / 128f
        }
        val average = (sum / (end - start)).coerceIn(0f, 1.4f)
        levels[index] = 0.12f + (average * 0.72f)
    }
}
