package com.flowna.musicplayer.player

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.MediaStyleNotificationHelper
import com.flowna.musicplayer.FlownaApp
import com.flowna.musicplayer.MainActivity

class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private val notificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updateForegroundNotification()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            updateForegroundNotification()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            updateForegroundNotification()
        }

        override fun onMediaMetadataChanged(mediaMetadata: androidx.media3.common.MediaMetadata) {
            updateForegroundNotification()
        }
    }

    companion object {
        private const val PLAYBACK_NOTIFICATION_ID = 3001
        private const val ACTION_PREVIOUS = "com.flowna.musicplayer.action.PREVIOUS"
        private const val ACTION_PLAY_PAUSE = "com.flowna.musicplayer.action.PLAY_PAUSE"
        private const val ACTION_NEXT = "com.flowna.musicplayer.action.NEXT"
        private const val ACTION_STOP = "com.flowna.musicplayer.action.STOP"
        private var _player: ExoPlayer? = null

        @OptIn(UnstableApi::class)
        fun getOrCreatePlayer(context: Context): ExoPlayer {
            return _player ?: ExoPlayer.Builder(context.applicationContext)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .setUsage(C.USAGE_MEDIA)
                        .build(),
                    true
                )
                .setHandleAudioBecomingNoisy(true)
                .build().also { _player = it }
        }
    }

    private val player: ExoPlayer
        get() = getOrCreatePlayer(this)

    override fun onCreate() {
        super.onCreate()
        player.addListener(playerListener)
        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(createContentIntent())
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_PREVIOUS -> if (player.hasPreviousMediaItem()) player.seekToPrevious()
            ACTION_PLAY_PAUSE -> if (player.isPlaying) player.pause() else player.play()
            ACTION_NEXT -> if (player.hasNextMediaItem()) player.seekToNext()
            ACTION_STOP -> {
                player.pause()
                player.clearMediaItems()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
        }
        ensureForegroundStarted()
        return START_STICKY
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!player.playWhenReady || player.mediaItemCount == 0) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onDestroy() {
        player.removeListener(playerListener)
        notificationManager.cancel(PLAYBACK_NOTIFICATION_ID)
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }

    private fun ensureForegroundStarted() {
        startPlaybackForeground(buildPlaybackNotification())
    }

    private fun updateForegroundNotification() {
        if (player.mediaItemCount == 0) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            notificationManager.cancel(PLAYBACK_NOTIFICATION_ID)
            stopSelf()
            return
        }

        startPlaybackForeground(buildPlaybackNotification())
    }

    private fun startPlaybackForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                PLAYBACK_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(PLAYBACK_NOTIFICATION_ID, notification)
        }
    }

    @OptIn(UnstableApi::class)
    private fun buildPlaybackNotification(): Notification {
        val session = mediaSession
        val metadata = player.currentMediaItem?.mediaMetadata
        val title = metadata?.title?.toString()?.takeIf { it.isNotBlank() } ?: "Flowna çalıyor"
        val subtitle = listOfNotNull(
            metadata?.artist?.toString()?.takeIf { it.isNotBlank() },
            metadata?.albumTitle?.toString()?.takeIf { it.isNotBlank() }
        ).joinToString(" | ").ifBlank {
            if (player.mediaItemCount > 0) "Müzik oynatılıyor" else "Hazırlanıyor"
        }

        val previousIntent = createServiceActionPendingIntent(ACTION_PREVIOUS, 11)
        val playPauseIntent = createServiceActionPendingIntent(ACTION_PLAY_PAUSE, 12)
        val nextIntent = createServiceActionPendingIntent(ACTION_NEXT, 13)
        val stopIntent = createServiceActionPendingIntent(ACTION_STOP, 14)

        val builder = NotificationCompat.Builder(this, FlownaApp.PLAYBACK_CHANNEL_ID)
            .setSmallIcon(
                if (player.isPlaying) android.R.drawable.ic_media_pause
                else android.R.drawable.ic_media_play
            )
            .setContentTitle(title)
            .setContentText(subtitle)
            .setContentIntent(createContentIntent())
            .setDeleteIntent(stopIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(player.isPlaying || player.mediaItemCount > 0)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setShowWhen(false)
            .addAction(
                NotificationCompat.Action(
                    android.R.drawable.ic_media_previous,
                    "Önceki",
                    previousIntent
                )
            )
            .addAction(
                NotificationCompat.Action(
                    if (player.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                    if (player.isPlaying) "Duraklat" else "Çal",
                    playPauseIntent
                )
            )
            .addAction(
                NotificationCompat.Action(
                    android.R.drawable.ic_media_next,
                    "Sonraki",
                    nextIntent
                )
            )

        loadLargeIcon(metadata)?.let { builder.setLargeIcon(it) }

        session?.let {
            builder.setStyle(
                MediaStyleNotificationHelper.MediaStyle(it)
                    .setShowActionsInCompactView(0, 1, 2)
            )
        }

        return builder.build()
    }

    private fun loadLargeIcon(metadata: androidx.media3.common.MediaMetadata?): Bitmap? {
        val rawBytes = metadata?.artworkData
        if (rawBytes != null && rawBytes.isNotEmpty()) {
            return BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size)
        }
        return null
    }

    private fun createContentIntent(): PendingIntent {
        return PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun createServiceActionPendingIntent(action: String, requestCode: Int): PendingIntent {
        return PendingIntent.getService(
            this,
            requestCode,
            Intent(this, PlaybackService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
}
