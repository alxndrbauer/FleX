package com.vrema.data.backup

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File
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
        return try {
            val json = backupRepository.createBackupJson()

            val backupDir = File(applicationContext.filesDir, "backups")
            backupDir.mkdirs()

            val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
            val file = File(backupDir, "vrema_backup_$timestamp.json")
            file.writeText(json, Charsets.UTF_8)

            backupPreferences.lastBackupTimestamp = System.currentTimeMillis()

            // Keep only the N most recent backups
            val maxBackups = backupPreferences.maxLocalBackups
            val backupFiles = backupDir.listFiles { f -> f.name.endsWith(".json") }
                ?.sortedByDescending { it.lastModified() }
                ?: emptyList()

            backupFiles.drop(maxBackups).forEach { it.delete() }

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "vrema_auto_backup"
    }
}
