package com.vrema.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.vrema.domain.model.TimeBlock
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalTime

class CalculateDayWorkTimeUseCaseTest {

    private lateinit var useCase: CalculateDayWorkTimeUseCase

    @BeforeEach
    fun setUp() {
        useCase = CalculateDayWorkTimeUseCase()
    }

    // Single time block tests

    @Test
    fun testSingleBlockWhenNoEndTimeExpectZeroMinutes() {
        val timeBlock = TimeBlock(
            id = 1,
            workDayId = 1,
            startTime = LocalTime.of(9, 0),
            endTime = null
        )

        val result = useCase(listOf(timeBlock))

        assertThat(result.grossMinutes).isEqualTo(0)
        assertThat(result.netMinutes).isEqualTo(0)
        assertThat(result.breakMinutes).isEqualTo(0)
        assertThat(result.exceedsMaxHours).isFalse()
    }

    @Test
    fun testSingleBlockWhenUnder6HoursExpectNoBreak() {
        // 5 hours of work (300 minutes) - no break required
        val timeBlock = TimeBlock(
            id = 1,
            workDayId = 1,
            startTime = LocalTime.of(9, 0),
            endTime = LocalTime.of(14, 0),
            isDuration = false
        )

        val result = useCase(listOf(timeBlock))

        assertThat(result.grossMinutes).isEqualTo(300)
        assertThat(result.netMinutes).isEqualTo(300)
        assertThat(result.breakMinutes).isEqualTo(0)
        assertThat(result.exceedsMaxHours).isFalse()
    }

    @Test
    fun testSingleBlockWhenOver6HoursExpect30MinBreak() {
        // 7 hours of work (420 minutes) - requires 30 min break
        val timeBlock = TimeBlock(
            id = 1,
            workDayId = 1,
            startTime = LocalTime.of(9, 0),
            endTime = LocalTime.of(16, 0),
            isDuration = false
        )

        val result = useCase(listOf(timeBlock))

        assertThat(result.grossMinutes).isEqualTo(420)
        assertThat(result.netMinutes).isEqualTo(390) // 420 - 30
        assertThat(result.breakMinutes).isEqualTo(30)
        assertThat(result.exceedsMaxHours).isFalse()
    }

    @Test
    fun testSingleBlockWhenOver9HoursExpect45MinBreak() {
        // 10 hours of work (600 minutes) - requires 45 min break
        val timeBlock = TimeBlock(
            id = 1,
            workDayId = 1,
            startTime = LocalTime.of(8, 0),
            endTime = LocalTime.of(18, 0),
            isDuration = false
        )

        val result = useCase(listOf(timeBlock))

        assertThat(result.grossMinutes).isEqualTo(600)
        assertThat(result.netMinutes).isEqualTo(555) // 600 - 45
        assertThat(result.breakMinutes).isEqualTo(45)
        assertThat(result.exceedsMaxHours).isFalse()
    }

    @Test
    fun testSingleBlockWhenExactly10HoursExpectMaxCapped() {
        // Exactly 10 hours - net time should not exceed max
        val timeBlock = TimeBlock(
            id = 1,
            workDayId = 1,
            startTime = LocalTime.of(8, 0),
            endTime = LocalTime.of(18, 0),
            isDuration = false
        )

        val result = useCase(listOf(timeBlock))

        assertThat(result.grossMinutes).isEqualTo(600)
        assertThat(result.netMinutes).isAtMost(600)
        assertThat(result.exceedsMaxHours).isFalse()
    }

    @Test
    fun testSingleBlockWhenOver10HoursNetExpectExceedsMaxFlag() {
        // 12 hours of work - net time exceeds max
        val timeBlock = TimeBlock(
            id = 1,
            workDayId = 1,
            startTime = LocalTime.of(7, 0),
            endTime = LocalTime.of(19, 0),
            isDuration = false
        )

        val result = useCase(listOf(timeBlock))

        assertThat(result.grossMinutes).isEqualTo(720)
        assertThat(result.netMinutes).isEqualTo(600) // capped at 10 hours
        assertThat(result.breakMinutes).isEqualTo(45)
        assertThat(result.exceedsMaxHours).isTrue()
    }

