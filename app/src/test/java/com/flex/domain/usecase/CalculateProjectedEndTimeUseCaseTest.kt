package com.flex.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.flex.domain.model.ProjectedEndTime
import com.flex.domain.model.TimeBlock
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalTime

class CalculateProjectedEndTimeUseCaseTest {

    private lateinit var useCase: CalculateProjectedEndTimeUseCase

    @BeforeEach
    fun setUp() {
        useCase = CalculateProjectedEndTimeUseCase()
    }

    // 1. Target <= 6h (no break required)

    @Test
    fun testTargetUnder6HoursWhenClockRunningExpectNoBreakDeducted() {
        // 5 hours target (300 min), started at 08:00, now 08:00 -> projected end 13:00
        val blocks = listOf(
            TimeBlock(id = 1, workDayId = 1, startTime = LocalTime.of(8, 0), endTime = null)
        )

        val result = useCase(
            timeBlocks = blocks,
            dailyTargetMinutes = 300,
            now = LocalTime.of(8, 0)
        )

        assertThat(result).isNotNull()
        result?.let {
            assertThat(it.requiredBreakMinutes).isEqualTo(0L)
            assertThat(it.takenBreakMinutes).isEqualTo(0L)
            assertThat(it.pendingBreakMinutes).isEqualTo(0L)
            assertThat(it.hasPendingBreak).isFalse()
            assertThat(it.projectedEndTime).isEqualTo(LocalTime.of(13, 0))
            assertThat(it.projectedEndTimeWithoutBreak).isEqualTo(LocalTime.of(13, 0))
            assertThat(it.formatWithBreakHint()).isEqualTo("13:00")
            assertThat(it.formatCompact()).isEqualTo("13:00")
        }
    }

    @Test
    fun testTargetExactly6HoursWhenClockRunningExpectNoBreakDeducted() {
        // Exactly 6 hours target (360 min) does not exceed 6h -> no break required by ArbZG
        val blocks = listOf(
            TimeBlock(id = 1, workDayId = 1, startTime = LocalTime.of(8, 0), endTime = null)
        )

        val result = useCase(
            timeBlocks = blocks,
            dailyTargetMinutes = 360,
            now = LocalTime.of(8, 0)
        )

        assertThat(result).isNotNull()
        result?.let {
            assertThat(it.requiredBreakMinutes).isEqualTo(0L)
            assertThat(it.pendingBreakMinutes).isEqualTo(0L)
            assertThat(it.hasPendingBreak).isFalse()
            assertThat(it.projectedEndTime).isEqualTo(LocalTime.of(14, 0))
            assertThat(it.projectedEndTimeWithoutBreak).isEqualTo(LocalTime.of(14, 0))
            assertThat(it.formatWithBreakHint()).isEqualTo("14:00")
            assertThat(it.formatCompact()).isEqualTo("14:00")
        }
    }

    // 2. Target > 6h without break

    @Test
    fun testTargetOver6HoursWithoutBreakExpect30MinBreakAdded() {
        // 7h 06m standard target (426 min), started at 08:00, no break taken
        val blocks = listOf(
            TimeBlock(id = 1, workDayId = 1, startTime = LocalTime.of(8, 0), endTime = null)
        )

        val result = useCase(
            timeBlocks = blocks,
            dailyTargetMinutes = 426,
            now = LocalTime.of(8, 0)
        )

        assertThat(result).isNotNull()
        result?.let {
            assertThat(it.requiredBreakMinutes).isEqualTo(30L)
            assertThat(it.takenBreakMinutes).isEqualTo(0L)
            assertThat(it.pendingBreakMinutes).isEqualTo(30L)
            assertThat(it.hasPendingBreak).isTrue()
            assertThat(it.projectedEndTime).isEqualTo(LocalTime.of(15, 36))
            assertThat(it.projectedEndTimeWithoutBreak).isEqualTo(LocalTime.of(15, 6))
            assertThat(it.formatWithBreakHint()).isEqualTo("15:36 (ohne Pause 15:06)")
            assertThat(it.formatCompact()).isEqualTo("15:36")
        }
    }

    // 3. Target > 6h with partial break (e.g. 15m)

