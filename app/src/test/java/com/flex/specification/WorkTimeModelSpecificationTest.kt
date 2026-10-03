package com.flex.specification

import android.content.ContentResolver
import android.net.Uri
import com.google.common.truth.Truth.assertThat
import com.flex.data.export.ExportService
import com.flex.domain.model.DayType
import com.flex.domain.model.DEFAULT_WORK_DAYS
import com.flex.domain.model.Settings
import com.flex.domain.model.TimeBlock
import com.flex.domain.model.WorkDay
import com.flex.domain.model.WorkLocation
import com.flex.domain.model.WorkTimeRule
import com.flex.domain.repository.SettingsRepository
import com.flex.domain.repository.WorkDayRepository
import com.flex.domain.usecase.BuildPrognosisDaysUseCase
import com.flex.domain.usecase.CalculateDayWorkTimeUseCase
import com.flex.domain.usecase.CalculateFlextimeUseCase
import com.flex.domain.usecase.PrepareExportDataUseCase
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.ByteArrayOutputStream
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

/**
 * BDD-Spezifikationstestsuite für verschiedene Arbeitszeitmodelle (WorkTimeRules).
 *
 * Spezifiziert und verifiziert:
 * 1. 40-Stunden-Woche (8:00 h / 480 min täglich)
 * 2. Teilzeit-Modell (20h-Woche / 4:00 h täglich) mit Urlaub & Gleittag
 * 3. 4-Tage-Woche (Mo–Do je 8:00 h, Freitag frei) inkl. Prognose & Arbeit an freien Tagen
 * 4. Unterjähriger Modellwechsel zum Stichtag (Vollzeit -> Teilzeit)
 * 5. Export- und Berichtsvalidierung für abweichende Sollzeiten
 */
@DisplayName("Spezifikation: Arbeitszeitmodelle BDD Testsuite")
class WorkTimeModelSpecificationTest {

    private val calculateDayWorkTime = CalculateDayWorkTimeUseCase()
    private val calculateFlextime = CalculateFlextimeUseCase(calculateDayWorkTime)
    private val buildPrognosisDays = BuildPrognosisDaysUseCase()

    private val baseSettings = Settings(
        dailyWorkMinutes = 426, // 7:06h Default als Fallback
        monthlyWorkMinutes = 9372,
        initialFlextimeMinutes = 0,
        initialOvertimeMinutes = 0
    )

    private fun createWorkDay(
        date: LocalDate,
        dayType: DayType = DayType.WORK,
        location: WorkLocation = WorkLocation.OFFICE,
        startTime: LocalTime,
        endTime: LocalTime
    ): WorkDay {
        return WorkDay(
            id = date.toEpochDay(),
            date = date,
            dayType = dayType,
            location = location,
            timeBlocks = listOf(
                TimeBlock(
                    id = date.toEpochDay(),
                    workDayId = date.toEpochDay(),
                    startTime = startTime,
                    endTime = endTime,
                    location = location
                )
            )
        )
    }

