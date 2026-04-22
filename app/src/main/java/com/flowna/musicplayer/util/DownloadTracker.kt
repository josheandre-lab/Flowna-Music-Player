package com.flowna.musicplayer.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class DownloadStatus {
    DOWNLOADING,
    CONVERTING,
    COMPLETED,
    FAILED
}

data class DownloadItem(
    val id: String,
    val title: String,
    val artist: String = "",
    val videoUrl: String = "",
    val status: DownloadStatus,
    val progress: Int = 0,
    val statusMessage: String? = null,
    val errorMessage: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
)

object DownloadTracker {

    private val _downloads = MutableStateFlow<List<DownloadItem>>(emptyList())
    val downloads: StateFlow<List<DownloadItem>> = _downloads.asStateFlow()

    fun addDownload(id: String, title: String, artist: String = "", videoUrl: String = "") {
        _downloads.update { list ->
            val newItem = DownloadItem(
                id = id,
                title = TextNormalizer.normalizeHumanText(title) ?: title,
                artist = TextNormalizer.normalizeHumanText(artist) ?: artist,
                videoUrl = videoUrl,
                status = DownloadStatus.DOWNLOADING,
                progress = 0,
                statusMessage = "Hazırlanıyor"
            )
            (list.filterNot { it.id == id } + newItem).sortedByDescending { it.updatedAt }
        }
    }

    fun updateProgress(id: String, progress: Int, statusMessage: String? = null) {
        _downloads.update { list ->
            list.map {
                if (it.id == id) {
                    it.copy(
                        status = DownloadStatus.DOWNLOADING,
                        progress = progress,
                        statusMessage = statusMessage ?: it.statusMessage,
                        errorMessage = null,
                        updatedAt = System.currentTimeMillis()
                    )
                } else it
            }.sortedByDescending { it.updatedAt }
        }
    }

    fun updateStatus(
        id: String,
        status: DownloadStatus,
        statusMessage: String? = null,
        errorMessage: String? = null
    ) {
        _downloads.update { list ->
            list.map {
                if (it.id == id) {
                    it.copy(
                        status = status,
                        progress = if (status == DownloadStatus.COMPLETED) 100 else it.progress,
                        statusMessage = statusMessage,
                        errorMessage = errorMessage,
                        updatedAt = System.currentTimeMillis()
                    )
                } else it
            }.sortedByDescending { it.updatedAt }
        }
    }

    fun removeDownload(id: String) {
        _downloads.update { list ->
            list.filter { it.id != id }
        }
    }

    fun clearCompleted() {
        _downloads.update { list ->
            list.filterNot { it.status == DownloadStatus.COMPLETED }
        }
    }

    fun clearFailed() {
        _downloads.update { list ->
            list.filterNot { it.status == DownloadStatus.FAILED }
        }
    }

    fun clearAll() {
        _downloads.value = emptyList()
    }
}