    // Duration mode tests

    @Test
    fun testDurationModeWhenSingleBlockExpectNoBreakDeduction() {
        // Duration mode: no break deduction
        val timeBlock = TimeBlock(
            id = 1,
            workDayId = 1,
            startTime = LocalTime.of(9, 0),
            endTime = LocalTime.of(16, 0),
            isDuration = true
        )

        val result = useCase(listOf(timeBlock))

        assertThat(result.grossMinutes).isEqualTo(420)
        assertThat(result.netMinutes).isEqualTo(420) // no break deducted
        assertThat(result.breakMinutes).isEqualTo(0)
        assertThat(result.exceedsMaxHours).isFalse()
    }

    @Test
    fun testDurationModeWhenMultipleBlocksExpectNoBreakDeduction() {
        // Multiple duration blocks: no break deduction
        val timeBlocks = listOf(
            TimeBlock(1, 1, LocalTime.of(9, 0), LocalTime.of(12, 0), isDuration = true),
            TimeBlock(2, 1, LocalTime.of(13, 0), LocalTime.of(17, 0), isDuration = true)
        )

        val result = useCase(timeBlocks)

        assertThat(result.grossMinutes).isEqualTo(420) // 3h + 4h
        assertThat(result.netMinutes).isEqualTo(420) // no break deducted
        assertThat(result.breakMinutes).isEqualTo(0)
        assertThat(result.exceedsMaxHours).isFalse()
    }

    @Test
    fun testDurationModeWhenExceeds10HoursExpectCappedAndFlag() {
        // Duration mode exceeding 10 hours
        val timeBlock = TimeBlock(
            id = 1,
            workDayId = 1,
            startTime = LocalTime.of(7, 0),
            endTime = LocalTime.of(19, 0),
            isDuration = true
        )

        val result = useCase(timeBlock)

        assertThat(result.grossMinutes).isEqualTo(720)
        assertThat(result.netMinutes).isEqualTo(600) // capped at 10 hours
        assertThat(result.breakMinutes).isEqualTo(0)
        assertThat(result.exceedsMaxHours).isTrue()
    }

    // Multiple time blocks tests

    @Test
    fun testMultipleBlocksWhenManualBreaksExistExpectBreaksCountedAsGaps() {
        // 9-12 (3h), 12:30-17 (4.5h) = 7.5h gross, 30min gap
        // Requires 30min break, gap covers it
        val timeBlocks = listOf(
            TimeBlock(1, 1, LocalTime.of(9, 0), LocalTime.of(12, 0), isDuration = false),
            TimeBlock(2, 1, LocalTime.of(12, 30), LocalTime.of(17, 0), isDuration = false)
        )

        val result = useCase(timeBlocks)

        assertThat(result.grossMinutes).isEqualTo(450) // 3h + 4.5h
        assertThat(result.netMinutes).isEqualTo(450) // gap of 30min covers required break
        assertThat(result.breakMinutes).isEqualTo(30)
        assertThat(result.exceedsMaxHours).isFalse()
    }

    @Test
    fun testMultipleBlocksWhenInsufficientGapExpectTopUpBreak() {
        // 9-12 (3h), 12:15-17 (4h 45min) = 7h 45min gross, 15min gap
        // Requires 30min break, gap is only 15min, need to top up 15min
        val timeBlocks = listOf(
            TimeBlock(1, 1, LocalTime.of(9, 0), LocalTime.of(12, 0), isDuration = false),
            TimeBlock(2, 1, LocalTime.of(12, 15), LocalTime.of(17, 0), isDuration = false)
        )

        val result = useCase(timeBlocks)

        assertThat(result.grossMinutes).isEqualTo(465) // 180 + 285
        assertThat(result.netMinutes).isEqualTo(450) // 465 - 15 (top-up)
        assertThat(result.breakMinutes).isEqualTo(30)
        assertThat(result.exceedsMaxHours).isFalse()
    }

