package com.flex.automation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.GeofencingRequest
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AutomationRestoreReceiver : BroadcastReceiver() {

    @Inject lateinit var automationRestorer: AutomationRestorer

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }

        val source = when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> "Neustart"
            Intent.ACTION_MY_PACKAGE_REPLACED -> "App-Update"
            else -> "Wiederherstellung"
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // If the device is already inside the fence (e.g. update happened at the
                // office), synthesize an ENTER so the day is not silently missed.
                automationRestorer.restore(
                    initialGeofenceTrigger = GeofencingRequest.INITIAL_TRIGGER_ENTER,
                    source = source
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to restore automation: ${e.message}", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        const val TAG = "AutomationRestore"
    }
}
