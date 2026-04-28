package com.flowna.musicplayer.data.repository

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.flowna.musicplayer.data.FlownaSong
import com.flowna.musicplayer.data.recommendation.RecommendationEngine
import com.flowna.musicplayer.data.recommendation.RecommendationItem
import com.flowna.musicplayer.util.MediaStoreHelper
import com.flowna.musicplayer.util.PermissionHelper
import com.flowna.musicplayer.util.PreferencesHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class LibraryState(
    val songs: List<FlownaSong> = emptyList(),
    val recommendedItems: List<RecommendationItem> = emptyList(),
    val mostPlayedSongs: List<FlownaSong> = emptyList(),
    val recentlyPlayedSongs: List<FlownaSong> = emptyList(),
    val isInitialized: Boolean = false,
    val isRefreshing: Boolean = false,
    val lastScanAt: Long? = null,
    val errorMessage: String? = null
)

object LibraryRepository {

    private const val CACHE_FILE_NAME = "flowna_library_cache_v2.json"
    private val repositoryMutex = Mutex()
    private val _state = MutableStateFlow(
        LibraryState(
            lastScanAt = PreferencesHelper.getLastLibraryScanAt().takeIf { it > 0L }
        )
    )
    val state: StateFlow<LibraryState> = _state.asStateFlow()

    @Volatile
    private var cacheLoaded = false

    @Volatile
    private var hasCacheFile = false

    suspend fun ensureInitialized(context: Context) {
        val appContext = context.applicationContext
        loadCacheIfNeeded(appContext)

        if (!PermissionHelper.hasAllPermissions(appContext)) {
            _state.update {
                it.copy(
                    isInitialized = hasCacheFile || PreferencesHelper.hasCompletedInitialLibraryScan()
                )
            }
            return
        }

        if (!hasCacheFile || !PreferencesHelper.hasCompletedInitialLibraryScan()) {
            refreshLibrary(appContext)
        } else {
            _state.update { it.copy(isInitialized = true) }
            refreshDerivedState(appContext, _state.value.songs, allowOnline = false)
        }
    }

    suspend fun refreshLibrary(context: Context): Result<Int> = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        loadCacheIfNeeded(appContext)

        if (!PermissionHelper.hasAllPermissions(appContext)) {
            return@withContext Result.failure(SecurityException("Kütüphane izni gerekli."))
        }

