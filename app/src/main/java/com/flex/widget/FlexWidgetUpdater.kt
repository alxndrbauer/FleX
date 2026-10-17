package com.flex.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager

object FlexWidgetUpdater {
    suspend fun updateAll(context: Context) {
        try {
            val manager = GlanceAppWidgetManager(context)
            
            val flexWidget = FlexWidget()
            manager.getGlanceIds(FlexWidget::class.java).forEach { glanceId ->
                try {
                    flexWidget.update(context, glanceId)
                } catch (_: Exception) {}
            }

            val quickActionsWidget = FlexQuickActionsWidget()
            manager.getGlanceIds(FlexQuickActionsWidget::class.java).forEach { glanceId ->
                try {
                    quickActionsWidget.update(context, glanceId)
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {
            // Ignoriere Widget-Update-Fehler, z.B. wenn Widgets noch nicht initialisiert sind
        }
    }
}
