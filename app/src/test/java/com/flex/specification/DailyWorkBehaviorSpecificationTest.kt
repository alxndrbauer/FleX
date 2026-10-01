package com.flex.specification

import com.flex.domain.model.TimeBlock
import com.flex.domain.model.WorkLocation
import com.flex.domain.usecase.CalculateDayWorkTimeUseCase
import com.flex.specification.fixture.September2026ExportFixture
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalTime

@DisplayName("Spezifikation: Reales Tagesverhalten & ArbZG-Pausen")
class DailyWorkBehaviorSpecificationTest {

    private lateinit var calculateDayWorkTime: CalculateDayWorkTimeUseCase

    @BeforeEach
    fun setUp() {
        calculateDayWorkTime = CalculateDayWorkTimeUseCase()
    }

    @Nested
    @DisplayName("01.09.2026 - 5-Minuten-Rundung & gesetzlicher 30-Minuten-Pausenabzug")
    inner class RoundingAndStatutoryBreakTest {

        @Test
        @DisplayName("Ein Block von 09:10 bis 17:01 im Büro wird gerundet und zieht gesetzliche 30-Minuten-Mindestpause ab")
        fun singleBlockRoundingAndStatutory30MinBreak() {
            // Given: Ein Block von 09:10 bis 17:01 im Büro aus der September 2026 Fixture
            val workDays = September2026ExportFixture.createSeptember2026WorkDays()
            val day = workDays.first { it.date == LocalDate.of(2026, 9, 1) }

            assertThat(day.location).isEqualTo(WorkLocation.OFFICE)
            assertThat(day.timeBlocks).hasSize(1)
            val rawBlock = day.timeBlocks.first()
            assertThat(rawBlock.startTime).isEqualTo(LocalTime.of(9, 10))
            assertThat(rawBlock.endTime).isEqualTo(LocalTime.of(17, 1))

            // When: CalculateDayWorkTimeUseCase wird aufgerufen
            val adjustedBlocks = CalculateDayWorkTimeUseCase.adjustTimeBlocks(day.timeBlocks)
            val result = calculateDayWorkTime(day.timeBlocks)

            // Then: Start gerundet auf 09:10, Ende gerundet auf 17:05 -> Brutto = 475 Min (7:55)
            val adjusted = adjustedBlocks.first()
            assertThat(adjusted.startTime).isEqualTo(LocalTime.of(9, 10))
            assertThat(adjusted.endTime).isEqualTo(LocalTime.of(17, 5))
            assertThat(result.grossMinutes).isEqualTo(475L) // 7:55 h

            // And: Gesetzliche Mindestpause (>6h) von 30 Min wird abgezogen -> Pause = 30 Min (0:30)
            assertThat(result.breakMinutes).isEqualTo(30L) // 0:30 h

            // And: Netto = 445 Min (7:25)
            assertThat(result.netMinutes).isEqualTo(445L) // 7:25 h

            // And: Tagesdifferenz zu 426 Min (7:06) ist exakt +19 Min (+0:19)
            val dayDifference = result.netMinutes - September2026ExportFixture.DAILY_TARGET_MINUTES
            assertThat(dayDifference).isEqualTo(19L)
            assertThat(formatDifference(dayDifference)).isEqualTo("+0:19")
            assertThat(result.exceedsMaxHours).isFalse()
        }
    }

