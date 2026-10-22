package com.flex.widget

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class GlanceWidgetUpdater @Inject constructor(
    @param:ApplicationContext private val context: Context
) : WidgetUpdater {
    override suspend fun updateAll() = FlexWidgetUpdater.updateAll(context)
}