    @Test
    fun testTargetOver6HoursWithPartialBreakExpectRemainingBreakAdded() {
        // Block 1: 08:00 - 12:00 (240 min), Gap: 12:00 - 12:15 (15 min pause)
        // Block 2: 12:15 - running
        val blocks = listOf(
            TimeBlock(id = 1, workDayId = 1, startTime = LocalTime.of(8, 0), endTime = LocalTime.of(12, 0)),
            TimeBlock(id = 2, workDayId = 1, startTime = LocalTime.of(12, 15), endTime = null)
        )

        val result = useCase(
            timeBlocks = blocks,
            dailyTargetMinutes = 426,
            now = LocalTime.of(12, 15)
        )

        assertThat(result).isNotNull()
        result?.let {
            assertThat(it.requiredBreakMinutes).isEqualTo(30L)
            assertThat(it.takenBreakMinutes).isEqualTo(15L)
            assertThat(it.pendingBreakMinutes).isEqualTo(15L)
            assertThat(it.hasPendingBreak).isTrue()
            assertThat(it.projectedEndTime).isEqualTo(LocalTime.of(15, 36))
            assertThat(it.projectedEndTimeWithoutBreak).isEqualTo(LocalTime.of(15, 21))
            assertThat(it.formatWithBreakHint()).isEqualTo("15:36 (ohne Pause 15:21)")
            assertThat(it.formatCompact()).isEqualTo("15:36")
        }
    }

    // 4. Target > 6h with full break (30m)

    @Test
    fun testTargetOver6HoursWithFullBreakExpectNoPendingBreak() {
        // Block 1: 08:00 - 12:00 (240 min), Gap: 12:00 - 12:30 (30 min pause)
        // Block 2: 12:30 - running
        val blocks = listOf(
            TimeBlock(id = 1, workDayId = 1, startTime = LocalTime.of(8, 0), endTime = LocalTime.of(12, 0)),
            TimeBlock(id = 2, workDayId = 1, startTime = LocalTime.of(12, 30), endTime = null)
        )

        val result = useCase(
            timeBlocks = blocks,
            dailyTargetMinutes = 426,
            now = LocalTime.of(12, 30)
        )

        assertThat(result).isNotNull()
        result?.let {
            assertThat(it.requiredBreakMinutes).isEqualTo(30L)
            assertThat(it.takenBreakMinutes).isEqualTo(30L)
            assertThat(it.pendingBreakMinutes).isEqualTo(0L)
            assertThat(it.hasPendingBreak).isFalse()
            assertThat(it.projectedEndTime).isEqualTo(LocalTime.of(15, 36))
            assertThat(it.projectedEndTimeWithoutBreak).isEqualTo(LocalTime.of(15, 36))
            assertThat(it.formatWithBreakHint()).isEqualTo("15:36")
            assertThat(it.formatCompact()).isEqualTo("15:36")
        }
    }

    // 5. Target > 6h with excess break (> 30m, e.g. 45m)

    @Test
    fun testTargetOver6HoursWithExcessBreakExpectNoPendingBreak() {
        // Block 1: 08:00 - 12:00 (240 min), Gap: 12:00 - 12:45 (45 min pause)
        // Block 2: 12:45 - running, now = 12:45
        val blocks = listOf(
            TimeBlock(id = 1, workDayId = 1, startTime = LocalTime.of(8, 0), endTime = LocalTime.of(12, 0)),
            TimeBlock(id = 2, workDayId = 1, startTime = LocalTime.of(12, 45), endTime = null)
        )

        val result = useCase(
            timeBlocks = blocks,
            dailyTargetMinutes = 426,
            now = LocalTime.of(12, 45)
        )

        assertThat(result).isNotNull()
        result?.let {
            assertThat(it.requiredBreakMinutes).isEqualTo(30L)
            assertThat(it.takenBreakMinutes).isEqualTo(45L)
            assertThat(it.pendingBreakMinutes).isEqualTo(0L)
            assertThat(it.hasPendingBreak).isFalse()
            // 426 min - 240 min = 186 min gross remaining. 12:45 + 186 min = 15:51
            assertThat(it.projectedEndTime).isEqualTo(LocalTime.of(15, 51))
            assertThat(it.projectedEndTimeWithoutBreak).isEqualTo(LocalTime.of(15, 51))
            assertThat(it.formatWithBreakHint()).isEqualTo("15:51")
        }
    }

    // 6. Target > 9h (45m break required)

