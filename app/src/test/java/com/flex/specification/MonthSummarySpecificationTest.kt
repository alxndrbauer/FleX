package com.flex.specification

import android.content.ContentResolver
import android.net.Uri
import com.google.common.truth.Truth.assertThat
import com.flex.data.export.ExportService
import com.flex.domain.model.DayType
import com.flex.domain.model.TimeBlock
import com.flex.domain.model.WorkDay
import com.flex.domain.model.WorkLocation
import com.flex.domain.repository.SettingsRepository
import com.flex.domain.repository.WorkDayRepository
import com.flex.domain.usecase.CalculateDayWorkTimeUseCase
import com.flex.domain.usecase.CalculateFlextimeUseCase
import com.flex.domain.usecase.CalculateQuotaUseCase
import com.flex.domain.usecase.PrepareExportDataUseCase
import com.flex.specification.fixture.September2026ExportFixture
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.LocalTime

@DisplayName("Spezifikation: Monatsabschluss & Export-Roundtrip BDD Test (September 2026)")
class MonthSummarySpecificationTest {

    private val calculateDayWorkTime = CalculateDayWorkTimeUseCase()
    private val calculateFlextime = CalculateFlextimeUseCase(calculateDayWorkTime)
    private val calculateQuota = CalculateQuotaUseCase(calculateDayWorkTime)

    private val defaultSettings = September2026ExportFixture.createDefaultSettings()
    private val septemberWorkDays = September2026ExportFixture.createSeptember2026WorkDays()

    @Nested
    @DisplayName("Szenario 1: Vollständiger Monatsabschluss September 2026")
    inner class FullMonthSummaryScenario {

        @Test
        @DisplayName("Berechnet Sollzeit, Istzeit und Gleitzeitsaldo für September 2026 exakt nach FleX-Export")
        fun calculatesSeptemberFlextimeCorrectly() {
            // Given: Default Settings (426 min / 7:06h tägliches Soll, 40% Quote, 8 Mindesttage)
            //        und 22 reale WorkDays aus September2026ExportFixture
            val workDays = septemberWorkDays
            val settings = defaultSettings

            // When: CalculateFlextimeUseCase für September 2026 aufgerufen wird
            val balance = calculateFlextime(
                workDays = workDays,
                settings = settings,
                yearMonth = September2026ExportFixture.YEAR_MONTH
            )

            // Then:
            // 22 Arbeitstage * 426 min = 9372L (156:12 h Sollzeit)
            assertThat(balance.targetMinutes).isEqualTo(September2026ExportFixture.EXPECTED_MONTHLY_TARGET_MINUTES)
            assertThat(balance.targetMinutes).isEqualTo(9372L)

            // Netto-Arbeitszeit: 9496 min - Sollzeit 9372 min = 124L (+2:04 h)
            assertThat(balance.earnedMinutes).isEqualTo(September2026ExportFixture.EXPECTED_DIFF_MINUTES)
            assertThat(balance.earnedMinutes).isEqualTo(124L)

            // Gesamt-Gleitzeitkonto (Initial 0 + Verdienst 124) = 124L
            assertThat(balance.totalMinutes).isEqualTo(124L)

            // Überstundenkonto bleibt unberührt (kein Samstag-/Überstundentag) = 0L
            assertThat(balance.overtimeMinutes).isEqualTo(0L)
            assertThat(balance.earnedOvertimeMinutes).isEqualTo(0L)
        }
    }

