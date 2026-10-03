package com.flex.specification

import android.content.ContentResolver
import android.net.Uri
import com.google.common.truth.Truth.assertThat
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
import java.time.YearMonth

/**
 * BDD-Spezifikation für das Export-Verhalten in FleX (CSV- und Datenaufbereitung).
 *
 * Spezifiziert:
 * - Detaillierte Zeilenausgabe bei Misch-Tagen mit unterschiedlichen Orten (HO & Büro)
 * - Beibehaltung der Einzelzeile bei Tagen mit nur einem Ort (auch bei mehreren Blöcken)
 * - Wegfall dominanter Orte in der Tagessummenzeile
 */
@DisplayName("Spezifikation: Report- & Export-Verhalten (BDD Verhaltensprüfung)")
class ExportBehaviorSpecificationTest {

    private val calculateDayWorkTime = CalculateDayWorkTimeUseCase()
    private val exportService = ExportService()
    private val defaultSettings = September2026ExportFixture.createDefaultSettings()
    private val testMonth = YearMonth.of(2026, 9)

    private lateinit var settingsRepository: SettingsRepository

    @BeforeEach
    fun setUp() {
        settingsRepository = mock()
        whenever(settingsRepository.getSettings()).thenReturn(flowOf(defaultSettings))
        whenever(settingsRepository.getWorkTimeRules()).thenReturn(flowOf(emptyList()))
        whenever(settingsRepository.getWorkTimeRuleForDate(any(), any())).thenReturn(null)
    }

    private fun createUseCase(workDays: List<WorkDay>): PrepareExportDataUseCase {
        val mockRepo: WorkDayRepository = mock()
        whenever(mockRepo.getWorkDaysForMonth(testMonth)).thenReturn(flowOf(workDays))
        return PrepareExportDataUseCase(
            workDayRepository = mockRepo,
            settingsRepository = settingsRepository,
            calculateDayWorkTime = calculateDayWorkTime
        )
    }

    private fun exportCsv(useCase: PrepareExportDataUseCase): List<String> {
        val exportData = kotlinx.coroutines.runBlocking { useCase(testMonth) }
        val outputStream = ByteArrayOutputStream()
        val contentResolver: ContentResolver = mock()
        val uri: Uri = mock()
        whenever(contentResolver.openOutputStream(uri)).thenReturn(outputStream)

        exportService.exportToCsv(exportData, uri, contentResolver)
        val csv = outputStream.toString(Charsets.UTF_8).removePrefix("\uFEFF")
        return csv.trim().lines()
    }

    // =========================================================================
    // Szenario 1: Misch-Tag mit mehreren Blöcken an unterschiedlichen Orten
    // =========================================================================

    @Nested
    @DisplayName("Szenario 1: Misch-Tag mit unterschiedlichen Orten (z. B. Vormittag HO, Nachmittag Büro)")
    inner class SplitLocationDayScenario {

        @Test
        @DisplayName("Gibt separate Blockzeilen mit jeweiligem Ort und eine neutrale Tagessummenzeile 'Gesamt' ohne dominanten Ort aus")
        fun mixedDayOutputsBlockRowsAndNeutralSummaryRow() = runTest {
            // Given: Ein Arbeitstag (15.09.2026) mit 2 Blöcken an unterschiedlichen Orten:
            // Block 1: 08:30–12:00 im Home Office (3:30h)
            // Block 2: 13:00–17:00 im Büro (4:00h)
            val mixedDay = WorkDay(
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
                ),
                note = "Split-Tag mit Kundenmeeting"
            )

            val useCase = createUseCase(listOf(mixedDay))

            // When: ExportData vorbereitet und CSV exportiert wird
            val exportData = useCase(testMonth)
            val day15Row = exportData.rows.first { it.date == LocalDate.of(2026, 9, 15) }

            // Then (Domain-Ebene):
            // 1. hasMultipleLocations ist true
            assertThat(day15Row.hasMultipleLocations).isTrue()
            // 2. Kein dominanter Ort auf Tagesebene (location == null)
            assertThat(day15Row.location).isNull()
            // 3. Beide Blöcke sind mit korrekter Dauer hinterlegt
            assertThat(day15Row.blocks).hasSize(2)
            assertThat(day15Row.blocks[0].location).isEqualTo(WorkLocation.HOME_OFFICE)
            assertThat(day15Row.blocks[0].durationMinutes).isEqualTo(210L) // 3:30
            assertThat(day15Row.blocks[1].location).isEqualTo(WorkLocation.OFFICE)
            assertThat(day15Row.blocks[1].durationMinutes).isEqualTo(240L) // 4:00

            // When: CSV Zeilen analysiert werden
            val lines = exportCsv(useCase)
            val day15Lines = lines.filter { it.startsWith("15.09.2026;") }

            // Then (CSV-Ebene):
            // Genau 2 Blockzeilen + 1 Tagessummenzeile = 3 Zeilen für diesen Tag
            assertThat(day15Lines).hasSize(3)

            // Block 1: HO, Dauer 3:30
            assertThat(day15Lines[0]).isEqualTo("15.09.2026;Dienstag;Arbeit;HO;08:30;12:00;3:30;-;3:30;-;-;")

            // Block 2: Büro, Dauer 4:00
            assertThat(day15Lines[1]).isEqualTo("15.09.2026;Dienstag;Arbeit;Büro;13:00;17:00;4:00;-;4:00;-;-;")

            // Tagessumme: Typ "Gesamt", Ort "-", Start 08:30, Ende 17:00, Brutto 7:30, Pause 1:00, Netto 7:30, Soll 7:06, Diff +0:24, Notiz
            assertThat(day15Lines[2]).isEqualTo("15.09.2026;Dienstag;Gesamt;-;08:30;17:00;7:30;1:00;7:30;7:06;+0:24;Split-Tag mit Kundenmeeting")
        }

