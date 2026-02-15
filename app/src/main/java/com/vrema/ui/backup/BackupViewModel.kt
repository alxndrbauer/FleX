package com.vrema.ui.backup

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.vrema.data.backup.BackupPreferences
import com.vrema.data.backup.BackupRepository
import com.vrema.data.backup.BackupWorker
import com.vrema.data.backup.ImportMode
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
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
    val pendingImportUri: Uri? = null
)

@HiltViewModel
class BackupViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
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
        val backupDir = File(appContext.filesDir, "backups")
        val count = backupDir.listFiles { f -> f.name.endsWith(".json") }?.size ?: 0

        _uiState.update {
            it.copy(
                isAutoBackupEnabled = backupPreferences.isAutoBackupEnabled,
                localBackupCount = count,
                lastBackupTime = if (lastBackup > 0) {
                    Instant.ofEpochMilli(lastBackup)
                        .atZone(ZoneId.systemDefault())
                        .format(dateTimeFormatter)
                } else null
            )
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

    fun toggleAutoBackup(enabled: Boolean) {
        backupPreferences.isAutoBackupEnabled = enabled
        _uiState.update { it.copy(isAutoBackupEnabled = enabled) }

        val workManager = WorkManager.getInstance(appContext)
        if (enabled) {
            val backupRequest = PeriodicWorkRequestBuilder<BackupWorker>(
                24, TimeUnit.HOURS
            ).build()

            workManager.enqueueUniquePeriodicWork(
                BackupWorker.WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                backupRequest
            )
        } else {
            workManager.cancelUniqueWork(BackupWorker.WORK_NAME)
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }
}
