package com.flowna.musicplayer.data.repository

import com.flowna.musicplayer.player.PreviewTrack
import com.flowna.musicplayer.util.NewPipeDownloader
import com.flowna.musicplayer.util.TextNormalizer
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.LinkedHashSet

data class SearchResult(
    val title: String,
    val thumbnailUrl: String,
    val duration: Long,
    val videoId: String,
    val videoUrl: String,
    val uploaderName: String
)

object SearchRepository {

    private var initialized = false

    private fun ensureInitialized() {
        if (!initialized) {
            NewPipe.init(NewPipeDownloader.getInstance())
            initialized = true
        }
    }

    suspend fun search(query: String): Result<List<SearchResult>> = withContext(Dispatchers.IO) {
        try {
            ensureInitialized()

            val service = ServiceList.YouTube
            val extractor = service.getSearchExtractor(query)
            extractor.fetchPage()

            val results = extractor.initialPage.items
                .filterIsInstance<StreamInfoItem>()
                .map { item ->
                    val thumbnailUrl = item.thumbnails.firstOrNull()?.url ?: ""
                    val videoId = extractVideoId(item.url)
                    val videoUrl = normalizeVideoUrl(item.url, videoId)

                    SearchResult(
                        title = TextNormalizer.normalizeHumanText(item.name) ?: item.name,
                        thumbnailUrl = thumbnailUrl,
                        duration = item.duration,
                        videoId = videoId,
                        videoUrl = videoUrl,
                        uploaderName = TextNormalizer.normalizeHumanText(item.uploaderName)
                            ?: item.uploaderName
                            ?: ""
                    )
                }

            Result.success(results)
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    suspend fun suggestions(query: String): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            ensureInitialized()

            val trimmedQuery = query.trim()
            if (trimmedQuery.length < 2) {
                return@withContext Result.success(emptyList())
            }

            val suggestionList = ServiceList.YouTube
                .suggestionExtractor
                .suggestionList(trimmedQuery)
                .map { it.trim() }
                .filter { it.isNotBlank() }

            Result.success(LinkedHashSet(suggestionList).take(6))
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    suspend fun resolvePreviewTrack(result: SearchResult): Result<PreviewTrack> {
        return resolvePreviewTrack(
            title = result.title,
            artist = result.uploaderName,
            thumbnailUrl = result.thumbnailUrl,
            durationSeconds = result.duration,
            videoId = result.videoId,
            videoUrl = result.videoUrl
        )
    }

    suspend fun resolvePreviewTrack(
        title: String,
        artist: String,
        thumbnailUrl: String,
        durationSeconds: Long,
        videoId: String,
        videoUrl: String
    ): Result<PreviewTrack> = withContext(Dispatchers.IO) {
        val normalizedUrl = normalizeVideoUrl(videoUrl, videoId)
        val safeTitle = TextNormalizer.normalizeHumanText(title) ?: title
        val safeArtist = TextNormalizer.normalizeHumanText(artist) ?: artist
        val safeVideoId = videoId.ifBlank { extractVideoId(videoUrl) }

        runCatching {
            resolvePreviewTrackWithYoutubeDl(
                title = safeTitle,
                artist = safeArtist,
                thumbnailUrl = thumbnailUrl,
                durationSeconds = durationSeconds,
                videoId = safeVideoId,
                videoUrl = normalizedUrl
            )
        }.recoverCatching {
            ensureInitialized()
            resolvePreviewTrackWithNewPipe(
                title = safeTitle,
                artist = safeArtist,
                thumbnailUrl = thumbnailUrl,
                durationSeconds = durationSeconds,
                videoId = safeVideoId,
                videoUrl = normalizedUrl
            )
        }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { Result.failure(IllegalStateException(mapPreviewError(it), it)) }
        )
    }

    private fun resolvePreviewTrackWithYoutubeDl(
        title: String,
        artist: String,
        thumbnailUrl: String,
        durationSeconds: Long,
        videoId: String,
        videoUrl: String
    ): PreviewTrack {
        val request = YoutubeDLRequest(videoUrl).apply {
            addOption("--no-playlist")
            addOption("--skip-download")
            addOption("--no-warnings")
            addOption("-f", "bestaudio")
            addOption("-g")
        }

        val response = YoutubeDL.getInstance().execute(request)
        if (response.exitCode != 0) {
            throw IllegalStateException(extractYoutubeDlError(response.out, response.err))
        }

        val streamUrl = response.out
            .lineSequence()
            .map { it.trim() }
            .firstOrNull { it.startsWith("http://") || it.startsWith("https://") }
            ?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Onizleme akisi bulunamadi.")

        return PreviewTrack(
            videoId = videoId,
            title = title,
            artist = artist,
            artworkUrl = thumbnailUrl,
            streamUrl = streamUrl,
            videoUrl = videoUrl,
            durationSeconds = durationSeconds
        )
    }

    private fun resolvePreviewTrackWithNewPipe(
        title: String,
        artist: String,
        thumbnailUrl: String,
        durationSeconds: Long,
        videoId: String,
        videoUrl: String
    ): PreviewTrack {
        val extractor = ServiceList.YouTube.getStreamExtractor(videoUrl)
        extractor.fetchPage()

        val audioStream = extractor.audioStreams
            .filter { !it.url.isNullOrBlank() }
            .maxByOrNull { maxOf(it.averageBitrate, it.bitrate) }

        val streamUrl = audioStream?.url?.takeIf { it.isNotBlank() }
            ?: extractor.hlsUrl?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Onizleme akisi bulunamadi.")

        return PreviewTrack(
            videoId = videoId,
            title = title,
            artist = artist,
            artworkUrl = extractor.thumbnails.firstOrNull()?.url ?: thumbnailUrl,
            streamUrl = streamUrl,
            videoUrl = videoUrl,
            durationSeconds = extractor.length.takeIf { it > 0 } ?: durationSeconds
        )
    }

    private fun extractYoutubeDlError(stdout: String?, stderr: String?): String {
        return listOfNotNull(stderr, stdout)
            .asSequence()
            .flatMap { it.lineSequence() }
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() }
            ?: "Onizleme baslatilamadi."
    }

