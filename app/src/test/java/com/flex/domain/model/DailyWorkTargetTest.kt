package com.flex.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

class DailyWorkTargetTest {
    @Test
    fun `unruled weekend has no daily target`() {
        val saturday = LocalDate.of(2025, 1, 4)

        assertThat(getDailyWorkTargetMinutes(saturday, DayType.WORK, 480, null)).isEqualTo(0)
    }

    @Test
    fun `configured work day uses rule target`() {
        val saturday = LocalDate.of(2025, 1, 4)
        val saturdayRule = WorkTimeRule(
            validFrom = YearMonth.of(2025, 1),
            dailyWorkMinutes = 360,
            monthlyWorkMinutes = 7_200,
            workDays = setOf(DayOfWeek.SATURDAY)
        )

        assertThat(getDailyWorkTargetMinutes(saturday, DayType.WORK, 480, saturdayRule))
            .isEqualTo(360)
    }

    @Test
    fun `non work day types have no daily target`() {
        val monday = LocalDate.of(2025, 1, 6)
        val nonWorkTypes = listOf(
            DayType.VACATION,
            DayType.SPECIAL_VACATION,
            DayType.FLEX_DAY,
            DayType.SATURDAY_BONUS,
            DayType.SICK_DAY,
            DayType.OVERTIME_DAY
        )

        nonWorkTypes.forEach { dayType ->
            assertThat(getDailyWorkTargetMinutes(monday, dayType, 480, null))
                .isEqualTo(0)
        }
    }
}
