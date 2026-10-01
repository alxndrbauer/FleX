package com.flex.specification

import com.google.common.truth.Truth.assertThat
import com.flex.domain.model.DayType
import com.flex.domain.model.Settings
import com.flex.domain.model.TimeBlock
import com.flex.domain.model.WorkDay
import com.flex.domain.model.WorkLocation
import com.flex.domain.usecase.CalculateDayWorkTimeUseCase
import com.flex.domain.usecase.CalculateFlextimeUseCase
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalTime

/**
 * BDD-Spezifikation für das Verhalten aller DayType-Arbeitstypen in FleX.
 *
 * Verifiziert die exakte Berechnung von Gleitzeit- und Überstundenkonten
 * mit echten Instanzen von [CalculateFlextimeUseCase] und [CalculateDayWorkTimeUseCase].
 *
 * Einstellungen: Tagessoll = 426 Minuten (7:06 h).
 */
@DisplayName("Spezifikation aller Arbeitstypen (DayType) - BDD Verhaltensprüfung")
class DayTypeBehaviorSpecificationTest {

    companion object {
        private const val DAILY_TARGET_MINUTES = 426 // 7:06 h Tagessoll
    }

    private lateinit var calculateDayWorkTimeUseCase: CalculateDayWorkTimeUseCase
    private lateinit var calculateFlextimeUseCase: CalculateFlextimeUseCase
    private lateinit var defaultSettings: Settings

    @BeforeEach
    fun setUp() {
        calculateDayWorkTimeUseCase = CalculateDayWorkTimeUseCase()
        calculateFlextimeUseCase = CalculateFlextimeUseCase(calculateDayWorkTimeUseCase)
        defaultSettings = Settings(
            dailyWorkMinutes = DAILY_TARGET_MINUTES,
            monthlyWorkMinutes = DAILY_TARGET_MINUTES * 22,
            initialFlextimeMinutes = 0,
            initialOvertimeMinutes = 0
        )
    }

    // =========================================================================
    // Szenario 1: DayType.WORK an Wochentagen
    // =========================================================================
    @Nested
    @DisplayName("Szenario 1: DayType.WORK an Wochentagen (Montag bis Freitag)")
    inner class WorkOnWeekdaysScenario {

        private val weekday = LocalDate.of(2026, 9, 2) // Mittwoch

        @Test
        @DisplayName("GIVEN Wochentag mit Tagessoll 426 min, WHEN 8h (480 min) gearbeitet, THEN +54 min Gleitzeit und 0 Überstunden")
        fun given8HoursWorkedOnWeekday_thenFlextimePlus54MinutesAndZeroOvertime() {
            // Given: 8h (480 min) Arbeitszeit als Dauer-Block
            val day = createWorkDay(
                date = weekday,
                dayType = DayType.WORK,
                timeBlocks = listOf(createDurationBlock(480))
            )

            // When
            val balance = calculateFlextimeUseCase(listOf(day), defaultSettings)

            // Then: 480 - 426 = +54 min
            assertThat(balance.earnedMinutes).isEqualTo(54L)
            assertThat(balance.totalMinutes).isEqualTo(54L)
            assertThat(balance.earnedOvertimeMinutes).isEqualTo(0L)
            assertThat(balance.overtimeMinutes).isEqualTo(0L)
        }

        @Test
        @DisplayName("GIVEN Wochentag mit Tagessoll 426 min, WHEN 6h (360 min) gearbeitet, THEN -66 min Gleitzeit und 0 Überstunden")
        fun given6HoursWorkedOnWeekday_thenFlextimeMinus66MinutesAndZeroOvertime() {
            // Given: 6h (360 min) Arbeitszeit
            val day = createWorkDay(
                date = weekday,
                dayType = DayType.WORK,
                timeBlocks = listOf(createDurationBlock(360))
            )

            // When
            val balance = calculateFlextimeUseCase(listOf(day), defaultSettings)

            // Then: 360 - 426 = -66 min
            assertThat(balance.earnedMinutes).isEqualTo(-66L)
            assertThat(balance.totalMinutes).isEqualTo(-66L)
            assertThat(balance.earnedOvertimeMinutes).isEqualTo(0L)
            assertThat(balance.overtimeMinutes).isEqualTo(0L)
        }

        @Test
        @DisplayName("GIVEN Wochentag mit Tagessoll 426 min, WHEN exakt 7:06h (426 min) gearbeitet, THEN 0 min Gleitzeit-Delta")
        fun givenExactDailyTargetWorkedOnWeekday_thenNeutralFlextimeDelta() {
            // Given: exakt 426 min gearbeitet
            val day = createWorkDay(
                date = weekday,
                dayType = DayType.WORK,
                timeBlocks = listOf(createDurationBlock(426))
            )

            // When
            val balance = calculateFlextimeUseCase(listOf(day), defaultSettings)

            // Then: 426 - 426 = 0 min
            assertThat(balance.earnedMinutes).isEqualTo(0L)
            assertThat(balance.totalMinutes).isEqualTo(0L)
            assertThat(balance.earnedOvertimeMinutes).isEqualTo(0L)
            assertThat(balance.overtimeMinutes).isEqualTo(0L)
        }

        @Test
        @DisplayName("GIVEN reale Uhrzeit-Intervalle mit 30 min Pause für 8h Nettozeit, THEN +54 min Gleitzeit")
        fun givenRealClockTimeBlocksWithPause_thenCalculatesNetCorrectlyAgainstTarget() {
            // Given: 08:00–12:00 (4h) und 12:30–16:30 (4h) mit 30 min Pause dazwischen = 8h netto
            val day = createWorkDay(
                date = weekday,
                dayType = DayType.WORK,
                timeBlocks = listOf(
                    createClockBlock(LocalTime.of(8, 0), LocalTime.of(12, 0)),
                    createClockBlock(LocalTime.of(12, 30), LocalTime.of(16, 30))
                )
            )

            // When
            val balance = calculateFlextimeUseCase(listOf(day), defaultSettings)

            // Then: Netto 480 min - Tagessoll 426 min = +54 min
            assertThat(balance.earnedMinutes).isEqualTo(54L)
            assertThat(balance.totalMinutes).isEqualTo(54L)
            assertThat(balance.earnedOvertimeMinutes).isEqualTo(0L)
        }
    }

