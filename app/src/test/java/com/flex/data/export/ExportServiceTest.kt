package com.flex.data.export

import android.content.ContentResolver
import android.net.Uri
import com.google.common.truth.Truth.assertThat
import com.flex.domain.model.DayType
import com.flex.domain.model.ExportBlockRow
import com.flex.domain.model.ExportData
import com.flex.domain.model.ExportDayRow
import com.flex.domain.model.Settings
import com.flex.domain.model.WorkLocation
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

class ExportServiceTest {

    private lateinit var exportService: ExportService
    private val contentResolver: ContentResolver = mock()
    private val uri: Uri = mock()

    @BeforeEach
    fun setUp() {
        exportService = ExportService()
    }

    @Test
    fun `exportToCsv outputs Dienstgang oder Dienstreise as label for BUSINESS_TRIP day`() {
        val row = ExportDayRow(
            date = LocalDate.of(2026, 4, 1),
            dayType = DayType.BUSINESS_TRIP,
            location = WorkLocation.OFFICE,
            startTime = LocalTime.of(7, 0),
            endTime = LocalTime.of(19, 0),
            grossMinutes = 720,
            breakMinutes = 0,
            netMinutes = 720,
            targetMinutes = 426,
            note = "Dienstreise München"
        )
        val data = ExportData(
            yearMonth = YearMonth.of(2026, 4),
            rows = listOf(row),
            totalNetMinutes = 720,
            totalTargetMinutes = 426,
            settings = Settings()
        )

        val outputStream = ByteArrayOutputStream()
        whenever(contentResolver.openOutputStream(uri)).thenReturn(outputStream)

        exportService.exportToCsv(data, uri, contentResolver)

        val csv = outputStream.toString(Charsets.UTF_8.name())
        assertThat(csv).contains("01.04.2026;Mittwoch;Dienstgang / Dienstreise;Büro;07:00;19:00;12:00;0:00;12:00;7:06;+4:54;Dienstreise München")
    }

    @Test
    fun `exportToCsv correctly formats business trip with manual pause`() {
        val row = ExportDayRow(
            date = LocalDate.of(2026, 4, 2),
            dayType = DayType.BUSINESS_TRIP,
            location = WorkLocation.OFFICE,
            startTime = LocalTime.of(8, 0),
            endTime = LocalTime.of(18, 0),
            grossMinutes = 600,
            breakMinutes = 60,
            netMinutes = 540,
            targetMinutes = 426,
            note = null
        )
        val data = ExportData(
            yearMonth = YearMonth.of(2026, 4),
            rows = listOf(row),
            totalNetMinutes = 540,
            totalTargetMinutes = 426,
            settings = Settings()
        )

        val outputStream = ByteArrayOutputStream()
        whenever(contentResolver.openOutputStream(uri)).thenReturn(outputStream)

        exportService.exportToCsv(data, uri, contentResolver)

        val csv = outputStream.toString(Charsets.UTF_8.name())
        assertThat(csv).contains("02.04.2026;Donnerstag;Dienstgang / Dienstreise;Büro;08:00;18:00;10:00;1:00;9:00;7:06;+1:54;")
    }

    @Test
    fun `exportToCsv summary row calculates diff correctly including business trip`() {
        val tripRow = ExportDayRow(
            date = LocalDate.of(2026, 4, 1),
            dayType = DayType.BUSINESS_TRIP,
            location = WorkLocation.OFFICE,
            startTime = LocalTime.of(7, 0),
            endTime = LocalTime.of(19, 0),
            grossMinutes = 720,
            breakMinutes = 0,
            netMinutes = 720,
            targetMinutes = 426,
            note = null
        )
        val workRow = ExportDayRow(
            date = LocalDate.of(2026, 4, 2),
            dayType = DayType.WORK,
            location = WorkLocation.HOME_OFFICE,
            startTime = LocalTime.of(8, 0),
            endTime = LocalTime.of(16, 30),
            grossMinutes = 510,
            breakMinutes = 30,
            netMinutes = 480,
            targetMinutes = 426,
            note = null
        )
        val totalNet = 720L + 480L // 1200
        val totalTarget = 426L + 426L // 852
        val data = ExportData(
            yearMonth = YearMonth.of(2026, 4),
            rows = listOf(tripRow, workRow),
            totalNetMinutes = totalNet,
            totalTargetMinutes = totalTarget,
            settings = Settings()
        )

        val outputStream = ByteArrayOutputStream()
        whenever(contentResolver.openOutputStream(uri)).thenReturn(outputStream)

        exportService.exportToCsv(data, uri, contentResolver)

        val csv = outputStream.toString(Charsets.UTF_8.name())
        // Total net: 1200 min = 20:00, target: 852 min = 14:12, diff: +348 min = +5:48
        assertThat(csv).contains(";;;;;;;Gesamt;;20:00;14:12;+5:48;")
    }

