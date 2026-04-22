package com.flowna.musicplayer.data.repository

import android.content.Context
import com.flowna.musicplayer.data.FlownaSong
import com.flowna.musicplayer.data.insights.ListeningInsightsDatabase
import com.flowna.musicplayer.data.insights.OnlineRecommendationEntity
import com.flowna.musicplayer.data.insights.TrackInsight
import com.flowna.musicplayer.data.insights.TrackInsightEntity
import com.flowna.musicplayer.data.insights.toModel
import com.flowna.musicplayer.data.recommendation.TrackHeuristics
import com.flowna.musicplayer.util.PreferencesHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ListeningInsightsRepository {

    private fun dao(context: Context) = ListeningInsightsDatabase.getInstance(context).insightsDao()

    suspend fun getAllTrackInsights(context: Context): List<TrackInsight> = withContext(Dispatchers.IO) {
        dao(context).getAllTrackInsights().map { it.toModel() }
    }

    suspend fun syncLibrarySongs(context: Context, songs: List<FlownaSong>) = withContext(Dispatchers.IO) {
        val existing = dao(context).getAllTrackInsights().associateBy { it.songUri }
        val merged = songs.map { song ->
            val fingerprint = TrackHeuristics.fingerprint(song)
            val previous = existing[song.uri.toString()]
            TrackInsightEntity(
                songUri = song.uri.toString(),
                title = song.title,
                artist = song.artist,
                album = song.album,
                lastPlayedAt = previous?.lastPlayedAt ?: 0L,
                completedPlayCount = previous?.completedPlayCount ?: 0,
                totalPlayedMs = previous?.totalPlayedMs ?: 0L,
                isFavorite = PreferencesHelper.isFavoriteSong(song.uri.toString()),
                genreTag = previous?.genreTag?.ifBlank { fingerprint.genreTag } ?: fingerprint.genreTag,
                moodTag = previous?.moodTag?.ifBlank { fingerprint.moodTag } ?: fingerprint.moodTag,
                tempoBucket = previous?.tempoBucket?.ifBlank { fingerprint.tempoBucket } ?: fingerprint.tempoBucket
            )
        }
        dao(context).upsertTrackInsights(merged)
    }

    suspend fun markSongStarted(context: Context, song: FlownaSong) = withContext(Dispatchers.IO) {
        val current = dao(context).getTrackInsight(song.uri.toString())
        val fingerprint = TrackHeuristics.fingerprint(song)
        dao(context).upsertTrackInsight(
            TrackInsightEntity(
                songUri = song.uri.toString(),
                title = song.title,
                artist = song.artist,
                album = song.album,
                lastPlayedAt = System.currentTimeMillis(),
                completedPlayCount = current?.completedPlayCount ?: 0,
                totalPlayedMs = current?.totalPlayedMs ?: 0L,
                isFavorite = PreferencesHelper.isFavoriteSong(song.uri.toString()),
                genreTag = current?.genreTag?.ifBlank { fingerprint.genreTag } ?: fingerprint.genreTag,
                moodTag = current?.moodTag?.ifBlank { fingerprint.moodTag } ?: fingerprint.moodTag,
                tempoBucket = current?.tempoBucket?.ifBlank { fingerprint.tempoBucket } ?: fingerprint.tempoBucket
            )
        )
    }

    suspend fun addPlaybackDuration(
        context: Context,
        song: FlownaSong,
        listenedMs: Long
    ) = withContext(Dispatchers.IO) {
        if (listenedMs <= 0L) return@withContext
        val current = dao(context).getTrackInsight(song.uri.toString())
        val fingerprint = TrackHeuristics.fingerprint(song)
        dao(context).upsertTrackInsight(
            TrackInsightEntity(
                songUri = song.uri.toString(),
                title = song.title,
                artist = song.artist,
                album = song.album,
                lastPlayedAt = current?.lastPlayedAt ?: System.currentTimeMillis(),
                completedPlayCount = current?.completedPlayCount ?: 0,
                totalPlayedMs = (current?.totalPlayedMs ?: 0L) + listenedMs,
                isFavorite = PreferencesHelper.isFavoriteSong(song.uri.toString()),
                genreTag = current?.genreTag?.ifBlank { fingerprint.genreTag } ?: fingerprint.genreTag,
                moodTag = current?.moodTag?.ifBlank { fingerprint.moodTag } ?: fingerprint.moodTag,
                tempoBucket = current?.tempoBucket?.ifBlank { fingerprint.tempoBucket } ?: fingerprint.tempoBucket
            )
        )
    }

    suspend fun incrementCompletedPlayCount(context: Context, song: FlownaSong) = withContext(Dispatchers.IO) {
        val current = dao(context).getTrackInsight(song.uri.toString())
        val fingerprint = TrackHeuristics.fingerprint(song)
        dao(context).upsertTrackInsight(
            TrackInsightEntity(
                songUri = song.uri.toString(),
                title = song.title,
                artist = song.artist,
                album = song.album,
                lastPlayedAt = System.currentTimeMillis(),
                completedPlayCount = (current?.completedPlayCount ?: 0) + 1,
                totalPlayedMs = current?.totalPlayedMs ?: 0L,
                isFavorite = PreferencesHelper.isFavoriteSong(song.uri.toString()),
                genreTag = current?.genreTag?.ifBlank { fingerprint.genreTag } ?: fingerprint.genreTag,
                moodTag = current?.moodTag?.ifBlank { fingerprint.moodTag } ?: fingerprint.moodTag,
                tempoBucket = current?.tempoBucket?.ifBlank { fingerprint.tempoBucket } ?: fingerprint.tempoBucket
            )
        )
    }

    suspend fun updateFavoriteState(context: Context, song: FlownaSong, isFavorite: Boolean) = withContext(Dispatchers.IO) {
        val current = dao(context).getTrackInsight(song.uri.toString())
        val fingerprint = TrackHeuristics.fingerprint(song)
        dao(context).upsertTrackInsight(
            TrackInsightEntity(
                songUri = song.uri.toString(),
                title = song.title,
                artist = song.artist,
                album = song.album,
                lastPlayedAt = current?.lastPlayedAt ?: 0L,
                completedPlayCount = current?.completedPlayCount ?: 0,
                totalPlayedMs = current?.totalPlayedMs ?: 0L,
                isFavorite = isFavorite,
                genreTag = current?.genreTag?.ifBlank { fingerprint.genreTag } ?: fingerprint.genreTag,
                moodTag = current?.moodTag?.ifBlank { fingerprint.moodTag } ?: fingerprint.moodTag,
                tempoBucket = current?.tempoBucket?.ifBlank { fingerprint.tempoBucket } ?: fingerprint.tempoBucket
            )
        )
    }

    suspend fun getFreshOnlineRecommendations(
        context: Context,
        maxAgeMs: Long
    ): List<OnlineRecommendationEntity> = withContext(Dispatchers.IO) {
        dao(context).getOnlineRecommendations(System.currentTimeMillis() - maxAgeMs)
    }

    suspend fun cacheOnlineRecommendations(
        context: Context,
        recommendations: List<OnlineRecommendationEntity>
    ) = withContext(Dispatchers.IO) {
        val insightsDao = dao(context)
        insightsDao.clearOnlineRecommendations()
        insightsDao.upsertOnlineRecommendations(recommendations)
    }
}
