package com.flex.wear

import androidx.wear.tiles.TileService
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.WearableListenerService

class WearListenerService : WearableListenerService() {

    override fun onDataChanged(events: DataEventBuffer) {
        val hasFlexUpdate = events.any { it.dataItem.matchesFlexPath() }
        if (hasFlexUpdate) {
            val updater = TileService.getUpdater(this)
            updater.requestUpdate(FlexTileService::class.java)
            updater.requestUpdate(FlextimeTileService::class.java)
            updater.requestUpdate(QuotaTileService::class.java)
        }
        events.release()
    }
}