    @Nested
    @DisplayName("Szenario 2: Quoten-Auswertung September 2026")
    inner class QuotaEvaluationScenario {

        @Test
        @DisplayName("Ermittelt 8 Büro- und 14 Home-Office-Tage, 158:16h Gesamtarbeitszeit und prüft Quotenerfüllung")
        fun evaluatesSeptemberQuotaCorrectly() {
            // Given: 22 Arbeitstage und Default Settings
            val workDays = septemberWorkDays
            val settings = defaultSettings

            // When: CalculateQuotaUseCase mit den September 2026 Arbeitstagen aufgerufen wird
            val quotaStatus = calculateQuota(
                workDays = workDays,
                settings = settings,
                yearMonth = September2026ExportFixture.YEAR_MONTH
            )

            // Then:
            // 8 Tage im Büro (01., 02., 07., 08., 14., 15., 22., 30.09.)
            assertThat(quotaStatus.officeDays).isEqualTo(September2026ExportFixture.EXPECTED_OFFICE_DAYS)
            assertThat(quotaStatus.officeDays).isEqualTo(8)

            // 14 Tage im Home Office
            assertThat(quotaStatus.homeOfficeDays).isEqualTo(September2026ExportFixture.EXPECTED_HOME_OFFICE_DAYS)
            assertThat(quotaStatus.homeOfficeDays).isEqualTo(14)

            // Summe der Netto-Minuten aus Büro + HO = 9496L (158:16 h)
            val totalMinutes = quotaStatus.officeMinutes + quotaStatus.homeOfficeMinutes
            assertThat(totalMinutes).isEqualTo(September2026ExportFixture.EXPECTED_NET_MINUTES)
            assertThat(totalMinutes).isEqualTo(9496L)
            assertThat(quotaStatus.officeMinutes).isEqualTo(3635L) // 60:35 h
            assertThat(quotaStatus.homeOfficeMinutes).isEqualTo(5861L) // 97:41 h

            // Tage-basierte Büro-Quote: 8 von 22 Tagen = 36.36% (~36.4%)
            val officeDaysPercentage = (quotaStatus.officeDays.toDouble() / (quotaStatus.officeDays + quotaStatus.homeOfficeDays)) * 100
            assertThat(officeDaysPercentage).isWithin(0.1).of(36.4)

            // Zeit-basierte Büro-Quote (UseCase): 3635 min / 9372 Sollminuten = 38.78% (~38.8%)
            assertThat(quotaStatus.officePercent).isWithin(0.1).of(38.8)

            // Tage-Quote erfüllt: 8 Tage >= 8 Mindesttage
            assertThat(quotaStatus.daysQuotaMet).isTrue()

            // Prozent-Quote nicht erfüllt: 38.8% < 40% Mindestquote
            assertThat(quotaStatus.percentQuotaMet).isFalse()
        }
    }