    @Nested
    @DisplayName("Szenario 1: 40-Stunden-Woche (8:00 h / 480 min täglich)")
    inner class FortyHourWeekScenario {

        private val rule40h = WorkTimeRule(
            id = 1L,
            validFrom = YearMonth.of(2026, 9),
            dailyWorkMinutes = 480, // 8:00 h
            monthlyWorkMinutes = 10560, // 22 Tage * 480 min
            workDays = DEFAULT_WORK_DAYS
        )

        @Test
        @DisplayName("Berechnet Tagesdifferenzen und Monatsendsaldo relativ zum 8:00h-Soll")
        fun calculates40hWeekDailyDeltasAndBalance() {
            // Given: 40h-Woche-Regel für September 2026
            val rules = listOf(rule40h)

            // 01.09.2026 (Di): 8:30 h gearbeitet (08:30 - 17:30, 30m Pause -> 8:30h Netto / 510m)
            val day1 = createWorkDay(
                date = LocalDate.of(2026, 9, 1),
                startTime = LocalTime.of(8, 30),
                endTime = LocalTime.of(17, 30) // 9:00h Brutto - 0:30h Pause = 8:30h Netto (510 min)
            )

            // 02.09.2026 (Mi): 7:00 h gearbeitet (09:00 - 16:30, 30m Pause -> 7:00h Netto / 420m)
            val day2 = createWorkDay(
                date = LocalDate.of(2026, 9, 2),
                startTime = LocalTime.of(9, 0),
                endTime = LocalTime.of(16, 30) // 7:30h Brutto - 0:30h Pause = 7:00h Netto (420 min)
            )

            // 03.09.2026 (Do): Punktlandung 8:00 h (08:30 - 17:00, 30m Pause -> 8:00h Netto / 480m)
            val day3 = createWorkDay(
                date = LocalDate.of(2026, 9, 3),
                startTime = LocalTime.of(8, 30),
                endTime = LocalTime.of(17, 0) // 8:30h Brutto - 0:30h Pause = 8:00h Netto (480 min)
            )

            // 05.09.2026 (Sa): 3:00 h Samstagsarbeit (180m)
            val day4 = createWorkDay(
                date = LocalDate.of(2026, 9, 5),
                startTime = LocalTime.of(9, 0),
                endTime = LocalTime.of(12, 0) // 3:00h Netto (180 min)
            )

            // When: Einzeltage für den Kalender berechnet werden
            val deltaDay1 = calculateFlextime(listOf(day1), baseSettings, workTimeRules = rules).totalMinutes
            val deltaDay2 = calculateFlextime(listOf(day2), baseSettings, workTimeRules = rules).totalMinutes
            val deltaDay3 = calculateFlextime(listOf(day3), baseSettings, workTimeRules = rules).totalMinutes
            val deltaDay4 = calculateFlextime(listOf(day4), baseSettings, workTimeRules = rules).totalMinutes

            // When: Monatsabschluss für September 2026 berechnet wird
            val monthBalance = calculateFlextime(
                workDays = listOf(day1, day2, day3, day4),
                settings = baseSettings,
                yearMonth = YearMonth.of(2026, 9),
                workTimeRules = rules
            )

            // Then:
            // Tag 1: 510m - 480m = +30m (+0:30)
            assertThat(deltaDay1).isEqualTo(30L)

            // Tag 2: 420m - 480m = -60m (-1:00)
            assertThat(deltaDay2).isEqualTo(-60L)

            // Tag 3: 480m - 480m = 0m (0:00)
            assertThat(deltaDay3).isEqualTo(0L)

            // Tag 4 (Samstag): 180m zählt voll als Gleitzeit (+3:00)
            assertThat(deltaDay4).isEqualTo(180L)

            // Monatssoll für September 2026 (22 Mo-Fr Arbeitstage à 480m = 10.560m / 176:00h)
            assertThat(monthBalance.targetMinutes).isEqualTo(22L * 480L)
            assertThat(monthBalance.targetMinutes).isEqualTo(10560L)

            // Gesamtsaldo = +30 - 60 + 0 + 180 = +150m (+2:30h)
            assertThat(monthBalance.earnedMinutes).isEqualTo(150L)
            assertThat(monthBalance.totalMinutes).isEqualTo(150L)
        }
    }

