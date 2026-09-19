package com.flex.data.local

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PausePreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("pause_prefs", Context.MODE_PRIVATE)

    var pauseStartTime: Long
        get() = prefs.getLong(KEY_PAUSE_START_TIME, 0L)
        set(value) {
            prefs.edit().putLong(KEY_PAUSE_START_TIME, value).commit()
        }

    fun clearPause() {
        prefs.edit().remove(KEY_PAUSE_START_TIME).commit()
    }

    val isPaused: Boolean
        get() {
            val start = pauseStartTime
            if (start <= 0L) return false
            val startDate = java.time.Instant.ofEpochMilli(start)
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalDate()
            if (startDate != java.time.LocalDate.now()) {
                clearPause()
                return false
            }
            return true
        }

    companion object {
        private const val KEY_PAUSE_START_TIME = "pause_start_time"
    }
}
