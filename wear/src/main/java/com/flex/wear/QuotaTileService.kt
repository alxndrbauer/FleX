package com.flex.wear

import android.net.Uri
import androidx.wear.tiles.ColorBuilders.argb
import androidx.wear.tiles.DimensionBuilders.dp
import androidx.wear.tiles.DimensionBuilders.sp
import androidx.wear.tiles.LayoutElementBuilders
import androidx.wear.tiles.LayoutElementBuilders.FONT_WEIGHT_BOLD
import androidx.wear.tiles.LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER
import androidx.wear.tiles.ModifiersBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.ResourceBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import androidx.wear.tiles.TimelineBuilders
import androidx.wear.tiles.material.CircularProgressIndicator
import androidx.wear.tiles.material.ProgressIndicatorColors
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
        buildTile(fetchWearStatus())
    }

    override fun onResourcesRequest(
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

    private fun buildTile(status: WearStatus): TileBuilders.Tile {
        val quotaColor = if (status.quotaMet) 0xFF4CAF50.toInt() else 0xFFFFC107.toInt()
        val trackColor = 0xFF333333.toInt()
        val progress = (status.officePercent / 100.0).coerceIn(0.0, 1.0).toFloat()

        val progressIndicator = CircularProgressIndicator.Builder()
            .setProgress(progress)
            .setCircularProgressIndicatorColors(
                ProgressIndicatorColors(argb(quotaColor), argb(trackColor))
            )
            .build()

        val centerContent = LayoutElementBuilders.Column.Builder()
            .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
            .addContent(
                LayoutElementBuilders.Text.Builder()
                    .setText(status.officePctFormatted)
                    .setFontStyle(
                        LayoutElementBuilders.FontStyle.Builder()
                            .setSize(sp(20f))
                            .setWeight(FONT_WEIGHT_BOLD)
                            .setColor(argb(quotaColor))
                            .build()
                    ).build()
            )
            .addContent(
                LayoutElementBuilders.Spacer.Builder().setHeight(dp(2f)).build()
            )
            .addContent(
                LayoutElementBuilders.Text.Builder()
                    .setText("${status.officeDays}d Büro")
                    .setFontStyle(
                        LayoutElementBuilders.FontStyle.Builder()
                            .setSize(sp(11f))
                            .setColor(argb(0xFFAAAAAA.toInt()))
                            .build()
                    ).build()
            )
            .addContent(
                LayoutElementBuilders.Spacer.Builder().setHeight(dp(2f)).build()
            )
            .addContent(
                LayoutElementBuilders.Text.Builder()
                    .setText(
                        if (status.quotaMet) "erfüllt ✓"
                        else "noch ${status.requiredOfficeDays}d"
                    )
                    .setFontStyle(
                        LayoutElementBuilders.FontStyle.Builder()
                            .setSize(sp(11f))
                            .setColor(argb(quotaColor))
                            .build()
                    ).build()
            )
            .build()

        val arc = androidx.wear.tiles.LayoutElementBuilders.Arc.Builder()
            .addContent(
                androidx.wear.tiles.LayoutElementBuilders.ArcAdapter.Builder()
                    .setContent(progressIndicator)
                    .build()
            )
            .build()

        val root = LayoutElementBuilders.Box.Builder()
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
            .addContent(arc)
            .addContent(centerContent)
            .build()

        return TileBuilders.Tile.Builder()
            .setResourcesVersion("1")
            .setFreshnessIntervalMillis(300_000L)
            .setTimeline(
                TimelineBuilders.Timeline.Builder()
                    .addTimelineEntry(
                        TimelineBuilders.TimelineEntry.Builder()
                            .setLayout(
                                LayoutElementBuilders.Layout.Builder()
                                    .setRoot(root)
                                    .build()
                            ).build()
                    ).build()
            ).build()
    }
}
