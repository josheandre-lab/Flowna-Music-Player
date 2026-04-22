package com.flowna.musicplayer.data.recommendation

import com.flowna.musicplayer.data.FlownaSong

sealed interface RecommendationItem {
    val stableId: String
    val title: String
    val subtitle: String

    data class LocalSong(
        val song: FlownaSong,
        val reason: String
    ) : RecommendationItem {
        override val stableId: String = "local:${song.uri}"
        override val title: String = song.title
        override val subtitle: String = listOfNotNull(
            song.artist.takeIf { it.isNotBlank() },
            reason.takeIf { it.isNotBlank() }
        ).joinToString(" • ")
    }

    data class OnlineCandidate(
        val videoId: String,
        val videoUrl: String,
        override val title: String,
        val artist: String,
        val thumbnailUrl: String,
        val durationSeconds: Long,
        val sourceReason: String
    ) : RecommendationItem {
        override val stableId: String = "online:$videoId"
        override val subtitle: String = listOfNotNull(
            artist.takeIf { it.isNotBlank() },
            "Online"
        ).joinToString(" • ")
    }
}

data class TrackFingerprint(
    val genreTag: String,
    val moodTag: String,
    val tempoBucket: String
)