    // =========================================================================
    // Szenario 2: DayType.WORK am Wochenende (Samstag/Sonntag)
    // =========================================================================
    @Nested
    @DisplayName("Szenario 2: DayType.WORK am Wochenende (Samstag / Sonntag)")
    inner class WorkOnWeekendScenario {

        private val saturday = LocalDate.of(2026, 9, 5) // Samstag
        private val sunday = LocalDate.of(2026, 9, 6)   // Sonntag

        @Test
        @DisplayName("GIVEN Samstag mit DayType.WORK, WHEN 4h (240 min) gearbeitet, THEN volle +240 min Gleitzeit ohne Tagessollabzug")
        fun given4HoursWorkedOnSaturday_thenFull240MinutesCreditedToFlextime() {
            // Given: Samstag mit 4h Arbeitszeit
            val day = createWorkDay(
                date = saturday,
                dayType = DayType.WORK,
                timeBlocks = listOf(createDurationBlock(240))
            )

            // When
            val balance = calculateFlextimeUseCase(listOf(day), defaultSettings)

            // Then: Wochenendarbeit zählt zu 100% als Gleitzeit ohne Tagessoll-Abzug
            assertThat(balance.earnedMinutes).isEqualTo(240L)
            assertThat(balance.totalMinutes).isEqualTo(240L)
            assertThat(balance.earnedOvertimeMinutes).isEqualTo(0L)
            assertThat(balance.overtimeMinutes).isEqualTo(0L)
        }

        @Test
        @DisplayName("GIVEN Sonntag mit DayType.WORK, WHEN 3h (180 min) gearbeitet, THEN volle +180 min Gleitzeit ohne Tagessollabzug")
        fun given3HoursWorkedOnSunday_thenFull180MinutesCreditedToFlextime() {
            // Given: Sonntag mit 3h Arbeitszeit
            val day = createWorkDay(
                date = sunday,
                dayType = DayType.WORK,
                timeBlocks = listOf(createDurationBlock(180))
            )

            // When
            val balance = calculateFlextimeUseCase(listOf(day), defaultSettings)

            // Then: Volle 180 min der Gleitzeit gutgeschrieben
            assertThat(balance.earnedMinutes).isEqualTo(180L)
            assertThat(balance.totalMinutes).isEqualTo(180L)
            assertThat(balance.earnedOvertimeMinutes).isEqualTo(0L)
        }

        @Test
        @DisplayName("GIVEN Wochenende ohne Arbeitszeitblöcke, THEN 0 min Gleitzeit (kein Minus-Sollabzug)")
        fun givenZeroHoursOnWeekend_thenZeroFlextimeDelta() {
            val day = createWorkDay(
                date = saturday,
                dayType = DayType.WORK,
                timeBlocks = emptyList()
            )

            val balance = calculateFlextimeUseCase(listOf(day), defaultSettings)

            assertThat(balance.earnedMinutes).isEqualTo(0L)
            assertThat(balance.totalMinutes).isEqualTo(0L)
            assertThat(balance.earnedOvertimeMinutes).isEqualTo(0L)
        }
    }

