package com.flex.data.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

object UpdateDownloader {

    const val ACTION_INSTALL_COMPLETE = "com.flex.ACTION_INSTALL_COMPLETE"

    private val ALLOWED_HOSTS = setOf(
        "github.com",
        "objects.githubusercontent.com",
        "raw.githubusercontent.com"
    )

    fun isAllowedDownloadUrl(downloadUrl: String): Boolean {
        val uri = runCatching { URI(downloadUrl) }.getOrNull() ?: return false
        if (!"https".equals(uri.scheme, ignoreCase = true)) {
            return false
        }
        val host = uri.host?.lowercase()?.takeIf { it.isNotEmpty() } ?: return false
        return ALLOWED_HOSTS.any { allowedHost ->
            host == allowedHost || host.endsWith(".$allowedHost")
        }
    }

    suspend fun downloadAndInstall(context: Context, downloadUrl: String) {
        if (!isAllowedDownloadUrl(downloadUrl)) {
            error("Ungültige oder unsichere Download-URL: $downloadUrl")
        }

        val apkFile = File(context.filesDir, "update.apk")
        if (apkFile.exists()) {
            apkFile.delete()
        }

        withContext(Dispatchers.IO) {
            val url = URL(downloadUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.instanceFollowRedirects = true
            connection.connect()

            if (connection.responseCode !in 200..299) {
                error("Download fehlgeschlagen: HTTP ${connection.responseCode}")
            }

            connection.inputStream.use { input ->
                FileOutputStream(apkFile).use { output ->
                    input.copyTo(output)
                }
            }
            connection.disconnect()
        }

        installApk(context, apkFile)
    }

    private fun installApk(context: Context, apkFile: File) {
        val packageInstaller = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(apkFile.length())
        }
        val sessionId = packageInstaller.createSession(params)
        val session = packageInstaller.openSession(sessionId)
        session.use { s ->
            apkFile.inputStream().use { input ->
                s.openWrite("flex_update", 0, apkFile.length()).use { output ->
                    input.copyTo(output)
                    s.fsync(output)
                }
            }
            val callbackIntent = Intent(context, com.flex.MainActivity::class.java).apply {
                action = ACTION_INSTALL_COMPLETE
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                sessionId,
                callbackIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            s.commit(pendingIntent.intentSender)
        }
    }

    fun openInstallPermissionSettings(context: Context) {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = Uri.parse("package:${context.packageName}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }
}
