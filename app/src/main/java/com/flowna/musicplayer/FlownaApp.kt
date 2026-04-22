package com.flowna.musicplayer

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.util.Log
import com.flowna.musicplayer.util.AppUpdateChecker
import com.flowna.musicplayer.util.PreferencesHelper
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class FlownaApp : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        PreferencesHelper.init(this)
        createNotificationChannels()
        initLibraries()
        scheduleAppUpdateCheck()
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
            scheduleYtDlpUpdateCheck()
        } catch (e: Throwable) {
            Log.e(TAG, "Kütüphaneler başlatılamadı", e)
        }
    }

    private fun scheduleYtDlpUpdateCheck() {
        if (!PreferencesHelper.shouldCheckForYtDlpUpdate()) return

        applicationScope.launch {
            runCatching {
                YoutubeDL.getInstance().updateYoutubeDL(
                    this@FlownaApp,
                    YoutubeDL.UpdateChannel._STABLE
                )
            }.onFailure {
                Log.w(TAG, "yt-dlp güncelleme kontrolü başarısız", it)
            }
            PreferencesHelper.markYtDlpUpdateChecked()
        }
    }

    private fun scheduleAppUpdateCheck() {
        if (!PreferencesHelper.shouldCheckForAppUpdate()) return

        applicationScope.launch {
            AppUpdateChecker.check()
                .onFailure { Log.w(TAG, "Uygulama güncelleme kontrolü başarısız", it) }
        }
    }

    companion object {
        private const val TAG = "FlownaApp"
        const val DOWNLOAD_CHANNEL_ID = "flowna_download"
        const val PLAYBACK_CHANNEL_ID = "flowna_playback"
    }
}