    @Nested
    @DisplayName("Szenario 2: Teilzeit-Modell (20h-Woche / 4:00 h täglich / 240 min Soll)")
    inner class PartTimeScenario {

        private val rulePartTime = WorkTimeRule(
            id = 2L,
            validFrom = YearMonth.of(2026, 9),
            dailyWorkMinutes = 240, // 4:00 h
            monthlyWorkMinutes = 5280, // 22 Tage * 240 min
            workDays = DEFAULT_WORK_DAYS
        )

        @Test
        @DisplayName("Zieht bei Gleittagen exakt 4:00 h ab und behandelt Urlaub/Krank neutral")
        fun partTimeHandlesWorkFlexVacationAndSickDays() {
            val rules = listOf(rulePartTime)

            // 01.09.2026 (Di): 5:00 h Netto gearbeitet (300 min) -> Tagessoll 240 min -> +60 min
            val workDay = createWorkDay(
                date = LocalDate.of(2026, 9, 1),
                startTime = LocalTime.of(8, 0),
                endTime = LocalTime.of(13, 0)
            )

            // 02.09.2026 (Mi): Urlaubstag
            val vacationDay = WorkDay(
                id = 2L,
                date = LocalDate.of(2026, 9, 2),
                dayType = DayType.VACATION,
                location = WorkLocation.HOME_OFFICE
            )

            // 03.09.2026 (Do): Gleittag -> muss genau 240 min abziehen!
            val flexDay = WorkDay(
                id = 3L,
                date = LocalDate.of(2026, 9, 3),
                dayType = DayType.FLEX_DAY,
                location = WorkLocation.HOME_OFFICE
            )

            // 04.09.2026 (Fr): Krank
            val sickDay = WorkDay(
                id = 4L,
                date = LocalDate.of(2026, 9, 4),
                dayType = DayType.SICK_DAY,
                location = WorkLocation.HOME_OFFICE
            )

            // When: Einzeltage berechnet werden
            val deltaWork = calculateFlextime(listOf(workDay), baseSettings, workTimeRules = rules).totalMinutes
            val deltaVacation = calculateFlextime(listOf(vacationDay), baseSettings, workTimeRules = rules).totalMinutes
            val deltaFlex = calculateFlextime(listOf(flexDay), baseSettings, workTimeRules = rules).totalMinutes
            val deltaSick = calculateFlextime(listOf(sickDay), baseSettings, workTimeRules = rules).totalMinutes

            // Then:
            // Arbeitstag: 300m - 240m = +60m (+1:00h)
            assertThat(deltaWork).isEqualTo(60L)

            // Urlaubstag: neutral (0m)
            assertThat(deltaVacation).isEqualTo(0L)

            // Gleittag: zieht genau 240m ab (-4:00h)
            assertThat(deltaFlex).isEqualTo(-240L)

            // Krankentag: neutral (0m)
            assertThat(deltaSick).isEqualTo(0L)

            // Gesamtsaldo aus allen 4 Tagen = +60 - 240 = -180m (-3:00h)
            val balance = calculateFlextime(
                workDays = listOf(workDay, vacationDay, flexDay, sickDay),
                settings = baseSettings,
                yearMonth = YearMonth.of(2026, 9),
                workTimeRules = rules
            )
            assertThat(balance.earnedMinutes).isEqualTo(-180L)
            assertThat(balance.targetMinutes).isEqualTo(22L * 240L)
        }
    }

    @Nested
    @DisplayName("Szenario 3: 4-Tage-Woche (Mo–Do je 8:00 h, Freitag frei)")
    inner class FourDayWeekScenario {

        private val fourDayWorkDays = setOf(
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY
        )

        private val rule4Days = WorkTimeRule(
            id = 3L,
            validFrom = YearMonth.of(2026, 9),
            dailyWorkMinutes = 480, // 8:00 h
            monthlyWorkMinutes = 8640, // 18 Mo-Do Tage * 480 min
            workDays = fourDayWorkDays
        )

        @Test
        @DisplayName("Monatssoll und Prognose ignorieren freie Freitage; Arbeit am Freitag zählt voll als Gleitzeit")
        fun fourDayWeekExcludesFridaysFromTargetAndCountsFridayWorkAsFlextime() {
            val rules = listOf(rule4Days)

            // 1. Monatssoll für September 2026 prüfen
            // Im September 2026 gibt es 30 Tage:
            // 4x Mo, 5x Di, 5x Mi, 4x Do = 18 Mo-Do Tage.
            // 4x Fr (4., 11., 18., 25.09.) sind arbeitsfrei.
            val emptyBalance = calculateFlextime(
                workDays = emptyList(),
                settings = baseSettings,
                yearMonth = YearMonth.of(2026, 9),
                workTimeRules = rules
            )
            assertThat(emptyBalance.targetMinutes).isEqualTo(18L * 480L) // 8.640 min = 144:00 h

            // 2. Prognose prüfen: enthält nur Mo-Do, keine Freitage
            val prognosis = buildPrognosisDays(
                month = YearMonth.of(2026, 9),
                existingDays = emptyList(),
                settings = baseSettings,
                workTimeRules = rules
            )
            assertThat(prognosis).hasSize(18)
            assertThat(prognosis.none { it.date.dayOfWeek == DayOfWeek.FRIDAY }).isTrue()
            assertThat(prognosis.none { it.date.dayOfWeek == DayOfWeek.SATURDAY }).isTrue()
            assertThat(prognosis.none { it.date.dayOfWeek == DayOfWeek.SUNDAY }).isTrue()

            // 3. Regulärer Arbeitstag (Donnerstag, 03.09.2026): 8:00 h gearbeitet -> Delta = 0
            val thursdayWork = createWorkDay(
                date = LocalDate.of(2026, 9, 3),
                startTime = LocalTime.of(8, 30),
                endTime = LocalTime.of(17, 0) // 8:00h Netto
            )
            val deltaThu = calculateFlextime(listOf(thursdayWork), baseSettings, workTimeRules = rules).totalMinutes
            assertThat(deltaThu).isEqualTo(0L)

            // 4. Arbeit an einem freien Freitag (04.09.2026): 4:00 h gearbeitet (240 min)
            // Da Freitag ein Nicht-Arbeitstag ist, muss die volle Zeit als Gleitzeit zählen (+240m)!
            val fridayWork = createWorkDay(
                date = LocalDate.of(2026, 9, 4),
                startTime = LocalTime.of(9, 0),
                endTime = LocalTime.of(13, 0) // 4:00h Netto (240 min)
            )
            val deltaFri = calculateFlextime(listOf(fridayWork), baseSettings, workTimeRules = rules).totalMinutes
            assertThat(deltaFri).isEqualTo(240L) // Volle 4h als Plusstunden
        }
    }

