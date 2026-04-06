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
import com.flex.domain.usecase.CalculateQuotaUseCase
import com.flex.domain.usecase.SaveWorkDayUseCase
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
 * Complex integration tests covering multi-layer workflows with real database.
 * Tests full end-to-end scenarios combining repository, use cases, and business logic.
 */
@RunWith(AndroidJUnit4::class)
class ComplexWorkflowIntegrationTest {

    private lateinit var database: FlexDatabase
    private lateinit var workDayRepository: WorkDayRepositoryImpl
    private lateinit var settingsRepository: SettingsRepositoryImpl
    private lateinit var calculateDayWorkTimeUseCase: CalculateDayWorkTimeUseCase
    private lateinit var calculateFlextimeUseCase: CalculateFlextimeUseCase
    private lateinit var calculateQuotaUseCase: CalculateQuotaUseCase
    private lateinit var saveWorkDayUseCase: SaveWorkDayUseCase

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
        calculateQuotaUseCase = CalculateQuotaUseCase(calculateDayWorkTimeUseCase)
        saveWorkDayUseCase = SaveWorkDayUseCase(workDayRepository)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun scenario_fullMonthWorkflow_20WorkDays_1SaturdayBonus() = runTest {
        // Setup: Default settings
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

        // Add work days (10h each = 600min)
        // February 2026: Feb 1 = Sunday, so weekdays in Feb are 2-6, 9-13, 16-20, 23-27
        // Days 3-24 gives us: 3, 4, 5, 6, 10, 11, 12, 13, 17, 18, 19, 20, 24 = 13 weekdays
        val workDays = (2..28).filter { day ->
            val date = LocalDate.of(2026, 2, day)
            date.dayOfWeek.value in 1..5 // Monday to Friday
        }.map { day ->
            WorkDay(
                date = LocalDate.of(2026, 2, day),
                location = WorkLocation.OFFICE,
                dayType = DayType.WORK,
                isPlanned = false,
                note = null,
                timeBlocks = listOf(
                    TimeBlock(
                        workDayId = 0,
                        startTime = LocalTime.of(8, 0),
                        endTime = LocalTime.of(18, 0),
                        isDuration = true
                    )
                )
            )
        }

        // Add 1 Saturday bonus day (2h = 120min)
        val saturdayBonus = WorkDay(
            date = LocalDate.of(2026, 2, 7), // Saturday
            location = WorkLocation.OFFICE,
            dayType = DayType.SATURDAY_BONUS,
            isPlanned = false,
            note = "Weekend work",
            timeBlocks = listOf(
                TimeBlock(
                    workDayId = 0,
                    startTime = LocalTime.of(10, 0),
                    endTime = LocalTime.of(12, 0),
                    isDuration = true
                )
            )
        )

        // Save all days
        workDays.forEach { day ->
            val id = saveWorkDayUseCase(day)
            day.timeBlocks.forEach { block ->
                workDayRepository.saveTimeBlock(block.copy(workDayId = id))
            }
        }
        val satId = saveWorkDayUseCase(saturdayBonus)
        saturdayBonus.timeBlocks.forEach { block ->
            workDayRepository.saveTimeBlock(block.copy(workDayId = satId))
        }

        // Retrieve all days
        val savedWorkDays = workDayRepository.getWorkDaysForMonth(YearMonth.of(2026, 2)).first()
        // Should have 20 work days (Mon-Fri from days 3-24) + 1 Saturday bonus
        assertThat(savedWorkDays).hasSize(21)

        // Calculate quota status
        val quotaStatus = calculateQuotaUseCase(savedWorkDays, settings, YearMonth.of(2026, 2))

        // All 20 work days are office, 1 Saturday bonus also office
        assertThat(quotaStatus.officeDays).isEqualTo(21) // 20 weekdays + 1 Saturday
        assertThat(quotaStatus.homeOfficeDays).isEqualTo(0)

        // Total office minutes: 20 * 600 + 1 * 120 = 12120min
        assertThat(quotaStatus.officeMinutes).isEqualTo(12120)
        assertThat(quotaStatus.percentQuotaMet).isTrue() // 12120 / (9266 - 0) = 130.6% >= 40%
        assertThat(quotaStatus.daysQuotaMet).isTrue() // 21 >= 8

        // Calculate flextime
        // Each work day: 600 - 426 = +174min
        // 20 work days: 20 * 174 = +3480min
        // Saturday bonus: +120min (full time to flextime)
        // Total earned: 3480 + 120 = 3600min
        val flextimeBalance = calculateFlextimeUseCase(savedWorkDays, settings, YearMonth.of(2026, 2))
        assertThat(flextimeBalance.earnedMinutes).isEqualTo(3600)
        assertThat(flextimeBalance.totalMinutes).isEqualTo(3600)

        // Overtime from Saturday bonus: 120 * 0.5 = 60min
        assertThat(flextimeBalance.overtimeMinutes).isEqualTo(60)
    }