    // =========================================================================
    // Szenario 3: DayType.VACATION und DayType.SPECIAL_VACATION
    // =========================================================================
    @Nested
    @DisplayName("Szenario 3: DayType.VACATION und DayType.SPECIAL_VACATION (Urlaub)")
    inner class VacationScenario {

        private val weekday = LocalDate.of(2026, 9, 7) // Montag

        @Test
        @DisplayName("GIVEN Wochentag mit DayType.VACATION und 0 gebuchten Stunden, THEN neutrales Delta (0 min) für Gleitzeit und Überstunden")
        fun givenVacationDayWithZeroHours_thenNeutralForFlextimeAndOvertime() {
            // Given: Regulärer Urlaubstag ohne Zeitblöcke
            val day = createWorkDay(
                date = weekday,
                dayType = DayType.VACATION,
                timeBlocks = emptyList()
            )

            // When
            val balance = calculateFlextimeUseCase(listOf(day), defaultSettings)

            // Then: Keine Minusstunden, Konten bleiben unberührt
            assertThat(balance.earnedMinutes).isEqualTo(0L)
            assertThat(balance.totalMinutes).isEqualTo(0L)
            assertThat(balance.earnedOvertimeMinutes).isEqualTo(0L)
            assertThat(balance.overtimeMinutes).isEqualTo(0L)
        }

        @Test
        @DisplayName("GIVEN Wochentag mit DayType.SPECIAL_VACATION und 0 gebuchten Stunden, THEN neutrales Delta (0 min)")
        fun givenSpecialVacationDayWithZeroHours_thenNeutralForFlextimeAndOvertime() {
            // Given: Sonderurlaub ohne Zeitblöcke
            val day = createWorkDay(
                date = weekday,
                dayType = DayType.SPECIAL_VACATION,
                timeBlocks = emptyList()
            )

            // When
            val balance = calculateFlextimeUseCase(listOf(day), defaultSettings)

            // Then: Keine Minusstunden
            assertThat(balance.earnedMinutes).isEqualTo(0L)
            assertThat(balance.totalMinutes).isEqualTo(0L)
            assertThat(balance.earnedOvertimeMinutes).isEqualTo(0L)
            assertThat(balance.overtimeMinutes).isEqualTo(0L)
        }

        @Test
        @DisplayName("GIVEN bestehende Startguthaben, WHEN Urlaubstag gebucht, THEN bleiben Gesamtkonten exakt unverändert")
        fun givenInitialBalances_whenVacationDay_thenBalancesRemainUnchanged() {
            val settingsWithBalance = defaultSettings.copy(
                initialFlextimeMinutes = 120,
                initialOvertimeMinutes = 60
            )
            val day = createWorkDay(
                date = weekday,
                dayType = DayType.VACATION
            )

            val balance = calculateFlextimeUseCase(listOf(day), settingsWithBalance)

            assertThat(balance.initialMinutes).isEqualTo(120)
            assertThat(balance.earnedMinutes).isEqualTo(0L)
            assertThat(balance.totalMinutes).isEqualTo(120L)
            assertThat(balance.earnedOvertimeMinutes).isEqualTo(0L)
            assertThat(balance.overtimeMinutes).isEqualTo(60L)
        }
    }

