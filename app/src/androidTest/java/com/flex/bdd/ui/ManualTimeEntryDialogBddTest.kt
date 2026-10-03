package com.flex.bdd.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.flex.domain.model.WorkLocation
import com.flex.testing.bddScenario
import com.flex.ui.home.ManualTimeEntryDialog
import com.flex.ui.theme.FlexTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalTime

@RunWith(AndroidJUnit4::class)
class ManualTimeEntryDialogBddTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun manualTimeEntryStartEndAndDurationWorkflow() {
        var confirmedStart: LocalTime? = null
        var confirmedEnd: LocalTime? = null
        var confirmedLocation: WorkLocation? = null
        var confirmedDuration: Int? = null
        var dismissed = false

        bddScenario("Manueller Zeiteintrag Dialog: Start/Ende und Gesamtzeit erfassen") {
            Given("der Dialog für manuellen Zeiteintrag ist geöffnet") {
                composeTestRule.setContent {
                    FlexTheme {
                        ManualTimeEntryDialog(
                            dailyWorkMinutes = 426,
                            selectedLocation = WorkLocation.OFFICE,
                            defaultStartTime = LocalTime.of(8, 0),
                            onDismiss = { dismissed = true },
                            onConfirmStartEnd = { start, end, loc ->
                                confirmedStart = start
                                confirmedEnd = end
                                confirmedLocation = loc
                            },
                            onConfirmDuration = { dur, loc ->
                                confirmedDuration = dur
                                confirmedLocation = loc
                            }
                        )
                    }
                }
                composeTestRule.waitForIdle()

                composeTestRule.onNodeWithText("Zeit erfassen").assertIsDisplayed()
                composeTestRule.onNodeWithText("Start / Ende").assertIsDisplayed()
                composeTestRule.onNodeWithText("Gesamtzeit").assertIsDisplayed()
                composeTestRule.onNodeWithText("Büro").assertIsDisplayed()
                composeTestRule.onNodeWithText("Home-Office").assertIsDisplayed()
            }

            When("der Benutzer den Standort auf Home-Office ändert") {
                composeTestRule.onNodeWithText("Home-Office").performClick()
                composeTestRule.waitForIdle()
            }

            And("auf Speichern tippt") {
                composeTestRule.onNodeWithText("Speichern").performClick()
                composeTestRule.waitForIdle()
            }

            Then("wird der Start/Ende-Eintrag für Home-Office bestätigt") {
                assertThat(confirmedLocation).isEqualTo(WorkLocation.HOME_OFFICE)
                assertThat(confirmedStart).isEqualTo(LocalTime.of(8, 0))
                assertThat(confirmedEnd).isNotNull()
            }
        }
    }

    @Test
    fun manualTimeEntryDurationTabWorkflow() {
        var confirmedDuration: Int? = null
        var confirmedLocation: WorkLocation? = null

        bddScenario("Manueller Zeiteintrag: Gesamtzeit-Tab auswählen und speichern") {
            Given("der Dialog ist auf dem Gesamtzeit-Tab geöffnet") {
                composeTestRule.setContent {
                    FlexTheme {
                        ManualTimeEntryDialog(
                            dailyWorkMinutes = 480, // 8h
                            selectedLocation = WorkLocation.OFFICE,
                            onDismiss = {},
                            onConfirmStartEnd = { _, _, _ -> },
                            onConfirmDuration = { dur, loc ->
                                confirmedDuration = dur
                                confirmedLocation = loc
                            }
                        )
                    }
                }
                composeTestRule.waitForIdle()

                composeTestRule.onNodeWithText("Gesamtzeit").performClick()
                composeTestRule.waitForIdle()
            }

            Then("werden Stunden- und Minutenfelder angezeigt") {
                composeTestRule.onNodeWithText("Stunden").assertIsDisplayed()
                composeTestRule.onNodeWithText("Minuten").assertIsDisplayed()
            }

            When("der Benutzer auf Speichern tippt") {
                composeTestRule.onNodeWithText("Speichern").performClick()
                composeTestRule.waitForIdle()
            }

            Then("wird die Dauer von 480 Minuten bestätigt") {
                assertThat(confirmedDuration).isEqualTo(480)
                assertThat(confirmedLocation).isEqualTo(WorkLocation.OFFICE)
            }
        }
    }
}
