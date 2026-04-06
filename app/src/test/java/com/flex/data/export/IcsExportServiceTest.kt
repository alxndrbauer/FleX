package com.flex.data.export

import com.google.common.truth.Truth.assertThat
import com.flex.calendar.CalendarEventMapper
import com.flex.domain.model.DayType
import com.flex.domain.model.Settings
import com.flex.domain.model.WorkDay
import com.flex.domain.model.WorkLocation
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * Unit tests for IcsExportService.
 * Verifies that consecutive vacation/special-vacation days are merged into a single
 * VEVENT, while non-vacation days and distinct vacation periods remain separate.
 */
class IcsExportServiceTest {

    private lateinit var service: IcsExportService

    @BeforeEach
    fun setUp() {
        service = IcsExportService(CalendarEventMapper())
    }

    private val syncAllSettings = Settings(
        calendarSyncEnabled = true,
        calendarSyncTypes = "VACATION,SPECIAL_VACATION,WORK,SICK_DAY,FLEX_DAY",
        calendarEventPrefix = "FleX"
    )

    private fun vacation(id: Long, date: LocalDate, type: DayType = DayType.VACATION) =
        WorkDay(id = id, date = date, location = WorkLocation.OFFICE, dayType = type)

    private fun workDay(id: Long, date: LocalDate) =
        WorkDay(id = id, date = date, location = WorkLocation.OFFICE, dayType = DayType.WORK)

    // ========== Vacation grouping ==========

    @Test
    fun `single vacation day produces one VEVENT with correct date range`() {
        val days = listOf(vacation(1, LocalDate.of(2026, 4, 7)))
        val (content, count) = service.exportToIcs(days, syncAllSettings)

        assertThat(count).isEqualTo(1)
        assertThat(content.countVevents()).isEqualTo(1)
        assertThat(content).contains("DTSTART;VALUE=DATE:20260407")
        assertThat(content).contains("DTEND;VALUE=DATE:20260408")
    }

    @Test
    fun `Mon-Fri vacation week produces single VEVENT spanning full range`() {
        val days = (7..11).map { vacation(it.toLong(), LocalDate.of(2026, 4, it)) }
        val (content, count) = service.exportToIcs(days, syncAllSettings)

        assertThat(count).isEqualTo(5)
        assertThat(content.countVevents()).isEqualTo(1)
        assertThat(content).contains("DTSTART;VALUE=DATE:20260407")
        assertThat(content).contains("DTEND;VALUE=DATE:20260412") // Apr 11 + 1
    }

    @Test
    fun `Fri and Mon vacation are bridged into single VEVENT`() {
        val days = listOf(
            vacation(1, LocalDate.of(2026, 4, 10)), // Fri
            vacation(2, LocalDate.of(2026, 4, 13)), // Mon
        )
        val (content, _) = service.exportToIcs(days, syncAllSettings)

        assertThat(content.countVevents()).isEqualTo(1)
        assertThat(content).contains("DTSTART;VALUE=DATE:20260410")
        assertThat(content).contains("DTEND;VALUE=DATE:20260414") // Mon Apr 13 + 1
    }

    @Test
    fun `two separate vacation periods produce two VEVENTs`() {
        val period1 = (7..11).map { vacation(it.toLong(), LocalDate.of(2026, 4, it)) }
        val period2 = (22..25).map { vacation((it + 100).toLong(), LocalDate.of(2026, 4, it)) }
        val (content, count) = service.exportToIcs(period1 + period2, syncAllSettings)

        assertThat(count).isEqualTo(9)
        assertThat(content.countVevents()).isEqualTo(2)
    }

    // ========== Type separation ==========

    @Test
    fun `VACATION and SPECIAL_VACATION on adjacent days produce two separate VEVENTs`() {
        val days = listOf(
            vacation(1, LocalDate.of(2026, 4, 7), DayType.VACATION),
            vacation(2, LocalDate.of(2026, 4, 8), DayType.SPECIAL_VACATION),
        )
        val (content, count) = service.exportToIcs(days, syncAllSettings)

        assertThat(count).isEqualTo(2)
        assertThat(content.countVevents()).isEqualTo(2)
    }

    @Test
    fun `SPECIAL_VACATION consecutive days produce single VEVENT`() {
        val days = (7..9).map { vacation(it.toLong(), LocalDate.of(2026, 4, it), DayType.SPECIAL_VACATION) }
        val (content, count) = service.exportToIcs(days, syncAllSettings)

        assertThat(count).isEqualTo(3)
        assertThat(content.countVevents()).isEqualTo(1)
        assertThat(content).contains("DTSTART;VALUE=DATE:20260407")
        assertThat(content).contains("DTEND;VALUE=DATE:20260410")
    }

    // ========== Non-vacation days are not grouped ==========

    @Test
    fun `adjacent WORK days each produce individual VEVENTs`() {
        val days = listOf(
            workDay(1, LocalDate.of(2026, 4, 7)),
            workDay(2, LocalDate.of(2026, 4, 8)),
        )
        val (content, count) = service.exportToIcs(days, syncAllSettings)

        assertThat(count).isEqualTo(2)
        assertThat(content.countVevents()).isEqualTo(2)
    }

    // ========== Mixed days ==========

    @Test
    fun `vacation group and work day produce correct VEVENT count`() {
        val days = listOf(
            vacation(1, LocalDate.of(2026, 4, 7)),
            vacation(2, LocalDate.of(2026, 4, 8)),
            workDay(3, LocalDate.of(2026, 4, 9)),
        )
        val (content, count) = service.exportToIcs(days, syncAllSettings)

        assertThat(count).isEqualTo(3)
        // vacation group → 1 VEVENT, work day → 1 VEVENT
        assertThat(content.countVevents()).isEqualTo(2)
    }

    // ========== Filter ==========

    @Test
    fun `vacation days not in calendarSyncTypes are excluded`() {
        val settings = syncAllSettings.copy(calendarSyncTypes = "WORK")
        val days = listOf(
            vacation(1, LocalDate.of(2026, 4, 7)),
            workDay(2, LocalDate.of(2026, 4, 8)),
        )
        val (content, count) = service.exportToIcs(days, settings)

        assertThat(count).isEqualTo(1)
        assertThat(content.countVevents()).isEqualTo(1)
    }

    // ========== Helper ==========

    private fun String.countVevents(): Int {
        var count = 0
        var index = 0
        while (true) {
            index = indexOf("BEGIN:VEVENT", index)
            if (index == -1) break
            count++
            index += "BEGIN:VEVENT".length
        }
        return count
    }
}
