package com.flex.bdd.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.flex.domain.model.DayType
import com.flex.domain.model.WorkDay
import com.flex.domain.model.WorkLocation
import com.flex.testing.bddScenario
import com.flex.ui.month.EditDayDialog
import com.flex.ui.theme.FlexTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class EditDayDialogBddTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun editDayTypeToVacationAndSaveWorkflow() {
        var savedDayType: DayType? = null
        var savedNote: String? = null
        var dismissed = false

        val testDay = WorkDay(
            id = 1L,
            date = LocalDate.of(2026, 9, 15),
            dayType = DayType.WORK,
            location = WorkLocation.OFFICE
        )

        bddScenario("Tag im Monat bearbeiten: Typ auf Urlaub ändern und speichern") {
            Given("der Dialog zur Bearbeitung eines Tages ist geöffnet") {
                composeTestRule.setContent {
                    FlexTheme {
                        EditDayDialog(
                            workDay = testDay,
                            onDismiss = { dismissed = true },
                            onSave = { type, note, _ ->
                                savedDayType = type
                                savedNote = note
                            },
                            onDelete = {}
                        )
                    }
                }
                composeTestRule.waitForIdle()

                composeTestRule.onNodeWithText("15. September 2026").assertIsDisplayed()
                composeTestRule.onNodeWithText("Tagestyp").assertIsDisplayed()
                composeTestRule.onNodeWithText("Arbeitstag").assertIsDisplayed()
                composeTestRule.onNodeWithText("Urlaub").assertIsDisplayed()
                composeTestRule.onNodeWithText("Krank").assertIsDisplayed()
                composeTestRule.onNodeWithText("Speichern").assertIsDisplayed()
                composeTestRule.onNodeWithText("Abbrechen").assertIsDisplayed()
            }

            When("der Benutzer den Tagestyp auf Urlaub ändert") {
                composeTestRule.onNodeWithText("Urlaub").performClick()
                composeTestRule.waitForIdle()
            }

            And("auf Speichern tippt") {
                composeTestRule.onNodeWithText("Speichern").performClick()
                composeTestRule.waitForIdle()
            }

            Then("wird der Tag als Urlaubstag gespeichert") {
                assertThat(savedDayType).isEqualTo(DayType.VACATION)
            }
        }
    }

    @Test
    fun editDayDeleteWorkflow() {
        var deleted = false

        val testDay = WorkDay(
            id = 2L,
            date = LocalDate.of(2026, 9, 16),
            dayType = DayType.WORK,
            location = WorkLocation.HOME_OFFICE
        )

        bddScenario("Tag löschen: Delete-Icon anklicken") {
            Given("der Dialog zur Bearbeitung des Tages ist geöffnet") {
                composeTestRule.setContent {
                    FlexTheme {
                        EditDayDialog(
                            workDay = testDay,
                            onDismiss = {},
                            onSave = { _, _, _ -> },
                            onDelete = { deleted = true }
                        )
                    }
                }
                composeTestRule.waitForIdle()
            }

            When("der Benutzer auf das Lösch-Icon tippt") {
                composeTestRule.onNodeWithContentDescription("Tag löschen").performClick()
                composeTestRule.waitForIdle()
            }

            Then("wird die Lösch-Aktion ausgelöst") {
                assertThat(deleted).isTrue()
            }
        }
    }
}