        var refreshedSongs: List<FlownaSong>? = null
        val result = repositoryMutex.withLock {
            _state.update { it.copy(isRefreshing = true, errorMessage = null) }

            runCatching {
                refreshedSongs = MediaStoreHelper.querySongs(appContext)
                val scannedAt = System.currentTimeMillis()

                writeCache(
                    context = appContext,
                    songs = refreshedSongs.orEmpty(),
                    scannedAt = scannedAt
                )

                PreferencesHelper.markLibraryScanCompleted(scannedAt)
                _state.value = _state.value.copy(
                    songs = refreshedSongs.orEmpty(),
                    isInitialized = true,
                    isRefreshing = false,
                    lastScanAt = scannedAt,
                    errorMessage = null
                )
                refreshedSongs.orEmpty().size
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        isInitialized = it.isInitialized || hasCacheFile,
                        isRefreshing = false,
                        errorMessage = error.message ?: "Kütüphane taraması başarısız oldu."
                    )
                }
            }
        }

        refreshedSongs?.let { refreshDerivedState(appContext, it, allowOnline = false) }
        result
    }

    suspend fun refreshRecommendations(
        context: Context,
        allowOnline: Boolean = true
    ) = withContext(Dispatchers.IO) {
        refreshDerivedState(context.applicationContext, _state.value.songs, allowOnline)
    }

    suspend fun addOrUpdateFromUri(context: Context, uri: Uri): Result<FlownaSong> = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        loadCacheIfNeeded(appContext)

        var mergedSongs: List<FlownaSong>? = null
        val result = repositoryMutex.withLock {
            runCatching {
                val song = MediaStoreHelper.querySongByUri(appContext, uri)
                    ?: error("Yeni şarkı kütüphane önbelleğine eklenemedi.")

                val currentState = _state.value
                mergedSongs = buildList {
                    add(song)
                    addAll(
                        currentState.songs.filterNot { cachedSong ->
                            cachedSong.id == song.id || cachedSong.uri.toString() == song.uri.toString()
                        }
                    )
                }

                val scannedAt = currentState.lastScanAt ?: System.currentTimeMillis()
                writeCache(
                    context = appContext,
                    songs = mergedSongs.orEmpty(),
                    scannedAt = scannedAt
                )

                if (!PreferencesHelper.hasCompletedInitialLibraryScan()) {
                    PreferencesHelper.markLibraryScanCompleted(scannedAt)
                }

                _state.value = currentState.copy(
                    songs = mergedSongs.orEmpty(),
                    isInitialized = true,
                    errorMessage = null,
                    lastScanAt = scannedAt
                )
                song
            }
        }

        mergedSongs?.let { refreshDerivedState(appContext, it, allowOnline = false) }
        result
    }

    suspend fun renameDownloadedSong(
        context: Context,
        song: FlownaSong,
        newTitle: String
    ): Result<FlownaSong> = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        loadCacheIfNeeded(appContext)

        if (!song.isFlownaDownload) {
            return@withContext Result.failure(IllegalArgumentException("Sadece Flowna indirilenleri yeniden adlandırılabilir."))
        }

        var updatedSongs: List<FlownaSong>? = null
        val result = repositoryMutex.withLock {
            runCatching {
                val renamedSong = MediaStoreHelper.renameSong(appContext, song, newTitle)
                    ?: error("Şarkı adı değiştirilemedi.")
                val currentState = _state.value
                updatedSongs = currentState.songs.map { cachedSong ->
                    if (cachedSong.uri == song.uri || cachedSong.id == song.id) renamedSong else cachedSong
                }
                writeCache(
                    context = appContext,
                    songs = updatedSongs.orEmpty(),
                    scannedAt = currentState.lastScanAt ?: System.currentTimeMillis()
                )
                _state.value = currentState.copy(songs = updatedSongs.orEmpty(), errorMessage = null)
                renamedSong
            }
        }

        updatedSongs?.let { refreshDerivedState(appContext, it, allowOnline = false) }
        result
    }

    suspend fun deleteDownloadedSong(
        context: Context,
        song: FlownaSong
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        loadCacheIfNeeded(appContext)

        if (!song.isFlownaDownload) {
            return@withContext Result.failure(IllegalArgumentException("Sadece Flowna indirilenleri silinebilir."))
        }

        var updatedSongs: List<FlownaSong>? = null
        val result = repositoryMutex.withLock {
            runCatching {
                if (!MediaStoreHelper.deleteSong(appContext, song)) {
                    error("Şarkı silinemedi.")
                }
                val currentState = _state.value
                updatedSongs = currentState.songs.filterNot { cachedSong ->
                    cachedSong.uri == song.uri || cachedSong.id == song.id
                }
                writeCache(
                    context = appContext,
                    songs = updatedSongs.orEmpty(),
                    scannedAt = currentState.lastScanAt ?: System.currentTimeMillis()
                )
                _state.value = currentState.copy(songs = updatedSongs.orEmpty(), errorMessage = null)
            }
        }

        updatedSongs?.let { refreshDerivedState(appContext, it, allowOnline = false) }
        result
    }

    suspend fun forgetCachedSong(
        context: Context,
        song: FlownaSong
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        loadCacheIfNeeded(appContext)

        var updatedSongs: List<FlownaSong>? = null
        val result = repositoryMutex.withLock {
            runCatching {
                val currentState = _state.value
                updatedSongs = currentState.songs.filterNot { cachedSong ->
                    cachedSong.uri == song.uri || cachedSong.id == song.id
                }
                writeCache(
                    context = appContext,
                    songs = updatedSongs.orEmpty(),
                    scannedAt = currentState.lastScanAt ?: System.currentTimeMillis()
                )
                _state.value = currentState.copy(songs = updatedSongs.orEmpty(), errorMessage = null)
            }
        }

        updatedSongs?.let { refreshDerivedState(appContext, it, allowOnline = false) }
        result
    }

    private suspend fun refreshDerivedState(
        context: Context,
        songs: List<FlownaSong>,
        allowOnline: Boolean
    ) {
        if (songs.isEmpty()) {
            _state.update {
                it.copy(
                    recommendedItems = emptyList(),
                    mostPlayedSongs = emptyList(),
                    recentlyPlayedSongs = emptyList()
                )
            }
            return
        }

        ListeningInsightsRepository.syncLibrarySongs(context, songs)
        val insights = ListeningInsightsRepository.getAllTrackInsights(context)
        val recommendations = RecommendationEngine.buildRecommendations(
            context = context,
            songs = songs,
            insights = insights,
            includeOnline = allowOnline
        )
        val mostPlayedSongs = RecommendationEngine.buildMostPlayedSongs(songs, insights)
        val recentSongs = RecommendationEngine.buildRecentSongs(songs, insights)

        _state.update {
            it.copy(
                recommendedItems = recommendations,
                mostPlayedSongs = mostPlayedSongs,
                recentlyPlayedSongs = recentSongs
            )
        }
    }

    private suspend fun loadCacheIfNeeded(context: Context) {
        if (cacheLoaded) return

        repositoryMutex.withLock {
            if (cacheLoaded) return

            val cacheFile = cacheFile(context)
            hasCacheFile = cacheFile.exists()

            val cachedState = runCatching {
                if (!hasCacheFile) {
                    LibraryState(
                        isInitialized = PreferencesHelper.hasCompletedInitialLibraryScan(),
                        lastScanAt = PreferencesHelper.getLastLibraryScanAt().takeIf { it > 0L }
                    )
                } else {
                    readCache(cacheFile)
                }
            }.getOrElse {
                hasCacheFile = false
                LibraryState(
                    isInitialized = false,
                    lastScanAt = PreferencesHelper.getLastLibraryScanAt().takeIf { it > 0L },
                    errorMessage = "Kütüphane önbellek dosyası okunamadı."
                )
            }

            _state.value = cachedState
            cacheLoaded = true
        }
    }

    private fun readCache(cacheFile: File): LibraryState {
        val root = JSONObject(cacheFile.readText())
        val songs = root.optJSONArray("songs")?.toSongs().orEmpty()
        val lastScanAt = root.optLong("lastScanAt").takeIf { it > 0L }

        return LibraryState(
            songs = songs,
            isInitialized = true,
            isRefreshing = false,
            lastScanAt = lastScanAt ?: PreferencesHelper.getLastLibraryScanAt().takeIf { it > 0L },
            errorMessage = null
        )
    }

    private fun writeCache(
        context: Context,
        songs: List<FlownaSong>,
        scannedAt: Long
    ) {
        val payload = JSONObject()
            .put("lastScanAt", scannedAt)
            .put("songs", JSONArray().apply {
                songs.forEach { put(it.toJson()) }
            })

        cacheFile(context).writeText(payload.toString())
        hasCacheFile = true
    }

    private fun cacheFile(context: Context): File {
        return File(context.filesDir, CACHE_FILE_NAME)
    }

    private fun JSONArray.toSongs(): List<FlownaSong> {
        return buildList(length()) {
            for (index in 0 until length()) {
                val songObject = optJSONObject(index) ?: continue
                val uri = songObject.optString("uri").takeIf { it.isNotBlank() } ?: continue
                add(
                    FlownaSong(
                        id = songObject.optLong("id"),
                        title = songObject.optString("title").ifBlank { "Bilinmeyen" },
                        artist = songObject.optString("artist").ifBlank { "Bilinmeyen Sanatçı" },
                        album = songObject.optString("album"),
                        duration = songObject.optLong("duration"),
                        uri = Uri.parse(uri),
                        albumArtUri = songObject.optString("albumArtUri")
                            .takeIf { it.isNotBlank() }
                            ?.let(Uri::parse),
                        embeddedArtwork = songObject.optString("embeddedArtwork")
                            .takeIf { it.isNotBlank() }
                            ?.let { Base64.decode(it, Base64.DEFAULT) },
                        isFlownaDownload = songObject.optBoolean("isFlownaDownload", false)
                    )
                )
            }
        }
    }

    private fun FlownaSong.toJson(): JSONObject {
        return JSONObject()
            .put("id", id)
            .put("title", title)
            .put("artist", artist)
            .put("album", album)
            .put("duration", duration)
            .put("uri", uri.toString())
            .put("albumArtUri", albumArtUri?.toString().orEmpty())
            .put("embeddedArtwork", cacheableArtwork())
            .put("isFlownaDownload", isFlownaDownload)
    }

    private fun FlownaSong.cacheableArtwork(): String {
        val artwork = embeddedArtwork ?: return ""
        return Base64.encodeToString(artwork, Base64.NO_WRAP)
    }
}
