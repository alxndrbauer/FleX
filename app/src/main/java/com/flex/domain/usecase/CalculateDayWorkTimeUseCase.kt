package com.flex.domain.usecase

import com.flex.domain.model.TimeBlock
import java.time.Duration
import java.time.LocalTime
import javax.inject.Inject

data class DayWorkTimeResult(
    val grossMinutes: Long,
    val netMinutes: Long,
    val breakMinutes: Long,
    val exceedsMaxHours: Boolean
)

class CalculateDayWorkTimeUseCase @Inject constructor() {

    companion object {
        const val MAX_WORK_MINUTES = 600L // 10 hours
        const val BREAK_THRESHOLD_6H = 360L // 6 hours in minutes
        const val BREAK_THRESHOLD_9H = 540L // 9 hours in minutes
        const val BREAK_30_MIN = 30L
        const val BREAK_45_MIN = 45L

        /** Sortiert Blöcke und wendet 5-Minuten-Rundung an (erster Start ↓, letztes Ende ↑). */
        fun adjustTimeBlocks(timeBlocks: List<TimeBlock>): List<TimeBlock> {
            val sorted = timeBlocks.sortedBy { it.startTime }
            return sorted.mapIndexed { index, block ->
                if (block.isDuration) return@mapIndexed block
                var start = block.startTime
                var end = block.endTime
                if (index == 0) start = roundDownTo5Min(start)
                if (index == sorted.lastIndex && end != null) end = roundUpTo5Min(end)
                block.copy(startTime = start, endTime = end)
            }
        }

        private fun roundDownTo5Min(time: LocalTime): LocalTime {
            val remainder = time.minute % 5
            return if (remainder == 0) time else time.minusMinutes(remainder.toLong())
        }

        private fun roundUpTo5Min(time: LocalTime): LocalTime {
            val remainder = time.minute % 5
            return if (remainder == 0) time else time.plusMinutes((5 - remainder).toLong())
        }
    }

    operator fun invoke(timeBlocks: List<TimeBlock>): DayWorkTimeResult {
        if (timeBlocks.isEmpty()) {
            return DayWorkTimeResult(0, 0, 0, false)
        }

        val adjusted = adjustTimeBlocks(timeBlocks)

        // Calculate gross time per block
        var totalGrossMinutes = 0L
        for (block in adjusted) {
            val end = block.endTime ?: continue // skip blocks without end
            val duration = Duration.between(block.startTime, end).toMinutes()
            if (duration > 0) totalGrossMinutes += duration
        }

        // If all blocks are duration-based (Gesamtzeit / Planung), skip break deduction
        val completedBlocks = adjusted.filter { it.endTime != null }
        val allDurationBased = completedBlocks.isNotEmpty() && completedBlocks.all { it.isDuration }

        if (allDurationBased) {
            val exceedsMax = totalGrossMinutes > MAX_WORK_MINUTES
            return DayWorkTimeResult(
                grossMinutes = totalGrossMinutes,
                netMinutes = minOf(totalGrossMinutes, MAX_WORK_MINUTES),
                breakMinutes = 0,
                exceedsMaxHours = exceedsMax
            )
        }

        // Calculate gaps between consecutive blocks.
        // Also counts the gap between the last completed block and a running block,
        // so that a break already taken is not deducted again.
        var totalGapMinutes = 0L
        for (i in 0 until adjusted.size - 1) {
            val currentEnd = adjusted[i].endTime ?: continue // running block has no end
            val nextStart = adjusted[i + 1].startTime
            val gap = Duration.between(currentEnd, nextStart).toMinutes()
            if (gap > 0) totalGapMinutes += gap
        }

        // Determine required legal break (graduated: up to 30 min for >6h, up to 15 min extra for >9h net)
        val stage1Break = if (totalGrossMinutes > BREAK_THRESHOLD_6H)
            minOf(totalGrossMinutes - BREAK_THRESHOLD_6H, BREAK_30_MIN)
        else 0L
        val afterStage1 = totalGrossMinutes - stage1Break
        val stage2Break = if (afterStage1 > BREAK_THRESHOLD_9H)
            minOf(afterStage1 - BREAK_THRESHOLD_9H, BREAK_45_MIN - BREAK_30_MIN)
        else 0L
        val requiredBreak = stage1Break + stage2Break

        val effectiveBreak: Long
        val netMinutes: Long

        if (adjusted.size <= 1) {
            // Single block (completed or running): apply automatic break
            effectiveBreak = requiredBreak
            netMinutes = totalGrossMinutes - effectiveBreak
        } else {
            // Multiple blocks: gaps count as breaks, top up if insufficient.
            // Includes gap to a running block so an existing break is not double-counted.
            effectiveBreak = maxOf(totalGapMinutes, requiredBreak)
            netMinutes = totalGrossMinutes - (effectiveBreak - totalGapMinutes).coerceAtLeast(0)
        }

        val cappedNet = netMinutes.coerceAtLeast(0)
        val exceedsMax = cappedNet > MAX_WORK_MINUTES

        return DayWorkTimeResult(
            grossMinutes = totalGrossMinutes,
            netMinutes = minOf(cappedNet, MAX_WORK_MINUTES),
            breakMinutes = effectiveBreak,
            exceedsMaxHours = exceedsMax
        )
    }

}
