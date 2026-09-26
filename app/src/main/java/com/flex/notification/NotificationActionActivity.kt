package com.flex.notification

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle

class NotificationActionActivity : Activity() {

    companion object {
        const val ACTION_PAUSE = "com.flex.notification.ACTION_PAUSE"
        const val ACTION_CLOCK_OUT = "com.flex.notification.ACTION_CLOCK_OUT"
        const val ACTION_VIEW_EXPORT = "com.flex.notification.ACTION_VIEW_EXPORT"
        const val ACTION_SHARE_EXPORT = "com.flex.notification.ACTION_SHARE_EXPORT"
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
            ACTION_VIEW_EXPORT -> {
                val uri = intent?.data
                val type = intent?.type
                if (uri != null) {
                    val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, type)
                        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(viewIntent)
                }
            }
            ACTION_SHARE_EXPORT -> {
                val uri = intent?.data
                val type = intent?.type
                if (uri != null) {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        this.type = type
                        putExtra(Intent.EXTRA_STREAM, uri)
                        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                    }
                    val chooserIntent = Intent.createChooser(shareIntent, "Export teilen").apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(chooserIntent)
                }
            }
        }
        finish()
        disableActivityTransitions()
    }

    private fun disableActivityTransitions() {
        if (Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        }
    }
}