    // =========================================================================
    // Szenario 4: DayType.FLEX_DAY (Gleittag)
    // =========================================================================
    @Nested
    @DisplayName("Szenario 4: DayType.FLEX_DAY (Gleittag)")
    inner class FlexDayScenario {

        private val weekday = LocalDate.of(2026, 9, 8) // Dienstag

        @Test
        @DisplayName("GIVEN DayType.FLEX_DAY, THEN volles Tagessoll (-426 min) wird von Gleitzeit abgezogen, Überstunden unverändert")
        fun givenFlexDay_thenFullDailyTargetDeductedFromFlextime() {
            // Given: Gleittag
            val day = createWorkDay(
                date = weekday,
                dayType = DayType.FLEX_DAY
            )

            // When
            val balance = calculateFlextimeUseCase(listOf(day), defaultSettings)

            // Then: -426 min von Gleitzeit abgezogen, Überstunden = 0
            assertThat(balance.earnedMinutes).isEqualTo(-426L)
            assertThat(balance.totalMinutes).isEqualTo(-426L)
            assertThat(balance.earnedOvertimeMinutes).isEqualTo(0L)
            assertThat(balance.overtimeMinutes).isEqualTo(0L)
        }

        @Test
        @DisplayName("GIVEN Startguthaben von +500 min Gleitzeit, WHEN Gleittag genommen, THEN verbleiben +74 min Gleitzeit")
        fun givenInitialFlextime_whenFlexDay_thenReducesTotalCorrectly() {
            val settingsWithBalance = defaultSettings.copy(
                initialFlextimeMinutes = 500,
                initialOvertimeMinutes = 100
            )
            val day = createWorkDay(
                date = weekday,
                dayType = DayType.FLEX_DAY
            )

            val balance = calculateFlextimeUseCase(listOf(day), settingsWithBalance)

            assertThat(balance.earnedMinutes).isEqualTo(-426L)
            assertThat(balance.totalMinutes).isEqualTo(74L) // 500 - 426 = 74
            assertThat(balance.earnedOvertimeMinutes).isEqualTo(0L)
            assertThat(balance.overtimeMinutes).isEqualTo(100L) // Überstunden unberührt
        }

        @Test
        @DisplayName("GIVEN zwei aufeinanderfolgende Gleittage, THEN wird zweimal das volle Tagessoll (-852 min) abgezogen")
        fun givenTwoConsecutiveFlexDays_thenDoubleDailyTargetDeducted() {
            val day1 = createWorkDay(date = LocalDate.of(2026, 9, 8), dayType = DayType.FLEX_DAY)
            val day2 = createWorkDay(date = LocalDate.of(2026, 9, 9), dayType = DayType.FLEX_DAY)

            val balance = calculateFlextimeUseCase(listOf(day1, day2), defaultSettings)

            assertThat(balance.earnedMinutes).isEqualTo(-852L) // -2 * 426
            assertThat(balance.totalMinutes).isEqualTo(-852L)
            assertThat(balance.earnedOvertimeMinutes).isEqualTo(0L)
        }
    }

    // =========================================================================
    // Szenario 5: DayType.OVERTIME_DAY (Überstunden-Abbau-Tag)
    // =========================================================================
    @Nested
    @DisplayName("Szenario 5: DayType.OVERTIME_DAY (Überstunden-Abbau-Tag)")
    inner class OvertimeDayScenario {

        private val weekday = LocalDate.of(2026, 9, 10) // Donnerstag

        @Test
        @DisplayName("GIVEN DayType.OVERTIME_DAY, THEN volles Tagessoll (-426 min) wird von Überstunden abgezogen, Gleitzeit unberührt (0 delta)")
        fun givenOvertimeDay_thenFullDailyTargetDeductedFromOvertimeAndFlextimeUntouched() {
            // Given: Überstunden-Abbau-Tag
            val day = createWorkDay(
                date = weekday,
                dayType = DayType.OVERTIME_DAY
            )

            // When
            val balance = calculateFlextimeUseCase(listOf(day), defaultSettings)

            // Then: Überstundenkonto wird um Tagessoll gemindert, Gleitzeitkonto bleibt bei 0 Delta
            assertThat(balance.earnedMinutes).isEqualTo(0L)
            assertThat(balance.totalMinutes).isEqualTo(0L)
            assertThat(balance.earnedOvertimeMinutes).isEqualTo(-426L)
            assertThat(balance.overtimeMinutes).isEqualTo(-426L)
        }

        @Test
        @DisplayName("GIVEN Start-Überstunden von +600 min, WHEN Überstunden-Abbau-Tag, THEN Überstunden = +174 min und Gleitzeit unberührt")
        fun givenInitialOvertime_whenOvertimeDay_thenReducesOvertimeBalance() {
            val settingsWithBalance = defaultSettings.copy(
                initialFlextimeMinutes = 80,
                initialOvertimeMinutes = 600
            )
            val day = createWorkDay(
                date = weekday,
                dayType = DayType.OVERTIME_DAY
            )

            val balance = calculateFlextimeUseCase(listOf(day), settingsWithBalance)

            assertThat(balance.earnedMinutes).isEqualTo(0L)
            assertThat(balance.totalMinutes).isEqualTo(80L) // Gleitzeit völlig unberührt
            assertThat(balance.earnedOvertimeMinutes).isEqualTo(-426L)
            assertThat(balance.overtimeMinutes).isEqualTo(174L) // 600 - 426 = 174
        }
    }

