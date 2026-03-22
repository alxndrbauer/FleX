package com.flex.wear

import android.net.Uri
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material3.ButtonColors
import androidx.wear.protolayout.material3.Typography
import androidx.wear.protolayout.material3.materialScope
import androidx.wear.protolayout.material3.primaryLayout
import androidx.wear.protolayout.material3.text
import androidx.wear.protolayout.material3.textEdgeButton
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
        val actionPath = if (status.isClockRunning) WearContract.MSG_CLOCK_OUT else WearContract.MSG_CLOCK_IN
        val buttonLabel = if (status.isClockRunning) "Ausstempeln" else "Einstempeln"
        val statusLabel = if (status.isClockRunning) "läuft" else "gestoppt"

        val green = 0xFF34A853.toInt().argb
        val red = 0xFFE53935.toInt().argb
        val white = 0xFFFFFFFF.toInt().argb
        val muted = 0xFF9E9FA8.toInt().argb
        val light = 0xFFE0E0E0.toInt().argb

        val statusColor = if (status.isClockRunning) green else muted
        val chipContainerColor = if (status.isClockRunning) red else green

        val clickable = ModifiersBuilders.Clickable.Builder()
            .setId("clock_action")
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

        val layout = materialScope(
            context = this@FlexTileService,
            deviceConfiguration = requestParams.deviceConfiguration
        ) {
            primaryLayout(
                titleSlot = {
                    text(
                        text = status.todayFormatted.layoutString,
                        typography = Typography.NUMERAL_LARGE,
                        color = light
                    )
                },
                mainSlot = {
                    text(
                        text = statusLabel.layoutString,
                        typography = Typography.BODY_SMALL,
                        color = statusColor
                    )
                },
                bottomSlot = {
                    textEdgeButton(
                        onClick = clickable,
                        colors = ButtonColors(
                            containerColor = chipContainerColor,
                            iconColor = white,
                            labelColor = white,
                            secondaryLabelColor = white
                        ),
                        labelContent = {
                            text(
                                text = buttonLabel.layoutString,
                                typography = Typography.LABEL_SMALL
                            )
                        }
                    )
                }
            )
        }

        TileBuilders.Tile.Builder()
            .setResourcesVersion("1")
            .setFreshnessIntervalMillis(60_000L)
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
            items.forEach { item ->
                if (item.matchesFlexPath()) result = item.toWearStatus()
            }
            items.release()
            result
        } catch (_: Exception) {
            WearStatus()
        }
    }
}
