package com.flex.wear

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
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
        // Load current value from DataClient
        try {
            val items = Wearable.getDataClient(context)
                .getDataItems(Uri.parse("wear://*${WearContract.DATA_PATH}"))
                .await()
            items.forEach { item ->
                if (item.matchesFlexPath()) status.value = item.toWearStatus()
            }
            items.release()
        } catch (_: Exception) {}

        // Listen for updates
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
    val green = Color(0xFF4CAF50)
    val amber = Color(0xFFFFC107)
    val quotaColor = if (status.quotaMet) green else amber

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(12.dp)
        ) {
            // Today's work time
            Text(
                text = status.todayFormatted,
                fontSize = 28.sp,
                color = MaterialTheme.colors.onSurface,
                textAlign = TextAlign.Center
            )

            Text(
                text = if (status.isClockRunning) "läuft" else "gestoppt",
                fontSize = 11.sp,
                color = if (status.isClockRunning) green else MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Quota info
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = status.officePctFormatted,
                    fontSize = 14.sp,
                    color = quotaColor
                )
                Text(
                    text = "  •  ${status.officeDays}d",
                    fontSize = 14.sp,
                    color = quotaColor
                )
            }

            Text(
                text = if (status.quotaMet) "Quote erfüllt" else "Quote offen",
                fontSize = 11.sp,
                color = quotaColor,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Clock button
            Button(
                onClick = if (status.isClockRunning) onClockOut else onClockIn,
                modifier = Modifier.fillMaxWidth(0.8f),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = if (status.isClockRunning) Color(0xFFE53935) else green
                )
            ) {
                Text(
                    text = if (status.isClockRunning) "Ausstempeln" else "Einstempeln",
                    fontSize = 12.sp
                )
            }
        }
    }
}
