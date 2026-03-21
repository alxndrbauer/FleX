package com.flex.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class TileActionActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val action = intent.getStringExtra("action")

        if (action == null) {
            finish()
            return
        }

        lifecycleScope.launch {
            try {
                val nodes: List<Node> = Wearable.getNodeClient(this@TileActionActivity)
                    .connectedNodes
                    .await()

                for (node in nodes) {
                    Wearable.getMessageClient(this@TileActionActivity)
                        .sendMessage(node.id, action, null)
                        .await()
                }
            } catch (_: Exception) {
                // Silently ignore send failures
            } finally {
                finish()
            }
        }
    }
}
