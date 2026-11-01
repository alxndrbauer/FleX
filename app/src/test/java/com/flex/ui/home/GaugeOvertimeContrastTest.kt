package com.flex.ui.home

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.flex.ui.theme.ClassicGreenGauge
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * WCAG contrast of the overtime gauge roles: band vs. base ring (non-text, 1.4.11 >= 3:1)
 * and overtime label vs. band (text, 1.4.3 >= 4.5:1).
 *
 * The THEME palettes below mirror the Material tokens in Theme.kt (LightColorScheme, DarkColorScheme,
 * AmoledColorScheme), which are private there. Keep them in sync when the palettes change.
 */
class GaugeOvertimeContrastTest {

    private data class GaugeRoles(val ring: Color, val overtimeBand: Color, val overtimeText: Color)

    @Test
    fun `light theme overtime band and label are distinguishable`() {
        assertRolesReadable(
            GaugeRoles(
                ring = Color(0xFF1A73E8),          // primary
                overtimeBand = Color(0xFFFFDDB8),  // tertiaryContainer
                overtimeText = Color(0xFF2B1700)   // onTertiaryContainer
            )
        )
    }

    @Test
    fun `dark theme overtime band and label are distinguishable`() {
        assertRolesReadable(
            GaugeRoles(
                ring = Color(0xFF4DA3FF),          // primary
                overtimeBand = Color(0xFF673B00),  // tertiaryContainer
                overtimeText = Color(0xFFFFDDB8)   // onTertiaryContainer
            )
        )
    }

    @Test
    fun `amoled theme overtime band and label are distinguishable`() {
        assertRolesReadable(
            GaugeRoles(
                ring = Color(0xFF4DA3FF),          // primary
                overtimeBand = Color(0xFF331E00),  // tertiaryContainer
                overtimeText = Color(0xFFFFDDB8)   // onTertiaryContainer
            )
        )
    }

    @Test
    fun `classic green overtime band and label are distinguishable`() {
        assertRolesReadable(
            GaugeRoles(
                ring = ClassicGreenGauge.ring,
                overtimeBand = ClassicGreenGauge.overtimeBand,
                overtimeText = ClassicGreenGauge.text
            )
        )
    }

    private fun assertRolesReadable(roles: GaugeRoles) {
        assertThat(contrastRatio(roles.overtimeBand, roles.ring)).isAtLeast(3.0)
        assertThat(contrastRatio(roles.overtimeText, roles.overtimeBand)).isAtLeast(4.5)
    }

    private fun contrastRatio(first: Color, second: Color): Double {
        val a = first.luminance().toDouble()
        val b = second.luminance().toDouble()
        val lighter = maxOf(a, b)
        val darker = minOf(a, b)
        return (lighter + 0.05) / (darker + 0.05)
    }
}
