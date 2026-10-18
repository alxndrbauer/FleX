package com.flex.notification

import com.flex.domain.model.ProjectedEndTime
import com.flex.domain.model.TimeBlock
import com.flex.domain.model.WorkLocation
import com.flex.domain.usecase.CalculateDayWorkTimeUseCase
import com.flex.domain.usecase.CalculateProjectedEndTimeUseCase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalTime

class WorkTimerNotificationFormatTest {

    private lateinit var calculateProjectedEndTime: CalculateProjectedEndTimeUseCase

    @BeforeEach
    fun setUp() {
        calculateProjectedEndTime = CalculateProjectedEndTimeUseCase(CalculateDayWorkTimeUseCase())
    }

    @Nested
    @DisplayName("Format Helper Functions")
    inner class FormatHelpers {
        @Test
        fun formatDuration_formatsCorrectly() {
            assertThat(WorkTimerService.formatDuration(0)).isEqualTo("0min")
            assertThat(WorkTimerService.formatDuration(45)).isEqualTo("45min")
            assertThat(WorkTimerService.formatDuration(60)).isEqualTo("1h 0min")
            assertThat(WorkTimerService.formatDuration(255)).isEqualTo("4h 15min")
            assertThat(WorkTimerService.formatDuration(426)).isEqualTo("7h 6min")
            assertThat(WorkTimerService.formatDuration(480)).isEqualTo("8h 0min")
        }

        @Test
        fun formatShortDuration_formatsCorrectly() {
            assertThat(WorkTimerService.formatShortDuration(0)).isEqualTo("0m")
            assertThat(WorkTimerService.formatShortDuration(45)).isEqualTo("45m")
            assertThat(WorkTimerService.formatShortDuration(255)).isEqualTo("4h 15m")
            assertThat(WorkTimerService.formatShortDuration(480)).isEqualTo("8h 0m")
        }

        @Test
        fun formatFlexDelta_formatsCorrectly() {
            assertThat(WorkTimerService.formatFlexDelta(15)).isEqualTo("+15min")
            assertThat(WorkTimerService.formatFlexDelta(75)).isEqualTo("+1h 15min")
            assertThat(WorkTimerService.formatFlexDelta(-15)).isEqualTo("−15min")
            assertThat(WorkTimerService.formatFlexDelta(-75)).isEqualTo("−1h 15min")
        }
    }

    @Nested
    @DisplayName("buildNotificationContent")
    inner class NotificationContent {
        @Test
        fun runningWithTarget_andProjectedEndTime_appendsCompactEndTime() {
            val projectedEndTime = ProjectedEndTime(
                projectedEndTime = LocalTime.of(16, 30),
                projectedEndTimeWithoutBreak = LocalTime.of(16, 0),
                pendingBreakMinutes = 30L,
                takenBreakMinutes = 0L,
                requiredBreakMinutes = 30L,
                remainingNetMinutes = 171L
            )

            val content = WorkTimerService.buildNotificationContent(
                netMinutes = 255L,
                targetMinutes = 426L,
                locationLabel = "Büro",
                isOvertime = false,
                projectedEndTime = projectedEndTime
            )

            assertThat(content).isEqualTo("4h 15min / 7h 6min · Büro · Ende 16:30")
        }

        @Test
        fun runningWithTarget_homeOfficeLocation_formatsCorrectly() {
            val projectedEndTime = ProjectedEndTime(
                projectedEndTime = LocalTime.of(17, 15),
                projectedEndTimeWithoutBreak = LocalTime.of(17, 15),
                pendingBreakMinutes = 0L,
                takenBreakMinutes = 30L,
                requiredBreakMinutes = 30L,
                remainingNetMinutes = 60L
            )

            val content = WorkTimerService.buildNotificationContent(
                netMinutes = 420L,
                targetMinutes = 480L,
                locationLabel = "Home-Office",
                isOvertime = false,
                projectedEndTime = projectedEndTime
            )

            assertThat(content).isEqualTo("7h 0min / 8h 0min · Home-Office · Ende 17:15")
        }

        @Test
        fun runningWithTarget_nullProjectedEndTime_doesNotAppendEndTime() {
            val content = WorkTimerService.buildNotificationContent(
                netMinutes = 255L,
                targetMinutes = 426L,
                locationLabel = "Büro",
                isOvertime = false,
                projectedEndTime = null
            )

            assertThat(content).isEqualTo("4h 15min / 7h 6min · Büro")
        }

        @Test
        fun targetReached_doesNotAppendEndTime() {
            val dummyPrognosis = ProjectedEndTime(
                projectedEndTime = LocalTime.of(16, 30),
                projectedEndTimeWithoutBreak = LocalTime.of(16, 30),
                pendingBreakMinutes = 0L,
                takenBreakMinutes = 30L,
                requiredBreakMinutes = 30L,
                remainingNetMinutes = 0L
            )

            val content = WorkTimerService.buildNotificationContent(
                netMinutes = 480L,
                targetMinutes = 480L,
                locationLabel = "Büro",
                isOvertime = false,
                projectedEndTime = dummyPrognosis
            )

            assertThat(content).isEqualTo("8h 0min / 8h 0min · Büro")
            assertThat(content).doesNotContain("Ende")
        }

        @Test
        fun overtime_doesNotAppendEndTimeAndShowsSingleDuration() {
            val dummyPrognosis = ProjectedEndTime(
                projectedEndTime = LocalTime.of(16, 30),
                projectedEndTimeWithoutBreak = LocalTime.of(16, 30),
                pendingBreakMinutes = 0L,
                takenBreakMinutes = 30L,
                requiredBreakMinutes = 30L,
                remainingNetMinutes = -30L
            )

            val content = WorkTimerService.buildNotificationContent(
                netMinutes = 510L,
                targetMinutes = 480L,
                locationLabel = "Büro",
                isOvertime = true,
                projectedEndTime = dummyPrognosis
            )

            assertThat(content).isEqualTo("8h 30min · Büro")
            assertThat(content).doesNotContain("Ende")
            assertThat(content).doesNotContain("/")
        }

        @Test
        fun targetZeroOrNegative_doesNotAppendEndTime() {
            val dummyPrognosis = ProjectedEndTime(
                projectedEndTime = LocalTime.of(16, 30),
                projectedEndTimeWithoutBreak = LocalTime.of(16, 30),
                pendingBreakMinutes = 0L,
                takenBreakMinutes = 0L,
                requiredBreakMinutes = 0L,
                remainingNetMinutes = 0L
            )

            val contentZero = WorkTimerService.buildNotificationContent(
                netMinutes = 120L,
                targetMinutes = 0L,
                locationLabel = "Büro",
                isOvertime = false,
                projectedEndTime = dummyPrognosis
            )
            assertThat(contentZero).isEqualTo("2h 0min / 0min · Büro")
            assertThat(contentZero).doesNotContain("Ende")

            val contentNegative = WorkTimerService.buildNotificationContent(
                netMinutes = 60L,
                targetMinutes = -30L,
                locationLabel = "Home-Office",
                isOvertime = false,
                projectedEndTime = dummyPrognosis
            )
            assertThat(contentNegative).isEqualTo("1h 0min / -30min · Home-Office")
            assertThat(contentNegative).doesNotContain("Ende")
        }
    }