    @Nested
    @DisplayName("Szenario 3: Export Data Preparation & CSV Output Roundtrip")
    inner class ExportDataPreparationAndCsvRoundtripScenario {

        private val workDayRepository: WorkDayRepository = mock()
        private val settingsRepository: SettingsRepository = mock()
        private val exportService = ExportService()
        private lateinit var prepareExportDataUseCase: PrepareExportDataUseCase

        @BeforeEach
        fun setUp() {
            prepareExportDataUseCase = PrepareExportDataUseCase(
                workDayRepository = workDayRepository,
                settingsRepository = settingsRepository,
                calculateDayWorkTime = calculateDayWorkTime
            )

            whenever(workDayRepository.getWorkDaysForMonth(September2026ExportFixture.YEAR_MONTH))
                .thenReturn(flowOf(septemberWorkDays))
            whenever(settingsRepository.getSettings())
                .thenReturn(flowOf(defaultSettings))
            whenever(settingsRepository.getWorkTimeRules())
                .thenReturn(flowOf(emptyList()))
            whenever(settingsRepository.getWorkTimeRuleForDate(any(), any()))
                .thenReturn(null)
        }

        @Test
        @DisplayName("PrepareExportDataUseCase erzeugt 30 Zeilen und korrekte Summen für September 2026")
        fun preparesExportDataCorrectly() = runTest {
            // When: PrepareExportDataUseCase für September 2026 aufgerufen wird
            val exportData = prepareExportDataUseCase(September2026ExportFixture.YEAR_MONTH)

            // Then:
            // Exakt 30 Zeilen für 30 Kalendertage im September
            assertThat(exportData.rows).hasSize(30)

            // Gesamte Netto-Arbeitszeit = 9496L (158:16 h)
            assertThat(exportData.totalNetMinutes).isEqualTo(September2026ExportFixture.EXPECTED_NET_MINUTES)
            assertThat(exportData.totalNetMinutes).isEqualTo(9496L)

            // Gesamte Sollzeit = 9372L (156:12 h)
            assertThat(exportData.totalTargetMinutes).isEqualTo(September2026ExportFixture.EXPECTED_MONTHLY_TARGET_MINUTES)
            assertThat(exportData.totalTargetMinutes).isEqualTo(9372L)

            // Exemplarische Prüfung einzelner Tage:
            // 01.09.2026: Büro-Tag
            val day1 = exportData.rows[0]
            assertThat(day1.date).isEqualTo(LocalDate.of(2026, 9, 1))
            assertThat(day1.dayType).isEqualTo(DayType.WORK)
            assertThat(day1.location).isEqualTo(WorkLocation.OFFICE)
            assertThat(day1.grossMinutes).isEqualTo(475L) // 7:55 h
            assertThat(day1.breakMinutes).isEqualTo(30L)  // 0:30 h
            assertThat(day1.netMinutes).isEqualTo(445L)    // 7:25 h
            assertThat(day1.targetMinutes).isEqualTo(426)  // 7:06 h

            // 05.09.2026: Samstag (Wochenende ohne Arbeitstag)
            val day5 = exportData.rows[4]
            assertThat(day5.date).isEqualTo(LocalDate.of(2026, 9, 5))
            assertThat(day5.dayType).isNull()
            assertThat(day5.location).isNull()
            assertThat(day5.netMinutes).isEqualTo(0L)
            assertThat(day5.targetMinutes).isEqualTo(0)
        }

        @Test
        @DisplayName("ExportService generiert exaktes CSV-Format inklusive Kopfzeile, Datenzeilen und Summenzeile")
        fun exportServiceProducesMatchingCsvRoundtrip() = runTest {
            // Given: ExportData aus PrepareExportDataUseCase
            val exportData = prepareExportDataUseCase(September2026ExportFixture.YEAR_MONTH)

            val outputStream = ByteArrayOutputStream()
            val contentResolver: ContentResolver = mock()
            val uri: Uri = mock()
            whenever(contentResolver.openOutputStream(uri)).thenReturn(outputStream)

            // When: ExportService.exportToCsv aufgerufen wird
            exportService.exportToCsv(exportData, uri, contentResolver)

            // Then: Das generierte CSV (ohne UTF-8 BOM) entspricht exakt dem erwarteten Format
            val generatedCsv = outputStream.toString(Charsets.UTF_8).removePrefix("\uFEFF")
            val expectedCsv = September2026ExportFixture.rawCsvContent()

            val generatedLines = generatedCsv.trim().lines()
            val expectedLines = expectedCsv.trim().lines()

            // Zeilenanzahl: 1 Kopfzeile + 29 Einzeltage + 3 Zeilen für 30.09. (2 Blöcke + Tagessumme) + 1 Monatssummenzeile = 34 Zeilen
            assertThat(generatedLines).hasSize(34)

            // Kopfzeile prüfen
            assertThat(generatedLines.first()).isEqualTo("Datum;Tag;Typ;Ort;Start;Ende;Brutto;Pause;Netto;Soll;Differenz;Notiz")

            // Einzelne Tage prüfen
            // 01.09.2026 (Büro)
            assertThat(generatedLines[1]).isEqualTo("01.09.2026;Dienstag;Arbeit;Büro;09:10;17:01;7:55;0:30;7:25;7:06;+0:19;")
            // 03.09.2026 (Home Office mit Minusstunden)
            assertThat(generatedLines[3]).isEqualTo("03.09.2026;Donnerstag;Arbeit;HO;08:51;16:38;5:40;2:10;5:40;7:06;1:26;")
            // 05.09.2026 (Wochenende)
            assertThat(generatedLines[5]).isEqualTo("05.09.2026;Samstag;-;-;-;-;-;-;-;-;-;")

            // 30.09.2026 (Misch-Tag: 2 Blöcke + Tagessumme)
            assertThat(generatedLines[30]).isEqualTo("30.09.2026;Mittwoch;Arbeit;Büro;09:02;15:00;5:58;-;5:58;-;-;")
            assertThat(generatedLines[31]).isEqualTo("30.09.2026;Mittwoch;Arbeit;HO;15:51;16:31;0:40;-;0:40;-;-;")
            assertThat(generatedLines[32]).isEqualTo("30.09.2026;Mittwoch;Gesamt;-;09:02;16:31;6:44;0:51;6:44;7:06;0:22;")

            // Summenzeile verifizieren
            val summaryLine = generatedLines.last()
            assertThat(summaryLine).isEqualTo(";;;;;;;Gesamt;;158:16;156:12;+2:04;")

            // Vollständiger CSV-Inhalt stimmt 1:1 überein
            assertThat(generatedCsv.trim()).isEqualTo(expectedCsv.trim())
        }

        @Test
        @DisplayName("ExportService erzeugt bei Split-Tagen mit unterschiedlichen Orten Blockzeilen und Tagessumme ohne dominanten Ort")
        fun exportServiceOutputsSplitDayBlocksAndSummaryRow() = runTest {
            // Given: Ein Tag mit 2 Blöcken an unterschiedlichen Orten (HO und Büro)
            val splitDay = WorkDay(
                date = LocalDate.of(2026, 9, 15),
                dayType = DayType.WORK,
                location = WorkLocation.OFFICE,
                timeBlocks = listOf(
                    TimeBlock(
                        id = 1L,
                        startTime = LocalTime.of(8, 30),
                        endTime = LocalTime.of(12, 0),
                        location = WorkLocation.HOME_OFFICE,
                        isDuration = false
                    ),
                    TimeBlock(
                        id = 2L,
                        startTime = LocalTime.of(13, 0),
                        endTime = LocalTime.of(17, 0),
                        location = WorkLocation.OFFICE,
                        isDuration = false
                    )
                )
            )

            val mockWorkDayRepo: WorkDayRepository = mock()
            whenever(mockWorkDayRepo.getWorkDaysForMonth(September2026ExportFixture.YEAR_MONTH))
                .thenReturn(flowOf(listOf(splitDay)))

            val useCase = PrepareExportDataUseCase(
                workDayRepository = mockWorkDayRepo,
                settingsRepository = settingsRepository,
                calculateDayWorkTime = calculateDayWorkTime
            )

            val exportData = useCase(September2026ExportFixture.YEAR_MONTH)
            val outputStream = ByteArrayOutputStream()
            val contentResolver: ContentResolver = mock()
            val uri: Uri = mock()
            whenever(contentResolver.openOutputStream(uri)).thenReturn(outputStream)

            // When: CSV exportiert wird
            exportService.exportToCsv(exportData, uri, contentResolver)

            val generatedCsv = outputStream.toString(Charsets.UTF_8).removePrefix("\uFEFF")
            val day15Lines = generatedCsv.trim().lines().filter { it.startsWith("15.09.2026;") }

            // Then: 2 Blockzeilen und 1 Tagessummenzeile
            assertThat(day15Lines).hasSize(3)
            assertThat(day15Lines[0]).isEqualTo("15.09.2026;Dienstag;Arbeit;HO;08:30;12:00;3:30;-;3:30;-;-;")
            assertThat(day15Lines[1]).isEqualTo("15.09.2026;Dienstag;Arbeit;Büro;13:00;17:00;4:00;-;4:00;-;-;")
            assertThat(day15Lines[2]).isEqualTo("15.09.2026;Dienstag;Gesamt;-;08:30;17:00;7:30;1:00;7:30;7:06;+0:24;")
        }
    }
}