    @Nested
    @DisplayName("03.09.2026 - Geteilter Arbeitstag mit echter Pause")
    inner class SplitWorkDayWithRealBreakTest {

        @Test
        @DisplayName("2 Blöcke im Home Office mit 130 Min echter Pause führen zu keinem zusätzlichen Pausenabzug")
        fun splitHomeOfficeDayWithLongRealBreak() {
            // Given: 2 Blöcke im Home Office: 08:51–12:00 und 14:10–16:38 (2:10h / 130 min Pause dazwischen)
            val workDays = September2026ExportFixture.createSeptember2026WorkDays()
            val day = workDays.first { it.date == LocalDate.of(2026, 9, 3) }

            assertThat(day.location).isEqualTo(WorkLocation.HOME_OFFICE)
            assertThat(day.timeBlocks).hasSize(2)
            assertThat(day.timeBlocks[0].startTime).isEqualTo(LocalTime.of(8, 51))
            assertThat(day.timeBlocks[0].endTime).isEqualTo(LocalTime.of(12, 0))
            assertThat(day.timeBlocks[1].startTime).isEqualTo(LocalTime.of(14, 10))
            assertThat(day.timeBlocks[1].endTime).isEqualTo(LocalTime.of(16, 38))

            // When: CalculateDayWorkTimeUseCase wird aufgerufen
            val adjustedBlocks = CalculateDayWorkTimeUseCase.adjustTimeBlocks(day.timeBlocks)
            val result = calculateDayWorkTime(day.timeBlocks)

            // Then: 5-Minuten-Rundung (Start auf 08:50 abgerundet, Ende auf 16:40 aufgerundet) -> Brutto = 340 Min (5:40)
            assertThat(adjustedBlocks[0].startTime).isEqualTo(LocalTime.of(8, 50))
            assertThat(adjustedBlocks[0].endTime).isEqualTo(LocalTime.of(12, 0))
            assertThat(adjustedBlocks[1].startTime).isEqualTo(LocalTime.of(14, 10))
            assertThat(adjustedBlocks[1].endTime).isEqualTo(LocalTime.of(16, 40))
            assertThat(result.grossMinutes).isEqualTo(340L) // 5:40 h

            // And: Pause = 130 Min (2:10)
            assertThat(result.breakMinutes).isEqualTo(130L) // 2:10 h reale Pause

            // And: Da reale Pause (130 Min) >= gesetzliche Vorgabe (0 Min bei <6h), kein zusätzlicher Pausenabzug -> Netto = 340 Min (5:40)
            assertThat(result.netMinutes).isEqualTo(340L) // 5:40 h

            // And: Tagesdifferenz zu 426 Min ist -86 Min (-1:26)
            val dayDifference = result.netMinutes - September2026ExportFixture.DAILY_TARGET_MINUTES
            assertThat(dayDifference).isEqualTo(-86L)
            assertThat(formatDifference(dayDifference)).isEqualTo("-1:26")
            assertThat(result.exceedsMaxHours).isFalse()
        }
    }

    @Nested
    @DisplayName("11.09.2026 - Kurzer Arbeitstag & Minusstunden")
    inner class ShortWorkDayAndMinusHoursTest {

        @Test
        @DisplayName("2 Blöcke im Home Office mit 62 Min Pause führen zu Minusstunden ohne zusätzlichen Abzug")
        fun shortHomeOfficeDayWithMinusHours() {
            // Given: 2 Blöcke im Home Office: 09:40–12:00 und 13:02–15:09 (62 Min Pause)
            val workDays = September2026ExportFixture.createSeptember2026WorkDays()
            val day = workDays.first { it.date == LocalDate.of(2026, 9, 11) }

            assertThat(day.location).isEqualTo(WorkLocation.HOME_OFFICE)
            assertThat(day.timeBlocks).hasSize(2)
            assertThat(day.timeBlocks[0].startTime).isEqualTo(LocalTime.of(9, 40))
            assertThat(day.timeBlocks[0].endTime).isEqualTo(LocalTime.of(12, 0))
            assertThat(day.timeBlocks[1].startTime).isEqualTo(LocalTime.of(13, 2))
            assertThat(day.timeBlocks[1].endTime).isEqualTo(LocalTime.of(15, 9))

            // When: CalculateDayWorkTimeUseCase wird aufgerufen
            val adjustedBlocks = CalculateDayWorkTimeUseCase.adjustTimeBlocks(day.timeBlocks)
            val result = calculateDayWorkTime(day.timeBlocks)

            // Then: 5-Minuten-Rundung (Start bleibt 09:40, Ende gerundet auf 15:10) -> Brutto = 268 Min (4:28)
            assertThat(adjustedBlocks[0].startTime).isEqualTo(LocalTime.of(9, 40))
            assertThat(adjustedBlocks[0].endTime).isEqualTo(LocalTime.of(12, 0))
            assertThat(adjustedBlocks[1].startTime).isEqualTo(LocalTime.of(13, 2))
            assertThat(adjustedBlocks[1].endTime).isEqualTo(LocalTime.of(15, 10))
            assertThat(result.grossMinutes).isEqualTo(268L) // 4:28 h

            // And: Pause = 62 Min (1:02)
            assertThat(result.breakMinutes).isEqualTo(62L) // 1:02 h

            // And: Netto = 268 Min (4:28)
            assertThat(result.netMinutes).isEqualTo(268L) // 4:28 h

            // And: Tagesdifferenz zu 426 Min ist -158 Min (-2:38)
            val dayDifference = result.netMinutes - September2026ExportFixture.DAILY_TARGET_MINUTES
            assertThat(dayDifference).isEqualTo(-158L)
            assertThat(formatDifference(dayDifference)).isEqualTo("-2:38")
            assertThat(result.exceedsMaxHours).isFalse()
        }
    }

