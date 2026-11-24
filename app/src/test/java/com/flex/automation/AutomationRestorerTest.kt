package com.flex.automation

import com.flex.data.local.GeofencePreferences
import com.flex.data.local.GeofenceRegistrationInfo
import com.flex.domain.model.DayType
import com.flex.domain.model.Settings
import com.flex.domain.model.TimeBlock
import com.flex.domain.model.WorkDay
import com.flex.domain.model.WorkLocation
import com.flex.domain.repository.SettingsRepository
import com.flex.domain.repository.WorkDayRepository
import com.flex.geofence.GeofenceManager
import com.flex.support.TestDates
import com.flex.support.fixedClock
import com.flex.wifi.WifiMonitor
import com.google.android.gms.location.GeofencingRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalTime

class AutomationRestorerTest {

    private val settingsRepository: SettingsRepository = mock()
    private val geofenceManager: GeofenceManager = mock()
    private val wifiMonitor: WifiMonitor = mock()
    private val geofencePreferences: GeofencePreferences = mock()
    private val workDayRepository: WorkDayRepository = mock()
    private val clock = fixedClock(date = TestDates.FRIDAY, time = LocalTime.of(12, 0))

    private val geofenceSettings = Settings(
        geofenceEnabled = true,
        geofenceLat = 53.5511,
        geofenceLon = 9.9937,
        geofenceRadiusMeters = 150f
    )

    private fun createRestorer() = AutomationRestorer(
        settingsRepository = settingsRepository,
        geofenceManager = geofenceManager,
        wifiMonitor = wifiMonitor,
        geofencePreferences = geofencePreferences,
        workDayRepository = workDayRepository,
        clock = clock
    )

    @BeforeEach
    fun setUp() {
        whenever(geofencePreferences.registration).thenReturn(MutableStateFlow(GeofenceRegistrationInfo()))
        whenever(workDayRepository.getWorkDay(TestDates.FRIDAY)).thenReturn(flowOf(null))
        doAnswer { invocation ->
            val onSuccess = invocation.getArgument<() -> Unit>(5)
            onSuccess()
        }.whenever(geofenceManager).registerGeofence(any(), any(), any(), any(), any(), any(), any())
    }

    @Test
    fun `restore passes the configured initial trigger and source to the geofence`() = runTest {
        whenever(settingsRepository.getSettings()).thenReturn(flowOf(geofenceSettings))

        createRestorer().restore(
            initialGeofenceTrigger = GeofencingRequest.INITIAL_TRIGGER_ENTER,
            source = "App-Update"
        )

        verify(geofenceManager).registerGeofence(
            eq(53.5511),
            eq(9.9937),
            eq(150f),
            eq(GeofencingRequest.INITIAL_TRIGGER_ENTER),
            eq("App-Update"),
            any(),
            any()
        )
    }

    @Test
    fun `normal restore does not synthesize an initial geofence transition`() = runTest {
        whenever(settingsRepository.getSettings()).thenReturn(flowOf(geofenceSettings))

        createRestorer().restore()

        verify(geofenceManager).registerGeofence(
            eq(53.5511),
            eq(9.9937),
            eq(150f),
            eq(0),
            eq("Wiederherstellung"),
            any(),
            any()
        )
    }

    @Test
    fun `restoreGeofenceSmart uses INITIAL_TRIGGER_ENTER when today has no non-duration time blocks`() = runTest {
        whenever(settingsRepository.getSettings()).thenReturn(flowOf(geofenceSettings))
        val plannedOnly = WorkDay(
            id = 1L,
            date = TestDates.FRIDAY,
            location = WorkLocation.OFFICE,
            dayType = DayType.WORK,
            isPlanned = true,
            timeBlocks = listOf(
                TimeBlock(
                    id = 10L,
                    workDayId = 1L,
                    startTime = LocalTime.of(8, 0),
                    endTime = LocalTime.of(15, 0),
                    isDuration = true,
                    location = WorkLocation.OFFICE
                )
            )
        )
        whenever(workDayRepository.getWorkDay(TestDates.FRIDAY)).thenReturn(flowOf(plannedOnly))

        createRestorer().restoreGeofenceSmart(source = "Hintergrund-Check")

        verify(geofenceManager).registerGeofence(
            eq(53.5511),
            eq(9.9937),
            eq(150f),
            eq(GeofencingRequest.INITIAL_TRIGGER_ENTER),
            eq("Hintergrund-Check"),
            any(),
            any()
        )
        verify(geofenceManager).scheduleMorningCheck(geofenceSettings.defaultStartTime)
    }

