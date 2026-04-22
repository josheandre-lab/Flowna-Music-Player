package com.flowna.musicplayer.data.recommendation

import com.flowna.musicplayer.data.FlownaSong
import java.util.Locale

object TrackHeuristics {

    fun fingerprint(song: FlownaSong): TrackFingerprint {
        val joined = buildString {
            append(song.title)
            append(' ')
            append(song.artist)
            append(' ')
            append(song.album)
        }.lowercase(Locale.ROOT)

        val genre = when {
            joined.contains("akustik") || joined.contains("acoustic") -> "acoustic"
            joined.contains("arabesk") -> "arabesk"
            joined.contains("turku") || joined.contains("türkü") || joined.contains("halk") -> "folk"
            joined.contains("rap") || joined.contains("hip hop") || joined.contains("trap") -> "rap"
            joined.contains("rock") || joined.contains("metal") -> "rock"
            joined.contains("pop") -> "pop"
            joined.contains("remix") || joined.contains("dance") -> "dance"
            joined.contains("slow") -> "slow"
            else -> "general"
        }

        val mood = when {
            joined.contains("akustik") || joined.contains("live") || joined.contains("acoustic") -> "calm"
            joined.contains("arabesk") || joined.contains("turku") || joined.contains("türkü") -> "emotional"
            joined.contains("remix") || joined.contains("dance") || joined.contains("party") -> "upbeat"
            joined.contains("rap") || joined.contains("trap") || joined.contains("rock") -> "energetic"
            joined.contains("slow") || joined.contains("acı") || joined.contains("hüzün") -> "soft"
            else -> "balanced"
        }

        val tempo = when {
            genre in setOf("dance", "rap", "rock") || mood == "energetic" -> "energetic"
            genre in setOf("acoustic", "slow") || mood in setOf("calm", "soft", "emotional") -> "slow"
            else -> "mid"
        }

        return TrackFingerprint(
            genreTag = genre,
            moodTag = mood,
            tempoBucket = tempo
        )
    }
}