    @Test
    fun testTargetOver9HoursWithoutBreakExpect45MinBreakAdded() {
        // 9h 30m target (570 min), started at 08:00, no break taken
        val blocks = listOf(
            TimeBlock(id = 1, workDayId = 1, startTime = LocalTime.of(8, 0), endTime = null)
        )

        val result = useCase(
            timeBlocks = blocks,
            dailyTargetMinutes = 570,
            now = LocalTime.of(8, 0)
        )

        assertThat(result).isNotNull()
        result?.let {
            assertThat(it.requiredBreakMinutes).isEqualTo(45L)
            assertThat(it.takenBreakMinutes).isEqualTo(0L)
            assertThat(it.pendingBreakMinutes).isEqualTo(45L)
            assertThat(it.hasPendingBreak).isTrue()
            // 08:00 + 570m + 45m = 08:00 + 615m = 18:15
            assertThat(it.projectedEndTime).isEqualTo(LocalTime.of(18, 15))
            // 08:00 + 570m = 17:30
            assertThat(it.projectedEndTimeWithoutBreak).isEqualTo(LocalTime.of(17, 30))
            assertThat(it.formatWithBreakHint()).isEqualTo("18:15 (ohne Pause 17:30)")
            assertThat(it.formatCompact()).isEqualTo("18:15")
        }
    }

    @Test
    fun testTargetOver9HoursWithPartialBreak30MinExpect15MinPendingBreak() {
        // 9h 30m target (570 min).
        // Block 1: 08:00 - 12:00 (240 min), Gap: 12:00 - 12:30 (30 min pause)
        // Block 2: 12:30 - running, now = 12:30
        val blocks = listOf(
            TimeBlock(id = 1, workDayId = 1, startTime = LocalTime.of(8, 0), endTime = LocalTime.of(12, 0)),
            TimeBlock(id = 2, workDayId = 1, startTime = LocalTime.of(12, 30), endTime = null)
        )

        val result = useCase(
            timeBlocks = blocks,
            dailyTargetMinutes = 570,
            now = LocalTime.of(12, 30)
        )

        assertThat(result).isNotNull()
        result?.let {
            assertThat(it.requiredBreakMinutes).isEqualTo(45L)
            assertThat(it.takenBreakMinutes).isEqualTo(30L)
            assertThat(it.pendingBreakMinutes).isEqualTo(15L)
            assertThat(it.hasPendingBreak).isTrue()
            // 570 + 15 pending = 585 total gross. Remaining gross = 585 - 240 = 345 min.
            // 12:30 + 345 min = 18:15
            assertThat(it.projectedEndTime).isEqualTo(LocalTime.of(18, 15))
            // Without break: 570 - 240 = 330 min. 12:30 + 330 min = 18:00
            assertThat(it.projectedEndTimeWithoutBreak).isEqualTo(LocalTime.of(18, 0))
            assertThat(it.formatWithBreakHint()).isEqualTo("18:15 (ohne Pause 18:00)")
        }
    }

    @Test
    fun testTargetOver9HoursWithFull45MinBreakExpectNoPendingBreak() {
        // 9h 30m target (570 min).
        // Block 1: 08:00 - 12:00 (240 min), Gap: 12:00 - 12:45 (45 min pause)
        // Block 2: 12:45 - running, now = 12:45
        val blocks = listOf(
            TimeBlock(id = 1, workDayId = 1, startTime = LocalTime.of(8, 0), endTime = LocalTime.of(12, 0)),
            TimeBlock(id = 2, workDayId = 1, startTime = LocalTime.of(12, 45), endTime = null)
        )

        val result = useCase(
            timeBlocks = blocks,
            dailyTargetMinutes = 570,
            now = LocalTime.of(12, 45)
        )

        assertThat(result).isNotNull()
        result?.let {
            assertThat(it.requiredBreakMinutes).isEqualTo(45L)
            assertThat(it.takenBreakMinutes).isEqualTo(45L)
            assertThat(it.pendingBreakMinutes).isEqualTo(0L)
            assertThat(it.hasPendingBreak).isFalse()
            assertThat(it.projectedEndTime).isEqualTo(LocalTime.of(18, 15))
            assertThat(it.projectedEndTimeWithoutBreak).isEqualTo(LocalTime.of(18, 15))
            assertThat(it.formatWithBreakHint()).isEqualTo("18:15")
        }
    }

