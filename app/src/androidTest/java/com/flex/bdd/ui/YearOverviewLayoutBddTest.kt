package com.flex.bdd.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.flex.ui.theme.FlexTheme
import com.flex.ui.year.YearOverviewScreen
import com.flex.ui.year.YearOverviewUiState
import com.flex.ui.year.YearOverviewViewModel
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class YearOverviewLayoutBddTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun currentMonthIsVisibleInHeatmapWhenYearScreenOpens() {
        val today = LocalDate.now()
        val viewModel = mockk<YearOverviewViewModel>(relaxed = true)
        every { viewModel.uiState } returns MutableStateFlow(YearOverviewUiState(year = today.year))

        composeTestRule.setContent {
            FlexTheme {
                YearOverviewScreen(viewModel = viewModel)
            }
        }

        composeTestRule.waitForIdle()
        val currentMonthLabel = today.month.getDisplayName(TextStyle.SHORT, Locale.GERMAN)
        composeTestRule.onNodeWithText(currentMonthLabel, useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun workdayCountBadgeStaysOnOneLine() {
        val year = LocalDate.now().year
        val viewModel = mockk<YearOverviewViewModel>(relaxed = true)
        every {
            viewModel.uiState
        } returns MutableStateFlow(
            YearOverviewUiState(
                year = year,
                summary = com.flex.ui.year.YearSummary(year = year, totalWorkDays = 171)
            )
        )

        composeTestRule.setContent {
            FlexTheme {
                YearOverviewScreen(viewModel = viewModel)
            }
        }

        composeTestRule.waitForIdle()
        val badge = composeTestRule
            .onNodeWithText("171 Arbeitstage", useUnmergedTree = true)
            .performScrollTo()
            .assertIsDisplayed()
            .fetchSemanticsNode()
        val rootHeight = composeTestRule.onRoot().fetchSemanticsNode().size.height
        assertThat(badge.size.height * 25).isLessThan(rootHeight)
    }
}
