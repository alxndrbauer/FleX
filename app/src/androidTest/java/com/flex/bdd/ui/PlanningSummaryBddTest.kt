package com.flex.bdd.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.flex.testing.bddScenario
import com.flex.ui.planning.MonthSummary
import com.flex.ui.planning.MonthSummaryCard
import com.flex.ui.planning.OfficeHoursDetail
import com.flex.ui.theme.FlexTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.YearMonth

@RunWith(AndroidJUnit4::class)
class PlanningSummaryBddTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun monthSummaryCardQuotaMetWorkflow() {
        var clicked = false
        val summary = MonthSummary(
            yearMonth = YearMonth.of(2026, 10),
            plannedDays = 20,
            officeDays = 12,
            homeOfficeDays = 8,
            vacationDays = 2,
            quotaMet = true,
            officePercent = 60.0,
            officeHours = OfficeHoursDetail(
                requiredOfficeMinutes = 3408, // 56h 48m
                plannedOfficeMinutes = 5112,  // 85h 12m
                plannedTotalMinutes = 8520,
                targetMonthlyMinutes = 8520
            )
        )

        bddScenario("Planungsübersicht: MonthSummaryCard zeigt Quotenstatus und reagiert auf Klick") {
            Given("eine MonthSummaryCard für Oktober 2026 mit erfüllter Quote ist gerendert") {
                composeTestRule.setContent {
                    FlexTheme {
                        MonthSummaryCard(
                            summary = summary,
                            isSelected = false,
                            onClick = { clicked = true }
                        )
                    }
                }
                composeTestRule.waitForIdle()

                composeTestRule.onNodeWithText("Oktober 2026").assertIsDisplayed()
                composeTestRule.onNodeWithText("✓").assertIsDisplayed()
                composeTestRule.onNodeWithText("Büro: 12  HO: 8  Urlaub: 2").assertIsDisplayed()
                composeTestRule.onNodeWithText("20 geplant").assertIsDisplayed()
            }

            When("der Benutzer auf die Monats-Karte tippt") {
                composeTestRule.onNodeWithText("Oktober 2026").performClick()
                composeTestRule.waitForIdle()
            }

            Then("wird der onClick Callback ausgelöst") {
                assertThat(clicked).isTrue()
            }
        }
    }

    @Test
    fun monthSummaryCardQuotaNotMetWorkflow() {
        val summary = MonthSummary(
            yearMonth = YearMonth.of(2026, 11),
            plannedDays = 10,
            officeDays = 2,
            homeOfficeDays = 8,
            vacationDays = 0,
            quotaMet = false,
            officePercent = 20.0,
            officeHours = OfficeHoursDetail(
                requiredOfficeMinutes = 3408,
                plannedOfficeMinutes = 1000,
                plannedTotalMinutes = 4000,
                targetMonthlyMinutes = 8520
            )
        )

        bddScenario("Planungsübersicht: Nicht erfüllte Quote wird mit ✗ markiert") {
            Given("eine MonthSummaryCard mit verfehlter Quote ist gerendert") {
                composeTestRule.setContent {
                    FlexTheme {
                        MonthSummaryCard(
                            summary = summary,
                            isSelected = false,
                            onClick = {}
                        )
                    }
                }
                composeTestRule.waitForIdle()
            }

            Then("wird das Fehl-Symbol ✗ angezeigt") {
                composeTestRule.onNodeWithText("✗").assertIsDisplayed()
                composeTestRule.onNodeWithText("November 2026").assertIsDisplayed()
                composeTestRule.onNodeWithText("Büro: 2  HO: 8  Urlaub: 0").assertIsDisplayed()
            }
        }
    }
}
