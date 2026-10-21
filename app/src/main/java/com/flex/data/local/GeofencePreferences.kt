package com.flex.data.local

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import com.flex.domain.model.AutoClockInPlannedDaySnapshot
import com.flex.domain.model.TimeBlock
import com.flex.domain.model.WorkLocation
import javax.inject.Inject
import javax.inject.Singleton
import java.time.LocalDate
import java.time.LocalTime

@Singleton
class GeofencePreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("geofence_prefs", Context.MODE_PRIVATE)

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
        const val SNAPSHOT_VERSION = "1"
        const val SNAPSHOT_HEADER_PARTS = 5
    }
}
