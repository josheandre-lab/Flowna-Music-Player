package com.flowna.musicplayer.data.recommendation

import android.content.Context
import com.flowna.musicplayer.data.FlownaSong
import com.flowna.musicplayer.data.insights.OnlineRecommendationEntity
import com.flowna.musicplayer.data.insights.TrackInsight
import com.flowna.musicplayer.data.repository.ListeningInsightsRepository
import com.flowna.musicplayer.data.repository.SearchRepository
import com.flowna.musicplayer.util.ConnectivityHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object RecommendationEngine {

    private const val ONLINE_CACHE_MAX_AGE_MS = 6 * 60 * 60 * 1000L

    fun buildMostPlayedSongs(
        songs: List<FlownaSong>,
        insights: List<TrackInsight>,
        limit: Int = 6
    ): List<FlownaSong> {
        val songByUri = songs.associateBy { it.uri.toString() }
        return insights
            .sortedWith(
                compareByDescending<TrackInsight> { it.completedPlayCount }
                    .thenByDescending { it.lastPlayedAt }
            )
            .mapNotNull { songByUri[it.songUri] }
            .take(limit)
    }

    fun buildRecentSongs(
        songs: List<FlownaSong>,
        insights: List<TrackInsight>,
        limit: Int = 8
    ): List<FlownaSong> {
        val songByUri = songs.associateBy { it.uri.toString() }
        return insights
            .filter { it.lastPlayedAt > 0L }
            .sortedByDescending { it.lastPlayedAt }
            .mapNotNull { songByUri[it.songUri] }
            .take(limit)
    }

    suspend fun buildRecommendations(
        context: Context,
        songs: List<FlownaSong>,
        insights: List<TrackInsight>,
        includeOnline: Boolean,
        limit: Int = 10
    ): List<RecommendationItem> = withContext(Dispatchers.Default) {
        if (songs.isEmpty()) return@withContext emptyList()

        val localRecommendations = buildLocalRecommendations(songs, insights, limit)
        if (!includeOnline) {
            return@withContext localRecommendations
        }

        val onlineRecommendations = loadOnlineRecommendations(context, songs, insights)
        mixRecommendations(localRecommendations, onlineRecommendations, limit)
    }

    private fun buildLocalRecommendations(
        songs: List<FlownaSong>,
        insights: List<TrackInsight>,
        limit: Int
    ): List<RecommendationItem.LocalSong> {
        val insightByUri = insights.associateBy { it.songUri }
        val recent = insights.sortedByDescending { it.lastPlayedAt }.take(6)
        val preferredArtists = recent.map { it.artist }.filter { it.isNotBlank() }.toSet() +
            insights.sortedByDescending { it.completedPlayCount }.take(5).map { it.artist }
        val preferredGenres = recent.map { it.genreTag }.filter { it.isNotBlank() }
        val preferredMoods = recent.map { it.moodTag }.filter { it.isNotBlank() }
        val preferredTempo = recent.map { it.tempoBucket }.filter { it.isNotBlank() }
        val recentlyPlayedUris = recent.map { it.songUri }.toSet()

        val scored = songs.map { song ->
            val fingerprint = TrackHeuristics.fingerprint(song)
            val insight = insightByUri[song.uri.toString()]
            var score = 0.0
            if (song.artist in preferredArtists) score += 4.0
            if (fingerprint.genreTag in preferredGenres) score += 2.2
            if (fingerprint.moodTag in preferredMoods) score += 1.8
            if (fingerprint.tempoBucket in preferredTempo) score += 1.2
            if (insight?.isFavorite == true) score += 2.5
            score += (insight?.completedPlayCount ?: 0) * 0.35
            score += ((insight?.totalPlayedMs ?: 0L) / 60_000f) * 0.08
            if (song.uri.toString() in recentlyPlayedUris) score -= 1.25

            val reason = when {
                song.artist in preferredArtists -> "Ayni sanatci"
                fingerprint.genreTag in preferredGenres -> "Benzer tur"
                fingerprint.moodTag in preferredMoods -> "Benzer mood"
                fingerprint.tempoBucket in preferredTempo -> "Benzer tempo"
                else -> "Kutuphane secimi"
            }
            Triple(song, score, reason)
        }

        return scored
            .sortedByDescending { it.second }
            .take(limit)
            .map { (song, _, reason) -> RecommendationItem.LocalSong(song = song, reason = reason) }
    }

    private suspend fun loadOnlineRecommendations(
        context: Context,
        songs: List<FlownaSong>,
        insights: List<TrackInsight>
    ): List<RecommendationItem.OnlineCandidate> {
        val cached = ListeningInsightsRepository.getFreshOnlineRecommendations(
            context = context,
            maxAgeMs = ONLINE_CACHE_MAX_AGE_MS
        )
        if (!ConnectivityHelper.isConnected(context) && cached.isNotEmpty()) {
            return cached.map { it.toRecommendationItem() }
        }

        if (!ConnectivityHelper.isConnected(context)) {
            return emptyList()
        }

        val seeds = buildSeeds(songs, insights)
        if (seeds.isEmpty()) {
            return cached.map { it.toRecommendationItem() }
        }

        val candidates = mutableListOf<OnlineRecommendationEntity>()
        seeds.forEach { seed ->
            SearchRepository.search(seed)
                .onSuccess { items ->
                    items.take(4).forEach { item ->
                        candidates += OnlineRecommendationEntity(
                            videoId = item.videoId.ifBlank { item.videoUrl },
                            videoUrl = item.videoUrl,
                            title = item.title,
                            artist = item.uploaderName,
                            thumbnailUrl = item.thumbnailUrl,
                            durationSeconds = item.duration,
                            sourceSeed = seed,
                            updatedAt = System.currentTimeMillis()
                        )
                    }
                }
        }

        val distinct = candidates
            .distinctBy { it.videoId }
            .take(8)

        return if (distinct.isNotEmpty()) {
            ListeningInsightsRepository.cacheOnlineRecommendations(context, distinct)
            distinct.map { it.toRecommendationItem() }
        } else {
            cached.map { it.toRecommendationItem() }
        }
    }

    private fun buildSeeds(
        songs: List<FlownaSong>,
        insights: List<TrackInsight>
    ): List<String> {
        val fromInsights = insights
            .sortedWith(compareByDescending<TrackInsight> { it.completedPlayCount }.thenByDescending { it.lastPlayedAt })
            .flatMap { listOf(it.artist, "${it.artist} ${it.genreTag}") }
        val fromLibrary = songs.take(3).flatMap { listOf(it.artist, "${it.title} ${it.artist}") }
        return (fromInsights + fromLibrary)
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.equals("Bilinmeyen Sanatçı", ignoreCase = true) }
            .distinct()
            .take(4)
    }

    private fun mixRecommendations(
        local: List<RecommendationItem.LocalSong>,
        online: List<RecommendationItem.OnlineCandidate>,
        limit: Int
    ): List<RecommendationItem> {
        if (online.isEmpty()) return local.take(limit)

        val mixed = mutableListOf<RecommendationItem>()
        val localIterator = local.iterator()
        val onlineIterator = online.iterator()

        while (mixed.size < limit && (localIterator.hasNext() || onlineIterator.hasNext())) {
            if (localIterator.hasNext()) {
                mixed += localIterator.next()
            }
            if (mixed.size >= limit) break
            if (onlineIterator.hasNext()) {
                mixed += onlineIterator.next()
            }
        }

        return mixed.take(limit)
    }

    private fun OnlineRecommendationEntity.toRecommendationItem(): RecommendationItem.OnlineCandidate {
        return RecommendationItem.OnlineCandidate(
            videoId = videoId,
            videoUrl = videoUrl,
            title = title,
            artist = artist,
            thumbnailUrl = thumbnailUrl,
            durationSeconds = durationSeconds,
            sourceReason = sourceSeed
        )
    }
}
