package com.flex.calendar

import com.google.common.truth.Truth.assertThat
import com.flex.domain.model.DayType
import com.flex.domain.model.Settings
import com.flex.domain.model.TimeBlock
import com.flex.domain.model.WorkDay
import com.flex.domain.model.WorkLocation
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalTime

/**
 * Unit tests for CalendarEventMapper.
 * Tests location calculation, sync type filtering, and event title generation.
 */
class CalendarEventMapperTest {

    private lateinit var mapper: CalendarEventMapper

    @BeforeEach
    fun setUp() {
        mapper = CalendarEventMapper()
    }

    // ========== effectiveLocation Tests ==========

    @Test
    fun `effectiveLocation with no blocks returns workDay location`() {
        // Given: WorkDay with no blocks, location = OFFICE
        val workDay = WorkDay(
            id = 1,
            date = LocalDate.of(2026, 4, 5),
            location = WorkLocation.OFFICE,
            dayType = DayType.WORK,
            timeBlocks = emptyList()
        )

        // When: Getting effective location
        val result = mapper.effectiveLocation(workDay)

        // Then: Should return workDay.location
        assertThat(result).isEqualTo(WorkLocation.OFFICE)
    }

    @Test
    fun `effectiveLocation with all OFFICE blocks returns OFFICE`() {
        // Given: WorkDay with all OFFICE blocks
        val workDay = WorkDay(
            id = 1,
            date = LocalDate.of(2026, 4, 5),
            location = WorkLocation.HOME_OFFICE,
            dayType = DayType.WORK,
            timeBlocks = listOf(
                TimeBlock(id = 1, workDayId = 1, startTime = LocalTime.of(9, 0), endTime = LocalTime.of(12, 0), location = WorkLocation.OFFICE),
                TimeBlock(id = 2, workDayId = 1, startTime = LocalTime.of(13, 0), endTime = LocalTime.of(17, 0), location = WorkLocation.OFFICE)
            )
        )

        // When: Getting effective location
        val result = mapper.effectiveLocation(workDay)

        // Then: Should return OFFICE
        assertThat(result).isEqualTo(WorkLocation.OFFICE)
    }

    @Test
    fun `effectiveLocation with all HOME_OFFICE blocks returns HOME_OFFICE`() {
        // Given: WorkDay with all HOME_OFFICE blocks
        val workDay = WorkDay(
            id = 1,
            date = LocalDate.of(2026, 4, 5),
            location = WorkLocation.OFFICE,
            dayType = DayType.WORK,
            timeBlocks = listOf(
                TimeBlock(id = 1, workDayId = 1, startTime = LocalTime.of(9, 0), endTime = LocalTime.of(12, 0), location = WorkLocation.HOME_OFFICE),
                TimeBlock(id = 2, workDayId = 1, startTime = LocalTime.of(13, 0), endTime = LocalTime.of(17, 0), location = WorkLocation.HOME_OFFICE)
            )
        )

        // When: Getting effective location
        val result = mapper.effectiveLocation(workDay)

        // Then: Should return HOME_OFFICE
        assertThat(result).isEqualTo(WorkLocation.HOME_OFFICE)
    }

    @Test
    fun `effectiveLocation with majority OFFICE returns OFFICE`() {
        // Given: WorkDay with 2 OFFICE blocks and 1 HOME_OFFICE block
        val workDay = WorkDay(
            id = 1,
            date = LocalDate.of(2026, 4, 5),
            location = WorkLocation.HOME_OFFICE,
            dayType = DayType.WORK,
            timeBlocks = listOf(
                TimeBlock(id = 1, workDayId = 1, startTime = LocalTime.of(9, 0), endTime = LocalTime.of(12, 0), location = WorkLocation.OFFICE),
                TimeBlock(id = 2, workDayId = 1, startTime = LocalTime.of(13, 0), endTime = LocalTime.of(16, 0), location = WorkLocation.OFFICE),
                TimeBlock(id = 3, workDayId = 1, startTime = LocalTime.of(16, 0), endTime = LocalTime.of(17, 0), location = WorkLocation.HOME_OFFICE)
            )
        )

        // When: Getting effective location
        val result = mapper.effectiveLocation(workDay)

        // Then: Should return OFFICE (majority)
        assertThat(result).isEqualTo(WorkLocation.OFFICE)
    }

