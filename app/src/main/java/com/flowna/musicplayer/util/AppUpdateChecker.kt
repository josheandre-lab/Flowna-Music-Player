package com.flowna.musicplayer.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.flowna.musicplayer.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.FileNotFoundException
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

object AppUpdateChecker {

    private const val TAG = "AppUpdateChecker"
    private const val REPO_API_URL = "https://api.github.com/repos/josheandre-lab/Flowna-Music-Player"
    private const val RELEASES_PAGE_URL = "https://github.com/josheandre-lab/Flowna-Music-Player/releases"
    private const val REPO_PAGE_URL = "https://github.com/josheandre-lab/Flowna-Music-Player"

    suspend fun check(force: Boolean = false): Result<AppUpdateInfo> = withContext(Dispatchers.IO) {
        runCatching {
            if (!force && !PreferencesHelper.shouldCheckForAppUpdate()) {
                AppUpdateState.info.value?.let { return@runCatching it }
            }

            val repoJson = fetchJson(REPO_API_URL)
            val pushedAt = repoJson.optString("pushed_at")
            val repoHtmlUrl = repoJson.optString("html_url").ifBlank { REPO_PAGE_URL }
            val latestCommitInstant = pushedAt.takeIf { it.isNotBlank() }?.let(Instant::parse)

            val releaseJson = fetchJsonOrNull("$REPO_API_URL/releases/latest")
            val releaseTitle = releaseJson?.optString("tag_name")
                ?.takeIf { it.isNotBlank() }
                ?: "Depo güncellemesi"
            val releasePageUrl = releaseJson?.optString("html_url")
                ?.takeIf { it.isNotBlank() }
                ?: RELEASES_PAGE_URL
            val releaseAssetUrl = releaseJson
                ?.optJSONArray("assets")
                ?.findFirstApkUrl()

            val hasPublishedApk = !releaseAssetUrl.isNullOrBlank()
            val latestTitle = releaseTitle
            val updateAvailable = when {
                releaseJson != null -> releaseTitle != BuildConfig.VERSION_NAME
                latestCommitInstant != null -> latestCommitInstant.toEpochMilli() > BuildConfig.BUILD_TIME_UTC
                else -> false
            }

            val summary = when {
                releaseJson != null && hasPublishedApk && updateAvailable -> {
                    "Yeni uygulama paketi bulundu."
                }

                releaseJson != null && updateAvailable -> {
                    "Yeni sürüm bilgisi bulundu, ancak APK eki yok."
                }

                updateAvailable -> {
                    "Kod deposunda bu uygulama derlemesinden daha yeni değişiklik var."
                }

                else -> {
                    "Uygulama güncel görünüyor."
                }
            }

            val info = AppUpdateInfo(
                updateAvailable = updateAvailable,
                latestTitle = latestTitle,
                summary = summary,
                openUrl = releaseAssetUrl ?: releasePageUrl.ifBlank { repoHtmlUrl },
                hasPublishedApk = hasPublishedApk,
                checkedAtMillis = System.currentTimeMillis()
            )

            PreferencesHelper.markAppUpdateChecked()
            AppUpdateState.publish(info)
            info
        }
    }

    fun openUpdate(context: Context, updateInfo: AppUpdateInfo) {
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(updateInfo.openUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }.onFailure {
            Log.e(TAG, "Güncelleme sayfası açılamadı", it)
        }
    }

    private fun fetchJson(url: String): JSONObject {
        return fetchJsonOrNull(url) ?: error("JSON okunamadi: $url")
    }

    private fun fetchJsonOrNull(url: String): JSONObject? {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "Flowna-Music-Player")
        }

        return try {
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) return null
            val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
            JSONObject(responseBody)
        } catch (_: FileNotFoundException) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun JSONArray.findFirstApkUrl(): String? {
        for (index in 0 until length()) {
            val asset = optJSONObject(index) ?: continue
            val assetName = asset.optString("name")
            val assetUrl = asset.optString("browser_download_url")
            if (assetName.endsWith(".apk", ignoreCase = true) && assetUrl.isNotBlank()) {
                return assetUrl
            }
        }
        return null
    }
}
