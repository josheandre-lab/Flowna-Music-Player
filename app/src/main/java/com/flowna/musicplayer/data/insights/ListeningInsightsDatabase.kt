package com.flowna.musicplayer.data.insights

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        TrackInsightEntity::class,
        OnlineRecommendationEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class ListeningInsightsDatabase : RoomDatabase() {

    abstract fun insightsDao(): ListeningInsightsDao

    companion object {
        @Volatile
        private var instance: ListeningInsightsDatabase? = null

        fun getInstance(context: Context): ListeningInsightsDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ListeningInsightsDatabase::class.java,
                    "flowna_listening_insights.db"
                ).fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
        }
    }
}
