package com.flowna.musicplayer.util

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import com.flowna.musicplayer.data.FlownaSong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

data class ArtworkPalette(
    val backgroundStart: Color,
    val backgroundEnd: Color,
    val accent: Color,
    val glow: Color
) {
    companion object {
        val Default = ArtworkPalette(
            backgroundStart = Color(0xFFF6E8DF),
            backgroundEnd = Color(0xFFF5F1FB),
            accent = Color(0xFF9A67F7),
            glow = Color(0x33B68CFF)
        )
    }
}

object ArtworkPaletteResolver {

    private val memoryCache = ConcurrentHashMap<String, ArtworkPalette>()

    suspend fun resolve(
        context: Context,
        song: FlownaSong?
    ): ArtworkPalette = withContext(Dispatchers.IO) {
        val targetSong = song ?: return@withContext ArtworkPalette.Default
        val cacheKey = targetSong.uri.toString()
        memoryCache[cacheKey]?.let { return@withContext it }

        val bitmap = runCatching {
            when {
                targetSong.embeddedArtwork != null -> {
                    BitmapFactory.decodeByteArray(
                        targetSong.embeddedArtwork,
                        0,
                        targetSong.embeddedArtwork.size
                    )
                }

                targetSong.albumArtUri != null -> {
                    context.contentResolver.openInputStream(targetSong.albumArtUri).use { stream ->
                        if (stream != null) BitmapFactory.decodeStream(stream) else null
                    }
                }

                else -> null
            }
        }.getOrNull()

        val palette = bitmap?.let { source ->
            val generated = Palette.from(source).clearFilters().generate()
            val accentColor = generated.vibrantSwatch?.rgb
                ?: generated.dominantSwatch?.rgb
                ?: generated.mutedSwatch?.rgb
            accentColor?.let { buildPalette(it) }
        } ?: ArtworkPalette.Default

        memoryCache[cacheKey] = palette
        palette
    }

    private fun buildPalette(baseColorInt: Int): ArtworkPalette {
        val accent = Color(baseColorInt)
        val softened = blend(baseColorInt, 0xFFFFFFFF.toInt(), 0.72f)
        val softenedSecondary = blend(baseColorInt, 0xFFF1E8FB.toInt(), 0.64f)
        val glow = blend(baseColorInt, 0xFFFFFFFF.toInt(), 0.22f)

        return ArtworkPalette(
            backgroundStart = Color(softened),
            backgroundEnd = Color(softenedSecondary),
            accent = accent,
            glow = Color(glow).copy(alpha = 0.32f)
        )
    }

    private fun blend(from: Int, to: Int, amount: Float): Int {
        return ColorUtils.blendARGB(from, to, amount.coerceIn(0f, 1f))
    }
}
