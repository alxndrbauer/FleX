package com.flex.data.local

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeofencePreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("geofence_prefs", Context.MODE_PRIVATE)

    var lastAutoTimeBlockId: Long
        get() = prefs.getLong("last_auto_block_id", -1L)
        set(value) { prefs.edit().putLong("last_auto_block_id", value).apply() }
}
