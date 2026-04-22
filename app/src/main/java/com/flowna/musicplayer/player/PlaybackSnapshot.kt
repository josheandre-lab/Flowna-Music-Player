package com.flowna.musicplayer.player

import com.flowna.musicplayer.data.FlownaSong

data class PlaybackSnapshot(
    val queue: List<FlownaSong>,
    val currentIndex: Int,
    val positionMs: Long,
    val wasPlaying: Boolean
)