    @Nested
    @DisplayName("Szenario 4: Unterjähriger Stichtagswechsel (Vollzeit -> Teilzeit)")
    inner class MidYearRuleChangeScenario {

        private val ruleVollzeit = WorkTimeRule(
            id = 1L,
            validFrom = YearMonth.of(2026, 1),
            dailyWorkMinutes = 480, // 8:00 h
            monthlyWorkMinutes = 0,
            workDays = DEFAULT_WORK_DAYS
        )

        private val ruleTeilzeit = WorkTimeRule(
            id = 2L,
            validFrom = YearMonth.of(2026, 7),
            dailyWorkMinutes = 360, // 6:00 h
            monthlyWorkMinutes = 0,
            workDays = DEFAULT_WORK_DAYS
        )

        @Test
        @DisplayName("Wendet vor und nach dem 01.07.2026 tagesgenau das jeweilige Tagessoll an")
        fun appliesCorrectDailyTargetAcrossRuleBoundary() {
            val rules = listOf(ruleVollzeit, ruleTeilzeit)

            // Letzter Tag vor dem Wechsel: 30.06.2026 (Di) mit 7:00 h (420m) Netto
            // Tagessoll = 480m (Vollzeit) -> Delta = 420 - 480 = -60m (-1:00h)
            val dayJune = createWorkDay(
                date = LocalDate.of(2026, 6, 30),
                startTime = LocalTime.of(9, 0),
                endTime = LocalTime.of(16, 30) // 7:00h Netto (420 min)
            )

            // Erster Tag nach dem Wechsel: 01.07.2026 (Mi) mit 7:00 h (420m) Netto
            // Tagessoll = 360m (Teilzeit) -> Delta = 420 - 360 = +60m (+1:00h)
            val dayJuly = createWorkDay(
                date = LocalDate.of(2026, 7, 1),
                startTime = LocalTime.of(9, 0),
                endTime = LocalTime.of(16, 30) // 7:00h Netto (420 min)
            )

            val deltaJune = calculateFlextime(listOf(dayJune), baseSettings, workTimeRules = rules).totalMinutes
            val deltaJuly = calculateFlextime(listOf(dayJuly), baseSettings, workTimeRules = rules).totalMinutes

            assertThat(deltaJune).isEqualTo(-60L)
            assertThat(deltaJuly).isEqualTo(60L)

            // Kumulierter Gesamtsaldo über beide Tage gleicht sich aus
            val combined = calculateFlextime(listOf(dayJune, dayJuly), baseSettings, workTimeRules = rules).totalMinutes
            assertThat(combined).isEqualTo(0L)
        }
    }

