package com.flowna.musicplayer.player

data class PreviewTrack(
    val videoId: String,
    val title: String,
    val artist: String,
    val artworkUrl: String,
    val streamUrl: String,
    val videoUrl: String,
    val durationSeconds: Long
)

data class PreviewPlaybackState(
    val isVisible: Boolean = false,
    val isLoading: Boolean = false,
    val track: PreviewTrack? = null,
    val isPlaying: Boolean = false,
    val progress: Float = 0f,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val errorMessage: String? = null
)
