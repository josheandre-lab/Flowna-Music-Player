package com.flowna.musicplayer.data.repository

import com.flowna.musicplayer.util.NewPipeDownloader
import com.flowna.musicplayer.util.TextNormalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.LinkedHashSet

data class SearchResult(
    val title: String,
    val thumbnailUrl: String,
    val duration: Long, // seconds
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
        } catch (e: Exception) {
            Result.failure(e)
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
        } catch (e: Exception) {
            Result.failure(e)
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
        } catch (e: Exception) {
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
