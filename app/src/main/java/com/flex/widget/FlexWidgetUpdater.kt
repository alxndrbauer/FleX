package com.flex.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager

object FlexWidgetUpdater {
    suspend fun updateAll(context: Context) {
        try {
            val manager = GlanceAppWidgetManager(context)
            val glanceIds = manager.getGlanceIds(FlexWidget::class.java)
            val widget = FlexWidget()
            glanceIds.forEach { glanceId ->
                widget.update(context, glanceId)
            }
        } catch (_: Exception) {
            // Ignoriere Widget-Update-Fehler, z.B. wenn Widgets noch nicht initialisiert sind
        }
    }
}
