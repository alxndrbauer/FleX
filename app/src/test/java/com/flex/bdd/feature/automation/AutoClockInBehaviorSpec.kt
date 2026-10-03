package com.flex.bdd.feature.automation

import com.flex.data.local.GeofencePreferences
import com.flex.domain.model.DayType
import com.flex.domain.model.TimeBlock
import com.flex.domain.model.WorkDay
import com.flex.domain.model.WorkLocation
import com.flex.domain.repository.WorkDayRepository
import com.flex.domain.usecase.AutoClockInUseCase
import io.kotest.core.spec.style.BehaviorSpec
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

class AutoClockInBehaviorSpec : BehaviorSpec({

    val today = LocalDate.now()

    Given("ein Arbeitstag ohne bisherige Zeiterfassung") {
        val workDayRepository = mockk<WorkDayRepository>(relaxed = true)
        val geofencePreferences = mockk<GeofencePreferences>(relaxed = true)
        val autoClockIn = AutoClockInUseCase(workDayRepository, geofencePreferences)

        coEvery { workDayRepository.getWorkDay(today) } returns flowOf(null)
        coEvery { workDayRepository.saveWorkDay(any()) } returns 101L
        coEvery { workDayRepository.saveTimeBlock(any()) } returns 201L

        When("der Nutzer das Büro betritt (Geofence-Enter oder WLAN-Verbindung)") {
            val generatedBlockId = autoClockIn()

            Then("wird automatisch ein neuer WorkDay für heute mit Standort OFFICE angelegt") {
                val daySlot = slot<WorkDay>()
                coVerify { workDayRepository.saveWorkDay(capture(daySlot)) }
                daySlot.captured.date shouldBe today
                daySlot.captured.location shouldBe WorkLocation.OFFICE
                daySlot.captured.dayType shouldBe DayType.WORK
            }

            Then("wird ein offener Zeitblock im Büro mit Startzeit erzeugt") {
                val blockSlot = slot<TimeBlock>()
                coVerify { workDayRepository.saveTimeBlock(capture(blockSlot)) }
                blockSlot.captured.workDayId shouldBe 101L
                blockSlot.captured.location shouldBe WorkLocation.OFFICE
                blockSlot.captured.startTime.shouldNotBeNull()
                blockSlot.captured.endTime.shouldBeNull()
            }

            Then("wird die erzeugte Block-ID für ein eventuelles Undo in den GeofencePreferences gemerkt") {
                generatedBlockId shouldBe 201L
                verify { geofencePreferences.lastAutoTimeBlockId = 201L }
            }
        }
    }

    Given("ein bestehender Arbeitstag mit bereits beendeten Zeitblöcken") {
        val workDayRepository = mockk<WorkDayRepository>(relaxed = true)
        val geofencePreferences = mockk<GeofencePreferences>(relaxed = true)
        val autoClockIn = AutoClockInUseCase(workDayRepository, geofencePreferences)

        val morningBlock = TimeBlock(
            id = 50L,
            workDayId = 100L,
            startTime = LocalTime.of(8, 0),
            endTime = LocalTime.of(12, 0),
            location = WorkLocation.OFFICE
        )
        val existingWorkDay = WorkDay(
            id = 100L,
            date = today,
            location = WorkLocation.OFFICE,
            dayType = DayType.WORK,
            timeBlocks = listOf(morningBlock)
        )

        coEvery { workDayRepository.getWorkDay(today) } returns flowOf(existingWorkDay)
        coEvery { workDayRepository.saveTimeBlock(any()) } returns 202L

        When("der Nutzer nach der Mittagspause erneut das Büro betritt") {
            val generatedBlockId = autoClockIn()

            Then("wird kein neuer WorkDay angelegt, sondern der bestehende Tag weitergeführt") {
                coVerify(exactly = 0) { workDayRepository.saveWorkDay(any()) }
            }

            Then("wird ein neuer Block mit der ID des bestehenden WorkDays gespeichert") {
                val blockSlot = slot<TimeBlock>()
                coVerify { workDayRepository.saveTimeBlock(capture(blockSlot)) }
                blockSlot.captured.workDayId shouldBe 100L
                blockSlot.captured.location shouldBe WorkLocation.OFFICE
                blockSlot.captured.endTime.shouldBeNull()
            }

            Then("wird die neue Block-ID 202L gemerkt und zurückgegeben") {
                generatedBlockId shouldBe 202L
                verify { geofencePreferences.lastAutoTimeBlockId = 202L }
            }
        }
    }

    Given("ein bereits laufender Zeitblock am heutigen Tag") {
        val workDayRepository = mockk<WorkDayRepository>(relaxed = true)
        val geofencePreferences = mockk<GeofencePreferences>(relaxed = true)
        val autoClockIn = AutoClockInUseCase(workDayRepository, geofencePreferences)

        val runningBlock = TimeBlock(
            id = 75L,
            workDayId = 100L,
            startTime = LocalTime.of(8, 30),
            endTime = null, // Block läuft noch
            location = WorkLocation.OFFICE
        )
        val activeWorkDay = WorkDay(
            id = 100L,
            date = today,
            location = WorkLocation.OFFICE,
            dayType = DayType.WORK,
            timeBlocks = listOf(runningBlock)
        )

        coEvery { workDayRepository.getWorkDay(today) } returns flowOf(activeWorkDay)

        When("ein erneutes Geofence- oder WLAN-Event eintrifft") {
            val result = autoClockIn()

            Then("wird nicht doppelt eingestempelt und null zurückgegeben") {
                result.shouldBeNull()
            }

            Then("wird kein weiterer Zeitblock in der Datenbank angelegt") {
                coVerify(exactly = 0) { workDayRepository.saveTimeBlock(any()) }
                coVerify(exactly = 0) { workDayRepository.saveWorkDay(any()) }
            }

            Then("werden die Undo-Präferenzen nicht überschrieben") {
                verify(exactly = 0) { geofencePreferences.lastAutoTimeBlockId = any() }
            }
        }
    }
})
