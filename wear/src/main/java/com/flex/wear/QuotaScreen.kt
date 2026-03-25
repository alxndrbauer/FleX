package com.flex.wear

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await

@Composable
fun rememberWearStatus(): State<WearStatus> {
    val context = LocalContext.current
    val status = remember { mutableStateOf(WearStatus()) }

    LaunchedEffect(Unit) {
        try {
            val items = Wearable.getDataClient(context)
                .getDataItems(Uri.parse("wear://*${WearContract.DATA_PATH}"))
                .await()
            items.forEach { item ->
                if (item.matchesFlexPath()) status.value = item.toWearStatus()
            }
            items.release()
        } catch (_: Exception) {}

        val listener = DataClient.OnDataChangedListener { events: DataEventBuffer ->
            events.filter {
                it.type == DataEvent.TYPE_CHANGED && it.dataItem.matchesFlexPath()
            }.forEach { event ->
                status.value = event.dataItem.toWearStatus()
            }
            events.release()
        }
        Wearable.getDataClient(context).addListener(listener)
    }

    return status
}

@Composable
fun QuotaScreen(
    status: WearStatus,
    onClockIn: () -> Unit,
    onClockOut: () -> Unit
) {
    val quotaColor = if (status.quotaMet) {
        MaterialTheme.colorScheme.secondary
    } else {
        MaterialTheme.colorScheme.tertiary
    }
    val chipContainerColor = if (status.isClockRunning) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.secondary
    }
    val chipContentColor = if (status.isClockRunning) {
        MaterialTheme.colorScheme.onError
    } else {
        MaterialTheme.colorScheme.onSecondary
    }

    val listState = rememberScalingLazyListState()

    ScreenScaffold(
        scrollState = listState,
        edgeButton = {
            EdgeButton(
                onClick = if (status.isClockRunning) onClockOut else onClockIn,
                buttonSize = EdgeButtonSize.Large,
                colors = ButtonDefaults.buttonColors(
                    containerColor = chipContainerColor,
                    contentColor = chipContentColor
                )
            ) {
                Text(
                    text = if (status.isClockRunning) "Ausstempeln" else "Einstempeln",
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center
                )
            }
        }
    ) { contentPadding ->
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = contentPadding,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
        ) {
            item {
                Text(
                    text = status.todayFormatted,
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                Text(
                    text = if (status.isClockRunning) "läuft" else "gestoppt",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (status.isClockRunning) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                Text(
                    text = status.officePctFormatted,
                    style = MaterialTheme.typography.bodyLarge,
                    color = quotaColor,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                Text(
                    text = "${status.officeDays}d / ${status.requiredOfficeDays}d",
                    style = MaterialTheme.typography.labelSmall,
                    color = quotaColor,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                Text(
                    text = if (status.quotaMet) "Quote erfüllt ✓" else "Quote offen",
                    style = MaterialTheme.typography.labelSmall,
                    color = quotaColor,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
