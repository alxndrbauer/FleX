package com.flex.data.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import android.widget.Toast

class PackageInstallerStatusReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmationIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }

                if (confirmationIntent != null) {
                    confirmationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(confirmationIntent)
                } else {
                    Log.e(TAG, "STATUS_PENDING_USER_ACTION empfangen, aber EXTRA_INTENT ist null")
                }
            }
            PackageInstaller.STATUS_SUCCESS -> {
                Log.i(TAG, "Update erfolgreich installiert")
            }
            else -> {
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                Log.e(TAG, "PackageInstaller Fehler (Status $status): $message")
                val userMessage = when (status) {
                    PackageInstaller.STATUS_FAILURE_CONFLICT -> "Update fehlgeschlagen: Die Signatur der App stimmt nicht mit der installierten Version überein."
                    PackageInstaller.STATUS_FAILURE_STORAGE -> "Update fehlgeschlagen: Nicht genügend freier Speicherplatz."
                    PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> "Update fehlgeschlagen: Die Version ist mit diesem Gerät nicht kompatibel."
                    PackageInstaller.STATUS_FAILURE_ABORTED -> null // Vom Nutzer abgebrochen
                    else -> message?.let { "Update fehlgeschlagen: $it" } ?: "Update fehlgeschlagen (Code $status)"
                }
                userMessage?.let { showToast(context, it) }
            }
        }
    }

    private fun showToast(context: Context, message: String) {
        runCatching {
            Toast.makeText(context, message, Toast.LENGTH_LONG)?.show()
        }
    }

    companion object {
        private const val TAG = "PackageInstallerReceiver"
    }
}
