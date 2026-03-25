package com.flex.data.update

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

object UpdateDownloader {

    suspend fun downloadAndInstall(context: Context, downloadUrl: String) {
        val apkFile = File(context.getExternalFilesDir(null), "update.apk")
        apkFile.delete()

        val downloadManager = context.getSystemService(DownloadManager::class.java)
        val request = DownloadManager.Request(Uri.parse(downloadUrl))
            .setDestinationUri(Uri.fromFile(apkFile))
            .setTitle("FleX Update")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setMimeType("application/vnd.android.package-archive")

        val downloadId = downloadManager.enqueue(request)
        waitForDownload(downloadManager, downloadId)
        installApk(context, apkFile)
    }

    private suspend fun waitForDownload(downloadManager: DownloadManager, downloadId: Long) =
        withContext(Dispatchers.IO) {
            val query = DownloadManager.Query().setFilterById(downloadId)
            while (true) {
                val cursor = downloadManager.query(query)
                if (cursor.moveToFirst()) {
                    val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    cursor.close()
                    when (status) {
                        DownloadManager.STATUS_SUCCESSFUL -> return@withContext
                        DownloadManager.STATUS_FAILED -> error("Download fehlgeschlagen")
                    }
                } else {
                    cursor.close()
                }
                delay(500)
            }
        }

    private fun installApk(context: Context, apkFile: File) {
        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            apkFile
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    fun openInstallPermissionSettings(context: Context) {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = Uri.parse("package:${context.packageName}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }
}
