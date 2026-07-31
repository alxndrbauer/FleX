package com.flex.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.flex.domain.model.DayType
import com.flex.domain.model.Settings
import com.flex.domain.model.WorkTimeRule
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
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
}
