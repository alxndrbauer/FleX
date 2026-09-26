package com.flex.data.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class PackageInstallerStatusReceiverTest {

    private lateinit var context: Context
    private lateinit var receiver: PackageInstallerStatusReceiver

    @BeforeEach
    fun setUp() {
        context = mock()
        receiver = PackageInstallerStatusReceiver()
    }

    @Test
    fun `onReceive with STATUS_PENDING_USER_ACTION starts confirmation activity with NEW_TASK flag`() {
        val confirmationIntent: Intent = mock()
        val intent: Intent = mock()
        whenever(intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1))
            .thenReturn(PackageInstaller.STATUS_PENDING_USER_ACTION)
        @Suppress("DEPRECATION")
        whenever(intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT))
            .thenReturn(confirmationIntent)
        whenever(intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java))
            .thenReturn(confirmationIntent)

        receiver.onReceive(context, intent)

        verify(confirmationIntent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        verify(context).startActivity(confirmationIntent)
    }

    @Test
    fun `onReceive with STATUS_PENDING_USER_ACTION and null confirmation intent does not start activity`() {
        val intent: Intent = mock()
        whenever(intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1))
            .thenReturn(PackageInstaller.STATUS_PENDING_USER_ACTION)
        @Suppress("DEPRECATION")
        whenever(intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT))
            .thenReturn(null)
        whenever(intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java))
            .thenReturn(null)

        receiver.onReceive(context, intent)

        verify(context, never()).startActivity(any())
    }

    @Test
    fun `onReceive with STATUS_SUCCESS does not start activity`() {
        val intent: Intent = mock()
        whenever(intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1))
            .thenReturn(PackageInstaller.STATUS_SUCCESS)

        receiver.onReceive(context, intent)

        verify(context, never()).startActivity(any())
    }

    @Test
    fun `onReceive with STATUS_FAILURE_CONFLICT does not start activity and does not throw`() {
        val intent: Intent = mock()
        whenever(intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1))
            .thenReturn(PackageInstaller.STATUS_FAILURE_CONFLICT)
        whenever(intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE))
            .thenReturn("Signatures do not match")

        receiver.onReceive(context, intent)

        verify(context, never()).startActivity(any())
    }

    @Test
    fun `onReceive with STATUS_FAILURE_ABORTED does not start activity`() {
        val intent: Intent = mock()
        whenever(intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1))
            .thenReturn(PackageInstaller.STATUS_FAILURE_ABORTED)

        receiver.onReceive(context, intent)

        verify(context, never()).startActivity(any())
    }
}
