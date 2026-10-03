package com.flex.bdd.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.flex.domain.model.DayType
import com.flex.domain.model.TimeBlock
import com.flex.domain.model.WorkDay
import com.flex.domain.model.WorkLocation
import com.flex.testing.bddScenario
import com.flex.ui.month.DayCell
import com.flex.ui.month.LegendItem
import com.flex.ui.month.WorkDayListItem
import com.flex.ui.theme.FlexTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalTime

@RunWith(AndroidJUnit4::class)
class MonthComponentsBddTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun workDayListItemRenderingAndClickWorkflow() {
        var clicked = false
        val testDay = WorkDay(
            id = 1L,
            date = LocalDate.of(2026, 9, 15),
            dayType = DayType.WORK,
            location = WorkLocation.OFFICE,
            timeBlocks = listOf(
                TimeBlock(
                    id = 1L,
                    startTime = LocalTime.of(8, 0),
                    endTime = LocalTime.of(16, 30),
                    location = WorkLocation.OFFICE
                )
            )
        )

        bddScenario("Monatsliste: WorkDayListItem zeigt Datum, Zeiten und reagiert auf Klick") {
            Given("ein WorkDayListItem für den 15. September ist gerendert") {
                composeTestRule.setContent {
                    FlexTheme {
                        WorkDayListItem(
                            workDay = testDay,
                            netMinutes = 480L,
                            onClick = { clicked = true }
                        )
                    }
                }
                composeTestRule.waitForIdle()

                composeTestRule.onNodeWithText("Büro").assertIsDisplayed()
                composeTestRule.onNodeWithText("08:00 – 16:30").assertIsDisplayed()
            }

            When("der Benutzer auf das Listenelement tippt") {
                composeTestRule.onNodeWithText("08:00 – 16:30").performClick()
                composeTestRule.waitForIdle()
            }

            Then("wird der Klick-Handler ausgeführt") {
                assertThat(clicked).isTrue()
            }
        }
    }

    @Test
    fun dayCellRenderingAndInteraction() {
        var cellClicked = false
        val date = LocalDate.of(2026, 9, 20)

        bddScenario("Kalenderzelle: DayCell zeigt Tageszahl und reagiert auf Klick") {
            Given("eine DayCell für den 20. September ist gerendert") {
                composeTestRule.setContent {
                    FlexTheme {
                        DayCell(
                            date = date,
                            workDay = null,
                            isToday = false,
                            onClick = { cellClicked = true }
                        )
                    }
                }
                composeTestRule.waitForIdle()

                composeTestRule.onNodeWithText("20").assertIsDisplayed()
            }

            When("der Benutzer auf die Zelle tippt") {
                composeTestRule.onNodeWithText("20").performClick()
                composeTestRule.waitForIdle()
            }

            Then("wird der onClick Callback der Zelle ausgelöst") {
                assertThat(cellClicked).isTrue()
            }
        }
    }

    @Test
    fun legendItemRendering() {
        bddScenario("Legende: LegendItem stellt Farbindikator und Beschriftung dar") {
            Given("ein LegendItem für Home-Office ist gerendert") {
                composeTestRule.setContent {
                    FlexTheme {
                        LegendItem(color = Color.Blue, label = "Home-Office")
                    }
                }
                composeTestRule.waitForIdle()
            }

            Then("wird das Label Home-Office angezeigt") {
                composeTestRule.onNodeWithText("Home-Office").assertIsDisplayed()
            }
        }
    }
}
