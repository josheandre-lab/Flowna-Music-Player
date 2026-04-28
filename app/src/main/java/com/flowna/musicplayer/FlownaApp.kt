package com.flowna.musicplayer

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentCallbacks2
import android.util.Log
import com.flowna.musicplayer.data.repository.SearchRepository
import com.flowna.musicplayer.util.PreferencesHelper
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL

class FlownaApp : Application() {

    override fun onCreate() {
        super.onCreate()
        PreferencesHelper.init(this)
        createNotificationChannels()
        initLibraries()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            SearchRepository.clearPreviewMemoryCache()
        }
    }

    private fun createNotificationChannels() {
        val downloadChannel = NotificationChannel(
            DOWNLOAD_CHANNEL_ID,
            "İndirme Bildirimleri",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Şarkı indirme durumları"
        }
        val playbackChannel = NotificationChannel(
            PLAYBACK_CHANNEL_ID,
            "Oynatma Bildirimleri",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Arka planda müzik oynatma durumu"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(downloadChannel)
        manager.createNotificationChannel(playbackChannel)
    }

    private fun initLibraries() {
        try {
            YoutubeDL.getInstance().init(this)
            FFmpeg.getInstance().init(this)
        } catch (error: Throwable) {
            Log.e(TAG, "Kütüphaneler başlatılamadı", error)
        }
    }

    companion object {
        private const val TAG = "FlownaApp"
        const val DOWNLOAD_CHANNEL_ID = "flowna_download"
        const val PLAYBACK_CHANNEL_ID = "flowna_playback"
    }
}
