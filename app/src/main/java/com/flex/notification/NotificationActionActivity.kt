package com.flex.notification

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle

class NotificationActionActivity : Activity() {

    companion object {
        const val ACTION_PAUSE = "com.flex.notification.ACTION_PAUSE"
        const val ACTION_CLOCK_OUT = "com.flex.notification.ACTION_CLOCK_OUT"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        disableActivityTransitions()

        when (intent?.action) {
            ACTION_PAUSE -> {
                sendBroadcast(Intent(this, PauseReceiver::class.java).apply {
                    action = PauseReceiver.ACTION_PAUSE
                })
            }
            ACTION_CLOCK_OUT -> {
                sendBroadcast(Intent(this, ClockOutReceiver::class.java).apply {
                    action = ClockOutReceiver.ACTION_CLOCK_OUT
                })
            }
        }
        finish()
        disableActivityTransitions()
    }

    private fun disableActivityTransitions() {
        if (Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }
}
