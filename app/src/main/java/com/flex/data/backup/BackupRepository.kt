package com.flex.data.backup

import android.content.ContentResolver
import android.net.Uri
import androidx.room.withTransaction
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.flex.data.local.FlexDatabase
import com.flex.data.local.dao.QuotaRuleDao
import com.flex.data.local.dao.SettingsDao
import com.flex.data.local.dao.TimeBlockDao
import com.flex.data.local.dao.WorkDayDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupRepository @Inject constructor(
    private val database: FlexDatabase,
    private val workDayDao: WorkDayDao,
    private val timeBlockDao: TimeBlockDao,
    private val settingsDao: SettingsDao,
    private val quotaRuleDao: QuotaRuleDao
) {
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    suspend fun createBackupJson(): String {
        val workDays = workDayDao.getAllWorkDays()
        val timeBlocks = timeBlockDao.getAllTimeBlocks()
        val settings = settingsDao.getSettingsSync()
        val quotaRules = quotaRuleDao.getAllRulesSync()

        val backupFile = BackupFile(
            version = 1,
            createdAt = Instant.now()
                .atOffset(ZoneOffset.UTC)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
            data = BackupData(
                workDays = workDays,
                timeBlocks = timeBlocks,
                settings = settings,
                quotaRules = quotaRules
            )
        )

        return gson.toJson(backupFile)
    }

    suspend fun exportToUri(contentResolver: ContentResolver, uri: Uri) = withContext(Dispatchers.IO) {
        val json = createBackupJson()
        contentResolver.openOutputStream(uri)?.use { outputStream ->
            outputStream.write(json.toByteArray(Charsets.UTF_8))
        } ?: throw IllegalStateException("Could not open output stream")
    }

    suspend fun importFromUri(contentResolver: ContentResolver, uri: Uri, mode: ImportMode) = withContext(Dispatchers.IO) {
        val json = contentResolver.openInputStream(uri)?.use { inputStream ->
            inputStream.bufferedReader().readText()
        } ?: throw IllegalStateException("Could not open input stream")

        val backupFile = gson.fromJson(json, BackupFile::class.java)

        database.withTransaction {
            when (mode) {
                ImportMode.REPLACE -> {
                    timeBlockDao.deleteAll()
                    workDayDao.deleteAll()
                    quotaRuleDao.deleteAll()

                    workDayDao.insertAll(backupFile.data.workDays)
                    timeBlockDao.insertAll(backupFile.data.timeBlocks)
                    backupFile.data.settings?.let { settingsDao.insert(it) }
                    quotaRuleDao.insertAll(backupFile.data.quotaRules)
                }
                ImportMode.MERGE -> {
                    workDayDao.insertAll(backupFile.data.workDays)
                    timeBlockDao.insertAll(backupFile.data.timeBlocks)
                    backupFile.data.settings?.let { settingsDao.insert(it) }
                    quotaRuleDao.insertAll(backupFile.data.quotaRules)
                }
            }
        }
    }
}