    // 7. Target reached / exceeded

    @Test
    fun testTargetReachedWhenNetWorkEqualsTargetExpectNull() {
        // 426 min target. Block 1: 08:00 - 15:36 (456 gross, 30 break -> 426 net).
        // Running block at 15:36: net work is 426, remaining net = 0.
        val blocks = listOf(
            TimeBlock(id = 1, workDayId = 1, startTime = LocalTime.of(8, 0), endTime = LocalTime.of(15, 36)),
            TimeBlock(id = 2, workDayId = 1, startTime = LocalTime.of(15, 36), endTime = null)
        )

        val result = useCase(
            timeBlocks = blocks,
            dailyTargetMinutes = 426,
            now = LocalTime.of(15, 36)
        )

        assertThat(result).isNull()
    }

    @Test
    fun testTargetExceededWhenNetWorkExceedsTargetExpectNull() {
        // 426 min target. Block 1: 08:00 - 16:00 (480 gross - 30 break = 450 net).
        val blocks = listOf(
            TimeBlock(id = 1, workDayId = 1, startTime = LocalTime.of(8, 0), endTime = LocalTime.of(16, 0)),
            TimeBlock(id = 2, workDayId = 1, startTime = LocalTime.of(16, 0), endTime = null)
        )

        val result = useCase(
            timeBlocks = blocks,
            dailyTargetMinutes = 426,
            now = LocalTime.of(16, 0)
        )

        assertThat(result).isNull()
    }

    // 8. Clock stopped (no block with endTime == null)

    @Test
    fun testClockStoppedWhenNoRunningBlockExpectNull() {
        val blocks = listOf(
            TimeBlock(id = 1, workDayId = 1, startTime = LocalTime.of(8, 0), endTime = LocalTime.of(16, 0))
        )

        val result = useCase(
            timeBlocks = blocks,
            dailyTargetMinutes = 426,
            now = LocalTime.of(16, 0)
        )

        assertThat(result).isNull()
    }

    @Test
    fun testClockStoppedWhenEmptyListExpectNull() {
        val result = useCase(
            timeBlocks = emptyList(),
            dailyTargetMinutes = 426,
            now = LocalTime.of(8, 0)
        )

        assertThat(result).isNull()
    }

    // 9. Zero / negative target

    @Test
    fun testZeroTargetExpectNull() {
        val blocks = listOf(
            TimeBlock(id = 1, workDayId = 1, startTime = LocalTime.of(8, 0), endTime = null)
        )

        val result = useCase(
            timeBlocks = blocks,
            dailyTargetMinutes = 0,
            now = LocalTime.of(8, 0)
        )

        assertThat(result).isNull()
    }

    @Test
    fun testNegativeTargetExpectNull() {
        val blocks = listOf(
            TimeBlock(id = 1, workDayId = 1, startTime = LocalTime.of(8, 0), endTime = null)
        )

        val result = useCase(
            timeBlocks = blocks,
            dailyTargetMinutes = -30,
            now = LocalTime.of(8, 0)
        )

        assertThat(result).isNull()
    }

    // 10. Business trip without 5-min rounding

    @Test
    fun testBusinessTripWithoutFiveMinuteRounding() {
        // Start at 07:58. Target = 300 min (5h).
        val blocks = listOf(
            TimeBlock(id = 1, workDayId = 1, startTime = LocalTime.of(7, 58), endTime = null)
        )

        val btResult = useCase(
            timeBlocks = blocks,
            dailyTargetMinutes = 300,
            now = LocalTime.of(7, 58),
            isBusinessTrip = true
        )

        // For business trips, 07:58 is not rounded to 07:55.
        // Gross = 0, remaining = 300 min -> 07:58 + 300 min = 12:58
        assertThat(btResult).isNotNull()
        assertThat(btResult?.projectedEndTime).isEqualTo(LocalTime.of(12, 58))

        // Normal day: 07:58 is rounded down to 07:55.
        // Gross required = 300 min -> 07:55 + 300 min = 12:55 (stable, does not drift)
        val normalResult = useCase(
            timeBlocks = blocks,
            dailyTargetMinutes = 300,
            now = LocalTime.of(7, 58),
            isBusinessTrip = false
        )
        assertThat(normalResult).isNotNull()
        assertThat(normalResult?.projectedEndTime).isEqualTo(LocalTime.of(12, 55))
    }

