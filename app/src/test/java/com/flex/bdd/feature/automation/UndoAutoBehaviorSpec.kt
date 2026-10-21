package com.flex.bdd.feature.automation

import android.content.Context
import android.content.SharedPreferences
import com.flex.data.local.GeofencePreferences
import com.flex.domain.model.DayType
import com.flex.domain.model.TimeBlock
import com.flex.domain.model.WorkDay
import com.flex.domain.model.WorkLocation
import com.flex.domain.repository.WorkDayRepository
import com.flex.domain.usecase.AutoClockInUseCase
import com.flex.domain.usecase.UndoAutoClockUseCase
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.flowOf
import java.time.LocalDate
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

    Given("ein Auto-Clock-in auf einem geplanten Tag") {
        val storedValues = mutableMapOf<String, Any>()
        val workDayRepository = mockk<WorkDayRepository>(relaxed = true)
        val preferencesBeforeClockIn = storedGeofencePreferences(storedValues)
        val today = LocalDate.now()
        val plannedBlock = TimeBlock(
            id = 50L,
            workDayId = 10L,
            startTime = LocalTime.of(8, 15),
            endTime = LocalTime.of(16, 15),
            isDuration = true,
            location = WorkLocation.HOME_OFFICE
        )
        val plannedDay = WorkDay(
            id = 10L,
            date = today,
            location = WorkLocation.HOME_OFFICE,
            dayType = DayType.VACATION,
            isPlanned = true,
            timeBlocks = listOf(plannedBlock)
        )
        val autoBlock = TimeBlock(
            id = 55L,
            workDayId = 10L,
            startTime = LocalTime.of(9, 0),
            location = WorkLocation.OFFICE
        )
        val autoClockInDay = plannedDay.copy(isPlanned = false, timeBlocks = listOf(autoBlock))
        coEvery { workDayRepository.getWorkDay(today) } returnsMany listOf(
            flowOf(plannedDay),
            flowOf(autoClockInDay)
        )
        coEvery { workDayRepository.saveTimeBlock(any()) } returnsMany listOf(55L, 50L)
        coEvery { workDayRepository.getTimeBlockById(55L) } returns autoBlock

        When("der automatische Eintrag rückgängig gemacht wird, auch nach einem Neustart") {
            val generatedBlockId = AutoClockInUseCase(
                workDayRepository,
                preferencesBeforeClockIn
            )()
            val preferencesAfterRestart = storedGeofencePreferences(storedValues)
            UndoAutoClockUseCase(workDayRepository, preferencesAfterRestart)()

            Then("wird der erzeugte Eintrag gelöscht und die Planung exakt wiederhergestellt") {
                generatedBlockId shouldBe 55L
                coVerify { workDayRepository.deleteTimeBlock(autoBlock) }
                coVerify { workDayRepository.saveTimeBlock(plannedBlock) }
                coVerify {
                    workDayRepository.saveWorkDay(match {
                        it.id == plannedDay.id &&
                            it.isPlanned &&
                            it.dayType == DayType.VACATION &&
                            it.location == WorkLocation.HOME_OFFICE
                    })
                }
            }
        }
    }
})

private fun storedGeofencePreferences(values: MutableMap<String, Any>): GeofencePreferences {
    val context = mockk<Context>()
    val sharedPreferences = mockk<SharedPreferences>()
    val editor = mockk<SharedPreferences.Editor>(relaxed = true)

    every {
        context.getSharedPreferences("geofence_prefs", Context.MODE_PRIVATE)
    } returns sharedPreferences
    every { sharedPreferences.getLong(any(), any()) } answers {
        values[firstArg()] as? Long ?: secondArg()
    }
    every { sharedPreferences.getString(any(), null) } answers {
        values[firstArg()] as? String ?: secondArg()
    }
    every { sharedPreferences.edit() } returns editor
    every { editor.putLong(any(), any()) } answers {
        values[firstArg()] = secondArg<Long>()
        editor
    }
    every { editor.putString(any(), any()) } answers {
        values[firstArg()] = secondArg<String>()
        editor
    }
    every { editor.putString(any(), null) } answers {
        values.remove(firstArg<String>())
        editor
    }
    every { editor.remove(any()) } answers {
        values.remove(firstArg<String>())
        editor
    }

    return GeofencePreferences(context)
}
