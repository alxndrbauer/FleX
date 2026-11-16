package com.flex.data.local

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BetaPreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("update_prefs", Context.MODE_PRIVATE)

    private val _betaUpdatesEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_BETA_UPDATES_ENABLED, false)
    )
    val betaUpdatesEnabledFlow: StateFlow<Boolean> = _betaUpdatesEnabled.asStateFlow()

    fun setBetaUpdatesEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BETA_UPDATES_ENABLED, enabled).apply()
        _betaUpdatesEnabled.value = enabled
    }

    companion object {
        private const val KEY_BETA_UPDATES_ENABLED = "beta_updates_enabled"
    }
}