    // =========================================================================
    // Szenario 6: DayType.SICK_DAY (Krankheitstag)
    // =========================================================================
    @Nested
    @DisplayName("Szenario 6: DayType.SICK_DAY (Krankheitstag)")
    inner class SickDayScenario {

        private val weekday = LocalDate.of(2026, 9, 11) // Freitag

        @Test
        @DisplayName("GIVEN Wochentag mit DayType.SICK_DAY und 0 gebuchten Stunden, THEN neutrales Delta (0 min) für beide Konten")
        fun givenSickDayWithZeroHours_thenNeutralForFlextimeAndOvertime() {
            // Given: Krankheitstag
            val day = createWorkDay(
                date = weekday,
                dayType = DayType.SICK_DAY,
                timeBlocks = emptyList()
            )

            // When
            val balance = calculateFlextimeUseCase(listOf(day), defaultSettings)

            // Then: Keine Minusstunden, Konten unverändert
            assertThat(balance.earnedMinutes).isEqualTo(0L)
            assertThat(balance.totalMinutes).isEqualTo(0L)
            assertThat(balance.earnedOvertimeMinutes).isEqualTo(0L)
            assertThat(balance.overtimeMinutes).isEqualTo(0L)
        }

        @Test
        @DisplayName("GIVEN mehrere aufeinanderfolgende Krankheitstage, THEN bleiben alle Konten vollständig unverändert (0 Delta)")
        fun givenMultipleConsecutiveSickDays_thenAllDeltasRemainZero() {
            val days = listOf(
                createWorkDay(date = LocalDate.of(2026, 9, 14), dayType = DayType.SICK_DAY),
                createWorkDay(date = LocalDate.of(2026, 9, 15), dayType = DayType.SICK_DAY),
                createWorkDay(date = LocalDate.of(2026, 9, 16), dayType = DayType.SICK_DAY)
            )

            val balance = calculateFlextimeUseCase(days, defaultSettings)

            assertThat(balance.earnedMinutes).isEqualTo(0L)
            assertThat(balance.totalMinutes).isEqualTo(0L)
            assertThat(balance.earnedOvertimeMinutes).isEqualTo(0L)
            assertThat(balance.overtimeMinutes).isEqualTo(0L)
        }
    }

