package com.flex.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.flex.domain.model.BreakViolationType
import com.flex.domain.model.TimeBlock
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalTime

class CheckBreakViolationUseCaseTest {

    private lateinit var useCase: CheckBreakViolationUseCase

    @BeforeEach
    fun setUp() {
        useCase = CheckBreakViolationUseCase()
    }

    // ---------------------------------------------------------------------------
    // §4 ArbZG scenario tests
    // ---------------------------------------------------------------------------

    @Test
    fun scenario1_whenFirstSegmentOver6hWithQualifyingGap_expectContinuousViolation() {
        // 6:00–12:15, 13:00–15:00
        // Gap = 45 min ≥ 15 min → qualifies as break → two segments: 6h15m and 2h
        // First segment 6h15m (375 min) > 6h → CONTINUOUS_WORK_EXCEEDS_6H
        // Total gross = 375 + 120 = 495 min (< 9h), qualifying break = 45 min ≥ 30 min → no INSUFFICIENT violation
        val timeBlocks = listOf(
            TimeBlock(1, 1, LocalTime.of(6, 0), LocalTime.of(12, 15)),
            TimeBlock(2, 1, LocalTime.of(13, 0), LocalTime.of(15, 0))
        )

        val result = useCase(timeBlocks)

        assertThat(result.skipped).isFalse()
        assertThat(result.violations).hasSize(1)
        assertThat(result.violations[0].type).isEqualTo(BreakViolationType.CONTINUOUS_WORK_EXCEEDS_6H)
        assertThat(result.violations[0].continuousWorkMinutes).isEqualTo(375)
    }

    @Test
    fun scenario2_whenTwoBlocksWithLargeGapAndTotalUnder9h_expectNoViolation() {
        // 6:00–7:00, 9:00–15:00
        // Gap = 120 min ≥ 15 min → qualifies → two segments: 1h and 6h (exactly 6h, not > 6h)
        // Total gross = 60 + 360 = 420 min (= 7h, ≥ 6h), qualifying break = 120 min ≥ 30 min
        val timeBlocks = listOf(
            TimeBlock(1, 1, LocalTime.of(6, 0), LocalTime.of(7, 0)),
            TimeBlock(2, 1, LocalTime.of(9, 0), LocalTime.of(15, 0))
        )

        val result = useCase(timeBlocks)

        assertThat(result.skipped).isFalse()
        assertThat(result.violations).isEmpty()
    }

    @Test
    fun scenario3_whenSecondSegmentOver6h_expectContinuousViolation() {
        // 7:00–9:00, 9:30–16:15
        // Gap = 30 min ≥ 15 min → qualifies → two segments: 2h and 6h45m
        // Second segment 6h45m (405 min) > 6h → CONTINUOUS_WORK_EXCEEDS_6H
        // Total gross = 120 + 405 = 525 min (< 9h), qualifying break = 30 min ≥ 30 min → no INSUFFICIENT violation
        val timeBlocks = listOf(
            TimeBlock(1, 1, LocalTime.of(7, 0), LocalTime.of(9, 0)),
            TimeBlock(2, 1, LocalTime.of(9, 30), LocalTime.of(16, 15))
        )

        val result = useCase(timeBlocks)

        assertThat(result.skipped).isFalse()
        assertThat(result.violations).hasSize(1)
        assertThat(result.violations[0].type).isEqualTo(BreakViolationType.CONTINUOUS_WORK_EXCEEDS_6H)
        assertThat(result.violations[0].continuousWorkMinutes).isEqualTo(405)
    }

    @Test
    fun scenario4_whenThreeBlocksWithExactly45minBreakAndExactly6hSegment_expectNoViolation() {
        // 7:00–9:00, 9:30–15:30, 15:45–17:00
        // Gap1 = 30 min ≥ 15 min → qualifies; Gap2 = 15 min ≥ 15 min → qualifies
        // Segments: 2h, 6h (exactly 360 min, NOT > 6h), 1h15m
        // Total gross = 120 + 360 + 75 = 555 min (> 9h)
        // Qualifying breaks = 30 + 15 = 45 min ≥ 45 min → no INSUFFICIENT_TOTAL_BREAK_9H
        val timeBlocks = listOf(
            TimeBlock(1, 1, LocalTime.of(7, 0), LocalTime.of(9, 0)),
            TimeBlock(2, 1, LocalTime.of(9, 30), LocalTime.of(15, 30)),
            TimeBlock(3, 1, LocalTime.of(15, 45), LocalTime.of(17, 0))
        )

        val result = useCase(timeBlocks)

        assertThat(result.skipped).isFalse()
        assertThat(result.violations).isEmpty()
    }

