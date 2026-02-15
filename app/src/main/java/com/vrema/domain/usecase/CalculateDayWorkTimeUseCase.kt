package com.vrema.domain.usecase

import com.vrema.domain.model.TimeBlock
import java.time.Duration
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
    }

    operator fun invoke(timeBlocks: List<TimeBlock>): DayWorkTimeResult {
        if (timeBlocks.isEmpty()) {
            return DayWorkTimeResult(0, 0, 0, false)
        }

        val sorted = timeBlocks.sortedBy { it.startTime }

        // Calculate gross time per block
        var totalGrossMinutes = 0L
        for (block in sorted) {
            val end = block.endTime ?: continue // skip blocks without end
            val duration = Duration.between(block.startTime, end).toMinutes()
            if (duration > 0) totalGrossMinutes += duration
        }

        // If all blocks are duration-based (Gesamtzeit / Planung), skip break deduction
        val completedBlocks = sorted.filter { it.endTime != null }
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

        // Calculate manual breaks (gaps between blocks)
        var manualBreakMinutes = 0L
        for (i in 0 until completedBlocks.size - 1) {
            val gapStart = completedBlocks[i].endTime!!
            val gapEnd = completedBlocks[i + 1].startTime
            val gap = Duration.between(gapStart, gapEnd).toMinutes()
            if (gap > 0) manualBreakMinutes += gap
        }

        // Determine required legal break
        val requiredBreak = when {
            totalGrossMinutes > BREAK_THRESHOLD_9H -> BREAK_45_MIN
            totalGrossMinutes > BREAK_THRESHOLD_6H -> BREAK_30_MIN
            else -> 0L
        }

        val effectiveBreak: Long
        val netMinutes: Long

        if (completedBlocks.size <= 1) {
            // Single block: apply automatic break
            effectiveBreak = requiredBreak
            netMinutes = totalGrossMinutes - effectiveBreak
        } else {
            // Multiple blocks: gaps count as breaks, top up if insufficient
            effectiveBreak = maxOf(manualBreakMinutes, requiredBreak)
            netMinutes = totalGrossMinutes - (effectiveBreak - manualBreakMinutes).coerceAtLeast(0)
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
