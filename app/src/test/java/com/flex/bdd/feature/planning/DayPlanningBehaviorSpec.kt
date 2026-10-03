package com.flex.bdd.feature.planning

import com.flex.domain.model.DayType
import com.flex.domain.model.TimeBlock
import com.flex.domain.model.WorkDay
import com.flex.domain.model.WorkLocation
import com.flex.domain.repository.WorkDayRepository
import com.flex.domain.usecase.SaveWorkDayUseCase
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import java.time.LocalDate
import java.time.LocalTime

class DayPlanningBehaviorSpec : BehaviorSpec({

    Given("die Planung eines zukünftigen Tages als Büro") {
        val workDayRepository = mockk<WorkDayRepository>(relaxed = true)
        val saveWorkDayUseCase = SaveWorkDayUseCase(workDayRepository)
        coEvery { workDayRepository.saveWorkDay(any()) } returns 101L

        When("ein Tag als Büro geplant wird (isPlanned = true, location = OFFICE)") {
            val date = LocalDate.of(2026, 11, 10)
            val startTime = LocalTime.of(8, 0)
            val endTime = LocalTime.of(16, 30)

            val officeDay = WorkDay(
                id = 0L,
                date = date,
                location = WorkLocation.OFFICE,
                dayType = DayType.WORK,
                isPlanned = true,
                timeBlocks = listOf(
                    TimeBlock(
                        workDayId = 0L,
                        startTime = startTime,
                        endTime = endTime,
                        location = WorkLocation.OFFICE,
                        isDuration = true
                    )
                )
            )

            val savedId = saveWorkDayUseCase(officeDay)

            Then("wird der Tag erfolgreich mit isPlanned = true und Standort OFFICE gespeichert") {
                savedId shouldBe 101L

                val slot = slot<WorkDay>()
                coVerify { workDayRepository.saveWorkDay(capture(slot)) }
                slot.captured.date shouldBe date
                slot.captured.isPlanned.shouldBeTrue()
                slot.captured.location shouldBe WorkLocation.OFFICE
                slot.captured.dayType shouldBe DayType.WORK
                slot.captured.timeBlocks.first().location shouldBe WorkLocation.OFFICE
                slot.captured.timeBlocks.first().isDuration.shouldBeTrue()
            }
        }
    }

    Given("die Planung eines zukünftigen Tages als Home-Office") {
        val workDayRepository = mockk<WorkDayRepository>(relaxed = true)
        val saveWorkDayUseCase = SaveWorkDayUseCase(workDayRepository)
        coEvery { workDayRepository.saveWorkDay(any()) } returns 102L

        When("ein Tag als Home-Office geplant wird (isPlanned = true, location = HOME_OFFICE)") {
            val date = LocalDate.of(2026, 11, 11)
            val startTime = LocalTime.of(8, 30)
            val endTime = LocalTime.of(17, 0)

            val homeOfficeDay = WorkDay(
                id = 0L,
                date = date,
                location = WorkLocation.HOME_OFFICE,
                dayType = DayType.WORK,
                isPlanned = true,
                timeBlocks = listOf(
                    TimeBlock(
                        workDayId = 0L,
                        startTime = startTime,
                        endTime = endTime,
                        location = WorkLocation.HOME_OFFICE,
                        isDuration = true
                    )
                )
            )

            val savedId = saveWorkDayUseCase(homeOfficeDay)

            Then("wird der Tag als geplanter Home-Office-Tag gespeichert") {
                savedId shouldBe 102L

                val slot = slot<WorkDay>()
                coVerify { workDayRepository.saveWorkDay(capture(slot)) }
                slot.captured.date shouldBe date
                slot.captured.isPlanned.shouldBeTrue()
                slot.captured.location shouldBe WorkLocation.HOME_OFFICE
                slot.captured.dayType shouldBe DayType.WORK
            }
        }
    }

    Given("die Planung eines Tages als Urlaub") {
        val workDayRepository = mockk<WorkDayRepository>(relaxed = true)
        val saveWorkDayUseCase = SaveWorkDayUseCase(workDayRepository)
        coEvery { workDayRepository.saveWorkDay(any()) } returns 103L

        When("ein Tag als Urlaub geplant wird (dayType = VACATION, isPlanned = true)") {
            val date = LocalDate.of(2026, 11, 12)

            val vacationDay = WorkDay(
                id = 0L,
                date = date,
                location = WorkLocation.HOME_OFFICE,
                dayType = DayType.VACATION,
                isPlanned = true,
                timeBlocks = emptyList()
            )

            val savedId = saveWorkDayUseCase(vacationDay)

            Then("wird der Tag mit DayType VACATION und isPlanned = true hinterlegt") {
                savedId shouldBe 103L

                val slot = slot<WorkDay>()
                coVerify { workDayRepository.saveWorkDay(capture(slot)) }
                slot.captured.date shouldBe date
                slot.captured.isPlanned.shouldBeTrue()
                slot.captured.dayType shouldBe DayType.VACATION
                slot.captured.timeBlocks shouldBe emptyList()
            }
        }
    }

    Given("ein bereits geplanter Tag, dessen Planung entfernt oder gelöscht werden soll") {
        val plannedDate = LocalDate.of(2026, 11, 13)
        val plannedWorkDay = WorkDay(
            id = 42L,
            date = plannedDate,
            location = WorkLocation.OFFICE,
            dayType = DayType.WORK,
            isPlanned = true,
            timeBlocks = listOf(
                TimeBlock(
                    id = 10L,
                    workDayId = 42L,
                    startTime = LocalTime.of(8, 0),
                    endTime = LocalTime.of(16, 30),
                    isDuration = true,
                    location = WorkLocation.OFFICE
                )
            )
        )

        When("der Plan für den Tag entfernt wird") {
            val workDayRepository = mockk<WorkDayRepository>(relaxed = true)
            coEvery { workDayRepository.deleteWorkDay(any()) } returns Unit

            workDayRepository.deleteWorkDay(plannedWorkDay)

            Then("wird der geplante Tag aus dem Repository gelöscht") {
                val deleteSlot = slot<WorkDay>()
                coVerify { workDayRepository.deleteWorkDay(capture(deleteSlot)) }
                deleteSlot.captured.id shouldBe 42L
                deleteSlot.captured.date shouldBe plannedDate
                deleteSlot.captured.isPlanned.shouldBeTrue()
            }
        }

        When("ein zuvor geplanter Tag durch eine neutrale Buchung überschrieben oder zurückgesetzt wird") {
            val workDayRepository = mockk<WorkDayRepository>(relaxed = true)
            val resetDay = plannedWorkDay.copy(
                isPlanned = false,
                dayType = DayType.FLEX_DAY,
                timeBlocks = emptyList()
            )

            coEvery { workDayRepository.saveWorkDay(resetDay) } returns 42L

            val saveWorkDayUseCase = SaveWorkDayUseCase(workDayRepository)
            val updatedId = saveWorkDayUseCase(resetDay)

            Then("ist die Planung aufgehoben und der Tag nicht mehr als geplant markiert") {
                updatedId shouldBe 42L

                val slot = slot<WorkDay>()
                coVerify { workDayRepository.saveWorkDay(capture(slot)) }
                slot.captured.isPlanned.shouldBeFalse()
                slot.captured.dayType shouldBe DayType.FLEX_DAY
            }
        }
    }
})
