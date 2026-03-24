package com.flex.wifi

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WifiPreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("wifi_prefs", Context.MODE_PRIVATE)

    var wasConnectedToTarget: Boolean
        get() = prefs.getBoolean("was_connected_to_target", false)
        set(value) { prefs.edit().putBoolean("was_connected_to_target", value).apply() }
}
