package com.flex.automation

import com.flex.domain.model.Settings
import com.flex.domain.repository.SettingsRepository
import com.flex.geofence.GeofenceManager
import com.flex.wifi.WifiMonitor
import com.google.android.gms.location.GeofencingRequest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class AutomationRestorerTest {

    private val settingsRepository: SettingsRepository = mock()
    private val geofenceManager: GeofenceManager = mock()
    private val wifiMonitor: WifiMonitor = mock()

    private val geofenceSettings = Settings(
        geofenceEnabled = true,
        geofenceLat = 53.5511,
        geofenceLon = 9.9937,
        geofenceRadiusMeters = 150f
    )

    @Test
    fun `restore passes the configured initial trigger to the geofence`() = runTest {
        whenever(settingsRepository.getSettings()).thenReturn(flowOf(geofenceSettings))
        doAnswer { invocation ->
            val onSuccess = invocation.getArgument<() -> Unit>(4)
            onSuccess()
        }.whenever(geofenceManager).registerGeofence(any(), any(), any(), any(), any(), any())

        AutomationRestorer(settingsRepository, geofenceManager, wifiMonitor)
            .restore(GeofencingRequest.INITIAL_TRIGGER_ENTER)

        verify(geofenceManager).registerGeofence(
            eq(53.5511),
            eq(9.9937),
            eq(150f),
            eq(GeofencingRequest.INITIAL_TRIGGER_ENTER),
            any(),
            any()
        )
    }

    @Test
    fun `normal restore does not synthesize an initial geofence transition`() = runTest {
        whenever(settingsRepository.getSettings()).thenReturn(flowOf(geofenceSettings))
        doAnswer { invocation ->
            val onSuccess = invocation.getArgument<() -> Unit>(4)
            onSuccess()
        }.whenever(geofenceManager).registerGeofence(any(), any(), any(), any(), any(), any())

        AutomationRestorer(settingsRepository, geofenceManager, wifiMonitor).restore()

        verify(geofenceManager).registerGeofence(
            eq(53.5511),
            eq(9.9937),
            eq(150f),
            eq(0),
            any(),
            any()
        )
    }
}