    // =========================================================================
    // Szenario 7: DayType.SATURDAY_BONUS (Samstagsarbeit mit 50% Bonus-Zuschlag)
    // =========================================================================
    @Nested
    @DisplayName("Szenario 7: DayType.SATURDAY_BONUS (Samstagsarbeit mit 50% Überstunden-Zuschlag)")
    inner class SaturdayBonusScenario {

        private val saturday = LocalDate.of(2026, 9, 19) // Samstag

        @Test
        @DisplayName("GIVEN SATURDAY_BONUS mit 4h (240 min) Arbeit, THEN +240 min Gleitzeit (100%) und +120 min Überstunden (50%)")
        fun given4HoursSaturdayBonus_then100PercentFlextimeAnd50PercentOvertimeBonus() {
            // Given: 4h Arbeit am Samstag mit Bonus
            val day = createWorkDay(
                date = saturday,
                dayType = DayType.SATURDAY_BONUS,
                timeBlocks = listOf(createDurationBlock(240))
            )

            // When
            val balance = calculateFlextimeUseCase(listOf(day), defaultSettings)

            // Then: 100% (240 min) auf Gleitzeit, 50% (120 min) auf Überstunden
            assertThat(balance.earnedMinutes).isEqualTo(240L)
            assertThat(balance.totalMinutes).isEqualTo(240L)
            assertThat(balance.earnedOvertimeMinutes).isEqualTo(120L)
            assertThat(balance.overtimeMinutes).isEqualTo(120L)
        }

        @Test
        @DisplayName("GIVEN SATURDAY_BONUS mit 5.5h (330 min) Arbeit, THEN +330 min Gleitzeit und +165 min Überstunden (gerundet)")
        fun givenOddHoursSaturdayBonus_thenCorrectlyRoundedBonusToOvertime() {
            // Given: 330 min (5h 30m) Arbeit
            val day = createWorkDay(
                date = saturday,
                dayType = DayType.SATURDAY_BONUS,
                timeBlocks = listOf(createDurationBlock(330))
            )

            // When
            val balance = calculateFlextimeUseCase(listOf(day), defaultSettings)

            // Then: 330 min Gleitzeit, 330 * 0.5 = 165 min Überstunden
            assertThat(balance.earnedMinutes).isEqualTo(330L)
            assertThat(balance.totalMinutes).isEqualTo(330L)
            assertThat(balance.earnedOvertimeMinutes).isEqualTo(165L)
            assertThat(balance.overtimeMinutes).isEqualTo(165L)
        }

        @Test
        @DisplayName("GIVEN SATURDAY_BONUS mit 8h (480 min) Arbeit, THEN +480 min Gleitzeit und +240 min Überstunden")
        fun given8HoursSaturdayBonus_then480MinutesFlextimeAnd240MinutesOvertime() {
            // Given: 8h Arbeit (480 min)
            val day = createWorkDay(
                date = saturday,
                dayType = DayType.SATURDAY_BONUS,
                timeBlocks = listOf(createDurationBlock(480))
            )

            // When
            val balance = calculateFlextimeUseCase(listOf(day), defaultSettings)

            // Then: 480 min Gleitzeit, 240 min Überstunden
            assertThat(balance.earnedMinutes).isEqualTo(480L)
            assertThat(balance.totalMinutes).isEqualTo(480L)
            assertThat(balance.earnedOvertimeMinutes).isEqualTo(240L)
            assertThat(balance.overtimeMinutes).isEqualTo(240L)
        }

        @Test
        @DisplayName("GIVEN SATURDAY_BONUS mit 0h gearbeitet, THEN 0 min Gleitzeit und 0 min Überstunden")
        fun givenZeroHoursSaturdayBonus_thenZeroDeltas() {
            val day = createWorkDay(
                date = saturday,
                dayType = DayType.SATURDAY_BONUS,
                timeBlocks = emptyList()
            )

            val balance = calculateFlextimeUseCase(listOf(day), defaultSettings)

            assertThat(balance.earnedMinutes).isEqualTo(0L)
            assertThat(balance.totalMinutes).isEqualTo(0L)
            assertThat(balance.earnedOvertimeMinutes).isEqualTo(0L)
            assertThat(balance.overtimeMinutes).isEqualTo(0L)
        }
    }

