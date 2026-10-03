package com.flex.bdd.feature.automation

import com.flex.data.local.PausePreferences
import com.flex.domain.model.DayType
import com.flex.domain.model.TimeBlock
import com.flex.domain.model.WorkDay
import com.flex.domain.model.WorkLocation
import com.flex.domain.repository.WorkDayRepository
import com.flex.domain.usecase.AutoClockOutUseCase
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import java.time.LocalDate
import java.time.LocalTime

class AutoClockOutBehaviorSpec : BehaviorSpec({

    val today = LocalDate.now()

    Given("ein laufender Arbeitszeitblock im Büro") {
        val workDayRepository = mockk<WorkDayRepository>(relaxed = true)
        val pausePreferences = mockk<PausePreferences>(relaxed = true)
        val autoClockOut = AutoClockOutUseCase(workDayRepository, pausePreferences)

        val runningBlock = TimeBlock(
            id = 42L,
            workDayId = 10L,
            startTime = LocalTime.of(8, 30),
            endTime = null,
            location = WorkLocation.OFFICE
        )
        val activeWorkDay = WorkDay(
            id = 10L,
            date = today,
            location = WorkLocation.OFFICE,
            dayType = DayType.WORK,
            timeBlocks = listOf(runningBlock)
        )

        every { pausePreferences.isPaused } returns false
        coEvery { workDayRepository.getWorkDay(today) } returns flowOf(activeWorkDay)

        When("der Nutzer das Büro verlässt (Geofence-Exit oder WLAN-Disconnect)") {
            val success = autoClockOut()

            Then("wird das Ausstempeln erfolgreich mit true bestätigt") {
                success.shouldBeTrue()
            }

            Then("wird der offene Zeitblock mit einer gültigen Endzeit versehen und gespeichert") {
                val blockSlot = slot<TimeBlock>()
                coVerify { workDayRepository.saveTimeBlock(capture(blockSlot)) }
                blockSlot.captured.id shouldBe 42L
                blockSlot.captured.location shouldBe WorkLocation.OFFICE
                blockSlot.captured.endTime.shouldNotBeNull()
            }
        }
    }

    Given("ein Nutzer in aktiver Arbeitspause beim Verlassen des Büros") {
        val workDayRepository = mockk<WorkDayRepository>(relaxed = true)
        val pausePreferences = mockk<PausePreferences>(relaxed = true)
        val autoClockOut = AutoClockOutUseCase(workDayRepository, pausePreferences)

        val completedBlock = TimeBlock(
            id = 11L,
            workDayId = 10L,
            startTime = LocalTime.of(8, 0),
            endTime = LocalTime.of(12, 0),
            location = WorkLocation.OFFICE
        )
        val workDay = WorkDay(
            id = 10L,
            date = today,
            location = WorkLocation.OFFICE,
            dayType = DayType.WORK,
            timeBlocks = listOf(completedBlock)
        )

        every { pausePreferences.isPaused } returns true
        coEvery { workDayRepository.getWorkDay(today) } returns flowOf(workDay)

        When("das Büro während der Pause verlassen wird") {
            val success = autoClockOut()

            Then("wird die aktive Pause beendet und gelöscht") {
                verify { pausePreferences.clearPause() }
            }

            Then("wird das Ausstempeln mit true bestätigt") {
                success.shouldBeTrue()
            }

            Then("wird kein bereits abgeschlossener Zeitblock erneut überschrieben") {
                coVerify(exactly = 0) { workDayRepository.saveTimeBlock(any()) }
            }
        }
    }

    Given("ein Tag mit bereits abgeschlossenen Blöcken und ohne aktive Pause") {
        val workDayRepository = mockk<WorkDayRepository>(relaxed = true)
        val pausePreferences = mockk<PausePreferences>(relaxed = true)
        val autoClockOut = AutoClockOutUseCase(workDayRepository, pausePreferences)

        val finishedBlock = TimeBlock(
            id = 15L,
            workDayId = 10L,
            startTime = LocalTime.of(8, 0),
            endTime = LocalTime.of(16, 30),
            location = WorkLocation.OFFICE
        )
        val finishedDay = WorkDay(
            id = 10L,
            date = today,
            location = WorkLocation.OFFICE,
            dayType = DayType.WORK,
            timeBlocks = listOf(finishedBlock)
        )

        every { pausePreferences.isPaused } returns false
        coEvery { workDayRepository.getWorkDay(today) } returns flowOf(finishedDay)

        When("ein Geofence-Exit oder WLAN-Disconnect ausgelöst wird") {
            val success = autoClockOut()

            Then("wird kein Zeiterfassungsblock verändert") {
                coVerify(exactly = 0) { workDayRepository.saveTimeBlock(any()) }
            }

            Then("wird false zurückgegeben, da keine Zeiterfassung aktiv war") {
                success.shouldBeFalse()
            }
        }
    }

    Given("ein Tag ohne jeglichen WorkDay-Eintrag") {
        val workDayRepository = mockk<WorkDayRepository>(relaxed = true)
        val pausePreferences = mockk<PausePreferences>(relaxed = true)
        val autoClockOut = AutoClockOutUseCase(workDayRepository, pausePreferences)

        every { pausePreferences.isPaused } returns false
        coEvery { workDayRepository.getWorkDay(today) } returns flowOf(null)

        When("AutoClockOut ausgeführt wird") {
            val success = autoClockOut()

            Then("gibt der UseCase false zurück") {
                success.shouldBeFalse()
            }
        }
    }
})
