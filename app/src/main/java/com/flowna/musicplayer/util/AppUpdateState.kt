package com.flowna.musicplayer.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppUpdateInfo(
    val updateAvailable: Boolean,
    val latestTitle: String,
    val summary: String,
    val openUrl: String,
    val hasPublishedApk: Boolean,
    val checkedAtMillis: Long
)

object AppUpdateState {

    private val _info = MutableStateFlow<AppUpdateInfo?>(null)
    val info: StateFlow<AppUpdateInfo?> = _info.asStateFlow()

    fun publish(info: AppUpdateInfo) {
        _info.value = info
    }
}
