package com.flowna.musicplayer.data.insights

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ListeningInsightsDao {

    @Query("SELECT * FROM track_insights")
    suspend fun getAllTrackInsights(): List<TrackInsightEntity>

    @Query("SELECT * FROM track_insights ORDER BY completedPlayCount DESC, lastPlayedAt DESC")
    fun observeTrackInsights(): Flow<List<TrackInsightEntity>>

    @Query("SELECT * FROM track_insights WHERE songUri = :songUri LIMIT 1")
    suspend fun getTrackInsight(songUri: String): TrackInsightEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTrackInsight(entity: TrackInsightEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTrackInsights(entities: List<TrackInsightEntity>)

    @Query("SELECT * FROM online_recommendation_cache WHERE updatedAt >= :minUpdatedAt ORDER BY updatedAt DESC")
    suspend fun getOnlineRecommendations(minUpdatedAt: Long): List<OnlineRecommendationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOnlineRecommendations(entities: List<OnlineRecommendationEntity>)

    @Query("DELETE FROM online_recommendation_cache")
    suspend fun clearOnlineRecommendations()
}
