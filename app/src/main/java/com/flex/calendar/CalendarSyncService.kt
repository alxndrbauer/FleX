package com.flex.calendar

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.provider.CalendarContract
import com.flex.data.local.dao.CalendarEventDao
import com.flex.data.local.entity.CalendarEventEntity
import com.flex.domain.model.DayType
import com.flex.domain.model.Settings
import com.flex.domain.model.WorkDay
import com.flex.domain.model.WorkLocation
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class CalendarInfo(
    val id: Long,
    val displayName: String,
    val accountName: String,
    val color: Int
)

@Singleton
class CalendarSyncService @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val calendarEventDao: CalendarEventDao,
    private val calendarEventMapper: CalendarEventMapper
) {
    private companion object { const val TAG = "CalendarSync" }

    fun getAvailableCalendars(): List<CalendarInfo> = try {
        queryAvailableCalendars()
    } catch (_: SecurityException) {
        emptyList()
    }

    private fun queryAvailableCalendars(): List<CalendarInfo> {
        val calendars = mutableListOf<CalendarInfo>()
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.CALENDAR_COLOR
        )
        val cursor: Cursor? = try {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                "${CalendarContract.Calendars.VISIBLE} = 1",
                null,
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME
            )
        } catch (_: SecurityException) {
            null
        }
        cursor?.use {
            while (it.moveToNext()) {
                calendars.add(
                    CalendarInfo(
                        id = it.getLong(0),
                        displayName = it.getString(1) ?: "",
                        accountName = it.getString(2) ?: "",
                        color = it.getInt(3)
                    )
                )
            }
        }
        return calendars
    }

    suspend fun syncWorkDay(workDay: WorkDay, settings: Settings) {
        Log.d(TAG, "syncWorkDay: id=${workDay.id} date=${workDay.date} type=${workDay.dayType} loc=${workDay.location} enabled=${settings.calendarSyncEnabled} calId=${settings.calendarId}")
        if (!settings.calendarSyncEnabled || settings.calendarId == -1L) {
            Log.d(TAG, "syncWorkDay: skipped (sync disabled or no calendar)")
            return
        }

        if (!calendarEventMapper.isSyncedType(workDay, settings)) {
            Log.d(TAG, "syncWorkDay: type not in filter → removing existing event if any")
            val existing = calendarEventDao.getByWorkDayId(workDay.id)
            if (existing != null) {
                deleteEventFromCalendar(existing.calendarEventId)
                calendarEventDao.deleteByWorkDayId(workDay.id)
            }
            return
        }

        val existing = calendarEventDao.getByWorkDayId(workDay.id)
        Log.d(TAG, "syncWorkDay: existing mapping=${existing?.calendarEventId}")
        val prefix = settings.calendarEventPrefix
        val noAlarm = settings.calendarEventNoAlarm
        if (existing != null) {
            updateEventInCalendar(existing.calendarEventId, workDay, prefix, noAlarm)
            Log.d(TAG, "syncWorkDay: updated existing calEventId=${existing.calendarEventId}")
            return
        }

        val eventId = insertEventToCalendar(workDay, settings.calendarId, prefix, noAlarm)
        Log.d(TAG, "syncWorkDay: insertEventToCalendar → eventId=$eventId")
        if (eventId == null) {
            Log.w(TAG, "syncWorkDay: insert returned null — no mapping saved")
            return
        }
        calendarEventDao.insert(
            CalendarEventEntity(
                workDayId = workDay.id,
                calendarEventId = eventId,
                calendarId = settings.calendarId
            )
        )
        Log.d(TAG, "syncWorkDay: mapping saved workDayId=${workDay.id} → calEventId=$eventId")
    }

    suspend fun deleteCalendarEvent(workDay: WorkDay, prefix: String = "FleX") {
        Log.d(TAG, "deleteCalendarEvent: workDayId=${workDay.id} date=${workDay.date}")
        val existing = calendarEventDao.getByWorkDayId(workDay.id)
        if (existing != null) {
            Log.d(TAG, "deleteCalendarEvent: found mapping calEventId=${existing.calendarEventId} → deleting")
            deleteEventFromCalendar(existing.calendarEventId)
            calendarEventDao.deleteByWorkDayId(workDay.id)
        } else {
            Log.w(TAG, "deleteCalendarEvent: no mapping found → fallback by date+title")
            deleteEventFromCalendarByDateAndTitle(workDay, prefix)
        }
    }

    /** Returns Pair(removed, total) of orphaned mappings. */
    suspend fun cleanupOrphanedMappings(): Pair<Int, Int> {
        val allMappings = calendarEventDao.getAll()
        var removed = 0
        withContext(Dispatchers.IO) {
            for (mapping in allMappings) {
                val exists = try {
                    context.contentResolver.query(
                        CalendarContract.Events.CONTENT_URI,
                        arrayOf(CalendarContract.Events._ID),
                        "${CalendarContract.Events._ID} = ? AND ${CalendarContract.Events.DELETED} = 0",
                        arrayOf(mapping.calendarEventId.toString()),
                        null
                    )?.use { it.count > 0 } ?: false
                } catch (_: Exception) { true } // assume exists on error
                if (!exists) {
                    Log.d(TAG, "cleanupOrphanedMappings: removing orphan workDayId=${mapping.workDayId} calEventId=${mapping.calendarEventId}")
                    calendarEventDao.deleteByWorkDayId(mapping.workDayId)
                    removed++
                }
            }
        }
        Log.d(TAG, "cleanupOrphanedMappings: removed=$removed of ${allMappings.size} mappings")
        return Pair(removed, allMappings.size)
    }

    /** Returns Pair(synced, total) where total = days matching sync filter */
    suspend fun syncAll(workDays: List<WorkDay>, settings: Settings): Pair<Int, Int> {
        Log.d(TAG, "syncAll: ${workDays.size} days total, calId=${settings.calendarId}")
        val toSync = workDays.filter { calendarEventMapper.isSyncedType(it, settings) }
        val toRemove = workDays.filter { !calendarEventMapper.isSyncedType(it, settings) }
        Log.d(TAG, "syncAll: ${toSync.size} to sync, ${toRemove.size} to remove")
        var synced = 0
        toSync.forEach {
            try {
                syncWorkDay(it, settings)
                synced++
            } catch (e: Exception) {
                Log.e(TAG, "syncAll: exception for ${it.date}: ${e::class.simpleName}: ${e.message}", e)
            }
        }
        toRemove.forEach {
            try {
                syncWorkDay(it, settings)
            } catch (e: Exception) {
                Log.e(TAG, "syncAll: remove exception for ${it.date}: ${e::class.simpleName}: ${e.message}", e)
            }
        }
        Log.d(TAG, "syncAll: done synced=$synced of ${toSync.size}")
        return Pair(synced, toSync.size)
    }


    private suspend fun insertEventToCalendar(workDay: WorkDay, calendarId: Long, prefix: String, noAlarm: Boolean): Long? {
        val title = calendarEventMapper.eventTitle(workDay, prefix)
        // All-day events require UTC midnight — NOT local midnight
        val dateMillis = workDay.date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val nextDayMillis = workDay.date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.DTSTART, dateMillis)
            put(CalendarContract.Events.DTEND, nextDayMillis)
            put(CalendarContract.Events.ALL_DAY, 1)
            put(CalendarContract.Events.EVENT_TIMEZONE, "UTC")
            put(CalendarContract.Events.EVENT_END_TIMEZONE, "UTC")
            put(CalendarContract.Events.HAS_ALARM, if (noAlarm) 0 else 1)
            workDay.note?.takeIf { it.isNotBlank() }?.let {
                put(CalendarContract.Events.DESCRIPTION, it)
            }
        }

        return withContext(Dispatchers.IO) {
            try {
                val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
                Log.d(TAG, "insertEventToCalendar: uri=$uri")
                uri?.lastPathSegment?.toLongOrNull()
            } catch (e: Exception) {
                Log.e(TAG, "insertEventToCalendar: exception ${e::class.simpleName}: ${e.message}", e)
                null
            }
        }
    }

    private suspend fun updateEventInCalendar(eventId: Long, workDay: WorkDay, prefix: String, noAlarm: Boolean) {
        val title = calendarEventMapper.eventTitle(workDay, prefix)
        val dateMillis = workDay.date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val nextDayMillis = workDay.date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

        val values = ContentValues().apply {
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.DTSTART, dateMillis)
            put(CalendarContract.Events.DTEND, nextDayMillis)
            put(CalendarContract.Events.ALL_DAY, 1)
            put(CalendarContract.Events.EVENT_TIMEZONE, "UTC")
            put(CalendarContract.Events.EVENT_END_TIMEZONE, "UTC")
            put(CalendarContract.Events.HAS_ALARM, if (noAlarm) 0 else 1)
            if (workDay.note?.isNotBlank() == true) {
                put(CalendarContract.Events.DESCRIPTION, workDay.note)
            } else {
                putNull(CalendarContract.Events.DESCRIPTION)
            }
        }

        withContext(Dispatchers.IO) {
            try {
                val uri = CalendarContract.Events.CONTENT_URI
                    .buildUpon()
                    .appendPath(eventId.toString())
                    .build()
                val rows = context.contentResolver.update(uri, values, null, null)
                Log.d(TAG, "updateEventInCalendar: eventId=$eventId updated=$rows rows")
            } catch (e: Exception) {
                Log.e(TAG, "updateEventInCalendar: exception ${e::class.simpleName}: ${e.message}", e)
            }
        }
    }

    private suspend fun deleteEventFromCalendar(eventId: Long) {
        withContext(Dispatchers.IO) {
            try {
                val uri = CalendarContract.Events.CONTENT_URI
                    .buildUpon()
                    .appendPath(eventId.toString())
                    .build()
                val rows = context.contentResolver.delete(uri, null, null)
                Log.d(TAG, "deleteEventFromCalendar: eventId=$eventId deleted=$rows rows")
            } catch (e: Exception) {
                Log.e(TAG, "deleteEventFromCalendar: exception ${e::class.simpleName}: ${e.message}", e)
            }
        }
    }

    private suspend fun deleteEventFromCalendarByDateAndTitle(workDay: WorkDay, prefix: String) {
        val title = calendarEventMapper.eventTitle(workDay, prefix)
        val dateMillis = workDay.date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        withContext(Dispatchers.IO) {
            try {
                val rows = context.contentResolver.delete(
                    CalendarContract.Events.CONTENT_URI,
                    "${CalendarContract.Events.TITLE} = ? AND ${CalendarContract.Events.DTSTART} = ?",
                    arrayOf(title, dateMillis.toString())
                )
                Log.d(TAG, "deleteEventFromCalendarByDateAndTitle: date=${workDay.date} deleted=$rows rows")
            } catch (e: Exception) {
                Log.e(TAG, "deleteEventFromCalendarByDateAndTitle: exception ${e::class.simpleName}: ${e.message}", e)
            }
        }
    }

}