    @Test
    fun `exportToCsv correctly prints block rows and day summary row when hasMultipleLocations is true`() {
        val blocks = listOf(
            ExportBlockRow(
                startTime = LocalTime.of(8, 30),
                endTime = LocalTime.of(12, 0),
                location = WorkLocation.HOME_OFFICE,
                durationMinutes = 210
            ),
            ExportBlockRow(
                startTime = LocalTime.of(13, 0),
                endTime = LocalTime.of(17, 0),
                location = WorkLocation.OFFICE,
                durationMinutes = 240
            )
        )
        val row = ExportDayRow(
            date = LocalDate.of(2026, 7, 15),
            dayType = DayType.WORK,
            location = null,
            startTime = LocalTime.of(8, 30),
            endTime = LocalTime.of(17, 0),
            grossMinutes = 450,
            breakMinutes = 60,
            netMinutes = 390,
            targetMinutes = 426,
            note = "Split tag",
            hasMultipleLocations = true,
            blocks = blocks
        )
        val data = ExportData(
            yearMonth = YearMonth.of(2026, 7),
            rows = listOf(row),
            totalNetMinutes = 390,
            totalTargetMinutes = 426,
            settings = Settings()
        )

        val outputStream = ByteArrayOutputStream()
        whenever(contentResolver.openOutputStream(uri)).thenReturn(outputStream)

        exportService.exportToCsv(data, uri, contentResolver)

        val csv = outputStream.toString(Charsets.UTF_8.name())
        assertThat(csv).contains("15.07.2026;Mittwoch;Arbeit;HO;08:30;12:00;3:30;-;3:30;-;-;\n")
        assertThat(csv).contains("15.07.2026;Mittwoch;Arbeit;Büro;13:00;17:00;4:00;-;4:00;-;-;\n")
        assertThat(csv).contains("15.07.2026;Mittwoch;Gesamt;-;08:30;17:00;7:30;1:00;6:30;7:06;0:36;Split tag\n")
    }

    @Test
    fun `exportToCsv prints single row when hasMultipleLocations is false even with multiple blocks at same location`() {
        val row = ExportDayRow(
            date = LocalDate.of(2026, 9, 3),
            dayType = DayType.WORK,
            location = WorkLocation.HOME_OFFICE,
            startTime = LocalTime.of(8, 51),
            endTime = LocalTime.of(16, 38),
            grossMinutes = 340,
            breakMinutes = 130,
            netMinutes = 340,
            targetMinutes = 426,
            note = null,
            hasMultipleLocations = false,
            blocks = emptyList()
        )
        val data = ExportData(
            yearMonth = YearMonth.of(2026, 9),
            rows = listOf(row),
            totalNetMinutes = 340,
            totalTargetMinutes = 426,
            settings = Settings()
        )

        val outputStream = ByteArrayOutputStream()
        whenever(contentResolver.openOutputStream(uri)).thenReturn(outputStream)

        exportService.exportToCsv(data, uri, contentResolver)

        val csv = outputStream.toString(Charsets.UTF_8.name())
        val linesForDate = csv.lines().filter { it.startsWith("03.09.2026;") }
        assertThat(linesForDate).hasSize(1)
        assertThat(linesForDate.first()).isEqualTo("03.09.2026;Donnerstag;Arbeit;HO;08:51;16:38;5:40;2:10;5:40;7:06;1:26;")
    }
}
