package com.flowna.musicplayer.data.insights

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "online_recommendation_cache")
data class OnlineRecommendationEntity(
    @PrimaryKey val videoId: String,
    val videoUrl: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String,
    val durationSeconds: Long,
    val sourceSeed: String,
    val updatedAt: Long
)