    @Nested
    @DisplayName("Szenario 5: Export- und Berichtsvalidierung für alternatives Arbeitszeitmodell")
    inner class ExportWithCustomWorkTimeModelScenario {

        private val ruleTeilzeit = WorkTimeRule(
            id = 1L,
            validFrom = YearMonth.of(2026, 9),
            dailyWorkMinutes = 360, // 6:00 h
            monthlyWorkMinutes = 7920, // 22 * 360 min
            workDays = DEFAULT_WORK_DAYS
        )

        @Test
        @DisplayName("ExportData und CSV-Export enthalten modellspezifisches Tagessoll und korrekte Differenzen")
        fun exportDataReflectsCustomModelTargetAndDifferences() = runTest {
            // Given: 2 Arbeitstage im September 2026 mit Teilzeit (360 min Soll)
            val day1 = createWorkDay(
                date = LocalDate.of(2026, 9, 1),
                startTime = LocalTime.of(9, 0),
                endTime = LocalTime.of(15, 30) // 6:00h Netto (360 min) -> Diff 0:00
            )
            val day2 = createWorkDay(
                date = LocalDate.of(2026, 9, 2),
                startTime = LocalTime.of(9, 0),
                endTime = LocalTime.of(16, 30) // 7:00h Netto (420 min) -> Diff +1:00
            )

            val workDayRepository: WorkDayRepository = mock()
            val settingsRepository: SettingsRepository = mock()
            val exportService = ExportService()

            whenever(workDayRepository.getWorkDaysForMonth(YearMonth.of(2026, 9)))
                .thenReturn(flowOf(listOf(day1, day2)))
            whenever(settingsRepository.getSettings())
                .thenReturn(flowOf(baseSettings))
            whenever(settingsRepository.getWorkTimeRules())
                .thenReturn(flowOf(listOf(ruleTeilzeit)))
            whenever(settingsRepository.getWorkTimeRuleForDate(any(), any()))
                .thenReturn(ruleTeilzeit)

            val prepareExportData = PrepareExportDataUseCase(
                workDayRepository = workDayRepository,
                settingsRepository = settingsRepository,
                calculateDayWorkTime = calculateDayWorkTime
            )

            // When: Exportdaten aufbereitet werden
            val exportData = prepareExportData(YearMonth.of(2026, 9))

            // Then:
            // Tag 1: Netto 360m, Soll 360m, Diff 0
            val row1 = exportData.rows[0]
            assertThat(row1.netMinutes).isEqualTo(360L)
            assertThat(row1.targetMinutes).isEqualTo(360)
            assertThat(row1.netMinutes - row1.targetMinutes).isEqualTo(0L)

            // Tag 2: Netto 420m, Soll 360m, Diff +60m
            val row2 = exportData.rows[1]
            assertThat(row2.netMinutes).isEqualTo(420L)
            assertThat(row2.targetMinutes).isEqualTo(360)
            assertThat(row2.netMinutes - row2.targetMinutes).isEqualTo(60L)

            // Monatssoll im Export = 22 Mo-Fr Arbeitstage * 360m = 7.920 min (132:00 h)
            assertThat(exportData.totalTargetMinutes).isEqualTo(22 * 360)

            // CSV exportieren und prüfen
            val outputStream = ByteArrayOutputStream()
            val contentResolver: ContentResolver = mock()
            val uri: Uri = mock()
            whenever(contentResolver.openOutputStream(uri)).thenReturn(outputStream)

            exportService.exportToCsv(exportData, uri, contentResolver)
            val csv = outputStream.toString(Charsets.UTF_8).removePrefix("\uFEFF")
            val lines = csv.trim().lines()

            // Zeile 01.09: Netto 6:00, Soll 6:00, Diff +0:00
            val line1 = lines.find { it.startsWith("01.09.2026;") }
            assertThat(line1).isNotNull()
            assertThat(line1).contains(";6:00;6:00;+0:00;")

            // Zeile 02.09: Netto 7:00, Soll 6:00, Diff +1:00
            val line2 = lines.find { it.startsWith("02.09.2026;") }
            assertThat(line2).isNotNull()
            assertThat(line2).contains(";7:00;6:00;+1:00;")

            // Summenzeile: Netto 13:00 (2 erfasste Tage), Monatssoll 132:00 (22 Tage à 6:00), Diff -119:00
            val summaryLine = lines.last()
            assertThat(summaryLine).contains(";13:00;132:00;119:00;")
        }
    }
}
