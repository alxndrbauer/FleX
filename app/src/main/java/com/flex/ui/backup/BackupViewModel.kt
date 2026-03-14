package com.flex.ui.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.flex.data.backup.BackupPreferences
import com.flex.data.backup.BackupRepository
import com.flex.data.backup.BackupWorker
import com.flex.data.backup.ImportMode
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class BackupUiState(
    val isLoading: Boolean = false,
    val isAutoBackupEnabled: Boolean = false,
    val lastBackupTime: String? = null,
    val localBackupCount: Int = 0,
    val message: String? = null,
    val showImportModeDialog: Boolean = false,
    val pendingImportUri: Uri? = null,
    val autoBackupDirectoryUri: Uri? = null,
    val autoBackupDirectoryName: String? = null
)

@HiltViewModel
class BackupViewModel @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    private val backupRepository: BackupRepository,
    private val backupPreferences: BackupPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(BackupUiState())
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

    private val dateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm", Locale.GERMAN)

    init {
        loadState()
    }

    private fun loadState() {
        val lastBackup = backupPreferences.lastBackupTimestamp
        val dirUriString = backupPreferences.autoBackupDirectoryUri
        val dirUri = dirUriString?.let { Uri.parse(it) }
        val count = if (dirUri != null) {
            DocumentFile.fromTreeUri(appContext, dirUri)
                ?.listFiles()
                ?.count { it.name?.startsWith("flex_backup_") == true && it.name?.endsWith(".json") == true }
                ?: 0
        } else 0
        val dirName = if (dirUri != null) {
            DocumentFile.fromTreeUri(appContext, dirUri)?.name ?: dirUri.lastPathSegment
        } else null

        _uiState.update {
            it.copy(
                isAutoBackupEnabled = backupPreferences.isAutoBackupEnabled,
                localBackupCount = count,
                lastBackupTime = if (lastBackup > 0) {
                    Instant.ofEpochMilli(lastBackup)
                        .atZone(ZoneId.systemDefault())
                        .format(dateTimeFormatter)
                } else null,
                autoBackupDirectoryUri = dirUri,
                autoBackupDirectoryName = dirName
            )
        }

        // Re-schedule if auto-backup was enabled before (survives app restarts)
        if (backupPreferences.isAutoBackupEnabled) {
            schedulePeriodicBackup()
        }
    }

    fun exportToLocalFile(contentResolver: android.content.ContentResolver, uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, message = null) }
            try {
                backupRepository.exportToUri(contentResolver, uri)
                backupPreferences.lastBackupTimestamp = System.currentTimeMillis()
                loadState()
                _uiState.update { it.copy(isLoading = false, message = "Export erfolgreich") }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, message = "Export fehlgeschlagen: ${e.message}") }
            }
        }
    }

    fun onImportFileSelected(uri: Uri) {
        _uiState.update { it.copy(showImportModeDialog = true, pendingImportUri = uri) }
    }

    fun dismissImportDialog() {
        _uiState.update { it.copy(showImportModeDialog = false, pendingImportUri = null) }
    }

    fun confirmImport(contentResolver: android.content.ContentResolver, mode: ImportMode) {
        val uri = _uiState.value.pendingImportUri ?: return
        _uiState.update { it.copy(showImportModeDialog = false, isLoading = true, message = null) }

        viewModelScope.launch {
            try {
                backupRepository.importFromUri(contentResolver, uri, mode)
                _uiState.update { it.copy(isLoading = false, message = "Import erfolgreich", pendingImportUri = null) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, message = "Import fehlgeschlagen: ${e.message}", pendingImportUri = null) }
            }
        }
    }

    fun selectBackupDirectory(uri: Uri) {
        appContext.contentResolver.takePersistableUriPermission(
            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        backupPreferences.autoBackupDirectoryUri = uri.toString()
        loadState()
    }

    fun toggleAutoBackup(enabled: Boolean) {
        if (enabled && backupPreferences.autoBackupDirectoryUri == null) {
            _uiState.update { it.copy(message = "Bitte zuerst ein Verzeichnis wählen") }
            return
        }

        backupPreferences.isAutoBackupEnabled = enabled
        _uiState.update { it.copy(isAutoBackupEnabled = enabled) }

        if (enabled) {
            schedulePeriodicBackup()
            // Run an immediate backup so the user sees it working right away
            WorkManager.getInstance(appContext)
                .enqueue(OneTimeWorkRequestBuilder<BackupWorker>().build())
        } else {
            WorkManager.getInstance(appContext).cancelUniqueWork(BackupWorker.WORK_NAME)
        }
    }

    private fun schedulePeriodicBackup() {
        val backupRequest = PeriodicWorkRequestBuilder<BackupWorker>(
            24, TimeUnit.HOURS
        ).build()

        WorkManager.getInstance(appContext).enqueueUniquePeriodicWork(
            BackupWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            backupRequest
        )
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }
}
