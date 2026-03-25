package com.flex.domain.usecase

import com.flex.domain.model.BreakCheckResult
import com.flex.domain.model.BreakViolation
import com.flex.domain.model.BreakViolationType
import com.flex.domain.model.TimeBlock
import java.time.Duration
import java.time.LocalTime
import javax.inject.Inject

class CheckBreakViolationUseCase @Inject constructor() {

    companion object {
        const val MIN_QUALIFYING_BREAK_MINUTES = 15L
        const val WORK_THRESHOLD_6H = 360L
        const val WORK_THRESHOLD_9H = 540L
        const val REQUIRED_BREAK_6H = 30L
        const val REQUIRED_BREAK_9H = 45L
    }

    operator fun invoke(
        timeBlocks: List<TimeBlock>,
        now: LocalTime = LocalTime.now()
    ): BreakCheckResult {
        // EXCLUSION: if any block has isDuration = true, skip check entirely
        if (timeBlocks.any { it.isDuration }) {
            return BreakCheckResult(violations = emptyList(), skipped = true)
        }

        // Resolve running blocks (endTime == null) to `now`, skip zero-duration blocks
        val resolved = timeBlocks
            .map { if (it.endTime == null) it.copy(endTime = now) else it }
            .filter { it.endTime != null && Duration.between(it.startTime, it.endTime).toMinutes() > 0 }
            .sortedBy { it.startTime }

        if (resolved.isEmpty()) return BreakCheckResult(emptyList(), skipped = false)

        // EXCLUSION: single block → automatic break deduction applies, no warning needed
        if (resolved.size == 1) return BreakCheckResult(emptyList(), skipped = true)

        // Build continuous segments: merge blocks where gap < 15 min
        val segments = mutableListOf<Pair<LocalTime, LocalTime>>()
        var segStart = resolved[0].startTime
        var segEnd = resolved[0].endTime!!
        for (i in 1 until resolved.size) {
            val gapMinutes = Duration.between(segEnd, resolved[i].startTime).toMinutes()
            if (gapMinutes >= MIN_QUALIFYING_BREAK_MINUTES) {
                segments.add(segStart to segEnd)
                segStart = resolved[i].startTime
            }
            // Update segEnd to the furthest end time in this segment
            if (resolved[i].endTime!! > segEnd) {
                segEnd = resolved[i].endTime!!
            }
        }
        segments.add(segStart to segEnd)

        val violations = mutableListOf<BreakViolation>()

        // Rule 1: any continuous segment > 6h (360 min) without a qualifying break
        for ((start, end) in segments) {
            val segMinutes = Duration.between(start, end).toMinutes()
            if (segMinutes > WORK_THRESHOLD_6H) {
                violations += BreakViolation(
                    type = BreakViolationType.CONTINUOUS_WORK_EXCEEDS_6H,
                    continuousWorkMinutes = segMinutes
                )
            }
        }

        // Calculate total gross work and qualifying break minutes
        val totalGrossMinutes = resolved.sumOf {
            Duration.between(it.startTime, it.endTime!!).toMinutes()
        }
        var qualifyingBreakMinutes = 0L
        for (i in 0 until resolved.size - 1) {
            val gap = Duration.between(resolved[i].endTime!!, resolved[i + 1].startTime).toMinutes()
            if (gap >= MIN_QUALIFYING_BREAK_MINUTES) {
                qualifyingBreakMinutes += gap
            }
        }

        // Rule 2: total work >= 6h → need >= 30 min qualifying break
        // Rule 3: total work > 9h → need >= 45 min qualifying break (supersedes Rule 2)
        if (totalGrossMinutes > WORK_THRESHOLD_9H && qualifyingBreakMinutes < REQUIRED_BREAK_9H) {
            violations += BreakViolation(
                type = BreakViolationType.INSUFFICIENT_TOTAL_BREAK_9H,
                actualBreakMinutes = qualifyingBreakMinutes,
                requiredBreakMinutes = REQUIRED_BREAK_9H
            )
        } else if (totalGrossMinutes >= WORK_THRESHOLD_6H && qualifyingBreakMinutes < REQUIRED_BREAK_6H) {
            violations += BreakViolation(
                type = BreakViolationType.INSUFFICIENT_TOTAL_BREAK,
                actualBreakMinutes = qualifyingBreakMinutes,
                requiredBreakMinutes = REQUIRED_BREAK_6H
            )
        }

        return BreakCheckResult(violations = violations, skipped = false)
    }
}
