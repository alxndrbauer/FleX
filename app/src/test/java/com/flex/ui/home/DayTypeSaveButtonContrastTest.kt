package com.flex.ui.home

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.flex.ui.theme.BusinessTripColor
import com.flex.ui.theme.FlexDayColor
import com.flex.ui.theme.HomeOfficeColor
import com.flex.ui.theme.OfficeColor
import com.flex.ui.theme.OvertimeDayColor
import com.flex.ui.theme.SaturdayBonusColor
import com.flex.ui.theme.SickDayColor
import com.flex.ui.theme.SpecialVacationColor
import com.flex.ui.theme.VacationColor
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class DayTypeSaveButtonContrastTest {
    @Test
    fun `button foreground maintains WCAG AA contrast for day type colors`() {
        val backgrounds = listOf(
            OfficeColor,
            HomeOfficeColor,
            VacationColor,
            SpecialVacationColor,
            FlexDayColor,
            SaturdayBonusColor,
            SickDayColor,
            OvertimeDayColor,
            BusinessTripColor
        )

        backgrounds.forEach { background ->
            val foreground = dayTypeSaveButtonContentColor(background)
            val contrast = contrastRatio(background.luminance(), foreground.luminance())
            assertThat(contrast).isAtLeast(4.5)
        }

        assertThat(dayTypeSaveButtonContentColor(VacationColor)).isEqualTo(Color.Black)
    }

    private fun contrastRatio(firstLuminance: Float, secondLuminance: Float): Double {
        val lighter = maxOf(firstLuminance, secondLuminance)
        val darker = minOf(firstLuminance, secondLuminance)
        return (lighter + 0.05) / (darker + 0.05)
    }
}
