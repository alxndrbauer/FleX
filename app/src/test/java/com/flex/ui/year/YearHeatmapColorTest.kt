package com.flex.ui.year

import com.flex.domain.model.DayType
import com.flex.domain.model.WorkLocation
import com.flex.ui.theme.OvertimeDayColor
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

class YearHeatmapColorTest {
    @Test
    fun `overtime day uses its category color in the heatmap`() {
        val entry = DayHeatmapEntry(
            date = LocalDate.of(2025, 1, 4),
            dayType = DayType.OVERTIME_DAY,
            isPublicHoliday = false,
            holidayName = null,
            netMinutes = 0,
            location = WorkLocation.OFFICE,
            isPlanned = false
        )

        assertThat(cellColor(entry, dailyWorkMinutes = 426, isSurface = false))
            .isEqualTo(OvertimeDayColor.copy(alpha = 0.8f))
    }
}