    @Test
    fun scenario5_whenTotalOver9hWithSufficientBreaks_expectNoViolation() {
        // 6:00–8:00, 9:00–13:00, 14:00–18:00
        // Gap1 = 60 min ≥ 15 min → qualifies; Gap2 = 60 min ≥ 15 min → qualifies
        // Segments: 2h, 4h, 4h (all ≤ 6h)
        // Total gross = 120 + 240 + 240 = 600 min (= 10h > 9h)
        // Qualifying breaks = 60 + 60 = 120 min ≥ 45 min → no violation
        val timeBlocks = listOf(
            TimeBlock(1, 1, LocalTime.of(6, 0), LocalTime.of(8, 0)),
            TimeBlock(2, 1, LocalTime.of(9, 0), LocalTime.of(13, 0)),
            TimeBlock(3, 1, LocalTime.of(14, 0), LocalTime.of(18, 0))
        )

        val result = useCase(timeBlocks)

        assertThat(result.skipped).isFalse()
        assertThat(result.violations).isEmpty()
    }

    @Test
    fun scenario6_whenFirstSegmentOver6hAndInsufficientBreakFor10hWork_expectTwoViolations() {
        // 5:00–13:00, 13:30–15:30
        // Gap = 30 min ≥ 15 min → qualifies → two segments: 8h (480 min) and 2h
        // First segment 8h (480 min) > 6h → CONTINUOUS_WORK_EXCEEDS_6H
        // Total gross = 480 + 120 = 600 min (> 9h), qualifying break = 30 min < 45 min → INSUFFICIENT_TOTAL_BREAK_9H
        val timeBlocks = listOf(
            TimeBlock(1, 1, LocalTime.of(5, 0), LocalTime.of(13, 0)),
            TimeBlock(2, 1, LocalTime.of(13, 30), LocalTime.of(15, 30))
        )

        val result = useCase(timeBlocks)

        assertThat(result.skipped).isFalse()
        assertThat(result.violations).hasSize(2)
        val types = result.violations.map { it.type }
        assertThat(types).contains(BreakViolationType.CONTINUOUS_WORK_EXCEEDS_6H)
        assertThat(types).contains(BreakViolationType.INSUFFICIENT_TOTAL_BREAK_9H)

        val breakViolation = result.violations.first { it.type == BreakViolationType.INSUFFICIENT_TOTAL_BREAK_9H }
        assertThat(breakViolation.actualBreakMinutes).isEqualTo(30)
        assertThat(breakViolation.requiredBreakMinutes).isEqualTo(45)
    }

    // ---------------------------------------------------------------------------
    // Additional edge-case tests
    // ---------------------------------------------------------------------------

    @Test
    fun whenEmptyList_expectNoViolationsAndNotSkipped() {
        val result = useCase(emptyList())

        assertThat(result.skipped).isFalse()
        assertThat(result.violations).isEmpty()
    }

    @Test
    fun whenAnyBlockIsDuration_expectSkipped() {
        val timeBlocks = listOf(
            TimeBlock(1, 1, LocalTime.of(9, 0), LocalTime.of(17, 0), isDuration = true)
        )

        val result = useCase(timeBlocks)

        assertThat(result.skipped).isTrue()
        assertThat(result.violations).isEmpty()
    }

    @Test
    fun whenMixedDurationAndTimeRange_expectSkipped() {
        val timeBlocks = listOf(
            TimeBlock(1, 1, LocalTime.of(9, 0), LocalTime.of(12, 0), isDuration = false),
            TimeBlock(2, 1, LocalTime.of(13, 0), LocalTime.of(17, 0), isDuration = true)
        )

        val result = useCase(timeBlocks)

        assertThat(result.skipped).isTrue()
        assertThat(result.violations).isEmpty()
    }

