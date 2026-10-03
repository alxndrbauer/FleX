package com.flex.bdd.feature.export

import android.content.ContentResolver
import android.net.Uri
import com.flex.data.export.ExportService
import com.flex.domain.model.DayType
import com.flex.domain.model.Settings
import com.flex.domain.model.TimeBlock
import com.flex.domain.model.WorkDay
import com.flex.domain.model.WorkLocation
import com.flex.domain.repository.SettingsRepository
import com.flex.domain.repository.WorkDayRepository
import com.flex.domain.usecase.CalculateDayWorkTimeUseCase
import com.flex.domain.usecase.PrepareExportDataUseCase
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

class CsvExportBehaviorSpec : BehaviorSpec({

    val calculateDayWorkTime = CalculateDayWorkTimeUseCase()
    val exportService = ExportService()

    val testMonth = YearMonth.of(2026, 9) // September 2026: 30 Tage
    val defaultSettings = Settings(
        dailyWorkMinutes = 426, // 7h 06min
        monthlyWorkMinutes = 8520,
        officeQuotaPercent = 40,
        officeQuotaMinDays = 8
    )

    fun createUseCase(workDays: List<WorkDay>): PrepareExportDataUseCase {
        val workDayRepo = mockk<WorkDayRepository>()
        val settingsRepo = mockk<SettingsRepository>()

        every { workDayRepo.getWorkDaysForMonth(testMonth) } returns flowOf(workDays)
        every { settingsRepo.getSettings() } returns flowOf(defaultSettings)
        every { settingsRepo.getWorkTimeRules() } returns flowOf(emptyList())
        every { settingsRepo.getWorkTimeRuleForDate(any(), any()) } returns null

        return PrepareExportDataUseCase(
            workDayRepository = workDayRepo,
            settingsRepository = settingsRepo,
            calculateDayWorkTime = calculateDayWorkTime
        )
    }

    fun exportToCsvLines(exportData: com.flex.domain.model.ExportData): List<String> {
        val outputStream = ByteArrayOutputStream()
        val contentResolver = mockk<ContentResolver>()
        val uri = mockk<Uri>()

        every { contentResolver.openOutputStream(uri) } returns outputStream

        exportService.exportToCsv(exportData, uri, contentResolver)
        val csv = outputStream.toString(Charsets.UTF_8).removePrefix("\uFEFF")
        return csv.trim().lines()
    }

    Given("ein Monat mit erfassten Arbeitstagen und Vorbereitung der Exportdaten") {
        // Tag 1: Montag, 14.09.2026 - Arbeit im Büro von 08:00 bis 16:30 (8,5h brutto = 510 Min, 30 Min Pause, 480 Min netto)
        val workDay1 = WorkDay(
            id = 1L,
            date = LocalDate.of(2026, 9, 14),
            dayType = DayType.WORK,
            location = WorkLocation.OFFICE,
            timeBlocks = listOf(
                TimeBlock(
                    id = 1L,
                    workDayId = 1L,
                    startTime = LocalTime.of(8, 0),
                    endTime = LocalTime.of(16, 30),
                    location = WorkLocation.OFFICE
                )
            ),
            note = "Projektmeeting"
        )

        // Tag 2: Dienstag, 15.09.2026 - Arbeit im Home-Office von 09:00 bis 17:00 (8h brutto = 480 Min, 30 Min Pause, 450 Min netto)
        val workDay2 = WorkDay(
            id = 2L,
            date = LocalDate.of(2026, 9, 15),
            dayType = DayType.WORK,
            location = WorkLocation.HOME_OFFICE,
            timeBlocks = listOf(
                TimeBlock(
                    id = 2L,
                    workDayId = 2L,
                    startTime = LocalTime.of(9, 0),
                    endTime = LocalTime.of(17, 0),
                    location = WorkLocation.HOME_OFFICE
                )
            ),
            note = "Entwicklung"
        )

        val useCase = createUseCase(listOf(workDay1, workDay2))

        When("PrepareExportDataUseCase für den Monat aufgerufen wird") {
            val exportData = useCase(testMonth)

            Then("erzeugt der UseCase ExportData mit exakt einer ExportDayRow je Kalendertag des Monats (30 Zeilen)") {
                exportData.rows shouldHaveSize 30
                exportData.rows.map { it.date } shouldBe (1..30).map { LocalDate.of(2026, 9, it) }
            }

            Then("enthält die ExportDayRow für den Bürotag alle Pflichtfelder wie Datum, DayType, Location, Brutto, Pause, Netto, Soll und Differenz") {
                val row14 = exportData.rows.first { it.date == LocalDate.of(2026, 9, 14) }
                row14.date shouldBe LocalDate.of(2026, 9, 14)
                row14.dayType shouldBe DayType.WORK
                row14.location shouldBe WorkLocation.OFFICE
                row14.startTime shouldBe LocalTime.of(8, 0)
                row14.endTime shouldBe LocalTime.of(16, 30)
                row14.grossMinutes shouldBe 510L // 8h 30m
                row14.breakMinutes shouldBe 30L  // gesetzl. Mindestpause
                row14.netMinutes shouldBe 480L    // 8h 00m
                row14.targetMinutes shouldBe 426 // 7h 06m
                (row14.netMinutes - row14.targetMinutes) shouldBe 54L // +54 min
                row14.note shouldBe "Projektmeeting"
            }

            Then("enthält die ExportDayRow für den Home-Office-Tag korrekte Berechnungen") {
                val row15 = exportData.rows.first { it.date == LocalDate.of(2026, 9, 15) }
                row15.date shouldBe LocalDate.of(2026, 9, 15)
                row15.dayType shouldBe DayType.WORK
                row15.location shouldBe WorkLocation.HOME_OFFICE
                row15.grossMinutes shouldBe 480L
                row15.breakMinutes shouldBe 30L
                row15.netMinutes shouldBe 450L
                row15.targetMinutes shouldBe 426
                (row15.netMinutes - row15.targetMinutes) shouldBe 24L // +24 min
                row15.note shouldBe "Entwicklung"
            }

            Then("erhalten Tage ohne Erfassung den Soll-Wert der Werktage und 0 Minuten Arbeitszeit") {
                // 01.09.2026 ist ein Dienstag (Werktag)
                val row1 = exportData.rows.first { it.date == LocalDate.of(2026, 9, 1) }
                row1.dayType shouldBe null
                row1.location shouldBe null
                row1.netMinutes shouldBe 0L
                row1.targetMinutes shouldBe 426

                // 06.09.2026 ist ein Sonntag (Wochenende)
                val row6 = exportData.rows.first { it.date == LocalDate.of(2026, 9, 6) }
                row6.dayType shouldBe null
                row6.targetMinutes shouldBe 0
            }
        }

        When("die Exportdaten als CSV exportiert werden (ExportService)") {
            val exportData = useCase(testMonth)
            val lines = exportToCsvLines(exportData)

            Then("enthält die Kopfzeile alle geforderten Spaltenbeschriftungen") {
                lines[0] shouldBe "Datum;Tag;Typ;Ort;Start;Ende;Brutto;Pause;Netto;Soll;Differenz;Notiz"
            }

            Then("enthält die CSV-Zeile für den 14.09. Datum, Wochentag, Typ, Ort, formatierte Zeiten und Notiz") {
                val row14Csv = lines.first { it.startsWith("14.09.2026;") }
                row14Csv shouldBe "14.09.2026;Montag;Arbeit;Büro;08:00;16:30;8:30;0:30;8:00;7:06;+0:54;Projektmeeting"
            }

            Then("enthält die CSV-Zeile für den 15.09. den Home-Office-Eintrag im Format HO") {
                val row15Csv = lines.first { it.startsWith("15.09.2026;") }
                row15Csv shouldBe "15.09.2026;Dienstag;Arbeit;HO;09:00;17:00;8:00;0:30;7:30;7:06;+0:24;Entwicklung"
            }

            Then("bildet die letzte Zeile eine standardisierte Summenzeile / Monatszusammenfassung") {
                val summaryLine = lines.last()
                summaryLine.shouldStartWith(";;;;;;;Gesamt;;")

                // Netto insgesamt: 480 (Tag 14) + 450 (Tag 15) = 930 Min = 15h 30m
                // Soll September 2026 (22 Werktage à 426 Min = 9372 Min = 156h 12m)
                // Differenz: 930 - 9372 = -8442 Min -> Absolut 140h 42m
                // Format nach ExportService: ;;;;;;;Gesamt;;15:30;156:12;140:42;
                summaryLine shouldBe ";;;;;;;Gesamt;;15:30;156:12;140:42;"
            }
        }
    }
})
