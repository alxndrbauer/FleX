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
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

class MixedDayExportBehaviorSpec : BehaviorSpec({

    val calculateDayWorkTime = CalculateDayWorkTimeUseCase()
    val exportService = ExportService()

    val testMonth = YearMonth.of(2026, 9)
    val defaultSettings = Settings(
        dailyWorkMinutes = 426, // 7h 06min
        monthlyWorkMinutes = 8520
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

    Given("ein Mischtag mit Blöcken an mehreren Standorten (Büro & Home-Office)") {
        val date = LocalDate.of(2026, 9, 15) // Dienstag

        // Block 1: 08:30–12:00 im Home Office (3h 30m = 210 Min)
        val blockHO = TimeBlock(
            id = 1L,
            workDayId = 1L,
            startTime = LocalTime.of(8, 30),
            endTime = LocalTime.of(12, 0),
            location = WorkLocation.HOME_OFFICE,
            isDuration = false
        )
        // Block 2: 13:00–17:00 im Büro (4h 00m = 240 Min)
        val blockOffice = TimeBlock(
            id = 2L,
            workDayId = 1L,
            startTime = LocalTime.of(13, 0),
            endTime = LocalTime.of(17, 0),
            location = WorkLocation.OFFICE,
            isDuration = false
        )

        val mixedDay = WorkDay(
            id = 1L,
            date = date,
            dayType = DayType.WORK,
            location = WorkLocation.OFFICE,
            timeBlocks = listOf(blockHO, blockOffice),
            note = "Vormittag HO, Nachmittag Office"
        )

        val useCase = createUseCase(listOf(mixedDay))

        When("die Exportdaten durch PrepareExportDataUseCase aufbereitet werden") {
            val exportData = useCase(testMonth)
            val dayRow = exportData.rows.first { it.date == date }

            Then("wird hasMultipleLocations auf true gesetzt") {
                dayRow.hasMultipleLocations.shouldBeTrue()
            }

            Then("besitzt die Tageszeile keinen dominierenden Einzelort (location ist null)") {
                dayRow.location.shouldBeNull()
            }

            Then("werden Unterzeilen (ExportBlockRow) für die einzelnen Abschnitte erzeugt") {
                dayRow.blocks shouldHaveSize 2
            }

            Then("stimmt die korrekte Zeitzuordnung und Dauer je Standort") {
                val b1 = dayRow.blocks[0]
                b1.location shouldBe WorkLocation.HOME_OFFICE
                b1.startTime shouldBe LocalTime.of(8, 30)
                b1.endTime shouldBe LocalTime.of(12, 0)
                b1.durationMinutes shouldBe 210L // 3h 30m

                val b2 = dayRow.blocks[1]
                b2.location shouldBe WorkLocation.OFFICE
                b2.startTime shouldBe LocalTime.of(13, 0)
                b2.endTime shouldBe LocalTime.of(17, 0)
                b2.durationMinutes shouldBe 240L // 4h 00m
            }
        }

        When("der Mischtag in CSV exportiert wird") {
            val exportData = useCase(testMonth)
            val lines = exportToCsvLines(exportData)
            val day15Lines = lines.filter { it.startsWith("15.09.2026;") }

            Then("werden genau 2 separate Blockzeilen und eine Tagessummenzeile erzeugt (insgesamt 3 Zeilen)") {
                day15Lines shouldHaveSize 3
            }

            Then("weist die erste Zeile den Home-Office-Block mit Ort HO und Netto 3:30 aus") {
                day15Lines[0] shouldBe "15.09.2026;Dienstag;Arbeit;HO;08:30;12:00;3:30;-;3:30;-;-;"
            }

            Then("weist die zweite Zeile den Büro-Block mit Ort Büro und Netto 4:00 aus") {
                day15Lines[1] shouldBe "15.09.2026;Dienstag;Arbeit;Büro;13:00;17:00;4:00;-;4:00;-;-;"
            }

            Then("fasst die dritte Zeile als 'Gesamt' ohne dominanten Ort die Tagessumme zusammen") {
                // Brutto: 08:30 bis 17:00 = 8h 30m (510 Min)
                // Pause: 12:00 bis 13:00 = 1h 00m (60 Min)
                // Netto: 210 + 240 = 450 Min (7h 30m)
                // Soll: 426 Min (7h 06m) -> Differenz: +0:24
                day15Lines[2] shouldBe "15.09.2026;Dienstag;Gesamt;-;08:30;17:00;7:30;1:00;7:30;7:06;+0:24;Vormittag HO, Nachmittag Office"
            }
        }
    }

    Given("ein Mischtag mit drei Abschnitten über wechselnde Standorte (HO -> Büro -> HO)") {
        val date = LocalDate.of(2026, 9, 21)

        val block1 = TimeBlock(id = 1, workDayId = 2, startTime = LocalTime.of(8, 0), endTime = LocalTime.of(10, 0), location = WorkLocation.HOME_OFFICE)
        val block2 = TimeBlock(id = 2, workDayId = 2, startTime = LocalTime.of(11, 0), endTime = LocalTime.of(14, 0), location = WorkLocation.OFFICE)
        val block3 = TimeBlock(id = 3, workDayId = 2, startTime = LocalTime.of(15, 0), endTime = LocalTime.of(17, 0), location = WorkLocation.HOME_OFFICE)

        val threeBlockDay = WorkDay(
            id = 2L,
            date = date,
            dayType = DayType.WORK,
            location = WorkLocation.HOME_OFFICE,
            timeBlocks = listOf(block1, block2, block3)
        )

        val useCase = createUseCase(listOf(threeBlockDay))

        When("der Mischtag exportiert wird") {
            val exportData = useCase(testMonth)
            val lines = exportToCsvLines(exportData)
            val day21Lines = lines.filter { it.startsWith("21.09.2026;") }

            Then("werden 3 Blockzeilen plus 1 Tagessummenzeile generiert") {
                day21Lines shouldHaveSize 4

                // Block 1 (HO: 2h)
                day21Lines[0] shouldBe "21.09.2026;Montag;Arbeit;HO;08:00;10:00;2:00;-;2:00;-;-;"
                // Block 2 (Büro: 3h)
                day21Lines[1] shouldBe "21.09.2026;Montag;Arbeit;Büro;11:00;14:00;3:00;-;3:00;-;-;"
                // Block 3 (HO: 2h)
                day21Lines[2] shouldBe "21.09.2026;Montag;Arbeit;HO;15:00;17:00;2:00;-;2:00;-;-;"
                // Tagessumme (Gesamt: 7h Netto, 2h Pause, Diff 0:06)
                day21Lines[3] shouldBe "21.09.2026;Montag;Gesamt;-;08:00;17:00;7:00;2:00;7:00;7:06;0:06;"
            }
        }
    }

    Given("ein geteilter Arbeitstag mit mehreren Blöcken am GLEICHEN Standort (kein Mischtag)") {
        val date = LocalDate.of(2026, 9, 22)

        val block1 = TimeBlock(id = 1, workDayId = 3, startTime = LocalTime.of(8, 0), endTime = LocalTime.of(12, 0), location = WorkLocation.HOME_OFFICE)
        val block2 = TimeBlock(id = 2, workDayId = 3, startTime = LocalTime.of(13, 0), endTime = LocalTime.of(17, 0), location = WorkLocation.HOME_OFFICE)

        val sameLocDay = WorkDay(
            id = 3L,
            date = date,
            dayType = DayType.WORK,
            location = WorkLocation.HOME_OFFICE,
            timeBlocks = listOf(block1, block2)
        )

        val useCase = createUseCase(listOf(sameLocDay))

        When("die Exportdaten aufbereitet werden") {
            val exportData = useCase(testMonth)
            val dayRow = exportData.rows.first { it.date == date }

            Then("ist hasMultipleLocations false und blocks ist leer") {
                dayRow.hasMultipleLocations.shouldBeFalse()
                dayRow.location shouldBe WorkLocation.HOME_OFFICE
                dayRow.blocks.shouldBeEmpty()
            }

            Then("erzeugt der CSV-Export genau eine einzige Zeile mit Standort HO") {
                val lines = exportToCsvLines(exportData)
                val day22Lines = lines.filter { it.startsWith("22.09.2026;") }
                day22Lines shouldHaveSize 1
                day22Lines[0] shouldBe "22.09.2026;Dienstag;Arbeit;HO;08:00;17:00;8:00;1:00;8:00;7:06;+0:54;"
            }
        }
    }
})
