package com.flex.data.update

import com.flex.BuildConfig
import com.google.gson.Gson
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val versionCode: Int = 0,
    val versionName: String = "",
    val changelog: String = "",
    val downloadUrl: String = "",
    val isBeta: Boolean = false
)

/**
 * Gist-Manifest: stabile Felder auf oberster Ebene (abwärtskompatibel zu alten Clients),
 * optionaler Beta-Kanal unter `beta`.
 */
private data class UpdateManifest(
    val versionCode: Int = 0,
    val versionName: String = "",
    val changelog: String = "",
    val downloadUrl: String = "",
    val beta: UpdateInfo? = null
)

object UpdateChecker {
    private const val TAG = "UpdateChecker"
    private val manifestUrl: String = BuildConfig.UPDATE_MANIFEST_URL

    suspend fun checkForUpdate(
        currentVersionCode: Int,
        includeBeta: Boolean = false
    ): UpdateInfo? =
        withContext(Dispatchers.IO) {
            runCatching {
                val connection = URL(manifestUrl).openConnection() as HttpURLConnection
                connection.connectTimeout = 5_000
                connection.readTimeout = 5_000
                val json = connection.inputStream.bufferedReader().readText()
                connection.disconnect()
                selectUpdate(json, currentVersionCode, includeBeta)
            }.onFailure {
                Log.w(TAG, "Update-Prüfung fehlgeschlagen ($manifestUrl)", it)
            }.getOrNull()
        }

    /**
     * Wählt den höchsten angebotenen Kanal oberhalb der installierten Version.
     * Liegt die stabile Version vor der Beta, gewinnt sie auch bei aktivem Opt-in.
     */
    internal fun selectUpdate(
        json: String,
        currentVersionCode: Int,
        includeBeta: Boolean
    ): UpdateInfo? {
        val manifest = runCatching {
            Gson().fromJson(json, UpdateManifest::class.java)
        }.getOrNull() ?: return null

        val candidates = buildList {
            manifest.stableUpdate()?.let(::add)
            if (includeBeta) {
                manifest.beta?.takeIf { it.isPublishable() }?.let {
                    add(it.copy(isBeta = true))
                }
            }
        }
        return candidates
            .filter { it.versionCode > currentVersionCode }
            .maxByOrNull { it.versionCode }
    }

    private fun UpdateManifest.stableUpdate(): UpdateInfo? {
        if (versionCode <= 0 || versionName.isBlank() || downloadUrl.isBlank()) return null
        return UpdateInfo(
            versionCode = versionCode,
            versionName = versionName,
            changelog = changelog,
            downloadUrl = downloadUrl,
            isBeta = false
        )
    }

    private fun UpdateInfo.isPublishable(): Boolean =
        versionCode > 0 && versionName.isNotBlank() && downloadUrl.isNotBlank()
}
