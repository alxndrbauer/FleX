package com.flex.bdd.feature.planning

import com.flex.domain.events.DataChangeEvent
import com.flex.domain.events.DataChangeEventBus
import com.flex.domain.model.DayType
import com.flex.domain.model.WorkDay
import com.flex.domain.model.WorkLocation
import com.flex.domain.repository.WorkDayRepository
import com.flex.domain.usecase.AutoBookPlannedDaysUseCase
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.time.LocalDate

class AutoBookBehaviorSpec : BehaviorSpec({

    Given("geplante Arbeitstage im Kalender und anstehende Bestätigung bis zu einem Stichtag") {
        val workDayRepository = mockk<WorkDayRepository>(relaxed = true)
        val dataChangeEventBus = mockk<DataChangeEventBus>(relaxed = true)
        val autoBookPlannedDays = AutoBookPlannedDaysUseCase(workDayRepository, dataChangeEventBus)

        val cutoffDate = LocalDate.of(2026, 10, 15)

        When("die automatische Buchung ausgeführt wird und geplante Tage vorhanden sind") {
            coEvery { workDayRepository.confirmPlannedDaysUpTo(cutoffDate) } returns 3

            val count = autoBookPlannedDays(cutoffDate)

            Then("wird die exakte Anzahl der bestätigten Tage zurückgegeben") {
                count shouldBe 3
            }

            Then("wird confirmPlannedDaysUpTo mit dem korrekten Stichtag am Repository aufgerufen") {
                coVerify { workDayRepository.confirmPlannedDaysUpTo(cutoffDate) }
            }

            Then("wird ein DataChangeEvent.WorkDayChanged emittiert") {
                coVerify(exactly = 1) { dataChangeEventBus.emit(DataChangeEvent.WorkDayChanged) }
            }
        }
    }

    Given("keine unbestätigten Tage bis zum Stichtag (count = 0)") {
        val workDayRepository = mockk<WorkDayRepository>(relaxed = true)
        val dataChangeEventBus = mockk<DataChangeEventBus>(relaxed = true)
        val autoBookPlannedDays = AutoBookPlannedDaysUseCase(workDayRepository, dataChangeEventBus)

        val emptyCutoff = LocalDate.of(2026, 10, 5)

        When("die automatische Buchung aufgerufen wird") {
            coEvery { workDayRepository.confirmPlannedDaysUpTo(emptyCutoff) } returns 0

            val count = autoBookPlannedDays(emptyCutoff)

            Then("wird 0 zurückgegeben") {
                count shouldBe 0
            }

            Then("wird kein DataChangeEvent ausgelöst") {
                coVerify(exactly = 0) { dataChangeEventBus.emit(any()) }
            }
        }
    }

    Given("ein konkreter Kalenderzustand mit Tagen vor, am und nach dem Stichtag") {
        val cutoffDate = LocalDate.of(2026, 10, 15)

        // 1. Geplanter Tag vor dem Stichtag
        val plannedBefore = WorkDay(
            id = 1L,
            date = LocalDate.of(2026, 10, 10),
            location = WorkLocation.OFFICE,
            dayType = DayType.WORK,
            isPlanned = true
        )
        // 2. Geplanter Tag am Stichtag
        val plannedOnCutoff = WorkDay(
            id = 2L,
            date = cutoffDate,
            location = WorkLocation.HOME_OFFICE,
            dayType = DayType.WORK,
            isPlanned = true
        )
        // 3. Geplanter Tag nach dem Stichtag
        val plannedAfterCutoff = WorkDay(
            id = 3L,
            date = LocalDate.of(2026, 10, 16),
            location = WorkLocation.OFFICE,
            dayType = DayType.WORK,
            isPlanned = true
        )
        // 4. Bereits bestätigter Tag vor dem Stichtag
        val alreadyConfirmed = WorkDay(
            id = 4L,
            date = LocalDate.of(2026, 10, 8),
            location = WorkLocation.OFFICE,
            dayType = DayType.WORK,
            isPlanned = false
        )

        val daysList = mutableListOf(plannedBefore, plannedOnCutoff, plannedAfterCutoff, alreadyConfirmed)

        val workDayRepository = mockk<WorkDayRepository>()
        val dataChangeEventBus = mockk<DataChangeEventBus>(relaxed = true)

        coEvery { workDayRepository.confirmPlannedDaysUpTo(cutoffDate) } answers {
            var updatedCount = 0
            for (i in daysList.indices) {
                val day = daysList[i]
                if (day.isPlanned && !day.date.isAfter(cutoffDate)) {
                    daysList[i] = day.copy(isPlanned = false)
                    updatedCount++
                }
            }
            updatedCount
        }

        val autoBookPlannedDays = AutoBookPlannedDaysUseCase(workDayRepository, dataChangeEventBus)

        When("die automatische Buchung ausgeführt wird") {
            val confirmedCount = autoBookPlannedDays(cutoffDate)

            Then("werden genau die geplanten Tage bis einschließlich Stichtag bestätigt") {
                confirmedCount shouldBe 2

                // Tag 1 (10.10.) wurde bestätigt -> isPlanned = false
                val day1 = daysList.first { it.id == 1L }
                day1.isPlanned.shouldBeFalse()

                // Tag 2 (15.10., Stichtag) wurde bestätigt -> isPlanned = false
                val day2 = daysList.first { it.id == 2L }
                day2.isPlanned.shouldBeFalse()
            }

            Then("bleiben Tage nach dem Stichtag unverändert als geplant markiert (isPlanned = true)") {
                // Tag 3 (16.10., nach Stichtag) -> bleibt isPlanned = true
                val day3 = daysList.first { it.id == 3L }
                day3.isPlanned.shouldBeTrue()
            }

            Then("bleiben bereits bestätigte Tage unberührt (isPlanned = false)") {
                // Tag 4 (08.10., war bereits bestätigt) -> bleibt isPlanned = false
                val day4 = daysList.first { it.id == 4L }
                day4.isPlanned.shouldBeFalse()
            }

            Then("wird das DataChangeEvent erfolgreich emittiert") {
                coVerify(exactly = 1) { dataChangeEventBus.emit(DataChangeEvent.WorkDayChanged) }
            }
        }
    }
})
