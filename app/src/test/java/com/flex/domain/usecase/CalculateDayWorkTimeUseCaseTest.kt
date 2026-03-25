package com.flex.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.flex.domain.model.TimeBlock
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

    @Test
    fun testTwoCompletedBlocksWhenTotalOver9HAndGapCovers45MinBreakExpectNoDeduction() {
        // Büro 8:20-14:34 (374 min), HO 15:30-19:00 (210 min) = 584 min gross
        // Total > 9h (540 min) → requires 45 min break
        // Gap = 56 min > 45 min → gap covers the required break → no deduction
        val timeBlocks = listOf(
            TimeBlock(1, 1, LocalTime.of(8, 20), LocalTime.of(14, 34), isDuration = false),
            TimeBlock(2, 1, LocalTime.of(15, 30), LocalTime.of(19, 0), isDuration = false)
        )

        val result = useCase(timeBlocks)

        assertThat(result.grossMinutes).isEqualTo(584)
        assertThat(result.netMinutes).isEqualTo(584) // gap covers required 45-min break, no deduction
        assertThat(result.breakMinutes).isEqualTo(56) // the actual gap
        assertThat(result.exceedsMaxHours).isFalse()
    }

    @Test
    fun testTwoCompletedBlocksWhenTotalOver9HAndGapUnder45MinExpectTopUp() {
        // Büro 8:20-14:34 (374 min), HO 15:00-19:00 (240 min) = 614 min gross
        // Total > 9h (540 min) → requires 45 min break
        // Gap = 26 min < 45 min → top-up 19 min needed
        val timeBlocks = listOf(
            TimeBlock(1, 1, LocalTime.of(8, 20), LocalTime.of(14, 34), isDuration = false),
            TimeBlock(2, 1, LocalTime.of(15, 0), LocalTime.of(19, 0), isDuration = false)
        )

        val result = useCase(timeBlocks)

        assertThat(result.grossMinutes).isEqualTo(614)
        assertThat(result.netMinutes).isEqualTo(595) // 614 - 19 (top-up: 45 required - 26 gap)
        assertThat(result.breakMinutes).isEqualTo(45)
        assertThat(result.exceedsMaxHours).isFalse()
    }

    @Test
    fun testMultipleBlocksWhenFirstOver6HAndGapCoversBreakWithRunningSecondExpectNoDeduction() {
        // Block 1: 9:00-16:00 (7h, completed), 45min gap, Block 2: 16:45-running
        // Gap of 45min covers the required 30min break → no additional deduction
        val timeBlocks = listOf(
            TimeBlock(1, 1, LocalTime.of(9, 0), LocalTime.of(16, 0), isDuration = false),
            TimeBlock(2, 1, LocalTime.of(16, 45), null, isDuration = false)
        )

        val result = useCase(timeBlocks)

        assertThat(result.grossMinutes).isEqualTo(420) // only completed block counts
        assertThat(result.netMinutes).isEqualTo(420)   // gap covers break, no deduction
        assertThat(result.breakMinutes).isEqualTo(45)
        assertThat(result.exceedsMaxHours).isFalse()
    }

    @Test
    fun testMultipleBlocksWhenFirstOver6HAndGapUnderBreakWithRunningSecondExpectTopUp() {
        // Block 1: 9:00-16:00 (7h, completed), 15min gap, Block 2: 16:15-running
        // Gap of 15min < required 30min → top up by 15min
        val timeBlocks = listOf(
            TimeBlock(1, 1, LocalTime.of(9, 0), LocalTime.of(16, 0), isDuration = false),
            TimeBlock(2, 1, LocalTime.of(16, 15), null, isDuration = false)
        )

        val result = useCase(timeBlocks)

        assertThat(result.grossMinutes).isEqualTo(420)
        assertThat(result.netMinutes).isEqualTo(405) // 420 - 15 top-up
        assertThat(result.breakMinutes).isEqualTo(30)
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

    // 5-Minuten-Rundung tests

    @Test
    fun testRoundingWhenSingleBlockNotOnBoundaryExpectRoundedGross() {
        // 8:39–16:56 → adjusted: 8:35–17:00 → gross = 505 min
        val timeBlock = TimeBlock(
            id = 1,
            workDayId = 1,
            startTime = LocalTime.of(8, 39),
            endTime = LocalTime.of(16, 56),
            isDuration = false
        )

        val result = useCase(listOf(timeBlock))

        assertThat(result.grossMinutes).isEqualTo(505) // 17:00 - 8:35 = 8h25min
        assertThat(result.netMinutes).isEqualTo(475)   // 505 - 30 (>6h break)
        assertThat(result.breakMinutes).isEqualTo(30)
    }

    @Test
    fun testRoundingWhenTwoBlocksExpectOnlyFirstStartAndLastEndRounded() {
        // 8:39–12:13 + 13:02–15:38 → adjusted: 8:35–12:13 + 13:02–15:40
        // Gross: (12:13-8:35)=218 + (15:40-13:02)=158 = 376 min
        // Gap: 13:02-12:13 = 49 min > required 30 min → no deduction
        val timeBlocks = listOf(
            TimeBlock(1, 1, LocalTime.of(8, 39), LocalTime.of(12, 13), isDuration = false),
            TimeBlock(2, 1, LocalTime.of(13, 2), LocalTime.of(15, 38), isDuration = false)
        )

        val result = useCase(timeBlocks)

        assertThat(result.grossMinutes).isEqualTo(376)
        assertThat(result.netMinutes).isEqualTo(376) // gap covers required break
        assertThat(result.breakMinutes).isEqualTo(49) // the actual gap
    }

    @Test
    fun testRoundingWhenAlreadyOnBoundaryExpectNoChange() {
        // 8:35–17:00 → already on boundary, no change → gross = 505 min
        val timeBlock = TimeBlock(
            id = 1,
            workDayId = 1,
            startTime = LocalTime.of(8, 35),
            endTime = LocalTime.of(17, 0),
            isDuration = false
        )

        val result = useCase(listOf(timeBlock))

        assertThat(result.grossMinutes).isEqualTo(505)
        assertThat(result.netMinutes).isEqualTo(475) // 505 - 30
        assertThat(result.breakMinutes).isEqualTo(30)
    }

    @Test
    fun testRoundingWhenDurationBlockExpectNoRounding() {
        // Duration block 8:39–16:56 → isDuration=true → no rounding applied
        // Gross = 16:56-8:39 = 497 min, no break deduction
        val timeBlock = TimeBlock(
            id = 1,
            workDayId = 1,
            startTime = LocalTime.of(8, 39),
            endTime = LocalTime.of(16, 56),
            isDuration = true
        )

        val result = useCase(listOf(timeBlock))

        assertThat(result.grossMinutes).isEqualTo(497)
        assertThat(result.netMinutes).isEqualTo(497) // duration: no break deducted
        assertThat(result.breakMinutes).isEqualTo(0)
    }

    @Test
    fun testRoundingWhenRunningLastBlockExpectOnlyStartRounded() {
        // Block 1: 8:39–12:00 (completed), Block 2: 12:30–null (running)
        // Adjusted[0]: start 8:39→8:35 (index 0), end 12:00 unchanged
        // Adjusted[1]: start 12:30 (on boundary), end null → no rounding
        // Gross = 12:00-8:35 = 205 min (vs. unrounded 201 min)
        val timeBlocks = listOf(
            TimeBlock(1, 1, LocalTime.of(8, 39), LocalTime.of(12, 0), isDuration = false),
            TimeBlock(2, 1, LocalTime.of(12, 30), null, isDuration = false)
        )

        val result = useCase(timeBlocks)

        assertThat(result.grossMinutes).isEqualTo(205)
    }

    // Gestaffelte Pausenlogik tests

    @Test
    fun testGraduatedBreakWhen1MinOver6HoursExpect1MinBreak() {
        // 6h 01m gross (361 min): stage1 = min(1, 30) = 1, net = 360
        // Single block can't yield 361 min (end rounds to 5-min boundary).
        // Two blocks with no gap: block 1 end is not rounded because it's not the last index.
        val timeBlocks = listOf(
            TimeBlock(1, 1, LocalTime.of(8, 0), LocalTime.of(14, 1), isDuration = false),
            TimeBlock(2, 1, LocalTime.of(14, 1), null, isDuration = false)
        )

        val result = useCase(timeBlocks)

        assertThat(result.grossMinutes).isEqualTo(361)
        assertThat(result.breakMinutes).isEqualTo(1)
        assertThat(result.netMinutes).isEqualTo(360)
    }

    @Test
    fun testGraduatedBreakWhenJustOver6HoursExpectPartialStage1() {
        // 6h 05m gross (365 min): stage1 = min(5, 30) = 5, net = 360
        val timeBlock = TimeBlock(
            id = 1,
            workDayId = 1,
            startTime = LocalTime.of(8, 0),
            endTime = LocalTime.of(14, 5),
            isDuration = false
        )

        val result = useCase(listOf(timeBlock))

        assertThat(result.grossMinutes).isEqualTo(365)
        assertThat(result.breakMinutes).isEqualTo(5)
        assertThat(result.netMinutes).isEqualTo(360)
    }

    @Test
    fun testGraduatedBreakWhenOver6HoursExpect15MinStage1() {
        // 6h 15m gross (375 min): stage1 = min(15, 30) = 15, net = 360
        val timeBlock = TimeBlock(
            id = 1,
            workDayId = 1,
            startTime = LocalTime.of(8, 0),
            endTime = LocalTime.of(14, 15),
            isDuration = false
        )

        val result = useCase(listOf(timeBlock))

        assertThat(result.grossMinutes).isEqualTo(375)
        assertThat(result.breakMinutes).isEqualTo(15)
        assertThat(result.netMinutes).isEqualTo(360)
    }

    @Test
    fun testGraduatedBreakWhenAtStage2ThresholdExpectNoStage2() {
        // 9h 30m gross (570 min): stage1=30, afterStage1=540 (not >540), stage2=0, break=30, net=540
        val timeBlock = TimeBlock(
            id = 1,
            workDayId = 1,
            startTime = LocalTime.of(8, 0),
            endTime = LocalTime.of(17, 30),
            isDuration = false
        )

        val result = useCase(listOf(timeBlock))

        assertThat(result.grossMinutes).isEqualTo(570)
        assertThat(result.breakMinutes).isEqualTo(30)
        assertThat(result.netMinutes).isEqualTo(540)
    }

    @Test
    fun testGraduatedBreakWhenSlightlyOver9hNetExpectPartialStage2() {
        // 9h 35m gross (575 min): stage1=30, afterStage1=545>540, stage2=min(5,15)=5, break=35, net=540
        val timeBlock = TimeBlock(
            id = 1,
            workDayId = 1,
            startTime = LocalTime.of(8, 0),
            endTime = LocalTime.of(17, 35),
            isDuration = false
        )

        val result = useCase(listOf(timeBlock))

        assertThat(result.grossMinutes).isEqualTo(575)
        assertThat(result.breakMinutes).isEqualTo(35)
        assertThat(result.netMinutes).isEqualTo(540)
    }

    @Test
    fun testGraduatedBreakWhenOver9h40mGrossExpect10MinStage2() {
        // 9h 40m gross (580 min): stage1=30, afterStage1=550>540, stage2=min(10,15)=10, break=40, net=540
        val timeBlock = TimeBlock(
            id = 1,
            workDayId = 1,
            startTime = LocalTime.of(8, 0),
            endTime = LocalTime.of(17, 40),
            isDuration = false
        )

        val result = useCase(listOf(timeBlock))

        assertThat(result.grossMinutes).isEqualTo(580)
        assertThat(result.breakMinutes).isEqualTo(40)
        assertThat(result.netMinutes).isEqualTo(540)
    }

    @Test
    fun testGraduatedBreakWhenStage2FullExpect45MinTotalBreak() {
        // 9h 45m gross (585 min): stage1=30, afterStage1=555>540, stage2=min(15,15)=15, break=45, net=540
        val timeBlock = TimeBlock(
            id = 1,
            workDayId = 1,
            startTime = LocalTime.of(8, 0),
            endTime = LocalTime.of(17, 45),
            isDuration = false
        )

        val result = useCase(listOf(timeBlock))

        assertThat(result.grossMinutes).isEqualTo(585)
        assertThat(result.breakMinutes).isEqualTo(45)
        assertThat(result.netMinutes).isEqualTo(540)
    }

    // Helper function to invoke use case with single block
    private operator fun CalculateDayWorkTimeUseCase.invoke(timeBlock: TimeBlock): DayWorkTimeResult {
        return this.invoke(listOf(timeBlock))
    }
}
