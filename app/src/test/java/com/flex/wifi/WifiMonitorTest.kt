package com.flex.wifi

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import com.flex.data.local.GeofencePreferences
import com.flex.domain.usecase.AutoClockInUseCase
import com.flex.domain.usecase.AutoClockOutUseCase
import com.flex.domain.usecase.IsAutoClockedInUseCase
import com.flex.geofence.GeofenceNotificationHelper
import com.flex.notification.BreakWarningScheduler
import com.flex.support.fixedClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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
    @Mock private lateinit var isAutoClockedIn: IsAutoClockedInUseCase
    @Mock private lateinit var notificationHelper: GeofenceNotificationHelper
    @Mock private lateinit var wifiPreferences: WifiPreferences
    @Mock private lateinit var geofencePreferences: GeofencePreferences
    @Mock private lateinit var breakWarningScheduler: BreakWarningScheduler
    @Mock private lateinit var connectionInfo: WifiConnectionInfo
    @Mock private lateinit var scheduler: WifiAutoStampScheduler
    @Mock private lateinit var mockNetwork: Network

    private lateinit var wifiMonitor: WifiMonitor

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        whenever(context.getSystemService(Context.CONNECTIVITY_SERVICE)).thenReturn(connectivityManager)
        wifiMonitor = WifiMonitor(
            context = context,
            autoClockIn = autoClockIn,
            autoClockOut = autoClockOut,
            isAutoClockedIn = isAutoClockedIn,
            notificationHelper = notificationHelper,
            wifiPreferences = wifiPreferences,
            geofencePreferences = geofencePreferences,
            breakWarningScheduler = breakWarningScheduler,
            connectionInfo = connectionInfo,
            scheduler = scheduler,
            clock = fixedClock(),
            dispatcher = testDispatcher
        )
    }

    // --- onLost delay tests ---

    @Test
    fun `onLost when wasConnectedToTarget does not clock out immediately`() = runTest(testDispatcher) {
        whenever(wifiPreferences.wasConnectedToTarget).thenReturn(true)
        whenever(autoClockOut()).thenReturn(false)
        val callback = wifiMonitor.buildNetworkCallback("MySSID")

        callback.onLost(mockNetwork)

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
        callback.onLost(mockNetwork)
        testDispatcher.scheduler.advanceTimeBy(15_001)

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

    // --- process restart / reconciliation tests ---

    @Test
    fun `unregister resets stale connection state and cancels worker`() {
        wifiMonitor.unregister()

        verify(wifiPreferences).wasConnectedToTarget = false
        verify(scheduler).cancel()
    }

    @Test
    fun `register schedules reconciliation and clocks in when connected`() = runTest(testDispatcher) {
        whenever(connectionInfo.hasLocationPermission()).thenReturn(true)
        whenever(connectionInfo.currentSsid()).thenReturn("Office")
        whenever(isAutoClockedIn()).thenReturn(false)
        whenever(wifiPreferences.wasConnectedToTarget).thenReturn(false)
        whenever(autoClockIn()).thenReturn(42L)

        wifiMonitor.register("Office")
        advanceUntilIdle()

        verify(scheduler).schedule()
        verify(autoClockIn).invoke()
        verify(wifiPreferences).wasConnectedToTarget = true
        verify(geofencePreferences).lastAutoTimeBlockId = 42L
    }

    @Test
    fun `reconcile clocks in when connected to target and no auto block is open`() = runTest(testDispatcher) {
        whenever(connectionInfo.hasLocationPermission()).thenReturn(true)
        whenever(connectionInfo.currentSsid()).thenReturn("Office")
        whenever(isAutoClockedIn()).thenReturn(false)
        whenever(wifiPreferences.wasConnectedToTarget).thenReturn(false)
        whenever(autoClockIn()).thenReturn(7L)

        wifiMonitor.reconcile("Office")

        verify(autoClockIn).invoke()
        verify(notificationHelper).showClockInNotification()
        verify(geofencePreferences).lastAutoTimeBlockId = 7L
    }

    @Test
    fun `reconcile does not clock in again when an auto block is still open`() = runTest(testDispatcher) {
        whenever(connectionInfo.hasLocationPermission()).thenReturn(true)
        whenever(connectionInfo.currentSsid()).thenReturn("Office")
        whenever(isAutoClockedIn()).thenReturn(true)

        wifiMonitor.reconcile("Office")

        verify(autoClockIn, never()).invoke()
        verify(wifiPreferences).wasConnectedToTarget = true
    }

    @Test
    fun `reconcile clocks out after grace period when target is gone`() = runTest(testDispatcher) {
        whenever(connectionInfo.hasLocationPermission()).thenReturn(true)
        whenever(connectionInfo.currentSsid()).thenReturn("Other")
        whenever(isAutoClockedIn()).thenReturn(true)
        whenever(wifiPreferences.wasConnectedToTarget).thenReturn(true)
        whenever(autoClockOut()).thenReturn(true)

        val job = launch { wifiMonitor.reconcile("Office") }
        advanceUntilIdle()
        job.join()

        verify(autoClockOut).invoke()
        verify(notificationHelper).showClockOutNotification()
    }

    @Test
    fun `reconcile keeps running block when target reconnects during grace period`() = runTest(testDispatcher) {
        whenever(connectionInfo.hasLocationPermission()).thenReturn(true)
        whenever(connectionInfo.currentSsid()).thenReturn("Other", "Office")
        whenever(isAutoClockedIn()).thenReturn(true)
        whenever(wifiPreferences.wasConnectedToTarget).thenReturn(true)

        val job = launch { wifiMonitor.reconcile("Office") }
        advanceUntilIdle()
        job.join()

        verify(autoClockOut, never()).invoke()
    }

    @Test
    fun `reconcile does nothing without location permission`() = runTest(testDispatcher) {
        whenever(connectionInfo.hasLocationPermission()).thenReturn(false)

        wifiMonitor.reconcile("Office")

        verify(connectionInfo, never()).currentSsid()
        verify(autoClockIn, never()).invoke()
        verify(autoClockOut, never()).invoke()
    }
}
