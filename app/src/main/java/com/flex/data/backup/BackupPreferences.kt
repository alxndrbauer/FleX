package com.flex.data.backup

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupPreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("backup_prefs", Context.MODE_PRIVATE)

    var lastBackupTimestamp: Long
        get() = prefs.getLong(KEY_LAST_BACKUP, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_BACKUP, value).apply()

    var isAutoBackupEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_BACKUP_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_BACKUP_ENABLED, value).apply()

    var maxLocalBackups: Int
        get() = prefs.getInt(KEY_MAX_LOCAL_BACKUPS, 5)
        set(value) = prefs.edit().putInt(KEY_MAX_LOCAL_BACKUPS, value).apply()

    var autoBackupDirectoryUri: String?
        get() = prefs.getString(KEY_AUTO_BACKUP_DIR, null)
        set(value) {
            if (value != null) {
                prefs.edit().putString(KEY_AUTO_BACKUP_DIR, value).apply()
            } else {
                prefs.edit().remove(KEY_AUTO_BACKUP_DIR).apply()
            }
        }

    companion object {
        private const val KEY_LAST_BACKUP = "last_backup_timestamp"
        private const val KEY_AUTO_BACKUP_ENABLED = "auto_backup_enabled"
        private const val KEY_MAX_LOCAL_BACKUPS = "max_local_backups"
        private const val KEY_AUTO_BACKUP_DIR = "auto_backup_directory_uri"
    }
}
