package com.flex.calendar

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.CalendarContract
import com.google.common.truth.Truth.assertThat
import com.flex.BaseUnitTest
import com.flex.data.local.dao.CalendarEventDao
import com.flex.data.local.entity.CalendarEventEntity
import com.flex.domain.model.DayType
import com.flex.domain.model.Settings
import com.flex.domain.model.WorkDay
import com.flex.domain.model.WorkLocation
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDate

/**
 * Unit tests verifying that CalendarSyncService correctly deletes
 * CalendarContract.Reminders after inserting or updating calendar events
 * when calendarEventNoAlarm = true.
 *
 * Background: HAS_ALARM = 0 is an informational flag only — Google Calendar
 * and other apps ignore it and still add default reminders. The fix is to
 * explicitly delete reminder rows from CalendarContract.Reminders after
 * the event is created/updated.
 */
class CalendarSyncServiceNoAlarmTest : BaseUnitTest() {

    @Mock private lateinit var context: Context
    @Mock private lateinit var contentResolver: ContentResolver
    @Mock private lateinit var calendarEventDao: CalendarEventDao
    @Mock private lateinit var eventUri: Uri

    private lateinit var service: CalendarSyncService

    private val syncSettings = Settings(
        calendarSyncEnabled = true,
        calendarId = 99L,
        calendarSyncTypes = "VACATION,WORK",
        calendarEventNoAlarm = true
    )
    private val alarmSettings = syncSettings.copy(calendarEventNoAlarm = false)

    @BeforeEach
    override fun setUp() {
        super.setUp()
        whenever(context.contentResolver).thenReturn(contentResolver)
        whenever(eventUri.lastPathSegment).thenReturn("42")
        whenever(contentResolver.insert(eq(CalendarContract.Events.CONTENT_URI), any()))
            .thenReturn(eventUri)
        service = CalendarSyncService(context, calendarEventDao, CalendarEventMapper())
    }

    // ========== syncVacationGroup — insert path ==========

    @Test
    fun `syncVacationGroup with noAlarm=true deletes reminders after insert`() = runTest {
        val days = listOf(vacationDay(1L, LocalDate.of(2026, 4, 7)))
        whenever(calendarEventDao.getByWorkDayId(1L)).thenReturn(null)

        service.syncVacationGroup(days, syncSettings)

        verify(contentResolver).delete(
            eq(CalendarContract.Reminders.CONTENT_URI),
            argThat { contains(CalendarContract.Reminders.EVENT_ID) },
            eq(arrayOf("42"))
        )
    }

    @Test
    fun `syncVacationGroup with noAlarm=false does NOT delete reminders`() = runTest {
        val days = listOf(vacationDay(1L, LocalDate.of(2026, 4, 7)))
        whenever(calendarEventDao.getByWorkDayId(1L)).thenReturn(null)

        service.syncVacationGroup(days, alarmSettings)

        verify(contentResolver, never()).delete(
            eq(CalendarContract.Reminders.CONTENT_URI),
            any(),
            any()
        )
    }

    @Test
    fun `syncVacationGroup multi-day with noAlarm=true deletes reminders once`() = runTest {
        val days = (7..11).map { vacationDay(it.toLong(), LocalDate.of(2026, 4, it)) }
        days.forEach { whenever(calendarEventDao.getByWorkDayId(it.id)).thenReturn(null) }

        service.syncVacationGroup(days, syncSettings)

        // Only one calendar event is created → reminders deleted once
        verify(contentResolver).delete(
            eq(CalendarContract.Reminders.CONTENT_URI),
            any(),
            eq(arrayOf("42"))
        )
    }

    // ========== syncWorkDay — insert path ==========

    @Test
    fun `syncWorkDay new event with noAlarm=true deletes reminders`() = runTest {
        val workDay = workDay(1L, LocalDate.of(2026, 4, 7))
        whenever(calendarEventDao.getByWorkDayId(1L)).thenReturn(null)

        service.syncWorkDay(workDay, syncSettings)

        verify(contentResolver).delete(
            eq(CalendarContract.Reminders.CONTENT_URI),
            argThat { contains(CalendarContract.Reminders.EVENT_ID) },
            eq(arrayOf("42"))
        )
    }

    @Test
    fun `syncWorkDay new event with noAlarm=false does NOT delete reminders`() = runTest {
        val workDay = workDay(1L, LocalDate.of(2026, 4, 7))
        whenever(calendarEventDao.getByWorkDayId(1L)).thenReturn(null)

        service.syncWorkDay(workDay, alarmSettings)

        verify(contentResolver, never()).delete(
            eq(CalendarContract.Reminders.CONTENT_URI),
            any(),
            any()
        )
    }

    // ========== syncWorkDay — update path ==========

    @Test
    fun `syncWorkDay update with noAlarm=true deletes reminders`() = runTest {
        val workDay = workDay(1L, LocalDate.of(2026, 4, 7))
        whenever(calendarEventDao.getByWorkDayId(1L))
            .thenReturn(CalendarEventEntity(workDayId = 1L, calendarEventId = 42L, calendarId = 99L))

        service.syncWorkDay(workDay, syncSettings)

        verify(contentResolver).delete(
            eq(CalendarContract.Reminders.CONTENT_URI),
            argThat { contains(CalendarContract.Reminders.EVENT_ID) },
            eq(arrayOf("42"))
        )
    }

    @Test
    fun `syncWorkDay update with noAlarm=false does NOT delete reminders`() = runTest {
        val workDay = workDay(1L, LocalDate.of(2026, 4, 7))
        whenever(calendarEventDao.getByWorkDayId(1L))
            .thenReturn(CalendarEventEntity(workDayId = 1L, calendarEventId = 42L, calendarId = 99L))

        service.syncWorkDay(workDay, alarmSettings)

        verify(contentResolver, never()).delete(
            eq(CalendarContract.Reminders.CONTENT_URI),
            any(),
            any()
        )
    }

    // ========== Helpers ==========

    private fun vacationDay(id: Long, date: LocalDate) =
        WorkDay(id = id, date = date, location = WorkLocation.OFFICE, dayType = DayType.VACATION)

    private fun workDay(id: Long, date: LocalDate) =
        WorkDay(id = id, date = date, location = WorkLocation.OFFICE, dayType = DayType.WORK)
}
