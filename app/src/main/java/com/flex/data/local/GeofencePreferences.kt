package com.flex.data.local

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import com.flex.domain.model.AutoClockInPlannedDaySnapshot
import com.flex.domain.model.TimeBlock
import com.flex.domain.model.WorkLocation
import com.google.android.gms.location.Geofence
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import java.time.LocalDate
import java.time.LocalTime

data class GeofenceRegistrationInfo(
    val success: Boolean? = null,
    val atEpochMs: Long? = null
)

data class GeofenceEventInfo(
    val transition: Int? = null,
    val errorCode: Int? = null,
    val atEpochMs: Long? = null
)

enum class GeofenceLogType {
    ENTER,
    DWELL,
    EXIT,
    ERROR,
    REGISTERED,
    REGISTER_FAILED
}

data class GeofenceLogEntry(
    val type: GeofenceLogType,
    val atEpochMs: Long,
    val detail: String = ""
)

@Singleton
class GeofencePreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("geofence_prefs", Context.MODE_PRIVATE)

    private val _registration = MutableStateFlow(loadRegistration())
    val registration: StateFlow<GeofenceRegistrationInfo> = _registration.asStateFlow()

    private val _lastEvent = MutableStateFlow(loadLastEvent())
    val lastEvent: StateFlow<GeofenceEventInfo> = _lastEvent.asStateFlow()

    private val _eventHistory = MutableStateFlow(loadEventHistory())
    val eventHistory: StateFlow<List<GeofenceLogEntry>> = _eventHistory.asStateFlow()

    fun recordEvent(transition: Int?, errorCode: Int?, atEpochMs: Long, detail: String = "") {
        prefs.edit().apply {
            if (transition == null) remove(KEY_EVENT_TRANSITION) else putInt(KEY_EVENT_TRANSITION, transition)
            if (errorCode == null) remove(KEY_EVENT_ERROR) else putInt(KEY_EVENT_ERROR, errorCode)
            putLong(KEY_EVENT_AT, atEpochMs)
        }.apply()
        _lastEvent.value = GeofenceEventInfo(transition, errorCode, atEpochMs)

        when {
            errorCode != null -> {
                val errorDetail = detail.ifBlank { "Code $errorCode" }
                appendLog(GeofenceLogType.ERROR, atEpochMs, errorDetail)
            }
            transition == Geofence.GEOFENCE_TRANSITION_ENTER -> {
                appendLog(GeofenceLogType.ENTER, atEpochMs, detail)
            }
            transition == Geofence.GEOFENCE_TRANSITION_DWELL -> {
                appendLog(GeofenceLogType.DWELL, atEpochMs, detail)
            }
            transition == Geofence.GEOFENCE_TRANSITION_EXIT -> {
                appendLog(GeofenceLogType.EXIT, atEpochMs, detail)
            }
        }
    }

    private fun loadLastEvent(): GeofenceEventInfo {
        val atEpochMs = prefs.getLong(KEY_EVENT_AT, 0L).takeIf { it > 0L } ?: return GeofenceEventInfo()
        val transition = if (prefs.contains(KEY_EVENT_TRANSITION)) {
            prefs.getInt(KEY_EVENT_TRANSITION, 0)
        } else {
            null
        }
        val errorCode = if (prefs.contains(KEY_EVENT_ERROR)) {
            prefs.getInt(KEY_EVENT_ERROR, 0)
        } else {
            null
        }
        return GeofenceEventInfo(transition, errorCode, atEpochMs)
    }

    fun markRegistrationPending() = persistRegistration(null, null)

    fun markRegistrationSuccess(atEpochMs: Long, source: String = "") {
        persistRegistration(true, atEpochMs)
        appendLog(GeofenceLogType.REGISTERED, atEpochMs, source)
    }

    fun markRegistrationFailure(atEpochMs: Long, source: String = "", errorMessage: String? = null) {
        persistRegistration(false, atEpochMs)
        val detail = listOfNotNull(
            source.takeIf { it.isNotBlank() },
            errorMessage?.takeIf { it.isNotBlank() }
        ).joinToString(": ")
        appendLog(GeofenceLogType.REGISTER_FAILED, atEpochMs, detail)
    }

    fun clearRegistration() = persistRegistration(null, null)

    fun appendLog(type: GeofenceLogType, atEpochMs: Long, detail: String = "") {
        val sanitizedDetail = detail.replace('|', ' ').replace('\n', ' ').replace('\r', ' ').trim()
        val updated = (listOf(GeofenceLogEntry(type, atEpochMs, sanitizedDetail)) + _eventHistory.value)
            .take(MAX_HISTORY_ENTRIES)
        persistEventHistory(updated)
    }

    fun clearEventHistory() {
        prefs.edit().remove(KEY_EVENT_HISTORY).apply()
        _eventHistory.value = emptyList()
    }

    private fun persistEventHistory(entries: List<GeofenceLogEntry>) {
        val encoded = if (entries.isEmpty()) {
            ""
        } else {
            val lines = entries.map { "${it.type.name}|${it.atEpochMs}|${it.detail}" }
            (listOf(HISTORY_VERSION) + lines).joinToString("\n")
        }
        prefs.edit().apply {
            if (encoded.isEmpty()) remove(KEY_EVENT_HISTORY) else putString(KEY_EVENT_HISTORY, encoded)
        }.apply()
        _eventHistory.value = entries
    }

    private fun loadEventHistory(): List<GeofenceLogEntry> {
        val raw = prefs.getString(KEY_EVENT_HISTORY, null) ?: return emptyList()
        val lines = raw.split('\n')
        if (lines.isEmpty() || lines[0] != HISTORY_VERSION) return emptyList()
        return lines.drop(1).mapNotNull { line ->
            if (line.isBlank()) return@mapNotNull null
            val parts = line.split('|', limit = 3)
            if (parts.size < 2) return@mapNotNull null
            runCatching {
                GeofenceLogEntry(
                    type = GeofenceLogType.valueOf(parts[0]),
                    atEpochMs = parts[1].toLong(),
                    detail = parts.getOrNull(2).orEmpty()
                )
            }.getOrNull()
        }.take(MAX_HISTORY_ENTRIES)
    }

    private fun loadRegistration(): GeofenceRegistrationInfo {
        val success = if (prefs.contains(KEY_REGISTRATION_SUCCESS)) {
            prefs.getBoolean(KEY_REGISTRATION_SUCCESS, false)
        } else {
            null
        }
        val atEpochMs = prefs.getLong(KEY_REGISTRATION_AT, 0L).takeIf { it > 0L }
        return GeofenceRegistrationInfo(success, atEpochMs)
    }

    private fun persistRegistration(success: Boolean?, atEpochMs: Long?) {
        prefs.edit().apply {
            if (success == null) remove(KEY_REGISTRATION_SUCCESS) else putBoolean(KEY_REGISTRATION_SUCCESS, success)
            if (atEpochMs == null) remove(KEY_REGISTRATION_AT) else putLong(KEY_REGISTRATION_AT, atEpochMs)
        }.apply()
        _registration.value = GeofenceRegistrationInfo(success, atEpochMs)
    }

    var lastAutoTimeBlockId: Long
        get() = prefs.getLong("last_auto_block_id", -1L)
        set(value) { prefs.edit().putLong("last_auto_block_id", value).apply() }

    var lastAutoClockInPlannedDaySnapshot: AutoClockInPlannedDaySnapshot?
        get() = prefs.getString(KEY_AUTO_CLOCK_IN_PLANNED_DAY_SNAPSHOT, null)?.let { encoded ->
            decodeSnapshot(encoded) ?: run {
                prefs.edit().remove(KEY_AUTO_CLOCK_IN_PLANNED_DAY_SNAPSHOT).apply()
                null
            }
        }
        set(value) {
            val editor = prefs.edit()
            if (value == null) {
                editor.remove(KEY_AUTO_CLOCK_IN_PLANNED_DAY_SNAPSHOT)
            } else {
                editor.putString(KEY_AUTO_CLOCK_IN_PLANNED_DAY_SNAPSHOT, encodeSnapshot(value))
            }
            editor.apply()
        }

    private fun encodeSnapshot(snapshot: AutoClockInPlannedDaySnapshot): String {
        val header = listOf(
            SNAPSHOT_VERSION,
            snapshot.workDayId.toString(),
            snapshot.date.toString(),
            snapshot.wasPlanned.toString(),
            snapshot.durationBlocks.size.toString()
        ).joinToString("|")
        if (snapshot.durationBlocks.isEmpty()) return header

        val blocks = snapshot.durationBlocks.joinToString("|") { block ->
            listOf(
                block.id.toString(),
                block.workDayId.toString(),
                block.startTime.toString(),
                block.endTime?.toString().orEmpty(),
                block.isDuration.toString(),
                block.location.name
            ).joinToString(",")
        }
        return "$header|$blocks"
    }

    private fun decodeSnapshot(encoded: String): AutoClockInPlannedDaySnapshot? = runCatching {
        val parts = encoded.split('|')
        require(parts.size >= SNAPSHOT_HEADER_PARTS)
        require(parts[0] == SNAPSHOT_VERSION)
        val blockCount = parts[4].toInt()
        require(blockCount >= 0 && parts.size == SNAPSHOT_HEADER_PARTS + blockCount)

        val durationBlocks = (SNAPSHOT_HEADER_PARTS until parts.size).map { index ->
            val blockParts = parts[index].split(',')
            require(blockParts.size == 6)
            TimeBlock(
                id = blockParts[0].toLong(),
                workDayId = blockParts[1].toLong(),
                startTime = LocalTime.parse(blockParts[2]),
                endTime = blockParts[3].takeIf { it.isNotEmpty() }?.let(LocalTime::parse),
                isDuration = blockParts[4].toBooleanStrict(),
                location = WorkLocation.valueOf(blockParts[5])
            )
        }

        AutoClockInPlannedDaySnapshot(
            workDayId = parts[1].toLong(),
            date = LocalDate.parse(parts[2]),
            wasPlanned = parts[3].toBooleanStrict(),
            durationBlocks = durationBlocks
        )
    }.getOrNull()

    private companion object {
        const val KEY_AUTO_CLOCK_IN_PLANNED_DAY_SNAPSHOT = "last_auto_planned_day_snapshot"
        const val KEY_REGISTRATION_SUCCESS = "geofence_registration_success"
        const val KEY_REGISTRATION_AT = "geofence_registration_at"
        const val KEY_EVENT_TRANSITION = "geofence_event_transition"
        const val KEY_EVENT_ERROR = "geofence_event_error"
        const val KEY_EVENT_AT = "geofence_event_at"
        const val KEY_EVENT_HISTORY = "geofence_event_history"
        const val HISTORY_VERSION = "1"
        const val MAX_HISTORY_ENTRIES = 20
        const val SNAPSHOT_VERSION = "1"
        const val SNAPSHOT_HEADER_PARTS = 5
    }
}