    @Test
    fun testProjectedEndTimeDoesNotDriftWithNowFor6HourTargetStartingAt8() {
        val blocks = listOf(
            TimeBlock(id = 1, workDayId = 1, startTime = LocalTime.of(8, 0), endTime = null)
        )
        val dailyTargetMinutes = 360 // 6h 0m

        val intermediateTimes = listOf(
            LocalTime.of(8, 0),
            LocalTime.of(8, 1),
            LocalTime.of(8, 5),
            LocalTime.of(9, 13),
            LocalTime.of(10, 39),
            LocalTime.of(10, 40),
            LocalTime.of(10, 41),
            LocalTime.of(11, 22),
            LocalTime.of(12, 0),
            LocalTime.of(13, 0),
            LocalTime.of(13, 50)
        )

        for (nowTime in intermediateTimes) {
            val result = useCase(
                timeBlocks = blocks,
                dailyTargetMinutes = dailyTargetMinutes,
                now = nowTime
            )
            com.google.common.truth.Truth.assertWithMessage("Result at $nowTime").that(result).isNotNull()
            result?.let {
                com.google.common.truth.Truth.assertWithMessage("projectedEndTime at $nowTime")
                    .that(it.projectedEndTime).isEqualTo(LocalTime.of(14, 0))
                com.google.common.truth.Truth.assertWithMessage("projectedEndTimeWithoutBreak at $nowTime")
                    .that(it.projectedEndTimeWithoutBreak).isEqualTo(LocalTime.of(14, 0))
                com.google.common.truth.Truth.assertWithMessage("formatCompact at $nowTime")
                    .that(it.formatCompact()).isEqualTo("14:00")
            }
        }
    }

    // 11. Multiple completed blocks with multiple gaps

    @Test
    fun testMultipleCompletedBlocksBeforeRunningBlock() {
        // Block 1: 08:00 - 10:00 (120m)
        // Gap 1:   10:00 - 10:15 (15m break)
        // Block 2: 10:15 - 12:15 (120m)
        // Gap 2:   12:15 - 12:30 (15m break)
        // Block 3: 12:30 - running
        // Total taken breaks = 15 + 15 = 30 min. Required = 30 min. Pending = 0 min.
        val blocks = listOf(
            TimeBlock(id = 1, workDayId = 1, startTime = LocalTime.of(8, 0), endTime = LocalTime.of(10, 0)),
            TimeBlock(id = 2, workDayId = 1, startTime = LocalTime.of(10, 15), endTime = LocalTime.of(12, 15)),
            TimeBlock(id = 3, workDayId = 1, startTime = LocalTime.of(12, 30), endTime = null)
        )

        val result = useCase(
            timeBlocks = blocks,
            dailyTargetMinutes = 426,
            now = LocalTime.of(12, 30)
        )

        assertThat(result).isNotNull()
        result?.let {
            assertThat(it.requiredBreakMinutes).isEqualTo(30L)
            assertThat(it.takenBreakMinutes).isEqualTo(30L)
            assertThat(it.pendingBreakMinutes).isEqualTo(0L)
            assertThat(it.hasPendingBreak).isFalse()
            assertThat(it.projectedEndTime).isEqualTo(LocalTime.of(15, 36))
            assertThat(it.formatWithBreakHint()).isEqualTo("15:36")
        }
    }

    // 12. LocalTime midnight crossing

    @Test
    fun testMidnightCrossingCalculation() {
        // 5 hours target (300 min), starting at 22:00 -> projected end 03:00 next day
        val blocks = listOf(
            TimeBlock(id = 1, workDayId = 1, startTime = LocalTime.of(22, 0), endTime = null)
        )

        val result = useCase(
            timeBlocks = blocks,
            dailyTargetMinutes = 300,
            now = LocalTime.of(22, 0)
        )

        assertThat(result).isNotNull()
        result?.let {
            assertThat(it.projectedEndTime).isEqualTo(LocalTime.of(3, 0))
            assertThat(it.formatCompact()).isEqualTo("03:00")
            assertThat(it.formatWithBreakHint()).isEqualTo("03:00")
        }
    }

    // 13. ProjectedEndTime data class direct methods

