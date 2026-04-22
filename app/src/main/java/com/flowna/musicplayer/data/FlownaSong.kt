package com.flowna.musicplayer.data

import android.net.Uri

data class FlownaSong(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String = "",
    val duration: Long, // milliseconds
    val uri: Uri,
    val albumArtUri: Uri? = null,
    val embeddedArtwork: ByteArray? = null,
    val isFlownaDownload: Boolean = false
)
