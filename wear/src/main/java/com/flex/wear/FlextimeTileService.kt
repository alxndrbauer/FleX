package com.flex.wear

import android.net.Uri
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material3.Typography
import androidx.wear.protolayout.material3.materialScope
import androidx.wear.protolayout.material3.primaryLayout
import androidx.wear.protolayout.material3.text
import androidx.wear.protolayout.types.argb
import androidx.wear.protolayout.types.layoutString
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.android.gms.wearable.Wearable
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.tasks.await

class FlextimeTileService : TileService() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest
    ): ListenableFuture<TileBuilders.Tile> = scope.future {
        val status = fetchWearStatus()
        val flextimeColor = (if (status.flextimeMinutes >= 0) 0xFF34A853.toInt() else 0xFFE53935.toInt()).argb
        val overtimeColor = (if (status.overtimeMinutes >= 0) 0xFF34A853.toInt() else 0xFFE53935.toInt()).argb
        val muted = 0xFF9E9FA8.toInt().argb

        val layout = materialScope(
            context = this@FlextimeTileService,
            deviceConfiguration = requestParams.deviceConfiguration
        ) {
            primaryLayout(
                titleSlot = {
                    text(
                        text = "Flexzeit".layoutString,
                        typography = Typography.LABEL_SMALL,
                        color = muted
                    )
                },
                mainSlot = {
                    text(
                        text = status.flextimeFormatted.layoutString,
                        typography = Typography.NUMERAL_LARGE,
                        color = flextimeColor
                    )
                },
                labelForBottomSlot = {
                    text(
                        text = "Überstunden".layoutString,
                        typography = Typography.LABEL_SMALL,
                        color = muted
                    )
                },
                bottomSlot = {
                    text(
                        text = status.overtimeFormatted.layoutString,
                        typography = Typography.NUMERAL_SMALL,
                        color = overtimeColor
                    )
                }
            )
        }

        TileBuilders.Tile.Builder()
            .setResourcesVersion("1")
            .setFreshnessIntervalMillis(300_000L)
            .setTileTimeline(
                TimelineBuilders.Timeline.Builder()
                    .addTimelineEntry(
                        TimelineBuilders.TimelineEntry.Builder()
                            .setLayout(
                                LayoutElementBuilders.Layout.Builder()
                                    .setRoot(layout).build()
                            ).build()
                    ).build()
            ).build()
    }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest
    ): ListenableFuture<ResourceBuilders.Resources> = scope.future {
        ResourceBuilders.Resources.Builder().setVersion(requestParams.version).build()
    }

    private suspend fun fetchWearStatus(): WearStatus {
        return try {
            val items = Wearable.getDataClient(this)
                .getDataItems(Uri.parse("wear://*${WearContract.DATA_PATH}"))
                .await()
            var result = WearStatus()
            items.forEach { item -> if (item.matchesFlexPath()) result = item.toWearStatus() }
            items.release()
            result
        } catch (_: Exception) { WearStatus() }
    }
}