    @Test
    fun testProjectedEndTimeFormattingMethods() {
        val modelWithBreak = ProjectedEndTime(
            projectedEndTime = LocalTime.of(16, 30),
            projectedEndTimeWithoutBreak = LocalTime.of(16, 0),
            pendingBreakMinutes = 30L,
            takenBreakMinutes = 0L,
            requiredBreakMinutes = 30L,
            remainingNetMinutes = 120L
        )

        assertThat(modelWithBreak.hasPendingBreak).isTrue()
        assertThat(modelWithBreak.formatWithBreakHint()).isEqualTo("16:30 (ohne Pause 16:00)")
        assertThat(modelWithBreak.formatCompact()).isEqualTo("16:30")

        val modelWithoutBreak = ProjectedEndTime(
            projectedEndTime = LocalTime.of(16, 0),
            projectedEndTimeWithoutBreak = LocalTime.of(16, 0),
            pendingBreakMinutes = 0L,
            takenBreakMinutes = 30L,
            requiredBreakMinutes = 30L,
            remainingNetMinutes = 120L
        )

        assertThat(modelWithoutBreak.hasPendingBreak).isFalse()
        assertThat(modelWithoutBreak.formatWithBreakHint()).isEqualTo("16:00")
        assertThat(modelWithoutBreak.formatCompact()).isEqualTo("16:00")

        assertThat(ProjectedEndTime.formatTime(LocalTime.of(9, 5))).isEqualTo("09:05")
        assertThat(ProjectedEndTime.formatTime(LocalTime.of(0, 0))).isEqualTo("00:00")
    }

    // 14. Stability of projectedEndTimeWithoutBreak across entire day (drift regression)

    @Test
    fun testContinuousWorkOver6Hours30MinutesRemovesBreakHint() {
        val blocks = listOf(
            TimeBlock(id = 1, workDayId = 1, startTime = LocalTime.of(8, 0), endTime = null)
        )
        val dailyTargetMinutes = 426

        // Before 6h 30m: automatic break is 0..29m, so pending break remains and hint is shown
        val before6h = useCase(blocks, dailyTargetMinutes, LocalTime.of(14, 0))
        assertThat(before6h).isNotNull()
        assertThat(before6h?.pendingBreakMinutes).isEqualTo(30L)
        assertThat(before6h?.formatWithBreakHint()).isEqualTo("15:36 (ohne Pause 15:06)")

        val at6h15m = useCase(blocks, dailyTargetMinutes, LocalTime.of(14, 15))
        assertThat(at6h15m).isNotNull()
        assertThat(at6h15m?.pendingBreakMinutes).isEqualTo(15L)
        assertThat(at6h15m?.formatWithBreakHint()).isEqualTo("15:36 (ohne Pause 15:21)")

        // At 6h 30m (14:30) and beyond: 30m break is fully applied by ArbZG -> hint disappears!
        val after6h30mTimes = listOf(
            LocalTime.of(14, 30),
            LocalTime.of(15, 0),
            LocalTime.of(15, 10),
            LocalTime.of(15, 15),
            LocalTime.of(15, 20),
            LocalTime.of(15, 25),
            LocalTime.of(15, 30),
            LocalTime.of(15, 35)
        )

        for (testTime in after6h30mTimes) {
            val result = useCase(blocks, dailyTargetMinutes, testTime)
            com.google.common.truth.Truth.assertWithMessage("Result at $testTime").that(result).isNotNull()
            result?.let {
                com.google.common.truth.Truth.assertWithMessage("projectedEndTime at $testTime")
                    .that(it.projectedEndTime).isEqualTo(LocalTime.of(15, 36))
                com.google.common.truth.Truth.assertWithMessage("pendingBreakMinutes at $testTime")
                    .that(it.pendingBreakMinutes).isEqualTo(0L)
                com.google.common.truth.Truth.assertWithMessage("hasPendingBreak at $testTime")
                    .that(it.hasPendingBreak).isFalse()
                // Break hint is gone, only pure end time shown
                com.google.common.truth.Truth.assertWithMessage("formatWithBreakHint at $testTime")
                    .that(it.formatWithBreakHint()).isEqualTo("15:36")
            }
        }

        // At 15:36, target reached -> null
        val atTarget = useCase(blocks, dailyTargetMinutes, LocalTime.of(15, 36))
        assertThat(atTarget).isNull()
    }

