package com.flex.wifi

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import com.flex.data.local.GeofencePreferences
import com.flex.domain.usecase.AutoClockInUseCase
import com.flex.domain.usecase.AutoClockOutUseCase
import com.flex.geofence.GeofenceNotificationHelper
import com.flex.notification.BreakWarningScheduler
import com.flex.wearable.WearSyncHelper
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class WifiMonitorTest {

    private val testDispatcher = StandardTestDispatcher()

    @Mock private lateinit var context: Context
    @Mock private lateinit var connectivityManager: ConnectivityManager
    @Mock private lateinit var autoClockIn: AutoClockInUseCase
    @Mock private lateinit var autoClockOut: AutoClockOutUseCase
    @Mock private lateinit var notificationHelper: GeofenceNotificationHelper
    @Mock private lateinit var wifiPreferences: WifiPreferences
    @Mock private lateinit var geofencePreferences: GeofencePreferences
    @Mock private lateinit var wearSyncHelper: WearSyncHelper
    @Mock private lateinit var breakWarningScheduler: BreakWarningScheduler
    @Mock private lateinit var mockNetwork: Network

    private lateinit var wifiMonitor: WifiMonitor

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        whenever(context.getSystemService(Context.CONNECTIVITY_SERVICE)).thenReturn(connectivityManager)
        wifiMonitor = WifiMonitor(
            context, autoClockIn, autoClockOut, notificationHelper,
            wifiPreferences, geofencePreferences, wearSyncHelper, breakWarningScheduler,
            testDispatcher
        )
    }

    // --- onLost delay tests ---

    @Test
    fun `onLost when wasConnectedToTarget does not clock out immediately`() = runTest(testDispatcher) {
        whenever(wifiPreferences.wasConnectedToTarget).thenReturn(true)
        whenever(autoClockOut()).thenReturn(false) // stub inside coroutine – prevents NPE on time advance
        val callback = wifiMonitor.buildNetworkCallback("MySSID")

        callback.onLost(mockNetwork)

        // verify before runTest auto-advances the clock at cleanup
        verify(autoClockOut, never()).invoke()
    }

    @Test
    fun `onLost after 30 seconds calls autoClockOut`() = runTest(testDispatcher) {
        whenever(wifiPreferences.wasConnectedToTarget).thenReturn(true)
        whenever(autoClockOut()).thenReturn(true)
        val callback = wifiMonitor.buildNetworkCallback("MySSID")

        callback.onLost(mockNetwork)
        testDispatcher.scheduler.advanceTimeBy(30_001)

        verify(autoClockOut).invoke()
    }

    @Test
    fun `onLost when wasConnectedToTarget is false does not start clock out job`() = runTest(testDispatcher) {
        whenever(wifiPreferences.wasConnectedToTarget).thenReturn(false)
        val callback = wifiMonitor.buildNetworkCallback("MySSID")

        callback.onLost(mockNetwork)
        testDispatcher.scheduler.advanceTimeBy(30_001)

        verify(autoClockOut, never()).invoke()
    }

    @Test
    fun `unregister cancels pending clock out job`() = runTest(testDispatcher) {
        whenever(wifiPreferences.wasConnectedToTarget).thenReturn(true)
        whenever(autoClockOut()).thenReturn(false)
        val callback = wifiMonitor.buildNetworkCallback("MySSID")

        callback.onLost(mockNetwork)
        wifiMonitor.unregister()
        testDispatcher.scheduler.advanceTimeBy(30_001)

        verify(autoClockOut, never()).invoke()
    }

    @Test
    fun `second onLost resets 30s timer so clock out does not fire after original 30s`() = runTest(testDispatcher) {
        whenever(wifiPreferences.wasConnectedToTarget).thenReturn(true)
        whenever(autoClockOut()).thenReturn(false)
        val callback = wifiMonitor.buildNetworkCallback("MySSID")

        callback.onLost(mockNetwork)
        testDispatcher.scheduler.advanceTimeBy(15_000)
        // second onLost resets the timer
        callback.onLost(mockNetwork)
        testDispatcher.scheduler.advanceTimeBy(15_001)

        // only 15s passed since second onLost – should NOT have clocked out yet
        verify(autoClockOut, never()).invoke()
    }

    @Test
    fun `second onLost clocks out after 30s from second call`() = runTest(testDispatcher) {
        whenever(wifiPreferences.wasConnectedToTarget).thenReturn(true)
        whenever(autoClockOut()).thenReturn(true)
        val callback = wifiMonitor.buildNetworkCallback("MySSID")

        callback.onLost(mockNetwork)
        testDispatcher.scheduler.advanceTimeBy(15_000)
        callback.onLost(mockNetwork)
        testDispatcher.scheduler.advanceTimeBy(30_001)

        verify(autoClockOut).invoke()
    }
}
