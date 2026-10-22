package com.flex.widget

/**
 * Abstraction over the widget refresh so ViewModels can be unit-tested
 * without touching Glance / DataStore.
 */
interface WidgetUpdater {
    suspend fun updateAll()
}