    @Test
    fun `effectiveLocation with tie returns OFFICE as tiebreaker`() {
        // Given: WorkDay with equal OFFICE and HOME_OFFICE blocks
        val workDay = WorkDay(
            id = 1,
            date = LocalDate.of(2026, 4, 5),
            location = WorkLocation.HOME_OFFICE,
            dayType = DayType.WORK,
            timeBlocks = listOf(
                TimeBlock(id = 1, workDayId = 1, startTime = LocalTime.of(9, 0), endTime = LocalTime.of(13, 0), location = WorkLocation.OFFICE),
                TimeBlock(id = 2, workDayId = 1, startTime = LocalTime.of(13, 0), endTime = LocalTime.of(17, 0), location = WorkLocation.HOME_OFFICE)
            )
        )

        // When: Getting effective location
        val result = mapper.effectiveLocation(workDay)

        // Then: Should return OFFICE (tiebreaker)
        assertThat(result).isEqualTo(WorkLocation.OFFICE)
    }

    // ========== isSyncedType Tests ==========

    @Test
    fun `isSyncedType returns false when dayType not in syncTypes`() {
        // Given: VACATION is not in syncTypes
        val workDay = WorkDay(
            id = 1,
            date = LocalDate.of(2026, 4, 5),
            location = WorkLocation.OFFICE,
            dayType = DayType.VACATION,
            timeBlocks = emptyList()
        )
        val settings = Settings(
            calendarSyncTypes = "WORK,SICK_DAY,FLEX_DAY"
        )

        // When: Checking if synced
        val result = mapper.isSyncedType(workDay, settings)

        // Then: Should return false
        assertThat(result).isFalse()
    }

    @Test
    fun `isSyncedType returns true when dayType in syncTypes`() {
        // Given: VACATION is in syncTypes
        val workDay = WorkDay(
            id = 1,
            date = LocalDate.of(2026, 4, 5),
            location = WorkLocation.OFFICE,
            dayType = DayType.VACATION,
            timeBlocks = emptyList()
        )
        val settings = Settings(
            calendarSyncTypes = "WORK,VACATION,SICK_DAY"
        )

        // When: Checking if synced
        val result = mapper.isSyncedType(workDay, settings)

        // Then: Should return true
        assertThat(result).isTrue()
    }

    @Test
    fun `isSyncedType with WORK and no blocks and OFFICE location and syncOffice disabled returns false`() {
        // Given: WORK day, no blocks, location OFFICE, syncOffice=false
        val workDay = WorkDay(
            id = 1,
            date = LocalDate.of(2026, 4, 5),
            location = WorkLocation.OFFICE,
            dayType = DayType.WORK,
            timeBlocks = emptyList()
        )
        val settings = Settings(
            calendarSyncTypes = "WORK",
            calendarSyncOffice = false,
            calendarSyncHomeOffice = true
        )

        // When: Checking if synced
        val result = mapper.isSyncedType(workDay, settings)

        // Then: Should return false
        assertThat(result).isFalse()
    }

    @Test
    fun `isSyncedType with WORK and no blocks and HOME_OFFICE location and syncHomeOffice disabled returns false`() {
        // Given: WORK day, no blocks, location HOME_OFFICE, syncHomeOffice=false
        val workDay = WorkDay(
            id = 1,
            date = LocalDate.of(2026, 4, 5),
            location = WorkLocation.HOME_OFFICE,
            dayType = DayType.WORK,
            timeBlocks = emptyList()
        )
        val settings = Settings(
            calendarSyncTypes = "WORK",
            calendarSyncOffice = true,
            calendarSyncHomeOffice = false
        )

        // When: Checking if synced
        val result = mapper.isSyncedType(workDay, settings)

        // Then: Should return false
        assertThat(result).isFalse()
    }

    @Test
    fun `isSyncedType with WORK and OFFICE block and syncOffice enabled returns true`() {
        // Given: WORK day with OFFICE block, syncOffice=true
        val workDay = WorkDay(
            id = 1,
            date = LocalDate.of(2026, 4, 5),
            location = WorkLocation.HOME_OFFICE,
            dayType = DayType.WORK,
            timeBlocks = listOf(
                TimeBlock(id = 1, workDayId = 1, startTime = LocalTime.of(9, 0), endTime = LocalTime.of(17, 0), location = WorkLocation.OFFICE)
            )
        )
        val settings = Settings(
            calendarSyncTypes = "WORK",
            calendarSyncOffice = true,
            calendarSyncHomeOffice = false
        )

        // When: Checking if synced
        val result = mapper.isSyncedType(workDay, settings)

        // Then: Should return true
        assertThat(result).isTrue()
    }

