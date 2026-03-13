package com.flex.data.backup

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@HiltWorker
class BackupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val backupRepository: BackupRepository,
    private val backupPreferences: BackupPreferences
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val dirUriString = backupPreferences.autoBackupDirectoryUri
            ?: return Result.failure()

        val dirUri = Uri.parse(dirUriString)
        val docDir = DocumentFile.fromTreeUri(applicationContext, dirUri)
            ?: return Result.failure()

        return try {
            val json = backupRepository.createBackupJson()

            val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
            val file = docDir.createFile("application/json", "flex_backup_$timestamp")
                ?: return Result.retry()

            applicationContext.contentResolver.openOutputStream(file.uri)?.use { outputStream ->
                outputStream.write(json.toByteArray(Charsets.UTF_8))
            }

            backupPreferences.lastBackupTimestamp = System.currentTimeMillis()

            // Keep only the N most recent backups
            val maxBackups = backupPreferences.maxLocalBackups
            val backupFiles = docDir.listFiles()
                .filter { it.name?.startsWith("flex_backup_") == true && it.name?.endsWith(".json") == true }
                .sortedByDescending { it.lastModified() }

            backupFiles.drop(maxBackups).forEach { it.delete() }

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "flex_auto_backup"
    }
}