    @Test
    fun scenario_settingsChange_overtimeImpact() = runTest {
        // Initial settings with 0 initial overtime
        val initialSettings = Settings(
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
        settingsRepository.saveSettings(initialSettings)

        // Add Saturday bonus day with 2h work
        val saturdayBonus = WorkDay(
            date = LocalDate.of(2026, 2, 14),
            location = WorkLocation.OFFICE,
            dayType = DayType.SATURDAY_BONUS,
            isPlanned = false,
            note = null,
            timeBlocks = listOf(
                TimeBlock(
                    workDayId = 0,
                    startTime = LocalTime.of(9, 0),
                    endTime = LocalTime.of(11, 0),
                    isDuration = true
                )
            )
        )
        val id = saveWorkDayUseCase(saturdayBonus)
        saturdayBonus.timeBlocks.forEach { block ->
            workDayRepository.saveTimeBlock(block.copy(workDayId = id))
        }

        val savedWorkDays1 = workDayRepository.getWorkDaysForMonth(YearMonth.of(2026, 2)).first()

        // Calculate with initial settings: overtime = 0 + 60 = 60
        val balance1 = calculateFlextimeUseCase(savedWorkDays1, initialSettings)
        assertThat(balance1.overtimeMinutes).isEqualTo(60)

        // Update settings with 60min initial overtime
        val updatedSettings = initialSettings.copy(initialOvertimeMinutes = 60)
        settingsRepository.saveSettings(updatedSettings)

        val retrievedSettings = settingsRepository.getSettings().first()
        val savedWorkDays2 = workDayRepository.getWorkDaysForMonth(YearMonth.of(2026, 2)).first()

        // Recalculate: overtime = 60 + 60 = 120
        val balance2 = calculateFlextimeUseCase(savedWorkDays2, retrievedSettings)
        assertThat(balance2.overtimeMinutes).isEqualTo(120)
    }

    @Test
    fun scenario_multipleDays_complexMix() = runTest {
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

        // Day 1: WORK with 8.5h (510min)
        val day1 = WorkDay(
            date = LocalDate.of(2026, 2, 3),
            location = WorkLocation.OFFICE,
            dayType = DayType.WORK,
            isPlanned = false,
            timeBlocks = listOf(
                TimeBlock(workDayId = 0, startTime = LocalTime.of(8, 0), endTime = LocalTime.of(17, 0), isDuration = true)
            )
        )
        val id1 = saveWorkDayUseCase(day1)
        day1.timeBlocks.forEach { workDayRepository.saveTimeBlock(it.copy(workDayId = id1)) }

        // Day 2: VACATION (neutral)
        val day2 = WorkDay(
            date = LocalDate.of(2026, 2, 4),
            location = WorkLocation.OFFICE,
            dayType = DayType.VACATION,
            isPlanned = false,
            timeBlocks = emptyList()
        )
        saveWorkDayUseCase(day2)

        // Day 3: SATURDAY_BONUS with 2h
        val day3 = WorkDay(
            date = LocalDate.of(2026, 2, 7),
            location = WorkLocation.OFFICE,
            dayType = DayType.SATURDAY_BONUS,
            isPlanned = false,
            timeBlocks = listOf(
                TimeBlock(workDayId = 0, startTime = LocalTime.of(10, 0), endTime = LocalTime.of(12, 0), isDuration = true)
            )
        )
        val id3 = saveWorkDayUseCase(day3)
        day3.timeBlocks.forEach { workDayRepository.saveTimeBlock(it.copy(workDayId = id3)) }

        // Day 4: FLEX_DAY (deducts full day)
        val day4 = WorkDay(
            date = LocalDate.of(2026, 2, 10),
            location = WorkLocation.OFFICE,
            dayType = DayType.FLEX_DAY,
            isPlanned = false,
            timeBlocks = emptyList()
        )
        saveWorkDayUseCase(day4)

        val savedWorkDays = workDayRepository.getWorkDaysForMonth(YearMonth.of(2026, 2)).first()
        assertThat(savedWorkDays).hasSize(4)

        // Calculate flextime
        // Day 1 (WORK): 540min (8:00-17:00 isDuration=true, 9h) - 426 = +114min
        // Day 2 (VACATION): 0min
        // Day 3 (SATURDAY_BONUS): +120min
        // Day 4 (FLEX_DAY): -426min
        // Total: 114 + 0 + 120 - 426 = -192min
        val balance = calculateFlextimeUseCase(savedWorkDays, settings)
        assertThat(balance.earnedMinutes).isEqualTo(114 + 120 - 426) // -192
        assertThat(balance.totalMinutes).isEqualTo(114 + 120 - 426) // -192

        // Overtime from Saturday bonus: 120 * 0.5 = 60min
        assertThat(balance.overtimeMinutes).isEqualTo(60)
    }

    @Test
    fun scenario_monthQuota_officeAndHomeOfficeSplit() = runTest {
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

        // Add 10 office days (each 8h)
        val officeDays = (3..12).map { day ->
            WorkDay(
                date = LocalDate.of(2026, 2, day),
                location = WorkLocation.OFFICE,
                dayType = DayType.WORK,
                isPlanned = false,
                timeBlocks = listOf(
                    TimeBlock(workDayId = 0, startTime = LocalTime.of(8, 0), endTime = LocalTime.of(16, 0), isDuration = true)
                )
            )
        }

        // Add 10 home office days (each 8h)
        val homeOfficeDays = (13..22).map { day ->
            WorkDay(
                date = LocalDate.of(2026, 2, day),
                location = WorkLocation.HOME_OFFICE,
                dayType = DayType.WORK,
                isPlanned = false,
                timeBlocks = listOf(
                    TimeBlock(workDayId = 0, startTime = LocalTime.of(9, 0), endTime = LocalTime.of(17, 0), isDuration = true, location = WorkLocation.HOME_OFFICE)
                )
            )
        }

        // Save all days
        (officeDays + homeOfficeDays).forEach { day ->
            val id = saveWorkDayUseCase(day)
            day.timeBlocks.forEach { block ->
                workDayRepository.saveTimeBlock(block.copy(workDayId = id))
            }
        }

        val savedWorkDays = workDayRepository.getWorkDaysForMonth(YearMonth.of(2026, 2)).first()

        // Calculate quota
        val quotaStatus = calculateQuotaUseCase(savedWorkDays, settings, YearMonth.of(2026, 2))

        assertThat(quotaStatus.officeDays).isEqualTo(10)
        assertThat(quotaStatus.homeOfficeDays).isEqualTo(10)

        // Each day: 8h = 480min
        assertThat(quotaStatus.officeMinutes).isEqualTo(4800) // 10 * 480
        assertThat(quotaStatus.homeOfficeMinutes).isEqualTo(4800) // 10 * 480

        // Office percent: 4800 / 9266 = 51.8%
        // Note: Quota is calculated against monthly target (9266), not sum of actual minutes
        assertThat(quotaStatus.officePercent).isWithin(0.1).of(51.8)
        assertThat(quotaStatus.percentQuotaMet).isTrue() // 51.8% >= 40%

        // Need 8 office days, have 10
        assertThat(quotaStatus.daysQuotaMet).isTrue() // 10 >= 8
        assertThat(quotaStatus.requiredOfficeDaysForQuota).isEqualTo(0)
    }

    @Test
    fun scenario_quotaWithVacationAndFlexDays() = runTest {
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

        // Add 5 office days, 5 home office days
        val workDays = mutableListOf<WorkDay>()

        // Office days
        (3..7).forEach { day ->
            workDays.add(
                WorkDay(
                    date = LocalDate.of(2026, 2, day),
                    location = WorkLocation.OFFICE,
                    dayType = DayType.WORK,
                    isPlanned = false,
                    timeBlocks = listOf(
                        TimeBlock(workDayId = 0, startTime = LocalTime.of(8, 0), endTime = LocalTime.of(16, 0), isDuration = true)
                    )
                )
            )
        }

        // Home office days
        (10..14).forEach { day ->
            workDays.add(
                WorkDay(
                    date = LocalDate.of(2026, 2, day),
                    location = WorkLocation.HOME_OFFICE,
                    dayType = DayType.WORK,
                    isPlanned = false,
                    timeBlocks = listOf(
                        TimeBlock(workDayId = 0, startTime = LocalTime.of(9, 0), endTime = LocalTime.of(17, 0), isDuration = true, location = WorkLocation.HOME_OFFICE)
                    )
                )
            )
        }

        // Add vacation days (neutral for quota)
        workDays.add(
            WorkDay(
                date = LocalDate.of(2026, 2, 17),
                location = WorkLocation.OFFICE,
                dayType = DayType.VACATION,
                isPlanned = false,
                timeBlocks = emptyList()
            )
        )
        workDays.add(
            WorkDay(
                date = LocalDate.of(2026, 2, 18),
                location = WorkLocation.OFFICE,
                dayType = DayType.VACATION,
                isPlanned = false,
                timeBlocks = emptyList()
            )
        )

        // Add flex day (neutral for quota)
        workDays.add(
            WorkDay(
                date = LocalDate.of(2026, 2, 19),
                location = WorkLocation.OFFICE,
                dayType = DayType.FLEX_DAY,
                isPlanned = false,
                timeBlocks = emptyList()
            )
        )

        // Save all days
        workDays.forEach { day ->
            val id = saveWorkDayUseCase(day)
            day.timeBlocks.forEach { block ->
                workDayRepository.saveTimeBlock(block.copy(workDayId = id))
            }
        }

        val savedWorkDays = workDayRepository.getWorkDaysForMonth(YearMonth.of(2026, 2)).first()
        assertThat(savedWorkDays).hasSize(13)

        // Calculate quota (vacation and flex days should be excluded)
        val quotaStatus = calculateQuotaUseCase(savedWorkDays, settings, YearMonth.of(2026, 2))

        // Only 10 working days count (5 office + 5 home office)
        assertThat(quotaStatus.officeDays).isEqualTo(5)
        assertThat(quotaStatus.homeOfficeDays).isEqualTo(5)

        // Each day: 8h = 480min
        assertThat(quotaStatus.officeMinutes).isEqualTo(2400) // 5 * 480
        assertThat(quotaStatus.homeOfficeMinutes).isEqualTo(2400) // 5 * 480

        // Office percent: 2400 / (9266 - 3*426) = 2400 / 7988 = 30%
        // Note: Quota is calculated against monthly target minus neutral days
        assertThat(quotaStatus.officePercent).isWithin(0.1).of(30.0)
        assertThat(quotaStatus.percentQuotaMet).isFalse() // 30% < 40%

        // Need 8 office days, have 5
        assertThat(quotaStatus.daysQuotaMet).isFalse() // 5 < 8
        assertThat(quotaStatus.requiredOfficeDaysForQuota).isEqualTo(3)
    }

    @Test
    fun scenario_negativeFlextime_recovery() = runTest {
        val settings = Settings(
            id = 1,
            dailyWorkMinutes = 426,
            monthlyWorkMinutes = 9266,
            officeQuotaPercent = 40,
            officeQuotaMinDays = 8,
            initialFlextimeMinutes = -600, // -10h initial debt
            initialOvertimeMinutes = 0,
            annualVacationDays = 30,
            carryOverVacationDays = 0,
            specialVacationDays = 5,
            settingsYear = 2026
        )
        settingsRepository.saveSettings(settings)

        // Work 5 days with 10h each to recover
        val workDays = (2..6).map { day ->
            WorkDay(
                date = LocalDate.of(2026, 2, day),
                location = WorkLocation.OFFICE,
                dayType = DayType.WORK,
                isPlanned = false,
                timeBlocks = listOf(
                    TimeBlock(workDayId = 0, startTime = LocalTime.of(8, 0), endTime = LocalTime.of(18, 0), isDuration = true)
                )
            )
        }

        workDays.forEach { day ->
            val id = saveWorkDayUseCase(day)
            day.timeBlocks.forEach { block ->
                workDayRepository.saveTimeBlock(block.copy(workDayId = id))
            }
        }

        val savedWorkDays = workDayRepository.getWorkDaysForMonth(YearMonth.of(2026, 2)).first()

        // Calculate: 5 days * (600 - 426) = 5 * 174 = +870min
        // Total: -600 + 870 = +270min
        val balance = calculateFlextimeUseCase(savedWorkDays, settings)
        assertThat(balance.initialMinutes).isEqualTo(-600)
        assertThat(balance.earnedMinutes).isEqualTo(870)
        assertThat(balance.totalMinutes).isEqualTo(270)
    }

    @Test
    fun scenario_mixedTimeBlocks_breakCalculation() = runTest {
        val settings = Settings()
        settingsRepository.saveSettings(settings)

        // Day with complex time blocks
        val workDay = WorkDay(
            date = LocalDate.of(2026, 2, 15),
            location = WorkLocation.OFFICE,
            dayType = DayType.WORK,
            isPlanned = false,
            timeBlocks = listOf(
                TimeBlock(workDayId = 0, startTime = LocalTime.of(7, 30), endTime = LocalTime.of(12, 0), isDuration = false),
                TimeBlock(workDayId = 0, startTime = LocalTime.of(12, 30), endTime = LocalTime.of(14, 0), isDuration = false),
                TimeBlock(workDayId = 0, startTime = LocalTime.of(14, 15), endTime = LocalTime.of(18, 30), isDuration = false)
            )
        )

        val id = saveWorkDayUseCase(workDay)
        workDay.timeBlocks.forEach { block ->
            workDayRepository.saveTimeBlock(block.copy(workDayId = id))
        }

        val savedWorkDays = workDayRepository.getWorkDaysForMonth(YearMonth.of(2026, 2)).first()
        val dayWorkTime = calculateDayWorkTimeUseCase(savedWorkDays[0].timeBlocks)

        // Block 1: 4.5h (270min)
        // Block 2: 1.5h (90min)
        // Block 3: 4.25h (255min)
        // Total gross: 615min (10.25h)

        assertThat(dayWorkTime.grossMinutes).isEqualTo(615)

        // Gaps: 30min + 15min = 45min manual break
        // Required break for 615min (>540, so >9h): 45min
        // effectiveBreak = maxOf(45, 45) = 45
        // netMinutes = 615 - (45 - 45).coerceAtLeast(0) = 615 - 0 = 615
        // But capped at 600 (10h max)
        assertThat(dayWorkTime.breakMinutes).isEqualTo(45)
        assertThat(dayWorkTime.netMinutes).isEqualTo(600) // Capped at 10h max
    }

    @Test
    fun scenario_durationBasedDay_noBreakDeduction() = runTest {
        val settings = Settings()
        settingsRepository.saveSettings(settings)

        // Day with duration-based entry (no break deduction)
        val workDay = WorkDay(
            date = LocalDate.of(2026, 2, 15),
            location = WorkLocation.OFFICE,
            dayType = DayType.WORK,
            isPlanned = false,
            timeBlocks = listOf(
                TimeBlock(workDayId = 0, startTime = LocalTime.of(8, 0), endTime = LocalTime.of(17, 0), isDuration = true)
            )
        )

        val id = saveWorkDayUseCase(workDay)
        workDay.timeBlocks.forEach { block ->
            workDayRepository.saveTimeBlock(block.copy(workDayId = id))
        }

        val savedWorkDays = workDayRepository.getWorkDaysForMonth(YearMonth.of(2026, 2)).first()
        val dayWorkTime = calculateDayWorkTimeUseCase(savedWorkDays[0].timeBlocks)

        // Duration-based: full time counts, no break
        assertThat(dayWorkTime.grossMinutes).isEqualTo(540) // 9h
        assertThat(dayWorkTime.breakMinutes).isEqualTo(0)
        assertThat(dayWorkTime.netMinutes).isEqualTo(540)
    }

    @Test
    fun scenario_specialVacation_neutralForFlextime() = runTest {
        val settings = Settings(
            id = 1,
            dailyWorkMinutes = 426,
            monthlyWorkMinutes = 9266,
            officeQuotaPercent = 40,
            officeQuotaMinDays = 8,
            initialFlextimeMinutes = 200,
            initialOvertimeMinutes = 0,
            annualVacationDays = 30,
            carryOverVacationDays = 0,
            specialVacationDays = 5,
            settingsYear = 2026
        )
        settingsRepository.saveSettings(settings)

        // Add special vacation day
        val specialVacation = WorkDay(
            date = LocalDate.of(2026, 2, 10),
            location = WorkLocation.OFFICE,
            dayType = DayType.SPECIAL_VACATION,
            isPlanned = false,
            timeBlocks = emptyList()
        )
        saveWorkDayUseCase(specialVacation)

        val savedWorkDays = workDayRepository.getWorkDaysForMonth(YearMonth.of(2026, 2)).first()
        val balance = calculateFlextimeUseCase(savedWorkDays, settings)

        // Special vacation should not affect flextime
        assertThat(balance.earnedMinutes).isEqualTo(0)
        assertThat(balance.totalMinutes).isEqualTo(200) // Just initial
    }
}
