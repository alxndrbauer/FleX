package com.flex.bdd.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.flex.testing.bddScenario
import com.flex.ui.theme.FlexTheme
import com.flex.ui.yearchange.YearChangeDialog
import com.flex.ui.yearchange.YearChangeState
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class YearChangeDialogBddTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun yearChangeDialogApplyAndDismissWorkflow() {
        var confirmedCarryOver: Int? = null
        var confirmedAnnual: Int? = null
        var dismissed = false

        val testState = YearChangeState(
            sourceYear = 2025,
            targetYear = 2026,
            usedVacationDays = 26,
            remainingVacationDays = 4,
            currentAnnualVacationDays = 30,
            flextimeMinutes = 480L, // 8h
            showDialog = true
        )

        bddScenario("Jahreswechsel Dialog: Salden prüfen und Jahreswechsel anwenden") {
            Given("der Dialog zum Jahreswechsel ist geöffnet") {
                composeTestRule.setContent {
                    FlexTheme {
                        YearChangeDialog(
                            state = testState,
                            onDismiss = { dismissed = true },
                            onConfirm = { carryOver, annual ->
                                confirmedCarryOver = carryOver
                                confirmedAnnual = annual
                            }
                        )
                    }
                }
                composeTestRule.waitForIdle()

                composeTestRule.onNodeWithText("Jahr 2025 abschließen").assertIsDisplayed()
                composeTestRule.onNodeWithText("Urlaubstage genutzt").assertIsDisplayed()
                composeTestRule.onNodeWithText("Verbleibender Urlaub").assertIsDisplayed()
                composeTestRule.onNodeWithText("Flextime-Saldo").assertIsDisplayed()
                composeTestRule.onNodeWithText("Einstellungen für 2026").assertIsDisplayed()
                composeTestRule.onNodeWithText("Übernehmen").assertIsDisplayed()
                composeTestRule.onNodeWithText("Jetzt nicht").assertIsDisplayed()
            }

            When("der Benutzer auf Übernehmen tippt") {
                composeTestRule.onNodeWithText("Übernehmen").performClick()
                composeTestRule.waitForIdle()
            }

            Then("werden die Resturlaubstage und der Jahresurlaub für das neue Jahr bestätigt") {
                assertThat(confirmedCarryOver).isEqualTo(4)
                assertThat(confirmedAnnual).isEqualTo(30)
            }
        }
    }

    @Test
    fun yearChangeDialogDismissWorkflow() {
        var dismissed = false

        val testState = YearChangeState(
            sourceYear = 2025,
            targetYear = 2026,
            remainingVacationDays = 5,
            showDialog = true
        )

        bddScenario("Jahreswechsel Dialog: Jetzt nicht wählen ohne Übernahme") {
            Given("der Dialog zum Jahreswechsel ist geöffnet") {
                composeTestRule.setContent {
                    FlexTheme {
                        YearChangeDialog(
                            state = testState,
                            onDismiss = { dismissed = true },
                            onConfirm = { _, _ -> }
                        )
                    }
                }
                composeTestRule.waitForIdle()
            }

            When("der Benutzer auf Jetzt nicht tippt") {
                composeTestRule.onNodeWithText("Jetzt nicht").performClick()
                composeTestRule.waitForIdle()
            }

            Then("wird der Dismiss-Callback aufgerufen") {
                assertThat(dismissed).isTrue()
            }
        }
    }
}
