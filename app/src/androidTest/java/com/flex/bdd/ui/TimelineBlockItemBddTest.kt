package com.flex.bdd.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.flex.domain.model.TimeBlock
import com.flex.domain.model.WorkLocation
import com.flex.testing.bddScenario
import com.flex.ui.home.TimelineBlockItem
import com.flex.ui.theme.FlexTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalTime

@RunWith(AndroidJUnit4::class)
class TimelineBlockItemBddTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun shortClickTogglesLocation() {
        var toggledBlock: TimeBlock? = null
        var editedBlock: TimeBlock? = null

        val block = TimeBlock(
            id = 1L,
            startTime = LocalTime.of(8, 0),
            endTime = LocalTime.of(12, 0),
            location = WorkLocation.OFFICE
        )

        bddScenario("Zeitblock: Kurzer Klick schaltet Arbeitsort um") {
            Given("ein Zeitblock wird in der Timeline angezeigt") {
                composeTestRule.setContent {
                    FlexTheme {
                        TimelineBlockItem(
                            block = block,
                            isFirst = true,
                            isLast = true,
                            onEdit = { editedBlock = it },
                            onDelete = {},
                            onToggleLocation = { toggledBlock = it }
                        )
                    }
                }
                composeTestRule.waitForIdle()
                composeTestRule.onNodeWithText("08:00 – 12:00").assertIsDisplayed()
            }

            When("der Benutzer kurz auf den Zeitblock klickt") {
                composeTestRule.onNodeWithText("08:00 – 12:00").performClick()
                composeTestRule.waitForIdle()
            }

            Then("wird onToggleLocation aufgerufen und nicht onEdit") {
                assertThat(toggledBlock).isEqualTo(block)
                assertThat(editedBlock).isNull()
            }
        }
    }

    @Test
    fun longClickTriggersEdit() {
        var toggledBlock: TimeBlock? = null
        var editedBlock: TimeBlock? = null

        val block = TimeBlock(
            id = 2L,
            startTime = LocalTime.of(13, 0),
            endTime = LocalTime.of(17, 0),
            location = WorkLocation.HOME_OFFICE
        )

        bddScenario("Zeitblock: Langes Gedrückthalten öffnet Bearbeiten-Dialog") {
            Given("ein Zeitblock wird in der Timeline angezeigt") {
                composeTestRule.setContent {
                    FlexTheme {
                        TimelineBlockItem(
                            block = block,
                            isFirst = true,
                            isLast = true,
                            onEdit = { editedBlock = it },
                            onDelete = {},
                            onToggleLocation = { toggledBlock = it }
                        )
                    }
                }
                composeTestRule.waitForIdle()
                composeTestRule.onNodeWithText("13:00 – 17:00").assertIsDisplayed()
            }

            When("der Benutzer lange auf den Zeitblock drückt") {
                composeTestRule.onNodeWithText("13:00 – 17:00").performTouchInput {
                    longClick()
                }
                composeTestRule.waitForIdle()
            }

            Then("wird onEdit aufgerufen und nicht onToggleLocation") {
                assertThat(editedBlock).isEqualTo(block)
                assertThat(toggledBlock).isNull()
            }
        }
    }

    @Test
    fun deleteButtonTriggersDelete() {
        var deletedBlock: TimeBlock? = null

        val block = TimeBlock(
            id = 3L,
            startTime = LocalTime.of(9, 0),
            endTime = LocalTime.of(10, 0),
            location = WorkLocation.OFFICE
        )

        bddScenario("Zeitblock: Löschen-Button tippen") {
            Given("ein Zeitblock wird in der Timeline angezeigt") {
                composeTestRule.setContent {
                    FlexTheme {
                        TimelineBlockItem(
                            block = block,
                            isFirst = true,
                            isLast = true,
                            onEdit = {},
                            onDelete = { deletedBlock = it },
                            onToggleLocation = {}
                        )
                    }
                }
                composeTestRule.waitForIdle()
            }

            When("der Benutzer auf das Löschen-Icon klickt") {
                composeTestRule.onNodeWithContentDescription("Löschen").performClick()
                composeTestRule.waitForIdle()
            }

            Then("wird onDelete aufgerufen") {
                assertThat(deletedBlock).isEqualTo(block)
            }
        }
    }
}
