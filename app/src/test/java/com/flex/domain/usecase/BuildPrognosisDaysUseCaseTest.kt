package com.flex.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.flex.domain.model.DayType
import com.flex.domain.model.Settings
import com.flex.domain.model.WorkDay
import com.flex.domain.model.WorkLocation
import com.flex.domain.model.WorkTimeRule
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

class BuildPrognosisDaysUseCaseTest {

    private val useCase = BuildPrognosisDaysUseCase()
    private val settings = Settings(dailyWorkMinutes = 420, monthlyWorkMinutes = 9000)

    @Test
    fun `default 5-day week includes all Mo-Fr working days`() {
        val rules = listOf(
            WorkTimeRule(
                id = 1L,
                validFrom = YearMonth.of(2026, 8),
                dailyWorkMinutes = 420,
                monthlyWorkMinutes = 0
            )
        )
        val result = useCase(YearMonth.of(2026, 8), emptyList(), settings, rules)
        val workDays = result.filter { it.dayType == DayType.WORK && it.isPlanned }

        assertThat(workDays).hasSize(21)
        assertThat(workDays.none { it.date.dayOfWeek == DayOfWeek.SATURDAY }).isTrue()
        assertThat(workDays.none { it.date.dayOfWeek == DayOfWeek.SUNDAY }).isTrue()
    }

    @Test
    fun `4-day week Mo-Do - no Fridays in prognosis`() {
        val rules = listOf(
            WorkTimeRule(
                id = 1L,
                validFrom = YearMonth.of(2026, 8),
                dailyWorkMinutes = 420,
                monthlyWorkMinutes = 0,
                workDays = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY)
            )
        )
        val result = useCase(YearMonth.of(2026, 8), emptyList(), settings, rules)
        val workDays = result.filter { it.dayType == DayType.WORK && it.isPlanned }

        assertThat(workDays.none { it.date.dayOfWeek == DayOfWeek.FRIDAY }).isTrue()
        assertThat(workDays).hasSize(17)
    }

    @Test
    fun `3-day week Mo Mi Fr - only those days in prognosis`() {
        val rules = listOf(
            WorkTimeRule(
                id = 1L,
                validFrom = YearMonth.of(2026, 8),
                dailyWorkMinutes = 480,
                monthlyWorkMinutes = 0,
                workDays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
            )
        )
        val result = useCase(YearMonth.of(2026, 8), emptyList(), settings, rules)
        val workDays = result.filter { it.dayType == DayType.WORK && it.isPlanned }

        assertThat(workDays.all {
            it.date.dayOfWeek in setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
        }).isTrue()
        assertThat(workDays).hasSize(13)
    }

    @Test
    fun `prognosis days use defaultStartTime from settings for planned days`() {
        val customStartTime = LocalTime.of(9, 15)
        val customSettings = settings.copy(defaultStartTime = customStartTime)
        val month = YearMonth.of(2026, 8)

        val result = useCase(month, emptyList(), customSettings)
        val workDays = result.filter { it.dayType == DayType.WORK && it.isPlanned }

        assertThat(workDays).isNotEmpty()
        assertThat(workDays.all { day ->
            val block = day.timeBlocks.firstOrNull()
            block != null && block.startTime == customStartTime &&
                block.endTime == customStartTime.plusMinutes(customSettings.dailyWorkMinutes.toLong())
        }).isTrue()
    }

    @Test
    fun `prognosis fills empty timeBlocks in existing work day using defaultStartTime`() {
        val customStartTime = LocalTime.of(7, 30)
        val customSettings = settings.copy(defaultStartTime = customStartTime)
        val month = YearMonth.of(2026, 8)
        val existing = WorkDay(
            id = 42L,
            date = month.atDay(3), // Monday
            location = WorkLocation.OFFICE,
            dayType = DayType.WORK,
            timeBlocks = emptyList()
        )

        val result = useCase(month, listOf(existing), customSettings)
        val day = result.first { it.date == existing.date }

        assertThat(day.timeBlocks).hasSize(1)
        assertThat(day.timeBlocks[0].startTime).isEqualTo(customStartTime)
        assertThat(day.timeBlocks[0].endTime).isEqualTo(customStartTime.plusMinutes(customSettings.dailyWorkMinutes.toLong()))
    }
}