    // =========================================================================
    // Szenario 8: Umfassende Integration über alle 7 DayType-Werte hinweg
    // =========================================================================
    @Nested
    @DisplayName("Szenario 8: Umfassende Kombination aller 7 Arbeitstypen")
    inner class ComprehensiveAllDayTypesIntegrationScenario {

        @Test
        @DisplayName("GIVEN 9-Tage-Sequenz mit allen 7 DayTypes, THEN kumulieren Gleitzeit und Überstunden exakt gemäß Spezifikation")
        fun givenAllDayTypesInSequence_thenAccumulatesBalancesAccurately() {
            // Tag 1 (Mo, 21.09.): WORK Wochentag, 8h = 480 min -> +54 min Gleitzeit, 0 ÜS
            val day1 = createWorkDay(
                date = LocalDate.of(2026, 9, 21),
                dayType = DayType.WORK,
                timeBlocks = listOf(createDurationBlock(480))
            )
            // Tag 2 (Di, 22.09.): WORK Wochentag, 6h = 360 min -> -66 min Gleitzeit, 0 ÜS
            val day2 = createWorkDay(
                date = LocalDate.of(2026, 9, 22),
                dayType = DayType.WORK,
                timeBlocks = listOf(createDurationBlock(360))
            )
            // Tag 3 (Mi, 23.09.): VACATION -> 0 Gleitzeit, 0 ÜS
            val day3 = createWorkDay(
                date = LocalDate.of(2026, 9, 23),
                dayType = DayType.VACATION
            )
            // Tag 4 (Do, 24.09.): SPECIAL_VACATION -> 0 Gleitzeit, 0 ÜS
            val day4 = createWorkDay(
                date = LocalDate.of(2026, 9, 24),
                dayType = DayType.SPECIAL_VACATION
            )
            // Tag 5 (Fr, 25.09.): SICK_DAY -> 0 Gleitzeit, 0 ÜS
            val day5 = createWorkDay(
                date = LocalDate.of(2026, 9, 25),
                dayType = DayType.SICK_DAY
            )
            // Tag 6 (Sa, 26.09.): SATURDAY_BONUS, 4h = 240 min -> +240 min Gleitzeit, +120 min ÜS
            val day6 = createWorkDay(
                date = LocalDate.of(2026, 9, 26),
                dayType = DayType.SATURDAY_BONUS,
                timeBlocks = listOf(createDurationBlock(240))
            )
            // Tag 7 (So, 27.09.): WORK Wochenende, 3h = 180 min -> +180 min Gleitzeit, 0 ÜS
            val day7 = createWorkDay(
                date = LocalDate.of(2026, 9, 27),
                dayType = DayType.WORK,
                timeBlocks = listOf(createDurationBlock(180))
            )
            // Tag 8 (Mo, 28.09.): FLEX_DAY -> -426 min Gleitzeit, 0 ÜS
            val day8 = createWorkDay(
                date = LocalDate.of(2026, 9, 28),
                dayType = DayType.FLEX_DAY
            )
            // Tag 9 (Di, 29.09.): OVERTIME_DAY -> 0 Gleitzeit, -426 min ÜS
            val day9 = createWorkDay(
                date = LocalDate.of(2026, 9, 29),
                dayType = DayType.OVERTIME_DAY
            )

            val settingsWithInitial = defaultSettings.copy(
                initialFlextimeMinutes = 100,
                initialOvertimeMinutes = 500
            )

            // When
            val allDays = listOf(day1, day2, day3, day4, day5, day6, day7, day8, day9)
            val balance = calculateFlextimeUseCase(allDays, settingsWithInitial)

            // Erwartete Gleitzeit-Ersparnis (earnedMinutes):
            // +54 (Mo) - 66 (Di) + 0 (Mi) + 0 (Do) + 0 (Fr) + 240 (Sa) + 180 (So) - 426 (Mo) + 0 (Di)
            // = -18 min
            assertThat(balance.earnedMinutes).isEqualTo(-18L)
            // totalMinutes: 100 (initial) + (-18) = 82 min
            assertThat(balance.totalMinutes).isEqualTo(82L)

            // Erwartete Überstunden-Ersparnis (earnedOvertimeMinutes):
            // 0 + 0 + 0 + 0 + 0 + 120 (Sa) + 0 (So) + 0 (Mo) - 426 (Di)
            // = -306 min
            assertThat(balance.earnedOvertimeMinutes).isEqualTo(-306L)
            // overtimeMinutes: 500 (initial) + (-306) = 194 min
            assertThat(balance.overtimeMinutes).isEqualTo(194L)
        }
    }

    // =========================================================================
    // Test-Hilfsmethoden
    // =========================================================================

    private fun createWorkDay(
        date: LocalDate,
        dayType: DayType = DayType.WORK,
        location: WorkLocation = WorkLocation.OFFICE,
        timeBlocks: List<TimeBlock> = emptyList()
    ): WorkDay = WorkDay(
        date = date,
        location = location,
        dayType = dayType,
        isPlanned = false,
        timeBlocks = timeBlocks
    )

    private fun createDurationBlock(
        minutes: Long,
        location: WorkLocation = WorkLocation.OFFICE
    ): TimeBlock = TimeBlock(
        startTime = LocalTime.of(8, 0),
        endTime = LocalTime.of(8, 0).plusMinutes(minutes),
        location = location,
        isDuration = true
    )

    private fun createClockBlock(
        start: LocalTime,
        end: LocalTime,
        location: WorkLocation = WorkLocation.OFFICE
    ): TimeBlock = TimeBlock(
        startTime = start,
        endTime = end,
        location = location,
        isDuration = false
    )
}