    @Test
    fun `isSyncedType with WORK and OFFICE block and syncOffice disabled returns false`() {
        // Given: WORK day with OFFICE block, syncOffice=false
        val workDay = WorkDay(
            id = 1,
            date = LocalDate.of(2026, 4, 5),
            location = WorkLocation.OFFICE,
            dayType = DayType.WORK,
            timeBlocks = listOf(
                TimeBlock(id = 1, workDayId = 1, startTime = LocalTime.of(9, 0), endTime = LocalTime.of(17, 0), location = WorkLocation.OFFICE)
            )
        )
        val settings = Settings(
            calendarSyncTypes = "WORK",
            calendarSyncOffice = false,
            calendarSyncHomeOffice = true
        )

        // When: Checking if synced
        val result = mapper.isSyncedType(workDay, settings)

        // Then: Should return false
        assertThat(result).isFalse()
    }

    @Test
    fun `isSyncedType with WORK and HOME_OFFICE block and syncHomeOffice enabled returns true`() {
        // Given: WORK day with HOME_OFFICE block, syncHomeOffice=true
        val workDay = WorkDay(
            id = 1,
            date = LocalDate.of(2026, 4, 5),
            location = WorkLocation.OFFICE,
            dayType = DayType.WORK,
            timeBlocks = listOf(
                TimeBlock(id = 1, workDayId = 1, startTime = LocalTime.of(9, 0), endTime = LocalTime.of(17, 0), location = WorkLocation.HOME_OFFICE)
            )
        )
        val settings = Settings(
            calendarSyncTypes = "WORK",
            calendarSyncOffice = false,
            calendarSyncHomeOffice = true
        )

        // When: Checking if synced
        val result = mapper.isSyncedType(workDay, settings)

        // Then: Should return true
        assertThat(result).isTrue()
    }

    @Test
    fun `isSyncedType with WORK and mixed blocks and syncOffice enabled returns true`() {
        // Given: WORK day with OFFICE and HOME_OFFICE blocks, syncOffice=true, syncHomeOffice=false
        val workDay = WorkDay(
            id = 1,
            date = LocalDate.of(2026, 4, 5),
            location = WorkLocation.HOME_OFFICE,
            dayType = DayType.WORK,
            timeBlocks = listOf(
                TimeBlock(id = 1, workDayId = 1, startTime = LocalTime.of(9, 0), endTime = LocalTime.of(13, 0), location = WorkLocation.OFFICE),
                TimeBlock(id = 2, workDayId = 1, startTime = LocalTime.of(13, 0), endTime = LocalTime.of(17, 0), location = WorkLocation.HOME_OFFICE)
            )
        )
        val settings = Settings(
            calendarSyncTypes = "WORK",
            calendarSyncOffice = true,
            calendarSyncHomeOffice = false
        )

        // When: Checking if synced
        val result = mapper.isSyncedType(workDay, settings)

        // Then: Should return true (OFFICE is allowed)
        assertThat(result).isTrue()
    }

    @Test
    fun `isSyncedType with sync disabled flag does not affect isSyncedType logic`() {
        // Given: calendarSyncEnabled=false but type is in syncTypes
        val workDay = WorkDay(
            id = 1,
            date = LocalDate.of(2026, 4, 5),
            location = WorkLocation.OFFICE,
            dayType = DayType.VACATION,
            timeBlocks = emptyList()
        )
        val settings = Settings(
            calendarSyncEnabled = false,
            calendarSyncTypes = "VACATION"
        )

        // When: Checking if synced
        val result = mapper.isSyncedType(workDay, settings)

        // Then: Should return true (enabled flag is not checked in isSyncedType)
        assertThat(result).isTrue()
    }

    // ========== eventTitle Tests ==========

    @Test
    fun `eventTitle for WORK OFFICE with default prefix returns Büro title`() {
        val workDay = WorkDay(id = 1, date = LocalDate.of(2026, 4, 5), location = WorkLocation.OFFICE, dayType = DayType.WORK)
        assertThat(mapper.eventTitle(workDay, "FleX")).isEqualTo("FleX: Büro 🏢")
    }

