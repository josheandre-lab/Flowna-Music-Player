package com.flowna.musicplayer.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.flowna.musicplayer.FlownaApp
import com.flowna.musicplayer.MainActivity
import com.flowna.musicplayer.data.repository.LibraryRepository
import com.flowna.musicplayer.util.DownloadStatus
import com.flowna.musicplayer.util.DownloadTracker
import com.flowna.musicplayer.util.MediaStoreHelper
import com.flowna.musicplayer.util.PreferencesHelper
import com.flowna.musicplayer.util.TextNormalizer
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLResponse
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.SocketTimeoutException
import java.util.Collections
import kotlin.math.abs

class DownloadService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val activeDownloadIds = Collections.synchronizedSet(mutableSetOf<String>())
    private val notificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val videoUrl = intent?.getStringExtra(EXTRA_VIDEO_URL) ?: run {
            stopSelf()
            return START_NOT_STICKY
        }
        val downloadId = intent.getStringExtra(EXTRA_DOWNLOAD_ID) ?: createDownloadId(videoUrl)
        val rawTitle = intent.getStringExtra(EXTRA_TITLE) ?: "Bilinmeyen Şarkı"
        val artist = intent.getStringExtra(EXTRA_ARTIST) ?: ""
        val sanitizedTitle = sanitizeTitle(rawTitle)

        activeDownloadIds += downloadId
        DownloadTracker.addDownload(downloadId, sanitizedTitle, artist, videoUrl)
        runCatching {
            startForeground(SERVICE_NOTIFICATION_ID, buildSummaryNotification(activeDownloadIds.size))
            updateDownloadNotification(downloadId, sanitizedTitle, "Hazırlanıyor", 0, true, false)
        }.onFailure { error ->
            activeDownloadIds -= downloadId
            DownloadTracker.updateStatus(
                id = downloadId,
                status = DownloadStatus.FAILED,
                statusMessage = "İndirme başlatılamadı",
                errorMessage = buildDetailedErrorMessage(error)
            )
            Log.e(TAG, "Foreground servis baslatilamadi [$downloadId]", error)
            stopSelf()
            return START_NOT_STICKY
        }

        serviceScope.launch {
            try {
                ensureLibrariesReady()
                if (MediaStoreHelper.isDuplicate(this@DownloadService, sanitizedTitle)) {
                    throw DownloadFailureException("Bu şarkı zaten kütüphanende var.")
                }

                downloadAndSave(downloadId, videoUrl, sanitizedTitle, artist)

                DownloadTracker.updateStatus(
                    id = downloadId,
                    status = DownloadStatus.COMPLETED,
                    statusMessage = "İndirme tamamlandı"
                )
                updateDownloadNotification(
                    downloadId = downloadId,
                    title = sanitizedTitle,
                    text = "İndirme tamamlandı",
                    progress = 100,
                    ongoing = false,
                    isError = false
                )

                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@DownloadService,
                        "$sanitizedTitle indirildi!",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Throwable) {
                val errorMessage = buildDetailedErrorMessage(e)
                Log.e(TAG, "İndirme hatası [$downloadId]", e)

                DownloadTracker.updateStatus(
                    id = downloadId,
                    status = DownloadStatus.FAILED,
                    statusMessage = "İndirme başarısız",
                    errorMessage = errorMessage
                )
                updateDownloadNotification(
                    downloadId = downloadId,
                    title = sanitizedTitle,
                    text = "İndirme başarısız",
                    progress = 0,
                    ongoing = false,
                    isError = true
                )

                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@DownloadService,
                        errorMessage.lineSequence().firstOrNull() ?: "İndirme başarısız oldu.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } finally {
                activeDownloadIds -= downloadId
                if (activeDownloadIds.isEmpty()) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } else {
                    notificationManager.notify(
                        SERVICE_NOTIFICATION_ID,
                        buildSummaryNotification(activeDownloadIds.size)
                    )
                }
            }
        }

        return START_NOT_STICKY
    }

    private suspend fun downloadAndSave(downloadId: String, videoUrl: String, title: String, artist: String) {
        val tempDir = File(cacheDir, "flowna_downloads/$downloadId").apply {
            deleteRecursively()
            mkdirs()
        }

        try {
            val request = YoutubeDLRequest(normalizeVideoUrl(videoUrl)).apply {
                addOption("--no-playlist")
                addOption("--no-mtime")
                addOption("--newline")
                addOption("-x")
                addOption("--audio-format", "mp3")
                addOption("--audio-quality", PreferencesHelper.getAudioQuality())
                addOption("--add-metadata")
                addOption("--embed-thumbnail")
                addOption("--convert-thumbnails", "jpg")
                addOption("--parse-metadata", "%(uploader)s:%(artist)s")
                addOption("-o", "${tempDir.absolutePath}/track.%(ext)s")
            }

            DownloadTracker.updateProgress(downloadId, 0, "İndirme başladı")
            updateDownloadNotification(downloadId, title, "İndiriliyor", 0, true, false)

            val response = YoutubeDL.getInstance().execute(request, downloadId) { progress, _, line ->
                val progressInt = progress.toInt().coerceIn(0, 100)
                val statusMessage = progressLineToStatus(progressInt, line)

                if (progressInt >= 100) {
                    DownloadTracker.updateStatus(
                        id = downloadId,
                        status = DownloadStatus.CONVERTING,
                        statusMessage = statusMessage
                    )
                } else {
                    DownloadTracker.updateProgress(downloadId, progressInt, statusMessage)
                }

                updateDownloadNotification(
                    downloadId = downloadId,
                    title = title,
                    text = statusMessage,
                    progress = progressInt,
                    ongoing = true,
                    isError = false
                )
            }

            if (response.exitCode != 0) {
                throw DownloadFailureException(extractResponseError(response))
            }

            DownloadTracker.updateStatus(
                id = downloadId,
                status = DownloadStatus.CONVERTING,
                statusMessage = "Dosya kütüphaneye ekleniyor"
            )
            updateDownloadNotification(
                downloadId = downloadId,
                title = title,
                text = "Kütüphane kaydı yapılıyor",
                progress = 100,
                ongoing = true,
                isError = false
            )

            val downloadedFile = tempDir
                .walkTopDown()
                .firstOrNull { it.isFile && it.extension.equals("mp3", ignoreCase = true) }
                ?: throw DownloadFailureException(
                    buildString {
                        append("Ses dosyası oluşturulamadı.")
                        val responseError = extractResponseError(response)
                        if (responseError.isNotBlank()) {
                            append("\nDetay: ")
                            append(responseError)
                        }
                    }
                )

            val savedUri = MediaStoreHelper.saveToMediaStore(this, downloadedFile, title, artist)
                ?: throw DownloadFailureException("İndirilen dosya kütüphaneye kaydedilemedi.")

            LibraryRepository.addOrUpdateFromUri(this, savedUri)
                .onFailure { Log.w(TAG, "Kütüphane önbelleğine ekleme başarısız", it) }
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun buildSummaryNotification(activeCount: Int): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val contentText = if (activeCount <= 1) {
            "1 indirme sürüyor"
        } else {
            "$activeCount indirme sürüyor"
        }

        return NotificationCompat.Builder(this, FlownaApp.DOWNLOAD_CHANNEL_ID)
            .setContentTitle("Flowna")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(0, 0, true)
            .setSilent(true)
            .build()
    }

    private fun buildDownloadNotification(
        downloadId: String,
        title: String,
        text: String,
        progress: Int,
        ongoing: Boolean,
        isError: Boolean
    ): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            downloadNotificationId(downloadId),
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, FlownaApp.DOWNLOAD_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(
                if (isError) android.R.drawable.stat_notify_error
                else android.R.drawable.stat_sys_download
            )
            .setContentIntent(pendingIntent)
            .setOngoing(ongoing)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setProgress(100, progress.coerceIn(0, 100), ongoing && progress == 0)
            .build()
    }

    private fun updateDownloadNotification(
        downloadId: String,
        title: String,
        text: String,
        progress: Int,
        ongoing: Boolean,
        isError: Boolean
    ) {
        val notification = buildDownloadNotification(downloadId, title, text, progress, ongoing, isError)
        notificationManager.notify(downloadNotificationId(downloadId), notification)
        notificationManager.notify(SERVICE_NOTIFICATION_ID, buildSummaryNotification(activeDownloadIds.size))
    }

    private fun progressLineToStatus(progress: Int, rawLine: String?): String {
        val line = rawLine.orEmpty().lowercase()
        return when {
            progress >= 100 -> "Dönüştürülüyor"
            "extract" in line || "ffmpeg" in line || "post-process" in line -> "Dönüştürülüyor"
            "destination" in line -> "Dosya hazırlanıyor"
            else -> "İndiriliyor"
        }
    }

    private fun extractResponseError(response: YoutubeDLResponse): String {
        val rawMessage = listOf(response.err, response.out)
            .firstOrNull { it.isNotBlank() }
            .orEmpty()

        return rawMessage
            .lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() && !"[".equals(it.firstOrNull()?.toString()) }
            ?: rawMessage.trim()
    }

    private fun normalizeVideoUrl(videoUrl: String): String {
        val trimmedUrl = videoUrl.trim()
        if (trimmedUrl.startsWith("http://") || trimmedUrl.startsWith("https://")) {
            return trimmedUrl
        }
        return if (trimmedUrl.startsWith("/")) {
            "https://www.youtube.com$trimmedUrl"
        } else {
            "https://www.youtube.com/$trimmedUrl"
        }
    }

    private fun buildDetailedErrorMessage(error: Throwable): String {
        val detail = error.message?.trim().orEmpty()
        val normalized = detail.lowercase()

        val friendly = when {
            error is SocketTimeoutException || "timeout" in normalized -> {
                "Bağlantı zaman aşımına uğradı. Lütfen tekrar deneyin."
            }

            "unsupported url" in normalized || "invalid url" in normalized -> {
                "Video bağlantısı yt-dlp için uygun değil."
            }

            error is SecurityException || "foreground service" in normalized -> {
                "İndirme servisi Android tarafında engellendi."
            }

            "sign in to confirm" in normalized || "login" in normalized || "private video" in normalized -> {
                "Bu video için ek doğrulama gerekiyor."
            }

            "ffmpeg" in normalized || "extractaudio" in normalized || "postprocessing" in normalized -> {
                "Ses dosyası oluşturulamadı."
            }

            "already kutuphanende var" in normalized || "zaten kutuphanende var" in normalized -> {
                "Bu şarkı zaten kütüphanende var."
            }

            "network" in normalized || "unable to download webpage" in normalized || "connection" in normalized -> {
                "Bağlantı hatası nedeniyle indirme tamamlanamadı."
            }

            detail.isNotBlank() -> detail
            else -> "İndirme başarısız oldu."
        }

        val technicalDetail = detail.takeIf {
            it.isNotBlank() && !friendly.contains(it, ignoreCase = true)
        }

        return if (technicalDetail != null) {
            "$friendly\nDetay: $technicalDetail"
        } else {
            friendly
        }
    }

    private fun ensureLibrariesReady() {
        YoutubeDL.getInstance().init(this)
        FFmpeg.getInstance().init(this)
    }

    private fun sanitizeTitle(title: String): String {
        var clean = TextNormalizer.normalizeHumanText(title) ?: title
        val patterns = listOf(
            "\\s*\\[Official\\s*(Music\\s*)?Video\\]",
            "\\s*\\(Official\\s*(Music\\s*)?Video\\)",
            "\\s*\\[Official\\s*Audio\\]",
            "\\s*\\(Official\\s*Audio\\)",
            "\\s*\\(Lyric(s)?\\s*(Video)?\\)",
            "\\s*\\[Lyric(s)?\\s*(Video)?\\]",
            "\\s*\\(HD\\)", "\\s*\\[HD\\]",
            "\\s*\\(4K\\)", "\\s*\\[4K\\]",
            "\\s*\\(HQ\\)", "\\s*\\[HQ\\]",
            "\\s*\\(Audio\\)", "\\s*\\[Audio\\]",
            "\\s*\\(Visualizer\\)", "\\s*\\[Visualizer\\]",
            "\\s*\\|\\s*Official.*$",
            "\\s*//\\s*Official.*$"
        )
        for (pattern in patterns) {
            clean = clean.replace(Regex(pattern, RegexOption.IGNORE_CASE), "")
        }
        clean = clean.replace(Regex("[\\p{So}\\p{Cn}]"), "")
        clean = clean.replace(Regex("[\\\\/:*?\"<>|]"), "")
        clean = clean.trim().replace(Regex("\\s+"), " ")
        clean = clean.trim('-', '.', ' ')
        return clean.ifEmpty { "Bilinmeyen Şarkı" }
    }

    private fun createDownloadId(videoUrl: String): String {
        return "download_${abs(videoUrl.hashCode())}_${System.currentTimeMillis()}"
    }

    private fun downloadNotificationId(downloadId: String): Int {
        return DOWNLOAD_NOTIFICATION_BASE_ID + abs(downloadId.hashCode() % 10_000)
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "DownloadService"
        private const val SERVICE_NOTIFICATION_ID = 1001
        private const val DOWNLOAD_NOTIFICATION_BASE_ID = 2000

        const val EXTRA_VIDEO_URL = "video_url"
        const val EXTRA_TITLE = "title"
        const val EXTRA_ARTIST = "artist"
        const val EXTRA_DOWNLOAD_ID = "download_id"

        fun start(
            context: Context,
            videoUrl: String,
            title: String,
            artist: String = "",
            downloadId: String? = null
        ): Result<String> {
            val resolvedDownloadId = downloadId ?: "download_${abs(videoUrl.hashCode())}_${System.currentTimeMillis()}"
            val intent = Intent(context, DownloadService::class.java).apply {
                putExtra(EXTRA_VIDEO_URL, videoUrl)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_ARTIST, artist)
                putExtra(EXTRA_DOWNLOAD_ID, resolvedDownloadId)
            }
            return runCatching {
                context.startForegroundService(intent)
                resolvedDownloadId
            }
        }
    }
}

private class DownloadFailureException(message: String) : Exception(message)
