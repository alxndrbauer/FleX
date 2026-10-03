package com.flex.bdd.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.flex.testing.bddScenario
import com.flex.ui.planning.PlanHoursDialog
import com.flex.ui.theme.FlexTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class PlanHoursDialogBddTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun planHoursConfirmWorkflow() {
        var confirmedMinutes: Int? = null
        val testDate = LocalDate.of(2026, 10, 5)

        bddScenario("Planungs-Dialog: Geplante Stunden bestätigen") {
            Given("der Dialog zur Stundenplanung ist geöffnet mit 7 Stunden und 6 Minuten") {
                composeTestRule.setContent {
                    FlexTheme {
                        PlanHoursDialog(
                            date = testDate,
                            initialHours = 7,
                            initialMinutes = 6,
                            onDismiss = {},
                            onConfirm = { minutes -> confirmedMinutes = minutes }
                        )
                    }
                }
                composeTestRule.waitForIdle()

                composeTestRule.onNodeWithText("5. Oktober 2026").assertIsDisplayed()
                composeTestRule.onNodeWithText("Geplante Arbeitszeit").assertIsDisplayed()
                composeTestRule.onNodeWithText("Std.").assertIsDisplayed()
                composeTestRule.onNodeWithText("Min.").assertIsDisplayed()
                composeTestRule.onNodeWithText("Speichern").assertIsDisplayed()
                composeTestRule.onNodeWithText("Abbrechen").assertIsDisplayed()
            }

            When("der Benutzer auf Speichern tippt") {
                composeTestRule.onNodeWithText("Speichern").performClick()
                composeTestRule.waitForIdle()
            }

            Then("werden 426 Minuten (7h 6m) als geplante Zeit übergeben") {
                assertThat(confirmedMinutes).isEqualTo(426)
            }
        }
    }

    @Test
    fun planHoursDeleteWorkflow() {
        var deleted = false
        val testDate = LocalDate.of(2026, 10, 6)

        bddScenario("Planungs-Dialog: Bestehenden Plan löschen") {
            Given("der Dialog für einen bereits geplanten Tag ist mit Löschen-Option geöffnet") {
                composeTestRule.setContent {
                    FlexTheme {
                        PlanHoursDialog(
                            date = testDate,
                            initialHours = 8,
                            initialMinutes = 0,
                            onDismiss = {},
                            onDelete = { deleted = true },
                            onConfirm = {}
                        )
                    }
                }
                composeTestRule.waitForIdle()

                composeTestRule.onNodeWithText("Löschen").assertIsDisplayed()
            }

            When("der Benutzer auf Löschen tippt") {
                composeTestRule.onNodeWithText("Löschen").performClick()
                composeTestRule.waitForIdle()
            }

            Then("wird der Delete-Callback aufgerufen") {
                assertThat(deleted).isTrue()
            }
        }
    }
}
