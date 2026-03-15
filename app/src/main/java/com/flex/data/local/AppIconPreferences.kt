package com.flex.data.local

import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import com.flex.domain.model.AppIconVariant
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppIconPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("app_icon_prefs", Context.MODE_PRIVATE)

    private val currentVariant: AppIconVariant = runCatching {
        AppIconVariant.valueOf(
            prefs.getString(KEY_VARIANT, AppIconVariant.CLASSIC.name) ?: AppIconVariant.CLASSIC.name
        )
    }.getOrDefault(AppIconVariant.CLASSIC)

    private val _variant = MutableStateFlow(currentVariant)
    val variantFlow: StateFlow<AppIconVariant> = _variant.asStateFlow()

    fun setVariant(variant: AppIconVariant) {
        prefs.edit().putString(KEY_VARIANT, variant.name).apply()
        _variant.value = variant
        applyVariant(context, variant)
    }

    companion object {
        private const val KEY_VARIANT = "app_icon_variant"

        /** Called early in Application.onCreate() — before Hilt — to fix stale PackageManager state. */
        fun fixOnStartup(context: Context) {
            val prefs = context.getSharedPreferences("app_icon_prefs", Context.MODE_PRIVATE)
            val variant = runCatching {
                AppIconVariant.valueOf(
                    prefs.getString(KEY_VARIANT, AppIconVariant.CLASSIC.name) ?: AppIconVariant.CLASSIC.name
                )
            }.getOrDefault(AppIconVariant.CLASSIC)
            applyVariant(context, variant)
        }

        private fun applyVariant(context: Context, variant: AppIconVariant) {
            val pm = context.packageManager
            val packageName = context.packageName
            AppIconVariant.entries.forEach { v ->
                val state = if (v == variant)
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                else
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                runCatching {
                    pm.setComponentEnabledSetting(
                        ComponentName(packageName, v.fullComponentName(packageName)),
                        state,
                        PackageManager.DONT_KILL_APP
                    )
                }
            }
        }
    }
}
