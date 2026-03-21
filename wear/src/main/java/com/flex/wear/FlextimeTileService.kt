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
        val flextimeColor = if (status.flextimeMinutes >= 0) 0xFF4CAF50.toInt() else 0xFFE53935.toInt()
        val overtimeColor = if (status.overtimeMinutes >= 0) 0xFF4CAF50.toInt() else 0xFFE53935.toInt()

        val layout = LayoutElementBuilders.Column.Builder()
            .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
            .addContent(label("Flexzeit", 0xFFAAAAAA.toInt()))
            .addContent(spacer(2f))
            .addContent(bigValue(status.flextimeFormatted, flextimeColor))
            .addContent(spacer(8f))
            .addContent(label("Überstunden", 0xFFAAAAAA.toInt()))
            .addContent(spacer(2f))
            .addContent(bigValue(status.overtimeFormatted, overtimeColor))
            .build()

        return TileBuilders.Tile.Builder()
            .setResourcesVersion("1")
            .setFreshnessIntervalMillis(300_000L) // 5 min
            .setTimeline(
                TimelineBuilders.Timeline.Builder()
                    .addTimelineEntry(
                        TimelineBuilders.TimelineEntry.Builder()
                            .setLayout(
                                LayoutElementBuilders.Layout.Builder()
                                    .setRoot(layout)
                                    .build()
                            ).build()
                    ).build()
            ).build()
    }

    private fun label(text: String, color: Int) =
        LayoutElementBuilders.Text.Builder()
            .setText(text)
            .setFontStyle(
                LayoutElementBuilders.FontStyle.Builder()
                    .setSize(sp(11f))
                    .setColor(argb(color))
                    .build()
            ).build()

    private fun bigValue(text: String, color: Int) =
        LayoutElementBuilders.Text.Builder()
            .setText(text)
            .setFontStyle(
                LayoutElementBuilders.FontStyle.Builder()
                    .setSize(sp(26f))
                    .setWeight(FONT_WEIGHT_BOLD)
                    .setColor(argb(color))
                    .build()
            ).build()

    private fun spacer(height: Float) =
        LayoutElementBuilders.Spacer.Builder().setHeight(dp(height)).build()
}
