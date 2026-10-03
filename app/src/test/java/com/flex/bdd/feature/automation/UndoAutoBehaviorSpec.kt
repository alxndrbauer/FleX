package com.flex.bdd.feature.automation

import com.flex.data.local.GeofencePreferences
import com.flex.domain.model.TimeBlock
import com.flex.domain.model.WorkLocation
import com.flex.domain.repository.WorkDayRepository
import com.flex.domain.usecase.UndoAutoClockUseCase
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.LocalTime

class UndoAutoBehaviorSpec : BehaviorSpec({

    Given("ein automatisch erfasster Zeitblock nach Büro-Betreten") {
        val workDayRepository = mockk<WorkDayRepository>(relaxed = true)
        val geofencePreferences = mockk<GeofencePreferences>(relaxed = true)
        val undoAutoClock = UndoAutoClockUseCase(workDayRepository, geofencePreferences)

        val autoCreatedBlock = TimeBlock(
            id = 55L,
            workDayId = 10L,
            startTime = LocalTime.of(8, 0),
            endTime = null,
            location = WorkLocation.OFFICE
        )

        every { geofencePreferences.lastAutoTimeBlockId } returns 55L
        coEvery { workDayRepository.getTimeBlockById(55L) } returns autoCreatedBlock
        coEvery { workDayRepository.deleteTimeBlock(autoCreatedBlock) } returns Unit

        When("der Nutzer die Benachrichtigung 'Rückgängig machen' antippt") {
            undoAutoClock()

            Then("wird der fälschlich erfasste Zeitblock aus der Datenbank gelöscht") {
                coVerify { workDayRepository.deleteTimeBlock(autoCreatedBlock) }
            }

            Then("wird die gespeicherte Block-ID in GeofencePreferences auf -1 zurückgesetzt") {
                verify { geofencePreferences.lastAutoTimeBlockId = -1L }
            }
        }
    }

    Given("kein vorheriges automatisches Einstempeln vorhanden") {
        val workDayRepository = mockk<WorkDayRepository>(relaxed = true)
        val geofencePreferences = mockk<GeofencePreferences>(relaxed = true)
        val undoAutoClock = UndoAutoClockUseCase(workDayRepository, geofencePreferences)

        every { geofencePreferences.lastAutoTimeBlockId } returns -1L

        When("die Undo-Aktion aufgerufen wird") {
            undoAutoClock()

            Then("wird kein Block gesucht oder gelöscht") {
                coVerify(exactly = 0) { workDayRepository.getTimeBlockById(any()) }
                coVerify(exactly = 0) { workDayRepository.deleteTimeBlock(any()) }
            }
        }
    }

    Given("ein hinterlegter Block, der in der Datenbank nicht mehr existiert") {
        val workDayRepository = mockk<WorkDayRepository>(relaxed = true)
        val geofencePreferences = mockk<GeofencePreferences>(relaxed = true)
        val undoAutoClock = UndoAutoClockUseCase(workDayRepository, geofencePreferences)

        every { geofencePreferences.lastAutoTimeBlockId } returns 99L
        coEvery { workDayRepository.getTimeBlockById(99L) } returns null

        When("die Undo-Aktion ausgeführt wird") {
            undoAutoClock()

            Then("wird kein Löschvorgang ausgelöst") {
                coVerify(exactly = 0) { workDayRepository.deleteTimeBlock(any()) }
            }

            Then("bleibt die ID in den Präferenzen unberührt") {
                verify(exactly = 0) { geofencePreferences.lastAutoTimeBlockId = -1L }
            }
        }
    }
})