    private fun mapPreviewError(error: Throwable): String {
        val message = error.message.orEmpty().trim()
        val normalized = message.lowercase()
        return when {
            "page needs to be reloaded" in normalized -> {
                "Onizleme akisi su an hazirlanamadi. Lutfen tekrar dene."
            }

            "sign in" in normalized || "private video" in normalized || "confirm your age" in normalized -> {
                "Bu video icin onizleme kullanilamiyor."
            }

            "network" in normalized || "timeout" in normalized || "connection" in normalized -> {
                "Baglanti nedeniyle onizleme baslatilamadi."
            }

            "onizleme akisi bulunamadi" in normalized -> {
                "Bu sarki icin onizleme bulunamadi."
            }

            message.isNotBlank() -> message
            else -> "Onizleme baslatilamadi."
        }
    }

    private fun extractVideoId(url: String): String {
        return try {
            when {
                url.contains("v=") -> url.substringAfter("v=").substringBefore("&")
                url.contains("youtu.be/") -> url.substringAfter("youtu.be/").substringBefore("?")
                url.contains("/shorts/") -> url.substringAfter("/shorts/").substringBefore("?")
                else -> url.substringAfterLast("/").substringBefore("?")
            }
        } catch (_: Exception) {
            ""
        }
    }

    private fun normalizeVideoUrl(rawUrl: String, videoId: String): String {
        if (videoId.isNotBlank()) {
            return "https://www.youtube.com/watch?v=$videoId"
        }

        return when {
            rawUrl.startsWith("http://") || rawUrl.startsWith("https://") -> rawUrl
            rawUrl.startsWith("/") -> "https://www.youtube.com$rawUrl"
            rawUrl.isNotBlank() -> "https://www.youtube.com/$rawUrl"
            else -> rawUrl
        }
    }
}
