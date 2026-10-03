package com.flex.bdd.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.flex.data.update.UpdateInfo
import com.flex.testing.bddScenario
import com.flex.ui.theme.FlexTheme
import com.flex.ui.update.UpdateDialog
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UpdateDialogBddTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun updateDialogAvailableAndConfirmWorkflow() {
        var updateTriggered = false
        var dismissed = false

        val updateInfo = UpdateInfo(
            versionCode = 19,
            versionName = "1.8.2",
            changelog = "- Neue BDD Test-Suite\n- Performance-Verbesserungen",
            downloadUrl = "https://example.com/flex.apk"
        )

        bddScenario("App-Update Dialog: Anzeige der neuen Version und Aktualisieren-Aktion") {
            Given("ein Update-Dialog für Version 1.8.2 ist geöffnet") {
                composeTestRule.setContent {
                    FlexTheme {
                        UpdateDialog(
                            updateInfo = updateInfo,
                            isDownloading = false,
                            onDismiss = { dismissed = true },
                            onUpdate = { updateTriggered = true }
                        )
                    }
                }
                composeTestRule.waitForIdle()

                composeTestRule.onNodeWithText("Update verfügbar").assertIsDisplayed()
                composeTestRule.onNodeWithText("Version 1.8.2 ist verfügbar.").assertIsDisplayed()
                composeTestRule.onNodeWithText("- Neue BDD Test-Suite\n- Performance-Verbesserungen").assertIsDisplayed()
                composeTestRule.onNodeWithText("Aktualisieren").assertIsDisplayed()
                composeTestRule.onNodeWithText("Später").assertIsDisplayed()
            }

            When("der Benutzer auf Aktualisieren tippt") {
                composeTestRule.onNodeWithText("Aktualisieren").performClick()
                composeTestRule.waitForIdle()
            }

            Then("wird der Download- und Installations-Vorgang gestartet") {
                assertThat(updateTriggered).isTrue()
            }
        }
    }

    @Test
    fun updateDialogDismissWorkflow() {
        var dismissed = false

        val updateInfo = UpdateInfo(
            versionCode = 19,
            versionName = "1.8.2",
            changelog = "Bugfixes",
            downloadUrl = "https://example.com/flex.apk"
        )

        bddScenario("App-Update Dialog: Benutzer verschiebt das Update") {
            Given("der Update-Dialog ist geöffnet") {
                composeTestRule.setContent {
                    FlexTheme {
                        UpdateDialog(
                            updateInfo = updateInfo,
                            isDownloading = false,
                            onDismiss = { dismissed = true },
                            onUpdate = {}
                        )
                    }
                }
                composeTestRule.waitForIdle()
            }

            When("der Benutzer auf Später tippt") {
                composeTestRule.onNodeWithText("Später").performClick()
                composeTestRule.waitForIdle()
            }

            Then("wird der Dialog geschlossen") {
                assertThat(dismissed).isTrue()
            }
        }
    }
}
