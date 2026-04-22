package com.flowna.musicplayer.player

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.flowna.musicplayer.data.FlownaSong
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private var player: ExoPlayer = PlaybackService.getOrCreatePlayer(appContext)

    private val _currentSong = MutableStateFlow<FlownaSong?>(null)
    val currentSong: StateFlow<FlownaSong?> = _currentSong.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private var playlist = listOf<FlownaSong>()

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(playing: Boolean) {
            _isPlaying.value = playing
            syncPlaybackState()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val activePlayer = ensurePlayer()
            val index = activePlayer.currentMediaItemIndex
            if (index in playlist.indices) {
                _currentSong.value = playlist[index]
            }
            syncPlaybackState()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                _isPlaying.value = false
            }
            syncPlaybackState()
        }
    }

    init {
        ensurePlayer().addListener(playerListener)

        viewModelScope.launch {
            while (true) {
                delay(250)
                syncPlaybackState()
            }
        }
    }

    fun playSong(song: FlownaSong, songs: List<FlownaSong>) {
        val activePlayer = ensurePlayer()
        playlist = songs
        val index = songs.indexOf(song).coerceAtLeast(0)

        activePlayer.clearMediaItems()
        songs.forEach { item ->
            activePlayer.addMediaItem(buildMediaItem(item))
        }

        activePlayer.seekTo(index, 0)
        activePlayer.prepare()
        activePlayer.play()
        _currentSong.value = song
        syncPlaybackState()

        try {
            val intent = Intent(appContext, PlaybackService::class.java)
            ContextCompat.startForegroundService(appContext, intent)
        } catch (error: Throwable) {
            Log.e(TAG, "Playback servisi baslatilamadi", error)
        }
    }

    fun togglePlayPause() {
        val activePlayer = ensurePlayer()
        if (activePlayer.isPlaying) {
            activePlayer.pause()
        } else {
            activePlayer.play()
        }
        syncPlaybackState()
    }

    fun next() {
        val activePlayer = ensurePlayer()
        if (activePlayer.hasNextMediaItem()) {
            activePlayer.seekToNext()
            syncPlaybackState()
        }
    }

    fun previous() {
        val activePlayer = ensurePlayer()
        if (activePlayer.hasPreviousMediaItem()) {
            activePlayer.seekToPrevious()
            syncPlaybackState()
        }
    }

    fun seekTo(position: Float) {
        val activePlayer = ensurePlayer()
        val duration = activePlayer.duration
        if (duration > 0) {
            activePlayer.seekTo((position.coerceIn(0f, 1f) * duration).toLong())
            syncPlaybackState()
        }
    }

    fun seekBy(deltaMs: Long) {
        val activePlayer = ensurePlayer()
        val duration = activePlayer.duration.takeIf { it > 0 } ?: return
        val targetPosition = (activePlayer.currentPosition + deltaMs).coerceIn(0L, duration)
        activePlayer.seekTo(targetPosition)
        syncPlaybackState()
    }

    fun seekForward() {
        seekBy(10_000L)
    }

    fun seekBackward() {
        seekBy(-10_000L)
    }

    fun addCurrentSongToQueue() {
        val song = _currentSong.value ?: return
        val activePlayer = ensurePlayer()
        val insertIndex = (activePlayer.currentMediaItemIndex + 1).coerceAtLeast(0)
        val boundedIndex = insertIndex.coerceAtMost(playlist.size)
        playlist = buildList {
            addAll(playlist.take(boundedIndex))
            add(song)
            addAll(playlist.drop(boundedIndex))
        }
        activePlayer.addMediaItem(boundedIndex, buildMediaItem(song))
    }

    override fun onCleared() {
        runCatching { player.removeListener(playerListener) }
        super.onCleared()
    }

    private fun ensurePlayer(): ExoPlayer {
        val latestPlayer = PlaybackService.getOrCreatePlayer(appContext)
        if (latestPlayer !== player) {
            runCatching { player.removeListener(playerListener) }
            player = latestPlayer
            player.addListener(playerListener)
        }
        return player
    }

    private fun syncPlaybackState() {
        val activePlayer = ensurePlayer()
        val duration = activePlayer.duration.takeIf { it > 0 } ?: 0L
        val position = activePlayer.currentPosition.coerceAtLeast(0L)

        _durationMs.value = duration
        _currentPositionMs.value = position
        _progress.value = if (duration > 0) {
            (position.toFloat() / duration).coerceIn(0f, 1f)
        } else {
            0f
        }

        if (_currentSong.value == null) {
            val index = activePlayer.currentMediaItemIndex
            if (index in playlist.indices) {
                _currentSong.value = playlist[index]
            }
        }
    }

    private fun buildMediaItem(item: FlownaSong): MediaItem {
        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(item.title)
            .setArtist(item.artist)
            .setAlbumTitle(item.album)

        item.albumArtUri?.let { metadataBuilder.setArtworkUri(it) }
        item.embeddedArtwork?.let { metadataBuilder.setArtworkData(it, MediaMetadata.PICTURE_TYPE_FRONT_COVER) }

        return MediaItem.Builder()
            .setUri(item.uri)
            .setMediaMetadata(metadataBuilder.build())
            .build()
    }

    companion object {
        private const val TAG = "PlayerViewModel"
    }
}
