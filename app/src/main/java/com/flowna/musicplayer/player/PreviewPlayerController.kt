package com.flowna.musicplayer.player

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

object PreviewPlayerController {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(PreviewPlaybackState())
    val state: StateFlow<PreviewPlaybackState> = _state.asStateFlow()

    private var player: ExoPlayer? = null
    private var progressJob: Job? = null

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            syncState()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            syncState()
            if (playbackState == Player.STATE_ENDED) {
                stopPreview()
            }
        }
    }

    fun showLoading(track: PreviewTrack? = null) {
        _state.value = PreviewPlaybackState(
            isVisible = true,
            isLoading = true,
            track = track,
            errorMessage = null
        )
    }

    fun showError(message: String) {
        _state.value = PreviewPlaybackState(
            isVisible = true,
            isLoading = false,
            errorMessage = message
        )
    }

    fun startPreview(context: Context, track: PreviewTrack) {
        val previewPlayer = getOrCreatePlayer(context)
        previewPlayer.clearMediaItems()
        previewPlayer.setMediaItem(MediaItem.fromUri(track.streamUrl))
        previewPlayer.prepare()
        previewPlayer.playWhenReady = true

        _state.value = PreviewPlaybackState(
            isVisible = true,
            isLoading = false,
            track = track,
            isPlaying = true,
            durationMs = track.durationSeconds * 1000L,
            errorMessage = null
        )
        startProgressTicker()
        syncState()
    }

    fun togglePlayPause() {
        val previewPlayer = player ?: return
        if (previewPlayer.isPlaying) {
            previewPlayer.pause()
        } else {
            previewPlayer.play()
        }
        syncState()
    }

    fun stopPreview() {
        progressJob?.cancel()
        progressJob = null
        player?.run {
            removeListener(listener)
            stop()
            clearMediaItems()
            release()
        }
        player = null
        _state.value = PreviewPlaybackState()
    }

    fun release() {
        stopPreview()
        scope.cancel()
    }

    private fun getOrCreatePlayer(context: Context): ExoPlayer {
        return player ?: ExoPlayer.Builder(context.applicationContext)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
            .also {
                it.addListener(listener)
                player = it
            }
    }

    private fun startProgressTicker() {
        if (progressJob?.isActive == true) return

        progressJob = scope.launch {
            while (currentCoroutineContext().isActive && _state.value.track != null) {
                syncState()
                delay(if (_state.value.isPlaying) 250L else 500L)
            }
            progressJob = null
        }
    }

    private fun syncState() {
        val previewPlayer = player
        val track = _state.value.track
        if (previewPlayer == null || track == null) return

        val duration = previewPlayer.duration.takeIf { it > 0 } ?: (track.durationSeconds * 1000L)
        val position = previewPlayer.currentPosition.coerceAtLeast(0L)
        _state.value = _state.value.copy(
            isVisible = true,
            isLoading = false,
            isPlaying = previewPlayer.isPlaying,
            durationMs = duration,
            positionMs = position,
            progress = if (duration > 0) {
                (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
            } else {
                0f
            }
        )
    }
}
