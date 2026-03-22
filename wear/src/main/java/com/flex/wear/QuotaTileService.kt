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

class QuotaTileService : TileService() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest
    ): ListenableFuture<TileBuilders.Tile> = scope.future {
        val status = fetchWearStatus()
        val green = 0xFF34A853.toInt().argb
        val amber = 0xFFFFC107.toInt().argb
        val muted = 0xFF9E9FA8.toInt().argb
        val quotaColor = if (status.quotaMet) green else amber
        val remainingDays = (status.requiredOfficeDays - status.officeDays).coerceAtLeast(0)
        val statusText = if (status.quotaMet) "Quote erfüllt ✓" else "$remainingDays Tage fehlen"

        val layout = materialScope(
            context = this@QuotaTileService,
            deviceConfiguration = requestParams.deviceConfiguration
        ) {
            primaryLayout(
                titleSlot = {
                    text(
                        text = "Büroquote".layoutString,
                        typography = Typography.LABEL_SMALL,
                        color = muted
                    )
                },
                mainSlot = {
                    text(
                        text = status.officePctFormatted.layoutString,
                        typography = Typography.NUMERAL_LARGE,
                        color = quotaColor
                    )
                },
                labelForBottomSlot = {
                    text(
                        text = "${status.officeDays}d / ${status.requiredOfficeDays}d".layoutString,
                        typography = Typography.LABEL_SMALL,
                        color = quotaColor
                    )
                },
                bottomSlot = {
                    text(
                        text = statusText.layoutString,
                        typography = Typography.BODY_SMALL,
                        color = quotaColor
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