    @Nested
    @DisplayName("End-to-End Notification Formatting with CalculateProjectedEndTimeUseCase")
    inner class UsecaseIntegration {
        @Test
        fun runningWithTarget_calculatesAndAppendsCorrectEndTime() {
            val blocks = listOf(
                TimeBlock(id = 1, workDayId = 1, startTime = LocalTime.of(8, 0), endTime = null, location = WorkLocation.OFFICE)
            )
            val now = LocalTime.of(12, 15) // worked 4h 15m = 255m
            val targetMinutes = 426 // 7h 06m, requires 30m break (>6h). Gross remaining = (426+30)-255 = 201m.
            // 12:15 + 201m = 15:36

            val projectedEndTime = calculateProjectedEndTime(
                timeBlocks = blocks,
                dailyTargetMinutes = targetMinutes,
                now = now,
                isBusinessTrip = false
            )

            assertThat(projectedEndTime).isNotNull()
            assertThat(projectedEndTime!!.formatCompact()).isEqualTo("15:36")

            val content = WorkTimerService.buildNotificationContent(
                netMinutes = 255L,
                targetMinutes = targetMinutes.toLong(),
                locationLabel = "Büro",
                isOvertime = false,
                projectedEndTime = projectedEndTime
            )

            assertThat(content).isEqualTo("4h 15min / 7h 6min · Büro · Ende 15:36")
        }

        @Test
        fun targetReached_usecaseReturnsNull_notificationHasNoEndTime() {
            val blocks = listOf(
                TimeBlock(id = 1, workDayId = 1, startTime = LocalTime.of(8, 0), endTime = null, location = WorkLocation.OFFICE)
            )
            val now = LocalTime.of(16, 30) // gross 510m - 30m break = 480m net
            val targetMinutes = 480

            val projectedEndTime = calculateProjectedEndTime(
                timeBlocks = blocks,
                dailyTargetMinutes = targetMinutes,
                now = now,
                isBusinessTrip = false
            )

            assertThat(projectedEndTime).isNull()

            val content = WorkTimerService.buildNotificationContent(
                netMinutes = 480L,
                targetMinutes = targetMinutes.toLong(),
                locationLabel = "Büro",
                isOvertime = false,
                projectedEndTime = projectedEndTime
            )

            assertThat(content).isEqualTo("8h 0min / 8h 0min · Büro")
            assertThat(content).doesNotContain("Ende")
        }

        @Test
        fun noTarget_usecaseReturnsNull_notificationHasNoEndTime() {
            val blocks = listOf(
                TimeBlock(id = 1, workDayId = 1, startTime = LocalTime.of(8, 0), endTime = null, location = WorkLocation.OFFICE)
            )
            val now = LocalTime.of(10, 0)

            val projectedEndTime = calculateProjectedEndTime(
                timeBlocks = blocks,
                dailyTargetMinutes = 0,
                now = now,
                isBusinessTrip = false
            )

            assertThat(projectedEndTime).isNull()

            val content = WorkTimerService.buildNotificationContent(
                netMinutes = 120L,
                targetMinutes = 0L,
                locationLabel = "Büro",
                isOvertime = false,
                projectedEndTime = projectedEndTime
            )

            assertThat(content).isEqualTo("2h 0min / 0min · Büro")
            assertThat(content).doesNotContain("Ende")
        }
    }
}