    @Test
    fun testProjectedEndTimeWithoutBreakRemainsStableDuringFinalMinutesWithPartialPendingBreak() {
        // Block 1: 08:00 - 12:00 (240 min)
        // Break: 12:00 - 12:15 (15 min taken)
        // Block 2: 12:15 - running
        // Required break: 30 min -> Pending break: 15 min
        // Daily target: 426 min -> Total gross work needed: 426 + 15 = 441 min
        // 441 - 240 = 201 min in Block 2 -> 12:15 + 201 min = 15:36
        // End time without break: 15:36 - 15 min = 15:21
        val blocks = listOf(
            TimeBlock(id = 1, workDayId = 1, startTime = LocalTime.of(8, 0), endTime = LocalTime.of(12, 0)),
            TimeBlock(id = 2, workDayId = 1, startTime = LocalTime.of(12, 15), endTime = null)
        )
        val dailyTargetMinutes = 426

        // Before ArbZG top-up kicks in (Block 2 < 2h = 120m, total gross < 360m):
        // 12:15 + 60m = 13:15 -> total gross = 300m <= 360m -> pending break remains 15m
        val resultBeforeTopUp = useCase(blocks, dailyTargetMinutes, LocalTime.of(13, 15))
        assertThat(resultBeforeTopUp).isNotNull()
        resultBeforeTopUp?.let {
            assertThat(it.projectedEndTime).isEqualTo(LocalTime.of(15, 36))
            assertThat(it.projectedEndTimeWithoutBreak).isEqualTo(LocalTime.of(15, 21))
            assertThat(it.pendingBreakMinutes).isEqualTo(15L)
            assertThat(it.formatWithBreakHint()).isEqualTo("15:36 (ohne Pause 15:21)")
        }

        // Once total gross reaches 390m (Block 2 >= 150m, 12:15 + 150m = 14:45):
        // The remaining 15m break is fully applied by ArbZG -> hint disappears!
        val afterFullDeductionTimes = listOf(
            LocalTime.of(14, 45),
            LocalTime.of(15, 0),
            LocalTime.of(15, 25),
            LocalTime.of(15, 30),
            LocalTime.of(15, 35)
        )

        for (testTime in afterFullDeductionTimes) {
            val result = useCase(
                timeBlocks = blocks,
                dailyTargetMinutes = dailyTargetMinutes,
                now = testTime
            )
            com.google.common.truth.Truth.assertWithMessage("Result at $testTime").that(result).isNotNull()
            result?.let {
                com.google.common.truth.Truth.assertWithMessage("projectedEndTime at $testTime").that(it.projectedEndTime).isEqualTo(LocalTime.of(15, 36))
                com.google.common.truth.Truth.assertWithMessage("pendingBreakMinutes at $testTime").that(it.pendingBreakMinutes).isEqualTo(0L)
                com.google.common.truth.Truth.assertWithMessage("hasPendingBreak at $testTime").that(it.hasPendingBreak).isFalse()
                com.google.common.truth.Truth.assertWithMessage("formatWithBreakHint at $testTime").that(it.formatWithBreakHint()).isEqualTo("15:36")
            }
        }

        // At 15:36, target reached -> null
        val atTarget = useCase(
            timeBlocks = blocks,
            dailyTargetMinutes = dailyTargetMinutes,
            now = LocalTime.of(15, 36)
        )
        assertThat(atTarget).isNull()
    }

