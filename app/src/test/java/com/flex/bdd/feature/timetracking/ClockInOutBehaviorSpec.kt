package com.flex.bdd.feature.timetracking

import com.flex.data.local.PausePreferences
import com.flex.domain.model.DayType
import com.flex.domain.model.TimeBlock
import com.flex.domain.model.WorkDay
import com.flex.domain.model.WorkLocation
import com.flex.domain.repository.WorkDayRepository
import com.flex.domain.usecase.AutoClockOutUseCase
import com.flex.domain.usecase.ClockInUseCase
import com.flex.domain.usecase.PauseWorkUseCase
import com.flex.domain.usecase.SwitchLocationUseCase
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
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

class ClockInOutBehaviorSpec : BehaviorSpec({

    Given("erstmaliges Einstempeln am Tag") {
        val workDayRepository = mockk<WorkDayRepository>(relaxed = true)
        val pausePreferences = mockk<PausePreferences>(relaxed = true)
        val clockInUseCase = ClockInUseCase(workDayRepository, pausePreferences)
        val today = LocalDate.now()

        coEvery { workDayRepository.getWorkDay(today) } returns flowOf(null)
        coEvery { workDayRepository.saveWorkDay(any()) } returns 101L
        coEvery { workDayRepository.saveTimeBlock(any()) } returns 201L

        When("der Nutzer sich im Büro einstempelt") {
            val success = clockInUseCase(WorkLocation.OFFICE)

            Then("wird das Einstempeln erfolgreich bestätigt") {
                success.shouldBeTrue()
            }

            Then("wird ein neuer WorkDay angelegt") {
                val workDaySlot = slot<WorkDay>()
                coVerify { workDayRepository.saveWorkDay(capture(workDaySlot)) }
                workDaySlot.captured.date shouldBe today
                workDaySlot.captured.location shouldBe WorkLocation.OFFICE
                workDaySlot.captured.dayType shouldBe DayType.WORK
            }

            Then("wird ein offener TimeBlock mit aktueller Startzeit und Standort OFFICE gespeichert") {
                val blockSlot = slot<TimeBlock>()
                coVerify { workDayRepository.saveTimeBlock(capture(blockSlot)) }
                blockSlot.captured.workDayId shouldBe 101L
                blockSlot.captured.location shouldBe WorkLocation.OFFICE
                blockSlot.captured.endTime.shouldBeNull()
                blockSlot.captured.startTime.shouldNotBeNull()
            }

            Then("wird eine eventuell aktive Pause gelöscht") {
                verify { pausePreferences.clearPause() }
            }
        }
    }

    Given("ein laufender Zeitblock beim Ausstempeln") {
        val workDayRepository = mockk<WorkDayRepository>(relaxed = true)
        val pausePreferences = mockk<PausePreferences>(relaxed = true)
        val autoClockOutUseCase = AutoClockOutUseCase(workDayRepository, pausePreferences)
        val today = LocalDate.now()

        val runningBlock = TimeBlock(
            id = 15L,
            workDayId = 101L,
            startTime = LocalTime.of(8, 30),
            endTime = null,
            location = WorkLocation.OFFICE
        )
        val workDay = WorkDay(
            id = 101L,
            date = today,
            location = WorkLocation.OFFICE,
            dayType = DayType.WORK,
            timeBlocks = listOf(runningBlock)
        )

        coEvery { workDayRepository.getWorkDay(today) } returns flowOf(workDay)
        every { pausePreferences.isPaused } returns false

        When("der Nutzer sich ausstempelt") {
            val clockedOut = autoClockOutUseCase()

            Then("wird das Ausstempeln erfolgreich durchgeführt") {
                clockedOut.shouldBeTrue()
            }

            Then("wird der aktive Block beendet und die Endzeit gesetzt") {
                val blockSlot = slot<TimeBlock>()
                coVerify { workDayRepository.saveTimeBlock(capture(blockSlot)) }
                blockSlot.captured.id shouldBe 15L
                blockSlot.captured.startTime shouldBe LocalTime.of(8, 30)
                blockSlot.captured.endTime.shouldNotBeNull()
            }
        }
    }

    Given("mehrfaches Ein- und Ausstempeln am selben Tag") {
        val workDayRepository = mockk<WorkDayRepository>(relaxed = true)
        val pausePreferences = mockk<PausePreferences>(relaxed = true)
        val clockInUseCase = ClockInUseCase(workDayRepository, pausePreferences)
        val today = LocalDate.now()

        val completedBlock1 = TimeBlock(
            id = 1L,
            workDayId = 50L,
            startTime = LocalTime.of(8, 0),
            endTime = LocalTime.of(12, 0),
            location = WorkLocation.OFFICE
        )
        val completedBlock2 = TimeBlock(
            id = 2L,
            workDayId = 50L,
            startTime = LocalTime.of(12, 30),
            endTime = LocalTime.of(15, 0),
            location = WorkLocation.OFFICE
        )
        val existingWorkDay = WorkDay(
            id = 50L,
            date = today,
            location = WorkLocation.OFFICE,
            dayType = DayType.WORK,
            timeBlocks = listOf(completedBlock1, completedBlock2)
        )

        coEvery { workDayRepository.getWorkDay(today) } returns flowOf(existingWorkDay)

        When("zum 3. Mal eingestempelt wird") {
            val result = clockInUseCase(WorkLocation.HOME_OFFICE)

            Then("wird die Aktion erfolgreich bestätigt") {
                result.shouldBeTrue()
            }

            Then("bleiben die alten Blöcke erhalten und ein 3. offener Block wird hinzugefügt") {
                val blockSlot = slot<TimeBlock>()
                coVerify { workDayRepository.saveTimeBlock(capture(blockSlot)) }
                blockSlot.captured.workDayId shouldBe 50L
                blockSlot.captured.location shouldBe WorkLocation.HOME_OFFICE
                blockSlot.captured.endTime.shouldBeNull()
            }

            Then("wird der bestehende WorkDay auf die neue Location aktualisiert") {
                val workDaySlot = slot<WorkDay>()
                coVerify { workDayRepository.saveWorkDay(capture(workDaySlot)) }
                workDaySlot.captured.id shouldBe 50L
                workDaySlot.captured.location shouldBe WorkLocation.HOME_OFFICE
            }
        }

        When("bereits ein Block aktiv läuft") {
            val runningBlock = TimeBlock(
                id = 3L,
                workDayId = 50L,
                startTime = LocalTime.of(15, 30),
                endTime = null,
                location = WorkLocation.HOME_OFFICE
            )
            val dayWithRunningBlock = existingWorkDay.copy(
                timeBlocks = listOf(completedBlock1, completedBlock2, runningBlock)
            )
            coEvery { workDayRepository.getWorkDay(today) } returns flowOf(dayWithRunningBlock)

            val result = clockInUseCase()

            Then("wird das erneute Einstempeln abgelehnt") {
                result.shouldBeFalse()
            }
        }
    }

    Given("pausieren der Arbeit während eines laufenden Blocks") {
        val workDayRepository = mockk<WorkDayRepository>(relaxed = true)
        val pausePreferences = mockk<PausePreferences>(relaxed = true)
        val pauseWorkUseCase = PauseWorkUseCase(workDayRepository, pausePreferences)
        val today = LocalDate.now()

        val runningBlock = TimeBlock(
            id = 77L,
            workDayId = 12L,
            startTime = LocalTime.of(8, 0),
            endTime = null,
            location = WorkLocation.OFFICE
        )
        val workDay = WorkDay(
            id = 12L,
            date = today,
            location = WorkLocation.OFFICE,
            dayType = DayType.WORK,
            timeBlocks = listOf(runningBlock)
        )

        coEvery { workDayRepository.getWorkDay(today) } returns flowOf(workDay)

        When("die Arbeit pausiert wird") {
            val paused = pauseWorkUseCase()

            Then("wird die Pause erfolgreich eingeleitet") {
                paused.shouldBeTrue()
            }

            Then("wird der aktive Block mit aktueller Endzeit beendet") {
                val blockSlot = slot<TimeBlock>()
                coVerify { workDayRepository.saveTimeBlock(capture(blockSlot)) }
                blockSlot.captured.id shouldBe 77L
                blockSlot.captured.endTime.shouldNotBeNull()
            }

            Then("speichert PausePreferences den aktuellen Zeitstempel") {
                verify { pausePreferences.pauseStartTime = any() }
            }
        }
    }

    Given("standortwechsel während eines laufenden Blocks") {
        val workDayRepository = mockk<WorkDayRepository>(relaxed = true)
        val switchLocationUseCase = SwitchLocationUseCase(workDayRepository)
        val today = LocalDate.now()

        val runningBlock = TimeBlock(
            id = 88L,
            workDayId = 25L,
            startTime = LocalTime.of(8, 0),
            endTime = null,
            location = WorkLocation.OFFICE
        )
        val workDay = WorkDay(
            id = 25L,
            date = today,
            location = WorkLocation.OFFICE,
            dayType = DayType.WORK,
            timeBlocks = listOf(runningBlock)
        )

        coEvery { workDayRepository.getWorkDay(today) } returns flowOf(workDay)

        When("der Standort auf HOME_OFFICE gewechselt wird") {
            val switched = switchLocationUseCase(WorkLocation.HOME_OFFICE)

            Then("wird der Wechsel erfolgreich bestätigt") {
                switched.shouldBeTrue()
            }

            Then("wird der laufende Block mit der neuen Location aktualisiert") {
                val blockSlot = slot<TimeBlock>()
                coVerify { workDayRepository.saveTimeBlock(capture(blockSlot)) }
                blockSlot.captured.id shouldBe 88L
                blockSlot.captured.location shouldBe WorkLocation.HOME_OFFICE
                blockSlot.captured.endTime.shouldBeNull()
            }

            Then("wird auch der Standort des WorkDays angepasst") {
                val daySlot = slot<WorkDay>()
                coVerify { workDayRepository.saveWorkDay(capture(daySlot)) }
                daySlot.captured.id shouldBe 25L
                daySlot.captured.location shouldBe WorkLocation.HOME_OFFICE
            }
        }
    }
})