    @Test
    fun `restoreGeofenceSmart uses 0 when today already has a non-duration time block`() = runTest {
        whenever(settingsRepository.getSettings()).thenReturn(flowOf(geofenceSettings))
        val clockedDay = WorkDay(
            id = 1L,
            date = TestDates.FRIDAY,
            location = WorkLocation.OFFICE,
            dayType = DayType.WORK,
            isPlanned = false,
            timeBlocks = listOf(
                TimeBlock(
                    id = 10L,
                    workDayId = 1L,
                    startTime = LocalTime.of(8, 0),
                    endTime = LocalTime.of(11, 30),
                    isDuration = false,
                    location = WorkLocation.OFFICE
                )
            )
        )
        whenever(workDayRepository.getWorkDay(TestDates.FRIDAY)).thenReturn(flowOf(clockedDay))

        createRestorer().restoreGeofenceSmart(source = "Hintergrund-Check")

        verify(geofenceManager).registerGeofence(
            eq(53.5511),
            eq(9.9937),
            eq(150f),
            eq(0),
            eq("Hintergrund-Check"),
            any(),
            any()
        )
    }

    @Test
    fun `healGeofenceIfStale skips re-registration when last success is younger than threshold`() = runTest {
        whenever(settingsRepository.getSettings()).thenReturn(flowOf(geofenceSettings))
        val thirtyMinutesAgo = clock.millis() - 30 * 60 * 1000L
        whenever(geofencePreferences.registration).thenReturn(
            MutableStateFlow(GeofenceRegistrationInfo(success = true, atEpochMs = thirtyMinutesAgo))
        )

        createRestorer().healGeofenceIfStale()

        verify(geofenceManager, never()).registerGeofence(any(), any(), any(), any(), any(), any(), any())
    }

    @Test
    fun `healGeofenceIfStale does nothing when geofencing is disabled`() = runTest {
        whenever(settingsRepository.getSettings()).thenReturn(
            flowOf(geofenceSettings.copy(geofenceEnabled = false))
        )

        createRestorer().healGeofenceIfStale()

        verify(geofenceManager, never()).registerGeofence(any(), any(), any(), any(), any(), any(), any())
        verify(geofenceManager, never()).removeGeofence()
        verify(geofenceManager, never()).scheduleMorningCheck(any())
    }

    @Test
    fun `healGeofenceIfStale re-registers when last success is at or older than threshold`() = runTest {
        whenever(settingsRepository.getSettings()).thenReturn(flowOf(geofenceSettings))
        val sixtyMinutesAgo = clock.millis() - 60 * 60 * 1000L
        whenever(geofencePreferences.registration).thenReturn(
            MutableStateFlow(GeofenceRegistrationInfo(success = true, atEpochMs = sixtyMinutesAgo))
        )

        createRestorer().healGeofenceIfStale()

        verify(geofenceManager).registerGeofence(
            eq(53.5511),
            eq(9.9937),
            eq(150f),
            eq(GeofencingRequest.INITIAL_TRIGGER_ENTER),
            eq("Vordergrund"),
            any(),
            any()
        )
    }

    @Test
    fun `healGeofenceIfStale re-registers when last registration failed or is unknown`() = runTest {
        whenever(settingsRepository.getSettings()).thenReturn(flowOf(geofenceSettings))
        whenever(geofencePreferences.registration).thenReturn(
            MutableStateFlow(GeofenceRegistrationInfo(success = false, atEpochMs = clock.millis() - 5_000L))
        )

        createRestorer().healGeofenceIfStale()

        verify(geofenceManager).registerGeofence(
            eq(53.5511),
            eq(9.9937),
            eq(150f),
            eq(GeofencingRequest.INITIAL_TRIGGER_ENTER),
            eq("Vordergrund"),
            any(),
            any()
        )
    }
}