    @Test
    fun testProjectedEndTimeWithoutBreakRemainsStableAcross9HourThreshold() {
        // Daily target: 570 min (9h 30m) -> requires 45 min statutory break
        // Start: 08:00
        // Expected with break: 08:00 + 570 + 45 = 18:15
        // Expected without break: 18:15 - 45 = 17:30
        val blocks = listOf(
            TimeBlock(id = 1, workDayId = 1, startTime = LocalTime.of(8, 0), endTime = null)
        )
        val dailyTargetMinutes = 570

        // At 14:00 (6h gross = 360m): 0m break applied so far -> 45m pending
        val at14 = useCase(blocks, dailyTargetMinutes, LocalTime.of(14, 0))
        assertThat(at14).isNotNull()
        assertThat(at14?.projectedEndTime).isEqualTo(LocalTime.of(18, 15))
        assertThat(at14?.projectedEndTimeWithoutBreak).isEqualTo(LocalTime.of(17, 30))
        assertThat(at14?.pendingBreakMinutes).isEqualTo(45L)
        assertThat(at14?.formatWithBreakHint()).isEqualTo("18:15 (ohne Pause 17:30)")

        // At 15:00 (7h gross): 30m break applied -> 15m pending break
        val at15 = useCase(blocks, dailyTargetMinutes, LocalTime.of(15, 0))
        assertThat(at15).isNotNull()
        assertThat(at15?.projectedEndTime).isEqualTo(LocalTime.of(18, 15))
        assertThat(at15?.projectedEndTimeWithoutBreak).isEqualTo(LocalTime.of(18, 0))
        assertThat(at15?.pendingBreakMinutes).isEqualTo(15L)
        assertThat(at15?.formatWithBreakHint()).isEqualTo("18:15 (ohne Pause 18:00)")

        // At 17:45 (9h 45m gross): full 45m break applied -> 0m pending break, hint gone
        val at1745 = useCase(blocks, dailyTargetMinutes, LocalTime.of(17, 45))
        assertThat(at1745).isNotNull()
        assertThat(at1745?.projectedEndTime).isEqualTo(LocalTime.of(18, 15))
        assertThat(at1745?.pendingBreakMinutes).isEqualTo(0L)
        assertThat(at1745?.hasPendingBreak).isFalse()
        assertThat(at1745?.formatWithBreakHint()).isEqualTo("18:15")

        // At 18:15, target reached
        val atTarget = useCase(
            timeBlocks = blocks,
            dailyTargetMinutes = dailyTargetMinutes,
            now = LocalTime.of(18, 15)
        )
        assertThat(atTarget).isNull()
    }

    @Test
    fun testProjectedEndTimeWithoutBreakRemainsStableMinutengenauDuringBusinessTrip() {
        // Business trip: no 5-minute rounding, pure minute precision
        val blocks = listOf(
            TimeBlock(id = 1, workDayId = 1, startTime = LocalTime.of(8, 0), endTime = null)
        )
        val dailyTargetMinutes = 426

        // Before 6h 30m (e.g. 14:00 = 6h gross): break is 0m -> 30m pending
        val earlyResult = useCase(blocks, dailyTargetMinutes, LocalTime.of(14, 0), isBusinessTrip = true)
        assertThat(earlyResult).isNotNull()
        assertThat(earlyResult?.projectedEndTime).isEqualTo(LocalTime.of(15, 36))
        assertThat(earlyResult?.projectedEndTimeWithoutBreak).isEqualTo(LocalTime.of(15, 6))
        assertThat(earlyResult?.pendingBreakMinutes).isEqualTo(30L)
        assertThat(earlyResult?.formatWithBreakHint()).isEqualTo("15:36 (ohne Pause 15:06)")

        // After 6h 30m (e.g. 15:10, 15:25, 15:35): 30m break fully applied -> hint gone
        val lateTimes = listOf(
            LocalTime.of(14, 30),
            LocalTime.of(15, 10),
            LocalTime.of(15, 25),
            LocalTime.of(15, 35)
        )

        for (testTime in lateTimes) {
            val result = useCase(
                timeBlocks = blocks,
                dailyTargetMinutes = dailyTargetMinutes,
                now = testTime,
                isBusinessTrip = true
            )
            com.google.common.truth.Truth.assertWithMessage("Result at $testTime").that(result).isNotNull()
            result?.let {
                com.google.common.truth.Truth.assertWithMessage("projectedEndTime at $testTime").that(it.projectedEndTime).isEqualTo(LocalTime.of(15, 36))
                com.google.common.truth.Truth.assertWithMessage("pendingBreakMinutes at $testTime").that(it.pendingBreakMinutes).isEqualTo(0L)
                assertThat(it.hasPendingBreak).isFalse()
                assertThat(it.formatWithBreakHint()).isEqualTo("15:36")
            }
        }

        val atTarget = useCase(
            timeBlocks = blocks,
            dailyTargetMinutes = dailyTargetMinutes,
            now = LocalTime.of(15, 36),
            isBusinessTrip = true
        )
        assertThat(atTarget).isNull()
    }
}