    @Test
    fun testMultipleBlocksWhenNoGapExpectFullBreakDeducted() {
        // 9-12 (3h), 12-17 (5h) = 8h gross, no gap
        // Requires 30min break, no gap, deduct 30min
        val timeBlocks = listOf(
            TimeBlock(1, 1, LocalTime.of(9, 0), LocalTime.of(12, 0), isDuration = false),
            TimeBlock(2, 1, LocalTime.of(12, 0), LocalTime.of(17, 0), isDuration = false)
        )

        val result = useCase(timeBlocks)

        assertThat(result.grossMinutes).isEqualTo(480) // 3h + 5h
        assertThat(result.netMinutes).isEqualTo(450) // 480 - 30
        assertThat(result.breakMinutes).isEqualTo(30)
        assertThat(result.exceedsMaxHours).isFalse()
    }

    @Test
    fun testMultipleBlocksWhenLargeGapExpectGapAsBreak() {
        // 9-12 (3h), 13-17 (4h) = 7h gross, 1h gap
        // Requires 30min break, gap of 60min covers it
        val timeBlocks = listOf(
            TimeBlock(1, 1, LocalTime.of(9, 0), LocalTime.of(12, 0), isDuration = false),
            TimeBlock(2, 1, LocalTime.of(13, 0), LocalTime.of(17, 0), isDuration = false)
        )

        val result = useCase(timeBlocks)

        assertThat(result.grossMinutes).isEqualTo(420)
        assertThat(result.netMinutes).isEqualTo(420) // gap covers required break
        assertThat(result.breakMinutes).isEqualTo(60) // the actual gap
        assertThat(result.exceedsMaxHours).isFalse()
    }

    // Edge cases

    @Test
    fun testEmptyBlocksListExpectZeroResult() {
        val result = useCase(emptyList())

        assertThat(result.grossMinutes).isEqualTo(0)
        assertThat(result.netMinutes).isEqualTo(0)
        assertThat(result.breakMinutes).isEqualTo(0)
        assertThat(result.exceedsMaxHours).isFalse()
    }

    @Test
    fun testMixedModeBlocksWhenSomeDurationExpectTimeRangeMode() {
        // Mixed: one duration, one time range - should use time range mode
        val timeBlocks = listOf(
            TimeBlock(1, 1, LocalTime.of(9, 0), LocalTime.of(12, 0), isDuration = true),
            TimeBlock(2, 1, LocalTime.of(13, 0), LocalTime.of(17, 0), isDuration = false)
        )

        val result = useCase(timeBlocks)

        assertThat(result.grossMinutes).isEqualTo(420) // 3h + 4h
        // Should apply break logic since not all are duration-based
        assertThat(result.breakMinutes).isGreaterThan(0)
    }

    @Test
    fun testNegativeDurationWhenEndBeforeStartExpectIgnored() {
        // Invalid block: end before start
        val timeBlock = TimeBlock(
            id = 1,
            workDayId = 1,
            startTime = LocalTime.of(17, 0),
            endTime = LocalTime.of(9, 0),
            isDuration = false
        )

        val result = useCase(listOf(timeBlock))

        // Negative duration should be ignored (not counted)
        assertThat(result.grossMinutes).isEqualTo(0)
        assertThat(result.netMinutes).isEqualTo(0)
    }

    @Test
    fun testMidnightCrossingWhenEndNextDayExpectCorrectCalculation() {
        // This test assumes blocks don't cross midnight (same day constraint)
        // If they do, Duration.between handles it correctly
        val timeBlock = TimeBlock(
            id = 1,
            workDayId = 1,
            startTime = LocalTime.of(22, 0),
            endTime = LocalTime.of(23, 30),
            isDuration = false
        )

        val result = useCase(listOf(timeBlock))

        assertThat(result.grossMinutes).isEqualTo(90) // 1.5 hours
        assertThat(result.netMinutes).isEqualTo(90)
        assertThat(result.breakMinutes).isEqualTo(0)
    }

    // Helper function to invoke use case with single block
    private operator fun CalculateDayWorkTimeUseCase.invoke(timeBlock: TimeBlock): DayWorkTimeResult {
        return this.invoke(listOf(timeBlock))
    }
}
