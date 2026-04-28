package com.flowna.musicplayer.util

import android.content.Context
import android.os.Build
import com.flowna.musicplayer.BuildConfig

data class InstalledAppVersion(
    val name: String,
    val code: Long
) {
    val display: String
        get() = "$name ($code)"
}

object AppVersionProvider {
    fun get(context: Context): InstalledAppVersion {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }

        return InstalledAppVersion(
            name = packageInfo.versionName ?: BuildConfig.VERSION_NAME,
            code = versionCode
        )
    }
}
