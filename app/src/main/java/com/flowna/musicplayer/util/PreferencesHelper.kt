package com.flowna.musicplayer.util

import android.content.Context
import android.content.SharedPreferences

object PreferencesHelper {

    private const val PREFS_NAME = "flowna_preferences"
    private const val KEY_AUDIO_QUALITY = "audio_quality"
    private const val KEY_LAST_YTDLP_UPDATE_CHECK = "last_ytdlp_update_check"
    private const val KEY_LAST_APP_UPDATE_CHECK = "last_app_update_check"
    private const val KEY_AUTO_YTDLP_UPDATE_CHECK = "auto_ytdlp_update_check"
    private const val KEY_AUTO_APP_UPDATE_CHECK = "auto_app_update_check"
    private const val KEY_LIBRARY_INITIAL_SCAN_COMPLETED = "library_initial_scan_completed"
    private const val KEY_LAST_LIBRARY_SCAN_AT = "last_library_scan_at"
    private const val KEY_FAVORITE_SONGS = "favorite_songs"
    private const val KEY_RECENT_SEARCHES = "recent_searches"
    private const val KEY_LAST_PREVIEW_ERROR = "last_preview_error"
    private const val KEY_LAST_DOWNLOAD_ERROR = "last_download_error"
    private const val AUTO_UPDATE_INTERVAL_MS = 24 * 60 * 60 * 1000L

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    fun getAudioQuality(): String {
        return prefs?.getString(KEY_AUDIO_QUALITY, "128K") ?: "128K"
    }

    fun setAudioQuality(quality: String) {
        prefs?.edit()?.putString(KEY_AUDIO_QUALITY, quality)?.apply()
    }

    fun getLastYtDlpUpdateCheck(): Long {
        return prefs?.getLong(KEY_LAST_YTDLP_UPDATE_CHECK, 0L) ?: 0L
    }

    fun isAutoYtDlpUpdateCheckEnabled(): Boolean {
        return prefs?.getBoolean(KEY_AUTO_YTDLP_UPDATE_CHECK, true) ?: true
    }

    fun setAutoYtDlpUpdateCheckEnabled(enabled: Boolean) {
        prefs?.edit()?.putBoolean(KEY_AUTO_YTDLP_UPDATE_CHECK, enabled)?.apply()
    }

    fun markYtDlpUpdateChecked(timestamp: Long = System.currentTimeMillis()) {
        prefs?.edit()?.putLong(KEY_LAST_YTDLP_UPDATE_CHECK, timestamp)?.apply()
    }

    fun shouldCheckForYtDlpUpdate(now: Long = System.currentTimeMillis()): Boolean {
        if (!isAutoYtDlpUpdateCheckEnabled()) return false
        return now - getLastYtDlpUpdateCheck() >= AUTO_UPDATE_INTERVAL_MS
    }

    fun getLastAppUpdateCheck(): Long {
        return prefs?.getLong(KEY_LAST_APP_UPDATE_CHECK, 0L) ?: 0L
    }

    fun isAutoAppUpdateCheckEnabled(): Boolean {
        return prefs?.getBoolean(KEY_AUTO_APP_UPDATE_CHECK, true) ?: true
    }

    fun setAutoAppUpdateCheckEnabled(enabled: Boolean) {
        prefs?.edit()?.putBoolean(KEY_AUTO_APP_UPDATE_CHECK, enabled)?.apply()
    }

    fun markAppUpdateChecked(timestamp: Long = System.currentTimeMillis()) {
        prefs?.edit()?.putLong(KEY_LAST_APP_UPDATE_CHECK, timestamp)?.apply()
    }

    fun shouldCheckForAppUpdate(now: Long = System.currentTimeMillis()): Boolean {
        if (!isAutoAppUpdateCheckEnabled()) return false
        return now - getLastAppUpdateCheck() >= AUTO_UPDATE_INTERVAL_MS
    }

    fun hasCompletedInitialLibraryScan(): Boolean {
        return prefs?.getBoolean(KEY_LIBRARY_INITIAL_SCAN_COMPLETED, false) ?: false
    }

    fun markLibraryScanCompleted(timestamp: Long = System.currentTimeMillis()) {
        prefs?.edit()
            ?.putBoolean(KEY_LIBRARY_INITIAL_SCAN_COMPLETED, true)
            ?.putLong(KEY_LAST_LIBRARY_SCAN_AT, timestamp)
            ?.apply()
    }

    fun getLastLibraryScanAt(): Long {
        return prefs?.getLong(KEY_LAST_LIBRARY_SCAN_AT, 0L) ?: 0L
    }

    fun isFavoriteSong(songUri: String): Boolean {
        return prefs?.getStringSet(KEY_FAVORITE_SONGS, emptySet())?.contains(songUri) == true
    }

    fun toggleFavoriteSong(songUri: String): Boolean {
        val current = prefs?.getStringSet(KEY_FAVORITE_SONGS, emptySet()).orEmpty().toMutableSet()
        val isFavorite = if (current.contains(songUri)) {
            current.remove(songUri)
            false
        } else {
            current.add(songUri)
            true
        }
        prefs?.edit()?.putStringSet(KEY_FAVORITE_SONGS, current)?.apply()
        return isFavorite
    }

    fun getRecentSearches(): List<String> {
        return prefs?.getString(KEY_RECENT_SEARCHES, null)
            ?.split("\u001F")
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            .orEmpty()
    }

    fun setRecentSearches(searches: List<String>) {
        val normalized = searches
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
            .take(6)
        prefs?.edit()?.putString(KEY_RECENT_SEARCHES, normalized.joinToString("\u001F"))?.apply()
    }

    fun clearRecentSearches() {
        prefs?.edit()?.remove(KEY_RECENT_SEARCHES)?.apply()
    }

    /**
     * Mevcut kalite degerlerinden birini dondurur: "128K", "192K", "320K"
     */
    fun getAvailableQualities(): List<String> = listOf("128K", "192K", "320K")

    fun recordLastPreviewError(message: String) {
        prefs?.edit()?.putString(KEY_LAST_PREVIEW_ERROR, message.take(240))?.apply()
    }

    fun getLastPreviewError(): String {
        return prefs?.getString(KEY_LAST_PREVIEW_ERROR, null).orEmpty()
    }

    fun recordLastDownloadError(message: String) {
        prefs?.edit()?.putString(KEY_LAST_DOWNLOAD_ERROR, message.take(240))?.apply()
    }

    fun getLastDownloadError(): String {
        return prefs?.getString(KEY_LAST_DOWNLOAD_ERROR, null).orEmpty()
    }

    fun clearDiagnostics() {
        prefs?.edit()
            ?.remove(KEY_LAST_PREVIEW_ERROR)
            ?.remove(KEY_LAST_DOWNLOAD_ERROR)
            ?.apply()
    }
}