    @Test
    fun `eventTitle for WORK OFFICE with custom prefix returns custom prefix`() {
        val workDay = WorkDay(id = 1, date = LocalDate.of(2026, 4, 5), location = WorkLocation.OFFICE, dayType = DayType.WORK)
        assertThat(mapper.eventTitle(workDay, "Mein Kalender")).isEqualTo("Mein Kalender: Büro 🏢")
    }

    @Test
    fun `eventTitle for WORK OFFICE with empty prefix returns title without prefix`() {
        val workDay = WorkDay(id = 1, date = LocalDate.of(2026, 4, 5), location = WorkLocation.OFFICE, dayType = DayType.WORK)
        assertThat(mapper.eventTitle(workDay, "")).isEqualTo("Büro 🏢")
    }

    @Test
    fun `eventTitle for WORK HOME_OFFICE with empty prefix returns title without prefix`() {
        val workDay = WorkDay(id = 1, date = LocalDate.of(2026, 4, 5), location = WorkLocation.HOME_OFFICE, dayType = DayType.WORK)
        assertThat(mapper.eventTitle(workDay, "")).isEqualTo("Homeoffice 🏠")
    }

    @Test
    fun `eventTitle for WORK with no blocks and HOME_OFFICE location returns home office title`() {
        val workDay = WorkDay(id = 1, date = LocalDate.of(2026, 4, 5), location = WorkLocation.HOME_OFFICE, dayType = DayType.WORK)
        assertThat(mapper.eventTitle(workDay, "FleX")).isEqualTo("FleX: Homeoffice 🏠")
    }

    @Test
    fun `eventTitle for WORK with OFFICE blocks returns Büro title`() {
        val workDay = WorkDay(
            id = 1, date = LocalDate.of(2026, 4, 5), location = WorkLocation.HOME_OFFICE, dayType = DayType.WORK,
            timeBlocks = listOf(TimeBlock(id = 1, workDayId = 1, startTime = LocalTime.of(9, 0), endTime = LocalTime.of(17, 0), location = WorkLocation.OFFICE))
        )
        assertThat(mapper.eventTitle(workDay, "FleX")).isEqualTo("FleX: Büro 🏢")
    }

    @Test
    fun `eventTitle for WORK with HOME_OFFICE blocks returns home office title`() {
        val workDay = WorkDay(
            id = 1, date = LocalDate.of(2026, 4, 5), location = WorkLocation.OFFICE, dayType = DayType.WORK,
            timeBlocks = listOf(TimeBlock(id = 1, workDayId = 1, startTime = LocalTime.of(9, 0), endTime = LocalTime.of(17, 0), location = WorkLocation.HOME_OFFICE))
        )
        assertThat(mapper.eventTitle(workDay, "FleX")).isEqualTo("FleX: Homeoffice 🏠")
    }

    @Test
    fun `eventTitle for VACATION returns vacation title`() {
        val workDay = WorkDay(id = 1, date = LocalDate.of(2026, 4, 5), location = WorkLocation.OFFICE, dayType = DayType.VACATION)
        assertThat(mapper.eventTitle(workDay, "FleX")).isEqualTo("FleX: Urlaub 🏖")
    }

    @Test
    fun `eventTitle for VACATION with empty prefix returns title without prefix`() {
        val workDay = WorkDay(id = 1, date = LocalDate.of(2026, 4, 5), location = WorkLocation.OFFICE, dayType = DayType.VACATION)
        assertThat(mapper.eventTitle(workDay, "")).isEqualTo("Urlaub 🏖")
    }

    @Test
    fun `eventTitle for SICK_DAY returns sick day title`() {
        val workDay = WorkDay(id = 1, date = LocalDate.of(2026, 4, 5), location = WorkLocation.OFFICE, dayType = DayType.SICK_DAY)
        assertThat(mapper.eventTitle(workDay, "FleX")).isEqualTo("FleX: Krank 🤒")
    }

    @Test
    fun `eventTitle for FLEX_DAY returns flex day title`() {
        val workDay = WorkDay(id = 1, date = LocalDate.of(2026, 4, 5), location = WorkLocation.OFFICE, dayType = DayType.FLEX_DAY)
        assertThat(mapper.eventTitle(workDay, "FleX")).isEqualTo("FleX: Flextag 💪")
    }
}
