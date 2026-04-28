package com.flowna.musicplayer.data.repository

import com.flowna.musicplayer.player.PreviewTrack
import com.flowna.musicplayer.util.NewPipeDownloader
import com.flowna.musicplayer.util.TextNormalizer
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.search.SearchExtractor
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.LinkedHashMap
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

    private const val PREVIEW_PREFETCH_COUNT = 4
    private const val PREVIEW_CACHE_SIZE = 12
    private const val PREVIEW_CACHE_TTL_MS = 15 * 60 * 1000L
    private const val PREVIEW_PREFETCH_CONCURRENCY = 2

    private var initialized = false
    private val previewScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val previewCacheMutex = Mutex()
    private val previewPrefetchSemaphore = Semaphore(PREVIEW_PREFETCH_CONCURRENCY)
    private val previewCache = LinkedHashMap<String, CachedPreviewTrack>(PREVIEW_CACHE_SIZE, 0.75f, true)
    private val inFlightPreviews = mutableMapOf<String, kotlinx.coroutines.Deferred<Result<PreviewTrack>>>()
    private val prefetchJobs = mutableMapOf<String, Job>()
    private val searchContinuationMutex = Mutex()
    private val searchContinuations = mutableMapOf<String, SearchContinuation>()

    private data class CachedPreviewTrack(
        val track: PreviewTrack,
        val cachedAtMillis: Long
    )

    private data class SearchContinuation(
        val extractor: SearchExtractor,
        val nextPage: Page?
    )

    private fun ensureInitialized() {
        if (!initialized) {
            NewPipe.init(NewPipeDownloader.getInstance())
            initialized = true
        }
    }

    suspend fun search(
        query: String,
        warmPreview: Boolean = true
    ): Result<List<SearchResult>> = withContext(Dispatchers.IO) {
        try {
            ensureInitialized()

            val service = ServiceList.YouTube
            val extractor = service.getSearchExtractor(query)
            extractor.fetchPage()

            val initialPage = extractor.initialPage
            val results = initialPage.items.toSearchResults()

            searchContinuationMutex.withLock {
                searchContinuations[query.cacheKey()] = SearchContinuation(
                    extractor = extractor,
                    nextPage = initialPage.nextPage
                )
            }

            if (warmPreview) {
                prefetchSearchResults(results.take(PREVIEW_PREFETCH_COUNT))
            }
            Result.success(results)
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    suspend fun searchMore(query: String): Result<List<SearchResult>> = withContext(Dispatchers.IO) {
        try {
            ensureInitialized()
            val continuation = searchContinuationMutex.withLock {
                searchContinuations[query.cacheKey()]
            } ?: return@withContext Result.success(emptyList())

            val nextPage = continuation.nextPage
                ?: return@withContext Result.success(emptyList())

            val nextItemsPage = continuation.extractor.getPage(nextPage)
            val results = nextItemsPage.items.toSearchResults()

            searchContinuationMutex.withLock {
                searchContinuations[query.cacheKey()] = continuation.copy(
                    nextPage = nextItemsPage.nextPage
                )
            }

            if (results.isNotEmpty()) {
                prefetchPreviews(results.take(PREVIEW_PREFETCH_COUNT))
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

    suspend fun resolvePreviewTrackPriority(result: SearchResult): Result<PreviewTrack> {
        return resolvePreviewTrackPriority(
            title = result.title,
            artist = result.uploaderName,
            thumbnailUrl = result.thumbnailUrl,
            durationSeconds = result.duration,
            videoId = result.videoId,
            videoUrl = result.videoUrl
        )
    }

    suspend fun resolvePreviewTrackPriority(
        title: String,
        artist: String,
        thumbnailUrl: String,
        durationSeconds: Long,
        videoId: String,
        videoUrl: String
    ): Result<PreviewTrack> {
        val normalizedUrl = normalizeVideoUrl(videoUrl, videoId)
        val safeVideoId = videoId.ifBlank { extractVideoId(videoUrl) }
        val cacheKey = buildPreviewCacheKey(safeVideoId, normalizedUrl)

        getCachedPreview(cacheKey)?.let { return Result.success(it) }
        previewCacheMutex.withLock {
            prefetchJobs.remove(cacheKey)?.cancel()
        }
        return resolvePreviewTrack(
            title = title,
            artist = artist,
            thumbnailUrl = thumbnailUrl,
            durationSeconds = durationSeconds,
            videoId = safeVideoId,
            videoUrl = normalizedUrl
        )
    }

    suspend fun resolvePreviewTrack(
        title: String,
        artist: String,
        thumbnailUrl: String,
        durationSeconds: Long,
        videoId: String,
        videoUrl: String
    ): Result<PreviewTrack> {
        val normalizedUrl = normalizeVideoUrl(videoUrl, videoId)
        val safeTitle = TextNormalizer.normalizeHumanText(title) ?: title
        val safeArtist = TextNormalizer.normalizeHumanText(artist) ?: artist
        val safeVideoId = videoId.ifBlank { extractVideoId(videoUrl) }
        val cacheKey = buildPreviewCacheKey(safeVideoId, normalizedUrl)

        getCachedPreview(cacheKey)?.let { return Result.success(it) }

        val deferred = previewCacheMutex.withLock {
            inFlightPreviews[cacheKey] ?: previewScope.async {
                resolvePreviewTrackUncached(
                    title = safeTitle,
                    artist = safeArtist,
                    thumbnailUrl = thumbnailUrl,
                    durationSeconds = durationSeconds,
                    videoId = safeVideoId,
                    videoUrl = normalizedUrl
                )
            }.also { inFlightPreviews[cacheKey] = it }
        }

        val result = deferred.await()
        previewCacheMutex.withLock {
            if (inFlightPreviews[cacheKey] === deferred) {
                inFlightPreviews.remove(cacheKey)
            }
            result.getOrNull()?.let { cachePreview(cacheKey, it) }
        }
        return result
    }

    fun prefetchPreviews(results: List<SearchResult>) {
        schedulePreviewPrefetch(results.take(PREVIEW_PREFETCH_COUNT), cancelExisting = false)
    }

    private fun prefetchSearchResults(results: List<SearchResult>) {
        schedulePreviewPrefetch(results, cancelExisting = true)
    }

    private fun schedulePreviewPrefetch(
        results: List<SearchResult>,
        cancelExisting: Boolean
    ) {
        if (results.isEmpty()) return

        previewScope.launch {
            if (cancelExisting) {
                previewCacheMutex.withLock {
                    prefetchJobs.values.forEach { it.cancel() }
                    prefetchJobs.clear()
                }
            }

            results.forEach { result ->
                val cacheKey = buildPreviewCacheKey(
                    videoId = result.videoId.ifBlank { extractVideoId(result.videoUrl) },
                    videoUrl = normalizeVideoUrl(result.videoUrl, result.videoId)
                )
                if (getCachedPreview(cacheKey) != null) return@forEach

                val prefetchJob = previewScope.launch {
                    try {
                        previewPrefetchSemaphore.withPermit {
                            resolvePreviewTrack(result)
                        }
                    } finally {
                        val activeJob = coroutineContext[Job]
                        previewCacheMutex.withLock {
                            if (prefetchJobs[cacheKey] === activeJob) {
                                prefetchJobs.remove(cacheKey)
                            }
                        }
                    }
                }

                previewCacheMutex.withLock {
                    prefetchJobs[cacheKey]?.cancel()
                    prefetchJobs[cacheKey] = prefetchJob
                }
            }
        }
    }

    fun cancelPreviewPrefetch() {
        previewScope.launch {
            previewCacheMutex.withLock {
                prefetchJobs.values.forEach { it.cancel() }
                prefetchJobs.clear()
            }
        }
    }

    fun clearPreviewMemoryCache() {
        previewScope.launch {
            previewCacheMutex.withLock {
                prefetchJobs.values.forEach { it.cancel() }
                prefetchJobs.clear()
                inFlightPreviews.values.forEach { it.cancel() }
                inFlightPreviews.clear()
                previewCache.clear()
            }
        }
    }

    private suspend fun resolvePreviewTrackUncached(
        title: String,
        artist: String,
        thumbnailUrl: String,
        durationSeconds: Long,
        videoId: String,
        videoUrl: String
    ): Result<PreviewTrack> = withContext(Dispatchers.IO) {
        runCatching {
            resolvePreviewTrackWithNewPipe(
                title = title,
                artist = artist,
                thumbnailUrl = thumbnailUrl,
                durationSeconds = durationSeconds,
                videoId = videoId,
                videoUrl = videoUrl
            )
        }.recoverCatching {
            resolvePreviewTrackWithYoutubeDl(
                title = title,
                artist = artist,
                thumbnailUrl = thumbnailUrl,
                durationSeconds = durationSeconds,
                videoId = videoId,
                videoUrl = videoUrl
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
            ?: throw IllegalStateException("Önizleme akışı bulunamadı.")

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
        ensureInitialized()
        val extractor = ServiceList.YouTube.getStreamExtractor(videoUrl)
        extractor.fetchPage()

        val audioStream = extractor.audioStreams
            .filter { !it.url.isNullOrBlank() }
            .maxByOrNull { maxOf(it.averageBitrate, it.bitrate) }

        val streamUrl = audioStream?.url?.takeIf { it.isNotBlank() }
            ?: extractor.hlsUrl?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Önizleme akışı bulunamadı.")

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

    private fun buildPreviewCacheKey(videoId: String, videoUrl: String): String {
        return videoId.ifBlank { videoUrl }
    }

    private suspend fun getCachedPreview(cacheKey: String): PreviewTrack? {
        return previewCacheMutex.withLock {
            val cached = previewCache[cacheKey] ?: return@withLock null
            if (System.currentTimeMillis() - cached.cachedAtMillis <= PREVIEW_CACHE_TTL_MS) {
                cached.track
            } else {
                previewCache.remove(cacheKey)
                null
            }
        }
    }

    private fun cachePreview(cacheKey: String, track: PreviewTrack) {
        previewCache[cacheKey] = CachedPreviewTrack(
            track = track,
            cachedAtMillis = System.currentTimeMillis()
        )
        while (previewCache.size > PREVIEW_CACHE_SIZE) {
            val eldestKey = previewCache.entries.firstOrNull()?.key ?: break
            previewCache.remove(eldestKey)
        }
    }

    private fun extractYoutubeDlError(stdout: String?, stderr: String?): String {
        return listOfNotNull(stderr, stdout)
            .asSequence()
            .flatMap { it.lineSequence() }
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() }
            ?: "Önizleme başlatılamadı."
    }

    private fun mapPreviewError(error: Throwable): String {
        val message = error.message.orEmpty().trim()
        val normalized = message.lowercase()
        return when {
            "page needs to be reloaded" in normalized -> {
                "Önizleme akışı şu an hazırlanamadı. Lütfen tekrar dene."
            }

            "sign in" in normalized || "private video" in normalized || "confirm your age" in normalized -> {
                "Bu video için önizleme kullanılamıyor."
            }

            "network" in normalized || "timeout" in normalized || "connection" in normalized -> {
                "Bağlantı nedeniyle önizleme başlatılamadı."
            }

            "onizleme akisi bulunamadi" in normalized || "önizleme akışı bulunamadı" in normalized -> {
                "Bu şarkı için önizleme bulunamadı."
            }

            message.isNotBlank() -> message
            else -> "Önizleme başlatılamadı."
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

    private fun Iterable<*>.toSearchResults(): List<SearchResult> {
        return filterIsInstance<StreamInfoItem>()
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
    }

    private fun String.cacheKey(): String {
        return trim().lowercase()
    }
}
