package com.flex.data.local

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WhatsNewPreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("whats_new_prefs", Context.MODE_PRIVATE)

    fun getLastSeenVersionCode(): Int =
        prefs.getInt(KEY_LAST_SEEN_VERSION_CODE, 0)

    fun setLastSeenVersionCode(versionCode: Int) {
        prefs.edit().putInt(KEY_LAST_SEEN_VERSION_CODE, versionCode).apply()
    }

    companion object {
        private const val KEY_LAST_SEEN_VERSION_CODE = "last_seen_version_code"
    }
}
