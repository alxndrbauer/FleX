package com.flex.bdd.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.flex.domain.model.TimeBlock
import com.flex.domain.model.WorkLocation
import com.flex.testing.bddScenario
import com.flex.ui.home.EditTimeBlockDialog
import com.flex.ui.theme.FlexTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalTime

@RunWith(AndroidJUnit4::class)
class EditTimeBlockDialogBddTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun editTimeBlockLocationChangeAndSaveWorkflow() {
        var savedStart: LocalTime? = null
        var savedEnd: LocalTime? = null
        var savedLocation: WorkLocation? = null
        var savedDuration: Boolean? = null

        val initialBlock = TimeBlock(
            id = 1L,
            startTime = LocalTime.of(9, 0),
            endTime = LocalTime.of(17, 0),
            location = WorkLocation.OFFICE,
            isDuration = false
        )

        bddScenario("Zeitblock bearbeiten: Standort wechseln und speichern") {
            Given("der Bearbeitungs-Dialog für einen bestehenden Zeitblock ist geöffnet") {
                composeTestRule.setContent {
                    FlexTheme {
                        EditTimeBlockDialog(
                            block = initialBlock,
                            onDismiss = {},
                            onSave = { start, end, loc, isDur ->
                                savedStart = start
                                savedEnd = end
                                savedLocation = loc
                                savedDuration = isDur
                            },
                            onDelete = {}
                        )
                    }
                }
                composeTestRule.waitForIdle()

                composeTestRule.onNodeWithText("Zeitblock bearbeiten").assertIsDisplayed()
                composeTestRule.onNodeWithText("Büro").assertIsDisplayed()
                composeTestRule.onNodeWithText("Home-Office").assertIsDisplayed()
                composeTestRule.onNodeWithText("Löschen").assertIsDisplayed()
                composeTestRule.onNodeWithText("Speichern").assertIsDisplayed()
            }

            When("der Benutzer den Standort auf Home-Office ändert") {
                composeTestRule.onNodeWithText("Home-Office").performClick()
                composeTestRule.waitForIdle()
            }

            And("auf Speichern tippt") {
                composeTestRule.onNodeWithText("Speichern").performClick()
                composeTestRule.waitForIdle()
            }

            Then("werden die aktualisierten Daten mit neuem Standort persistiert") {
                assertThat(savedLocation).isEqualTo(WorkLocation.HOME_OFFICE)
                assertThat(savedStart).isEqualTo(LocalTime.of(9, 0))
                assertThat(savedEnd).isEqualTo(LocalTime.of(17, 0))
                assertThat(savedDuration).isFalse()
            }
        }
    }

    @Test
    fun editTimeBlockDeleteWorkflow() {
        var deleted = false

        val initialBlock = TimeBlock(
            id = 2L,
            startTime = LocalTime.of(10, 0),
            endTime = LocalTime.of(12, 0),
            location = WorkLocation.HOME_OFFICE
        )

        bddScenario("Zeitblock löschen: Delete-Aktion auslösen") {
            Given("der Zeitblock-Dialog ist geöffnet") {
                composeTestRule.setContent {
                    FlexTheme {
                        EditTimeBlockDialog(
                            block = initialBlock,
                            onDismiss = {},
                            onSave = { _, _, _, _ -> },
                            onDelete = { deleted = true }
                        )
                    }
                }
                composeTestRule.waitForIdle()
            }

            When("der Benutzer auf Löschen tippt") {
                composeTestRule.onNodeWithText("Löschen").performClick()
                composeTestRule.waitForIdle()
            }

            Then("wird der Delete-Callback ausgeführt") {
                assertThat(deleted).isTrue()
            }
        }
    }
}
