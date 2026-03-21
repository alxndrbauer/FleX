package com.flex.wear

import android.net.Uri
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.FONT_WEIGHT_BOLD
import androidx.wear.protolayout.LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.ResourceBuilders
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

class FlexTileService : TileService() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest
    ): ListenableFuture<TileBuilders.Tile> = scope.future {
        val status = fetchWearStatus()
        buildTile(status)
    }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest
    ): ListenableFuture<ResourceBuilders.Resources> = scope.future {
        ResourceBuilders.Resources.Builder()
            .setVersion(requestParams.version)
            .build()
    }

    private suspend fun fetchWearStatus(): WearStatus {
        return try {
            val items = Wearable.getDataClient(this)
                .getDataItems(Uri.parse("wear://*${WearContract.DATA_PATH}"))
                .await()
            var result = WearStatus()
            items.forEach { item ->
                if (item.matchesFlexPath()) result = item.toWearStatus()
            }
            items.release()
            result
        } catch (_: Exception) {
            WearStatus()
        }
    }

    private fun buildTile(status: WearStatus): TileBuilders.Tile {
        val actionPath = if (status.isClockRunning) WearContract.MSG_CLOCK_OUT else WearContract.MSG_CLOCK_IN
        val buttonLabel = if (status.isClockRunning) "Ausstempeln" else "Einstempeln"
        val statusLabel = if (status.isClockRunning) "läuft" else "gestoppt"

        val clickable = ModifiersBuilders.Clickable.Builder()
            .setOnClick(
                ActionBuilders.LaunchAction.Builder()
                    .setAndroidActivity(
                        ActionBuilders.AndroidActivity.Builder()
                            .setPackageName(packageName)
                            .setClassName(TileActionActivity::class.java.name)
                            .addKeyToExtraMapping(
                                "action",
                                ActionBuilders.AndroidStringExtra.Builder()
                                    .setValue(actionPath)
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .build()

        val timeText = LayoutElementBuilders.Text.Builder()
            .setText(status.todayFormatted)
            .setFontStyle(
                LayoutElementBuilders.FontStyle.Builder()
                    .setSize(sp(28f))
                    .setWeight(FONT_WEIGHT_BOLD)
                    .setColor(argb(0xFFFFFFFF.toInt()))
                    .build()
            )
            .build()

        val statusText = LayoutElementBuilders.Text.Builder()
            .setText(statusLabel)
            .setFontStyle(
                LayoutElementBuilders.FontStyle.Builder()
                    .setSize(sp(12f))
                    .setColor(
                        argb(if (status.isClockRunning) 0xFF4CAF50.toInt() else 0x99FFFFFF.toInt())
                    )
                    .build()
            )
            .build()

        val buttonText = LayoutElementBuilders.Text.Builder()
            .setText(buttonLabel)
            .setFontStyle(
                LayoutElementBuilders.FontStyle.Builder()
                    .setSize(sp(13f))
                    .setColor(argb(0xFFFFFFFF.toInt()))
                    .build()
            )
            .build()

        val buttonBgColor = if (status.isClockRunning) 0xFFE53935.toInt() else 0xFF4CAF50.toInt()

        val buttonModifiers = ModifiersBuilders.Modifiers.Builder()
            .setClickable(clickable)
            .setBackground(
                ModifiersBuilders.Background.Builder()
                    .setColor(argb(buttonBgColor))
                    .setCorner(
                        ModifiersBuilders.Corner.Builder()
                            .setRadius(dp(16f))
                            .build()
                    )
                    .build()
            )
            .setPadding(
                ModifiersBuilders.Padding.Builder()
                    .setStart(dp(12f))
                    .setEnd(dp(12f))
                    .setTop(dp(8f))
                    .setBottom(dp(8f))
                    .build()
            )
            .build()

        val button = LayoutElementBuilders.Box.Builder()
            .setModifiers(buttonModifiers)
            .addContent(buttonText)
            .build()

        val layout = LayoutElementBuilders.Column.Builder()
            .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
            .addContent(timeText)
            .addContent(
                LayoutElementBuilders.Spacer.Builder().setHeight(dp(2f)).build()
            )
            .addContent(statusText)
            .addContent(
                LayoutElementBuilders.Spacer.Builder().setHeight(dp(8f)).build()
            )
            .addContent(button)
            .build()

        return TileBuilders.Tile.Builder()
            .setResourcesVersion("1")
            .setFreshnessIntervalMillis(60_000L)
            .setTileTimeline(
                TimelineBuilders.Timeline.Builder()
                    .addTimelineEntry(
                        TimelineBuilders.TimelineEntry.Builder()
                            .setLayout(
                                LayoutElementBuilders.Layout.Builder()
                                    .setRoot(layout)
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .build()
    }
}