        @Test
        @DisplayName("Unterstützt auch 3 Blöcke mit mehreren Orten (HO -> Büro -> HO)")
        fun threeBlocksMixedDayOutputsAllBlocks() = runTest {
            // Given: 3 Blöcke am selben Tag mit wechselndem Ort
            val mixedDay = WorkDay(
                date = LocalDate.of(2026, 9, 20),
                dayType = DayType.WORK,
                location = WorkLocation.HOME_OFFICE,
                timeBlocks = listOf(
                    TimeBlock(id = 1L, startTime = LocalTime.of(8, 0), endTime = LocalTime.of(10, 0), location = WorkLocation.HOME_OFFICE, isDuration = false),
                    TimeBlock(id = 2L, startTime = LocalTime.of(11, 0), endTime = LocalTime.of(14, 0), location = WorkLocation.OFFICE, isDuration = false),
                    TimeBlock(id = 3L, startTime = LocalTime.of(15, 0), endTime = LocalTime.of(17, 0), location = WorkLocation.HOME_OFFICE, isDuration = false)
                )
            )

            val useCase = createUseCase(listOf(mixedDay))
            val lines = exportCsv(useCase)
            val day20Lines = lines.filter { it.startsWith("20.09.2026;") }

            // Then: 3 Blockzeilen + 1 Tagessummenzeile = 4 Zeilen
            assertThat(day20Lines).hasSize(4)
            assertThat(day20Lines[0]).contains(";Arbeit;HO;08:00;10:00;2:00;-;2:00;-;-;")
            assertThat(day20Lines[1]).contains(";Arbeit;Büro;11:00;14:00;3:00;-;3:00;-;-;")
            assertThat(day20Lines[2]).contains(";Arbeit;HO;15:00;17:00;2:00;-;2:00;-;-;")
            assertThat(day20Lines[3]).contains(";Gesamt;-;08:00;17:00;7:00;2:00;7:00;")
        }
    }

    // =========================================================================
    // Szenario 2: Geteilter Tag am GLEICHEN Ort (keine Mischung)
    // =========================================================================

    @Nested
    @DisplayName("Szenario 2: Geteilter Tag am selben Ort (z. B. geteilte Schicht im HO)")
    inner class SplitShiftSameLocationScenario {

        @Test
        @DisplayName("Erzeugt wie bisher exakt eine Zeile mit dem Ort HO und ohne Blockaufteilung")
        fun splitShiftSameLocationProducesSingleRow() = runTest {
            // Given: 2 Blöcke am 03.09.2026, beide im Home Office mit Pause dazwischen (Realer September-Fall)
            val splitDaySameLoc = WorkDay(
                date = LocalDate.of(2026, 9, 3),
                dayType = DayType.WORK,
                location = WorkLocation.HOME_OFFICE,
                timeBlocks = listOf(
                    TimeBlock(id = 1L, startTime = LocalTime.of(8, 51), endTime = LocalTime.of(12, 0), location = WorkLocation.HOME_OFFICE, isDuration = false),
                    TimeBlock(id = 2L, startTime = LocalTime.of(14, 10), endTime = LocalTime.of(16, 38), location = WorkLocation.HOME_OFFICE, isDuration = false)
                )
            )

            val useCase = createUseCase(listOf(splitDaySameLoc))
            val exportData = useCase(testMonth)
            val day3Row = exportData.rows.first { it.date == LocalDate.of(2026, 9, 3) }

            // Then (Domain-Ebene):
            assertThat(day3Row.hasMultipleLocations).isFalse()
            assertThat(day3Row.location).isEqualTo(WorkLocation.HOME_OFFICE)
            assertThat(day3Row.blocks).isEmpty()

            // When: CSV exportiert wird
            val lines = exportCsv(useCase)
            val day3Lines = lines.filter { it.startsWith("03.09.2026;") }

            // Then: Genau 1 Zeile mit Typ "Arbeit" und Ort "HO"
            assertThat(day3Lines).hasSize(1)
            assertThat(day3Lines.first()).isEqualTo("03.09.2026;Donnerstag;Arbeit;HO;08:51;16:38;5:40;2:10;5:40;7:06;1:26;")
        }
    }

    // =========================================================================
    // Szenario 3: Regulärer Einzeltag
    // =========================================================================

    @Nested
    @DisplayName("Szenario 3: Regulärer Einzeltag mit einem Block")
    inner class SingleBlockDayScenario {

        @Test
        @DisplayName("Erzeugt genau 1 Zeile mit dem konkreten Ort")
        fun singleBlockProducesSingleRow() = runTest {
            // Given: Ein Tag mit 1 Block im Büro (01.09.2026)
            val singleDay = WorkDay(
                date = LocalDate.of(2026, 9, 1),
                dayType = DayType.WORK,
                location = WorkLocation.OFFICE,
                timeBlocks = listOf(
                    TimeBlock(id = 1L, startTime = LocalTime.of(9, 10), endTime = LocalTime.of(17, 1), location = WorkLocation.OFFICE, isDuration = false)
                )
            )

            val useCase = createUseCase(listOf(singleDay))
            val lines = exportCsv(useCase)
            val day1Lines = lines.filter { it.startsWith("01.09.2026;") }

            // Then: Genau 1 Zeile mit Ort "Büro"
            assertThat(day1Lines).hasSize(1)
            assertThat(day1Lines.first()).isEqualTo("01.09.2026;Dienstag;Arbeit;Büro;09:10;17:01;7:55;0:30;7:25;7:06;+0:19;")
        }
    }
}
