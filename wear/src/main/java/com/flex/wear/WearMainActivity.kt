package com.flex.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
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
                QuotaScreen(
                    status = status.value,
                    onClockIn = { sendMessage(WearContract.MSG_CLOCK_IN) },
                    onClockOut = { sendMessage(WearContract.MSG_CLOCK_OUT) }
                )
            }
        }
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
