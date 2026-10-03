package com.flex.bdd.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.flex.testing.bddScenario
import com.flex.ui.onboarding.OnboardingScreen
import com.flex.ui.theme.FlexTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnboardingScreenBddTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun onboardingPagingAndCompletionWorkflow() {
        var onFinishCalled = false

        bddScenario("Onboarding Einführung durchblättern und abschließen") {
            Given("der Benutzer sieht den ersten Onboarding-Bildschirm") {
                composeTestRule.setContent {
                    FlexTheme {
                        OnboardingScreen(onFinish = { onFinishCalled = true })
                    }
                }
                composeTestRule.waitForIdle()

                composeTestRule.onNodeWithText("Willkommen bei FleX").assertIsDisplayed()
                composeTestRule.onNodeWithText("Deine persönliche Arbeitszeitverfolgung – einfach, schnell, übersichtlich.").assertIsDisplayed()
                composeTestRule.onNodeWithText("Weiter").assertIsDisplayed()
                composeTestRule.onNodeWithText("Überspringen").assertIsDisplayed()
            }

            When("der Benutzer auf Weiter tippt") {
                composeTestRule.onNodeWithText("Weiter").performClick()
                composeTestRule.waitForIdle()
            }

            Then("erscheint die zweite Seite zum Ein- und Ausstempeln") {
                composeTestRule.onNodeWithText("Einstempeln & Ausstempeln").assertIsDisplayed()
            }

            When("der Benutzer auf Überspringen tippt") {
                composeTestRule.onNodeWithText("Überspringen").performClick()
                composeTestRule.waitForIdle()
            }

            Then("wird der Onboarding-Abschluss-Callback aufgerufen") {
                assertThat(onFinishCalled).isTrue()
            }
        }
    }
}
