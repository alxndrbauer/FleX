package com.flex.domain.usecase

import com.flex.domain.model.ProjectedEndTime
import com.flex.domain.model.TimeBlock
import java.time.Duration
import java.time.LocalTime
import javax.inject.Inject

class CalculateProjectedEndTimeUseCase @Inject constructor(
    private val calculateDayWorkTime: CalculateDayWorkTimeUseCase
) {
    constructor() : this(CalculateDayWorkTimeUseCase())

    companion object {
        const val THRESHOLD_6H = 360L
        const val THRESHOLD_9H = 540L
        const val BREAK_30_MIN = 30L
        const val BREAK_45_MIN = 45L
    }

    operator fun invoke(
        timeBlocks: List<TimeBlock>,
        dailyTargetMinutes: Int,
        now: LocalTime = LocalTime.now(),
        isBusinessTrip: Boolean = false
    ): ProjectedEndTime? {
        if (dailyTargetMinutes <= 0) return null

        val sorted = timeBlocks.sortedBy { it.startTime }
        val runningBlockIndex = sorted.indexOfLast { it.endTime == null }
        if (runningBlockIndex == -1) return null
        val runningBlock = sorted[runningBlockIndex]

        val resolved = sorted.map {
            if (it.endTime == null) it.copy(endTime = now) else it
        }

        val workTimeResult = calculateDayWorkTime(resolved, isBusinessTrip)
        val remainingNetMinutes = dailyTargetMinutes.toLong() - workTimeResult.netMinutes
        if (remainingNetMinutes <= 0L) return null

        val requiredBreakMinutes: Long = when {
            dailyTargetMinutes > THRESHOLD_9H -> BREAK_45_MIN
            dailyTargetMinutes > THRESHOLD_6H -> BREAK_30_MIN
            else -> 0L
        }

        var completedGrossMinutes = 0L
        for (i in 0 until runningBlockIndex) {
            val b = sorted[i]
            val start = if (i == 0 && !isBusinessTrip) CalculateDayWorkTimeUseCase.roundDownTo5Min(b.startTime) else b.startTime
            val end = b.endTime ?: continue
            val dur = Duration.between(start, end).toMinutes()
            if (dur > 0) completedGrossMinutes += dur
        }

        var takenBreakMinutes = 0L
        for (i in 0 until runningBlockIndex) {
            val end = sorted[i].endTime ?: continue
            val nextStart = sorted[i + 1].startTime
            val gap = Duration.between(end, nextStart).toMinutes()
            if (gap > 0) takenBreakMinutes += gap
        }

        val additionalBreakToDeduct = maxOf(0L, requiredBreakMinutes - takenBreakMinutes)
        val totalGrossNeeded = dailyTargetMinutes + additionalBreakToDeduct
        val remainingRunningGross = totalGrossNeeded - completedGrossMinutes

        if (remainingRunningGross <= 0L) return null

        val runningStart = if (runningBlockIndex == 0 && !isBusinessTrip) {
            CalculateDayWorkTimeUseCase.roundDownTo5Min(runningBlock.startTime)
        } else {
            runningBlock.startTime
        }

        val projectedEndTime = runningStart.plusMinutes(remainingRunningGross)

        // The pending break is whatever statutory break has NOT YET been applied
        // (neither via taken real breaks/gaps nor via automatic ArbZG deduction)
        val effectiveAppliedBreak = maxOf(takenBreakMinutes, workTimeResult.breakMinutes)
        val pendingBreakMinutes = maxOf(0L, requiredBreakMinutes - effectiveAppliedBreak)
        val projectedEndTimeWithoutBreak = projectedEndTime.minusMinutes(pendingBreakMinutes)

        return ProjectedEndTime(
            projectedEndTime = projectedEndTime,
            projectedEndTimeWithoutBreak = projectedEndTimeWithoutBreak,
            pendingBreakMinutes = pendingBreakMinutes,
            takenBreakMinutes = effectiveAppliedBreak,
            requiredBreakMinutes = requiredBreakMinutes,
            remainingNetMinutes = remainingNetMinutes
        )
    }
}
