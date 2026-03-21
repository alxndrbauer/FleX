package com.flex.wear

import androidx.wear.tiles.TileService
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.WearableListenerService

class WearListenerService : WearableListenerService() {

    override fun onDataChanged(events: DataEventBuffer) {
        val hasFlexUpdate = events.any { it.dataItem.matchesFlexPath() }
        if (hasFlexUpdate) {
            TileService.getUpdater(this).requestUpdate(FlexTileService::class.java)
        }
        events.release()
    }
}
