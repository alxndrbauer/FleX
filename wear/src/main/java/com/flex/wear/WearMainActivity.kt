package com.flex.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.wear.compose.foundation.pager.HorizontalPager
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.HorizontalPagerScaffold
import androidx.wear.compose.material3.TimeText
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class WearMainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val status = rememberWearStatus()
            WearTheme {
                WearApp(
                    status = status.value,
                    onClockIn = { sendMessage(WearContract.MSG_CLOCK_IN) },
                    onClockOut = { sendMessage(WearContract.MSG_CLOCK_OUT) }
                )
            }
        }
        sendMessage(WearContract.MSG_REQUEST_STATUS)
    }

    private fun sendMessage(path: String) {
        lifecycleScope.launch {
            try {
                val nodes: List<Node> = Wearable.getNodeClient(this@WearMainActivity)
                    .connectedNodes.await()
                nodes.forEach { node ->
                    Wearable.getMessageClient(this@WearMainActivity)
                        .sendMessage(node.id, path, null).await()
                }
            } catch (_: Exception) {}
        }
    }
}

@Composable
private fun WearApp(
    status: WearStatus,
    onClockIn: () -> Unit,
    onClockOut: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { 2 })

    AppScaffold(timeText = { TimeText() }) {
        HorizontalPagerScaffold(pagerState = pagerState) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (page) {
                    0 -> QuotaScreen(status = status, onClockIn = onClockIn, onClockOut = onClockOut)
                    1 -> FlextimeScreen(status = status)
                }
            }
        }
    }
}
