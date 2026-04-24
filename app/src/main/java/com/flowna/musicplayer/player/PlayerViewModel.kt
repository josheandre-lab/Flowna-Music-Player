package com.flowna.musicplayer.player

import android.annotation.SuppressLint
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
import com.flowna.musicplayer.data.recommendation.RecommendationItem
import com.flowna.musicplayer.data.repository.ListeningInsightsRepository
import com.flowna.musicplayer.data.repository.SearchRepository
import com.flowna.musicplayer.service.DownloadService
import com.flowna.musicplayer.util.PreferencesHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@SuppressLint("UnsafeOptInUsageError")
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

    private val _audioSessionId = MutableStateFlow(player.audioSessionId)
    val audioSessionId: StateFlow<Int> = _audioSessionId.asStateFlow()

    val previewState: StateFlow<PreviewPlaybackState> = PreviewPlayerController.state

    private var playlist = listOf<FlownaSong>()
    private var previewSnapshot: PlaybackSnapshot? = null
    private var progressJob: Job? = null
    private var playbackUiVisible = false
    private var trackedSongUri: String? = null
    private var trackedPositionAnchorMs: Long = 0L
    private var completionHandledForCurrentSong = false

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(playing: Boolean) {
            _isPlaying.value = playing
            if (!playing) {
                flushTrackedPlayback()
            } else {
                beginTrackingCurrentSong()
            }
            syncPlaybackState()
            updateProgressTicker()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val previousSong = _currentSong.value
            flushTrackedPlayback()

            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO &&
                previousSong != null &&
                !completionHandledForCurrentSong &&
                previewSnapshot == null
            ) {
                completionHandledForCurrentSong = true
                viewModelScope.launch {
                    ListeningInsightsRepository.incrementCompletedPlayCount(appContext, previousSong)
                }
            }

            val activePlayer = ensurePlayer()
            val index = activePlayer.currentMediaItemIndex
            _currentSong.value = playlist.getOrNull(index)
            trackedSongUri = null
            trackedPositionAnchorMs = 0L
            completionHandledForCurrentSong = false
            beginTrackingCurrentSong()
            syncPlaybackState()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                flushTrackedPlayback()
                val song = _currentSong.value
                if (song != null && !completionHandledForCurrentSong && previewSnapshot == null) {
                    completionHandledForCurrentSong = true
                    viewModelScope.launch {
                        ListeningInsightsRepository.incrementCompletedPlayCount(appContext, song)
                    }
                }
                _isPlaying.value = false
            }
            syncPlaybackState()
            updateProgressTicker()
        }
    }

    init {
        ensurePlayer().addListener(playerListener)
        syncPlaybackState()
    }

    fun playSong(song: FlownaSong, songs: List<FlownaSong>) {
        clearPreviewState(restore = false)

        val activePlayer = ensurePlayer()
        playlist = songs
        val index = songs.indexOf(song).coerceAtLeast(0)

        activePlayer.clearMediaItems()
        songs.forEach { item ->
            activePlayer.addMediaItem(buildMediaItem(item))
        }

        completionHandledForCurrentSong = false
        trackedSongUri = null
        trackedPositionAnchorMs = 0L

        activePlayer.seekTo(index, 0)
        activePlayer.prepare()
        activePlayer.play()
        _currentSong.value = song
        beginTrackingCurrentSong()
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
            trackedPositionAnchorMs = activePlayer.currentPosition
            syncPlaybackState()
        }
    }

    fun seekBy(deltaMs: Long) {
        val activePlayer = ensurePlayer()
        val duration = activePlayer.duration.takeIf { it > 0 } ?: return
        val targetPosition = (activePlayer.currentPosition + deltaMs).coerceIn(0L, duration)
        activePlayer.seekTo(targetPosition)
        trackedPositionAnchorMs = activePlayer.currentPosition
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

    fun setPlaybackUiVisible(visible: Boolean) {
        playbackUiVisible = visible
        if (visible) {
            syncPlaybackState()
        }
        updateProgressTicker()
    }

    suspend fun startPreview(result: com.flowna.musicplayer.data.repository.SearchResult): Result<Unit> {
        PreviewPlayerController.showLoading(
            PreviewTrack(
                videoId = result.videoId,
                title = result.title,
                artist = result.uploaderName,
                artworkUrl = result.thumbnailUrl,
                streamUrl = "",
                videoUrl = result.videoUrl,
                durationSeconds = result.duration
            )
        )
        return SearchRepository.resolvePreviewTrack(result)
            .fold(
                onSuccess = { beginPreviewPlayback(it) },
                onFailure = {
                    PreviewPlayerController.stopPreview()
                    Result.failure(it)
                }
            )
    }

    suspend fun startPreview(candidate: RecommendationItem.OnlineCandidate): Result<Unit> {
        PreviewPlayerController.showLoading(
            PreviewTrack(
                videoId = candidate.videoId,
                title = candidate.title,
                artist = candidate.artist,
                artworkUrl = candidate.thumbnailUrl,
                streamUrl = "",
                videoUrl = candidate.videoUrl,
                durationSeconds = candidate.durationSeconds
            )
        )
        return SearchRepository.resolvePreviewTrack(
            title = candidate.title,
            artist = candidate.artist,
            thumbnailUrl = candidate.thumbnailUrl,
            durationSeconds = candidate.durationSeconds,
            videoId = candidate.videoId,
            videoUrl = candidate.videoUrl
        ).fold(
            onSuccess = { beginPreviewPlayback(it) },
            onFailure = {
                PreviewPlayerController.stopPreview()
                Result.failure(it)
            }
        )
    }

    fun stopPreviewAndRestore() {
        clearPreviewState(restore = true)
    }

    fun togglePreviewPlayPause() {
        PreviewPlayerController.togglePlayPause()
    }

    fun downloadPreviewTrack() {
        val track = previewState.value.track ?: return
        val result = DownloadService.start(
            context = appContext,
            videoUrl = track.videoUrl,
            title = track.title,
            artist = track.artist
        )
        if (result.isSuccess) {
            clearPreviewState(restore = true)
        }
    }

    fun toggleFavorite(song: FlownaSong): Boolean {
        val isFavorite = PreferencesHelper.toggleFavoriteSong(song.uri.toString())
        viewModelScope.launch {
            ListeningInsightsRepository.updateFavoriteState(appContext, song, isFavorite)
        }
        return isFavorite
    }

    override fun onCleared() {
        runCatching { player.removeListener(playerListener) }
        progressJob?.cancel()
        PreviewPlayerController.stopPreview()
        super.onCleared()
    }

    private fun ensurePlayer(): ExoPlayer {
        val latestPlayer = PlaybackService.getOrCreatePlayer(appContext)
        if (latestPlayer !== player) {
            runCatching { player.removeListener(playerListener) }
            flushTrackedPlayback()
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
        _audioSessionId.value = activePlayer.audioSessionId

        if (_currentSong.value == null) {
            val index = activePlayer.currentMediaItemIndex
            _currentSong.value = playlist.getOrNull(index)
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

    private suspend fun beginPreviewPlayback(track: PreviewTrack): Result<Unit> {
        return runCatching {
            if (previewSnapshot == null) {
                previewSnapshot = captureSnapshot()
            }
            pauseForPreview()
            PreviewPlayerController.startPreview(appContext, track)
        }
    }

    private fun captureSnapshot(): PlaybackSnapshot? {
        val activePlayer = ensurePlayer()
        if (playlist.isEmpty() || activePlayer.mediaItemCount == 0) return null
        return PlaybackSnapshot(
            queue = playlist,
            currentIndex = activePlayer.currentMediaItemIndex.coerceAtLeast(0),
            positionMs = activePlayer.currentPosition.coerceAtLeast(0L),
            wasPlaying = activePlayer.isPlaying
        )
    }

    private fun restoreSnapshot(snapshot: PlaybackSnapshot) {
        val activePlayer = ensurePlayer()
        playlist = snapshot.queue
        activePlayer.clearMediaItems()
        snapshot.queue.forEach { activePlayer.addMediaItem(buildMediaItem(it)) }
        activePlayer.prepare()

        val safeIndex = snapshot.currentIndex.coerceIn(0, snapshot.queue.lastIndex.coerceAtLeast(0))
        activePlayer.seekTo(safeIndex, snapshot.positionMs)
        _currentSong.value = snapshot.queue.getOrNull(safeIndex)
        completionHandledForCurrentSong = false
        trackedSongUri = null
        trackedPositionAnchorMs = 0L

        if (snapshot.wasPlaying) {
            activePlayer.play()
            beginTrackingCurrentSong()
        } else {
            activePlayer.pause()
        }

        syncPlaybackState()
    }

    private fun pauseForPreview() {
        val activePlayer = ensurePlayer()
        if (activePlayer.isPlaying) {
            activePlayer.pause()
        }
        syncPlaybackState()
    }

    private fun clearPreviewState(restore: Boolean) {
        PreviewPlayerController.stopPreview()
        val snapshot = previewSnapshot
        previewSnapshot = null
        if (restore && snapshot != null) {
            restoreSnapshot(snapshot)
        }
    }

    private fun beginTrackingCurrentSong() {
        if (previewSnapshot != null || !_isPlaying.value) return
        val song = _currentSong.value ?: return
        val songUri = song.uri.toString()
        trackedSongUri = songUri
        trackedPositionAnchorMs = ensurePlayer().currentPosition
        viewModelScope.launch {
            ListeningInsightsRepository.markSongStarted(appContext, song)
        }
    }

    private fun flushTrackedPlayback() {
        if (previewSnapshot != null) return
        val song = _currentSong.value ?: return
        val songUri = trackedSongUri ?: return
        if (song.uri.toString() != songUri) return

        val listenedMs = (ensurePlayer().currentPosition - trackedPositionAnchorMs).coerceAtLeast(0L)
        if (listenedMs > 0L) {
            viewModelScope.launch {
                ListeningInsightsRepository.addPlaybackDuration(appContext, song, listenedMs)
            }
        }
        trackedPositionAnchorMs = ensurePlayer().currentPosition
    }

    private fun updateProgressTicker() {
        val shouldRun = playbackUiVisible && (ensurePlayer().isPlaying || ensurePlayer().playbackState == Player.STATE_READY)
        if (!shouldRun) {
            progressJob?.cancel()
            progressJob = null
            return
        }

        if (progressJob?.isActive == true) return

        progressJob = viewModelScope.launch {
            while (currentCoroutineContext().isActive) {
                syncPlaybackState()
                delay(if (_isPlaying.value) 250L else 500L)
                val activePlayer = ensurePlayer()
                if (!playbackUiVisible || (!activePlayer.isPlaying && activePlayer.playbackState != Player.STATE_READY)) {
                    break
                }
            }
            progressJob = null
        }
    }

    companion object {
        private const val TAG = "PlayerViewModel"
    }
}
