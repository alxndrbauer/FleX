package com.flex.integration

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.flex.calendar.CalendarEventMapper
import com.flex.calendar.CalendarSyncService
import com.flex.data.local.FlexDatabase
import com.flex.data.repository.SettingsRepositoryImpl
import com.flex.data.repository.WorkDayRepositoryImpl
import com.flex.domain.model.DayType
import com.flex.domain.model.Settings
import com.flex.domain.model.TimeBlock
import com.flex.domain.model.WorkDay
import com.flex.domain.model.WorkLocation
import com.flex.domain.usecase.CalculateDayWorkTimeUseCase
import com.flex.domain.usecase.CalculateFlextimeUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

/**
 * Real-time flextime update bug integration test.
 *
 * This test reproduces the exact scenario where:
 * 1. Database starts empty
 * 2. User views HomeScreen (with initial flextime = 0)
 * 3. User adds a work day via MonthScreen
 * 4. HomeScreen should immediately show updated flextime
 *
 * BUG: HomeScreen does NOT update when new work days are added while app is running.
 * This test helps identify WHY the Flow is not emitting updates.
 */
@RunWith(AndroidJUnit4::class)
class FlextimeRealTimeUpdateTest {

    private lateinit var database: FlexDatabase
    private lateinit var workDayRepository: WorkDayRepositoryImpl
    private lateinit var settingsRepository: SettingsRepositoryImpl
    private lateinit var calculateDayWorkTimeUseCase: CalculateDayWorkTimeUseCase
    private lateinit var calculateFlextimeUseCase: CalculateFlextimeUseCase

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            FlexDatabase::class.java
        ).build()

        settingsRepository = SettingsRepositoryImpl(database.settingsDao(), database.quotaRuleDao())
        val calendarSyncService = CalendarSyncService(context, database.calendarEventDao(), CalendarEventMapper())
        workDayRepository = WorkDayRepositoryImpl(database.workDayDao(), database.timeBlockDao(), calendarSyncService, settingsRepository)

        calculateDayWorkTimeUseCase = CalculateDayWorkTimeUseCase()
        calculateFlextimeUseCase = CalculateFlextimeUseCase(calculateDayWorkTimeUseCase)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun addWorkDayWithTimeBlocks_flextimeBalanceShouldUpdate() = runTest {
        // Setup: Create default settings (7.1h = 426 min daily work requirement)
        val settings = Settings(
            id = 1,
            dailyWorkMinutes = 426,
            monthlyWorkMinutes = 9266,
            officeQuotaPercent = 40,
            officeQuotaMinDays = 8,
            initialFlextimeMinutes = 0,
            initialOvertimeMinutes = 0,
            annualVacationDays = 30,
            carryOverVacationDays = 0,
            specialVacationDays = 5,
            settingsYear = 2026
        )
        settingsRepository.saveSettings(settings)

        // Step 1: Get initial flextime (should be 0 because no work days)
        val yearMonth = YearMonth.of(2026, 2)
        val initialWorkDays = workDayRepository.getWorkDaysForYear(2026).first()

        // Debug: Log initial state
        println("INITIAL STATE: ${initialWorkDays.size} work days in database")
        assertThat(initialWorkDays).isEmpty()

        val initialFlextime = calculateFlextimeUseCase(initialWorkDays, settings, yearMonth)
        println("Initial flextime: total=${initialFlextime.totalMinutes}, earned=${initialFlextime.earnedMinutes}")

        assertThat(initialFlextime.totalMinutes).isEqualTo(0)
        assertThat(initialFlextime.earnedMinutes).isEqualTo(0)

        // Step 2: Add a work day with 8.5 hours
        // This simulates user tapping "Add Work Day" in MonthScreen
        val workDay = WorkDay(
            date = LocalDate.of(2026, 2, 11),
            location = WorkLocation.OFFICE,
            dayType = DayType.WORK,
            isPlanned = false,
            timeBlocks = emptyList()
        )

        // Save the work day first
        val workDayId = workDayRepository.saveWorkDay(workDay)
        println("Saved work day with ID: $workDayId")

        // Then add time block: 8:00 - 16:30 = 8.5h = 510 min
        val timeBlock = TimeBlock(
            workDayId = workDayId,
            startTime = LocalTime.of(8, 0),
            endTime = LocalTime.of(16, 30),
            isDuration = true
        )
        workDayRepository.saveTimeBlock(timeBlock)
        println("Saved time block: 8:00-16:30 (510 minutes)")

        // Step 3: CRITICAL - Get updated work days via Flow
        // This is where the bug likely is: Flow not emitting the new work day
        val updatedWorkDays = workDayRepository.getWorkDaysForYear(2026).first()

        // Debug: Check if the new work day is in the list
        println("AFTER ADD: ${updatedWorkDays.size} work days in database")
        assertThat(updatedWorkDays).hasSize(1)

        val addedDay = updatedWorkDays[0]
        assertThat(addedDay.date).isEqualTo(LocalDate.of(2026, 2, 11))
        assertThat(addedDay.timeBlocks).hasSize(1)
        assertThat(addedDay.timeBlocks[0].startTime).isEqualTo(LocalTime.of(8, 0))
        assertThat(addedDay.timeBlocks[0].endTime).isEqualTo(LocalTime.of(16, 30))
        println("Work day retrieved: ${addedDay.date}, ${addedDay.timeBlocks.size} blocks")

        // Step 4: Recalculate flextime with new data
        val updatedFlextime = calculateFlextimeUseCase(updatedWorkDays, settings, yearMonth)

        // Expected: 510 min - 426 min = +84 min earned
        println("Updated flextime: total=${updatedFlextime.totalMinutes}, earned=${updatedFlextime.earnedMinutes}")

        assertThat(updatedFlextime.earnedMinutes).isEqualTo(84)
        assertThat(updatedFlextime.totalMinutes).isEqualTo(84)

        // Should NOT be 0 (that was the bug!)
        assertThat(updatedFlextime.totalMinutes).isGreaterThan(0)
    }

    @Test
    fun addMultipleDaysSequentially_flextimeShouldAccumulateImmediately() = runTest {
        // Setup
        val settings = Settings(
            id = 1,
            dailyWorkMinutes = 426,
            monthlyWorkMinutes = 9266,
            officeQuotaPercent = 40,
            officeQuotaMinDays = 8,
            initialFlextimeMinutes = 0,
            initialOvertimeMinutes = 0,
            annualVacationDays = 30,
            carryOverVacationDays = 0,
            specialVacationDays = 5,
            settingsYear = 2026
        )
        settingsRepository.saveSettings(settings)

        val yearMonth = YearMonth.of(2026, 2)

        // Add first work day: 9 hours = 540 min
        val day1 = WorkDay(
            date = LocalDate.of(2026, 2, 10),
            location = WorkLocation.OFFICE,
            dayType = DayType.WORK,
            isPlanned = false,
            timeBlocks = emptyList()
        )
        val id1 = workDayRepository.saveWorkDay(day1)
        workDayRepository.saveTimeBlock(
            TimeBlock(
                workDayId = id1,
                startTime = LocalTime.of(8, 0),
                endTime = LocalTime.of(17, 0),
                isDuration = true
            )
        )

        // Check after first day: 540 - 426 = +114
        var workDays = workDayRepository.getWorkDaysForYear(2026).first()
        println("After day 1: ${workDays.size} days")
        assertThat(workDays).hasSize(1)

        var flextime = calculateFlextimeUseCase(workDays, settings, yearMonth)
        println("After day 1: flextime=${flextime.totalMinutes} (expected 114)")
        assertThat(flextime.totalMinutes).isEqualTo(114)

        // Add second work day: 8 hours = 480 min
        val day2 = WorkDay(
            date = LocalDate.of(2026, 2, 11),
            location = WorkLocation.OFFICE,
            dayType = DayType.WORK,
            isPlanned = false,
            timeBlocks = emptyList()
        )
        val id2 = workDayRepository.saveWorkDay(day2)
        workDayRepository.saveTimeBlock(
            TimeBlock(
                workDayId = id2,
                startTime = LocalTime.of(8, 0),
                endTime = LocalTime.of(16, 0),
                isDuration = true
            )
        )

        // Check after second day: 114 + (480 - 426) = 114 + 54 = +168
        workDays = workDayRepository.getWorkDaysForYear(2026).first()
        println("After day 2: ${workDays.size} days")
        assertThat(workDays).hasSize(2)

        flextime = calculateFlextimeUseCase(workDays, settings, yearMonth)
        println("After day 2: flextime=${flextime.totalMinutes} (expected 168)")
        assertThat(flextime.totalMinutes).isEqualTo(168)

        // Add third work day: 7.5 hours = 450 min
        val day3 = WorkDay(
            date = LocalDate.of(2026, 2, 20),
            location = WorkLocation.OFFICE,
            dayType = DayType.WORK,
            isPlanned = false,
            timeBlocks = emptyList()
        )
        val id3 = workDayRepository.saveWorkDay(day3)
        workDayRepository.saveTimeBlock(
            TimeBlock(
                workDayId = id3,
                startTime = LocalTime.of(8, 30),
                endTime = LocalTime.of(16, 0),
                isDuration = true
            )
        )

        // Check after third day: 168 + (450 - 426) = 168 + 24 = +192
        workDays = workDayRepository.getWorkDaysForYear(2026).first()
        println("After day 3: ${workDays.size} days")
        assertThat(workDays).hasSize(3)

        flextime = calculateFlextimeUseCase(workDays, settings, yearMonth)
        println("After day 3: flextime=${flextime.totalMinutes} (expected 192)")
        assertThat(flextime.totalMinutes).isEqualTo(192)
    }

    @Test
    fun flowEmissionAfterInsert_shouldNotBeEmpty() = runTest {
        // This test specifically checks if the Flow is emitting after insert
        val settings = Settings(settingsYear = 2026)
        settingsRepository.saveSettings(settings)

        // Get initial state
        val initialEmission = workDayRepository.getWorkDaysForYear(2026).first()
        println("Initial emission: ${initialEmission.size} items")
        assertThat(initialEmission).isEmpty()

        // Insert a work day
        val workDay = WorkDay(
            date = LocalDate.of(2026, 2, 15),
            location = WorkLocation.OFFICE,
            dayType = DayType.WORK,
            isPlanned = false,
            timeBlocks = emptyList()
        )
        val workDayId = workDayRepository.saveWorkDay(workDay)
        println("Inserted work day with ID: $workDayId")

        // Add time block
        workDayRepository.saveTimeBlock(
            TimeBlock(
                workDayId = workDayId,
                startTime = LocalTime.of(8, 0),
                endTime = LocalTime.of(16, 0),
                isDuration = true
            )
        )
        println("Inserted time block")

        // Get the next emission - THIS IS WHERE THE BUG MANIFESTS
        val afterInsert = workDayRepository.getWorkDaysForYear(2026).first()
        println("After insert emission: ${afterInsert.size} items")

        assertThat(afterInsert).hasSize(1)

        // Verify the work day has the time block
        assertThat(afterInsert[0].timeBlocks).hasSize(1)
    }

    @Test
    fun monthViewFlow_shouldEmitAfterInsert() = runTest {
        // Test the specific Flow used by MonthScreen
        val settings = Settings(settingsYear = 2026)
        settingsRepository.saveSettings(settings)

        val yearMonth = YearMonth.of(2026, 2)

        // Initial state
        val initialMonthDays = workDayRepository.getWorkDaysForMonth(yearMonth).first()
        println("Initial month view: ${initialMonthDays.size} items")
        assertThat(initialMonthDays).isEmpty()

        // Add work day on Feb 15
        val workDay = WorkDay(
            date = LocalDate.of(2026, 2, 15),
            location = WorkLocation.OFFICE,
            dayType = DayType.WORK,
            isPlanned = false,
            timeBlocks = emptyList()
        )
        val id = workDayRepository.saveWorkDay(workDay)
        workDayRepository.saveTimeBlock(
            TimeBlock(
                workDayId = id,
                startTime = LocalTime.of(9, 0),
                endTime = LocalTime.of(17, 0),
                isDuration = true
            )
        )

        // Check month view
        val monthViewAfterInsert = workDayRepository.getWorkDaysForMonth(yearMonth).first()
        println("After insert month view: ${monthViewAfterInsert.size} items")

        assertThat(monthViewAfterInsert).hasSize(1)
        assertThat(monthViewAfterInsert[0].date).isEqualTo(LocalDate.of(2026, 2, 15))
        assertThat(monthViewAfterInsert[0].timeBlocks).hasSize(1)
    }

    @Test
    fun consecutiveAdditions_allFlowsShouldUpdate() = runTest {
        // Test that all repository Flows update correctly when adding multiple days
        val settings = Settings(settingsYear = 2026)
        settingsRepository.saveSettings(settings)

        // Add 3 days rapidly and check all Flows
        repeat(3) { i ->
            val workDay = WorkDay(
                date = LocalDate.of(2026, 2, (10 + i * 3)),
                location = WorkLocation.OFFICE,
                dayType = DayType.WORK,
                isPlanned = false,
                timeBlocks = emptyList()
            )
            val id = workDayRepository.saveWorkDay(workDay)
            workDayRepository.saveTimeBlock(
                TimeBlock(
                    workDayId = id,
                    startTime = LocalTime.of(8, 0),
                    endTime = LocalTime.of(16, 0),
                    isDuration = true
                )
            )
            println("Added day $i")
        }

        // Check year view
        val yearDays = workDayRepository.getWorkDaysForYear(2026).first()
        println("Year view: ${yearDays.size} days")
        assertThat(yearDays).hasSize(3)

        // Check month view
        val monthDays = workDayRepository.getWorkDaysForMonth(YearMonth.of(2026, 2)).first()
        println("Month view: ${monthDays.size} days")
        assertThat(monthDays).hasSize(3)

        // Check that each day has its time block
        monthDays.forEach { day ->
            assertThat(day.timeBlocks).isNotEmpty()
        }
    }
}