    @Test
    fun whenSingleRunningBlockStarted7hAgo_expectSkipped() {
        // Single running block → automatic break deduction applies → skip (no warning)
        val fixedNow = LocalTime.of(15, 0)
        val startTime = fixedNow.minusHours(7) // 8:00

        val timeBlocks = listOf(
            TimeBlock(1, 1, startTime, endTime = null)
        )

        val result = useCase(timeBlocks, now = fixedNow)

        assertThat(result.skipped).isTrue()
        assertThat(result.violations).isEmpty()
    }

    @Test
    fun whenGapIs14Minutes_gapDoesNotQualifyAndBlocksAreContinuous() {
        // 8:00–14:01, 14:15–16:00
        // Gap = 14 min < 15 min → does NOT qualify → single segment: 8:00–16:00 = 480 min > 6h
        val timeBlocks = listOf(
            TimeBlock(1, 1, LocalTime.of(8, 0), LocalTime.of(14, 1)),
            TimeBlock(2, 1, LocalTime.of(14, 15), LocalTime.of(16, 0))
        )

        val result = useCase(timeBlocks)

        assertThat(result.skipped).isFalse()
        // Merged into one segment of 480 min → CONTINUOUS_WORK_EXCEEDS_6H
        assertThat(result.violations.map { it.type })
            .contains(BreakViolationType.CONTINUOUS_WORK_EXCEEDS_6H)
    }

    @Test
    fun whenGapIs15Minutes_gapQualifiesAndBreaksSplitsSegments() {
        // 8:00–14:00, 14:15–16:00
        // Gap = 15 min ≥ 15 min → qualifies → two segments: 6h (360 min, NOT > 6h) and 1h45m
        // Total gross = 360 + 105 = 465 min (< 9h, ≥ 6h), qualifying break = 15 min < 30 min → INSUFFICIENT_TOTAL_BREAK
        val timeBlocks = listOf(
            TimeBlock(1, 1, LocalTime.of(8, 0), LocalTime.of(14, 0)),
            TimeBlock(2, 1, LocalTime.of(14, 15), LocalTime.of(16, 0))
        )

        val result = useCase(timeBlocks)

        assertThat(result.skipped).isFalse()
        // No CONTINUOUS violation (both segments ≤ 6h)
        assertThat(result.violations.map { it.type })
            .doesNotContain(BreakViolationType.CONTINUOUS_WORK_EXCEEDS_6H)
        // INSUFFICIENT_TOTAL_BREAK because 15 min < 30 min required
        assertThat(result.violations.map { it.type })
            .contains(BreakViolationType.INSUFFICIENT_TOTAL_BREAK)

        val breakViolation = result.violations.first { it.type == BreakViolationType.INSUFFICIENT_TOTAL_BREAK }
        assertThat(breakViolation.actualBreakMinutes).isEqualTo(15)
        assertThat(breakViolation.requiredBreakMinutes).isEqualTo(30)
    }

    @Test
    fun whenSingleBlockExactly6h_expectSkipped() {
        // Single block → automatic break deduction applies → skip
        val timeBlocks = listOf(
            TimeBlock(1, 1, LocalTime.of(9, 0), LocalTime.of(15, 0))
        )

        val result = useCase(timeBlocks)

        assertThat(result.skipped).isTrue()
        assertThat(result.violations).isEmpty()
    }

    @Test
    fun whenSingleBlockUnder6h_expectSkipped() {
        // Single block → automatic break deduction applies → skip
        val timeBlocks = listOf(
            TimeBlock(1, 1, LocalTime.of(9, 0), LocalTime.of(14, 0))
        )

        val result = useCase(timeBlocks)

        assertThat(result.skipped).isTrue()
        assertThat(result.violations).isEmpty()
    }

    @Test
    fun whenSingleRunningBlock_expectSkipped() {
        // Single running block (no endTime) → single block after resolution → skip
        val now = LocalTime.of(16, 0)
        val timeBlocks = listOf(
            TimeBlock(1, 1, LocalTime.of(8, 0), null)
        )

        val result = useCase(timeBlocks, now)

        assertThat(result.skipped).isTrue()
        assertThat(result.violations).isEmpty()
    }
}
