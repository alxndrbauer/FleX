package com.flex.data.update

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val versionCode: Int = 0,
    val versionName: String = "",
    val changelog: String = "",
    val downloadUrl: String = ""
)

object UpdateChecker {
    // TODO: Replace with your Gist raw URL after creating it
    // Format: https://gist.githubusercontent.com/YOUR_USERNAME/YOUR_GIST_ID/raw/version.json
    private const val MANIFEST_URL = "https://gist.githubusercontent.com/alxndrbauer/1eddf47c2c689bdc8b27ca2ef7395b02/raw/version.json"

    suspend fun checkForUpdate(currentVersionCode: Int): UpdateInfo? =
        withContext(Dispatchers.IO) {
            runCatching {
                val connection = URL(MANIFEST_URL).openConnection() as HttpURLConnection
                connection.connectTimeout = 5_000
                connection.readTimeout = 5_000
                val json = connection.inputStream.bufferedReader().readText()
                connection.disconnect()
                val info = Gson().fromJson(json, UpdateInfo::class.java)
                if (info.versionCode > currentVersionCode) info else null
            }.getOrNull()
        }
}
