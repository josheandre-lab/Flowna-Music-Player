package com.flowna.musicplayer.data.insights

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "track_insights")
data class TrackInsightEntity(
    @PrimaryKey val songUri: String,
    val title: String,
    val artist: String,
    val album: String,
    val lastPlayedAt: Long,
    val completedPlayCount: Int,
    val totalPlayedMs: Long,
    val isFavorite: Boolean,
    val genreTag: String,
    val moodTag: String,
    val tempoBucket: String
)

data class TrackInsight(
    val songUri: String,
    val title: String,
    val artist: String,
    val album: String,
    val lastPlayedAt: Long,
    val completedPlayCount: Int,
    val totalPlayedMs: Long,
    val isFavorite: Boolean,
    val genreTag: String,
    val moodTag: String,
    val tempoBucket: String
)

fun TrackInsightEntity.toModel(): TrackInsight {
    return TrackInsight(
        songUri = songUri,
        title = title,
        artist = artist,
        album = album,
        lastPlayedAt = lastPlayedAt,
        completedPlayCount = completedPlayCount,
        totalPlayedMs = totalPlayedMs,
        isFavorite = isFavorite,
        genreTag = genreTag,
        moodTag = moodTag,
        tempoBucket = tempoBucket
    )
}