    @Nested
    @DisplayName("Gesetzliche Höchstarbeitszeit nach ArbZG (>10h)")
    inner class MaximumWorkHoursCapTest {

        @Test
        @DisplayName("Arbeitszeit > 10 Stunden (660 Min Brutto, 45 Min Pause) kappt Netto auf 600 Min und setzt exceedsMaxHours")
        fun workTimeOver10HoursCappedAt600MinutesWith660Gross() {
            // Given: Arbeitszeit > 10 Stunden mit 660 Min Brutto (07:00–18:00) und 45 Min gesetzlicher Pause -> 615 Min ungekapptes Netto
            val blocks = listOf(
                TimeBlock(
                    id = 1,
                    workDayId = 1,
                    startTime = LocalTime.of(7, 0),
                    endTime = LocalTime.of(18, 0),
                    location = WorkLocation.OFFICE
                )
            )

            // When: CalculateDayWorkTimeUseCase wird aufgerufen
            val result = calculateDayWorkTime(blocks)

            // Then: Brutto = 660 Min, gesetzliche Pause = 45 Min
            assertThat(result.grossMinutes).isEqualTo(660L)
            assertThat(result.breakMinutes).isEqualTo(45L)

            // And: exceedsMaxHours ist true
            assertThat(result.exceedsMaxHours).isTrue()

            // And: netMinutes wird auf 600 Min (10 Stunden) gekappt
            assertThat(result.netMinutes).isEqualTo(600L)
        }

        @Test
        @DisplayName("Arbeitszeit von 07:00 bis 18:45 mit 45 Min gesetzlicher Pause kappt Netto auf 600 Min und setzt exceedsMaxHours")
        fun workTimeFrom0700To1845CappedAt600Minutes() {
            // Given: Arbeitszeit > 10 Stunden von 07:00 bis 18:45
            val blocks = listOf(
                TimeBlock(
                    id = 1,
                    workDayId = 1,
                    startTime = LocalTime.of(7, 0),
                    endTime = LocalTime.of(18, 45),
                    location = WorkLocation.OFFICE
                )
            )

            // When: CalculateDayWorkTimeUseCase wird aufgerufen
            val result = calculateDayWorkTime(blocks)

            // Then: Brutto = 705 Min, gesetzliche Pause = 45 Min
            assertThat(result.grossMinutes).isEqualTo(705L)
            assertThat(result.breakMinutes).isEqualTo(45L)

            // And: exceedsMaxHours ist true
            assertThat(result.exceedsMaxHours).isTrue()

            // And: netMinutes wird auf 600 Min (10 Stunden) gekappt
            assertThat(result.netMinutes).isEqualTo(600L)
        }
    }

    private fun formatDifference(diffMinutes: Long): String {
        val sign = if (diffMinutes > 0) "+" else if (diffMinutes < 0) "-" else ""
        val absMinutes = kotlin.math.abs(diffMinutes)
        val hours = absMinutes / 60
        val mins = absMinutes % 60
        return String.format("%s%d:%02d", sign, hours, mins)
    }
}
