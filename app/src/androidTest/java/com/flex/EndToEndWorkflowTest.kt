package com.flex

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.flex.data.local.FlexDatabase
import com.flex.data.local.dao.SettingsDao
import com.flex.data.local.dao.TimeBlockDao
import com.flex.data.local.dao.WorkDayDao
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

@RunWith(AndroidJUnit4::class)
class EndToEndWorkflowTest {

    private lateinit var database: FlexDatabase
    private lateinit var workDayDao: WorkDayDao
    private lateinit var timeBlockDao: TimeBlockDao
    private lateinit var settingsDao: SettingsDao

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

        workDayDao = database.workDayDao()
        timeBlockDao = database.timeBlockDao()
        settingsDao = database.settingsDao()

        workDayRepository = WorkDayRepositoryImpl(workDayDao, timeBlockDao)
        settingsRepository = SettingsRepositoryImpl(settingsDao, database.quotaRuleDao())

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
    fun scenarioA_addWorkday_calculateFlextime() = runTest {
        // Setup: Insert default settings
        val settings = Settings(
            id = 1,
            dailyWorkMinutes = 426, // 7h 6min
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

        // Scenario: Add work day with 8h 30min (510 minutes)
        val workDay = WorkDay(
            date = LocalDate.of(2026, 2, 15),
            location = WorkLocation.OFFICE,
            dayType = DayType.WORK,
            isPlanned = false,
            note = "Test day",
            timeBlocks = listOf(
                TimeBlock(
                    workDayId = 0,
                    startTime = LocalTime.of(8, 0),
                    endTime = LocalTime.of(12, 30),
                    isDuration = false
                ),
                TimeBlock(
                    workDayId = 0,
                    startTime = LocalTime.of(13, 0),
                    endTime = LocalTime.of(17, 30),
                    isDuration = false
                )
            )
        )

        val workDayId = saveWorkDayUseCase(workDay)

        // Save time blocks
        workDay.timeBlocks.forEach { block ->
            workDayRepository.saveTimeBlock(block.copy(workDayId = workDayId))
        }

        // Retrieve and verify
        val savedWorkDays = workDayRepository.getWorkDaysForMonth(YearMonth.of(2026, 2)).first()
        assertThat(savedWorkDays).hasSize(1)

        val savedDay = savedWorkDays[0]
        assertThat(savedDay.timeBlocks).hasSize(2)

        // Calculate work time: 4.5h + 4.5h = 9h gross
        // Both blocks have isDuration=false, so break deduction applies
        // Manual break (gap): 30min
        // Required break for 540min (>540? No, = 540): 30min
        // effectiveBreak = maxOf(30, 30) = 30
        // netMinutes = 540 - (30 - 30).coerceAtLeast(0) = 540 - 0 = 540
        val dayWorkTime = calculateDayWorkTimeUseCase(savedDay.timeBlocks)
        assertThat(dayWorkTime.grossMinutes).isEqualTo(540) // 9 hours
        assertThat(dayWorkTime.breakMinutes).isEqualTo(30)
        assertThat(dayWorkTime.netMinutes).isEqualTo(540) // 9 hours, manual break meets requirement

        // Calculate flextime: 540min - 426min = 114min (1h 54min)
        val flextimeBalance = calculateFlextimeUseCase(savedWorkDays, settings)
        assertThat(flextimeBalance.earnedMinutes).isEqualTo(114)
        assertThat(flextimeBalance.totalMinutes).isEqualTo(114)
        assertThat(flextimeBalance.overtimeMinutes).isEqualTo(0)
    }

    @Test
    fun scenarioB_saturdayBonus_flow() = runTest {
        // Setup: Insert default settings with initial overtime
        val settings = Settings(
            id = 1,
            dailyWorkMinutes = 426,
            monthlyWorkMinutes = 9266,
            officeQuotaPercent = 40,
            officeQuotaMinDays = 8,
            initialFlextimeMinutes = 0,
            initialOvertimeMinutes = 100, // 1h 40min initial overtime
            annualVacationDays = 30,
            carryOverVacationDays = 0,
            specialVacationDays = 5,
            settingsYear = 2026
        )
        settingsRepository.saveSettings(settings)

        // Scenario: Add Saturday bonus day with 2h work
        val workDay = WorkDay(
            date = LocalDate.of(2026, 2, 14), // Saturday
            location = WorkLocation.OFFICE,
            dayType = DayType.SATURDAY_BONUS,
            isPlanned = false,
            note = "Weekend work",
            timeBlocks = listOf(
                TimeBlock(
                    workDayId = 0,
                    startTime = LocalTime.of(9, 0),
                    endTime = LocalTime.of(11, 0),
                    isDuration = true // Duration entry, no break
                )
            )
        )

        val workDayId = saveWorkDayUseCase(workDay)
        workDay.timeBlocks.forEach { block ->
            workDayRepository.saveTimeBlock(block.copy(workDayId = workDayId))
        }

        val savedWorkDays = workDayRepository.getWorkDaysForMonth(YearMonth.of(2026, 2)).first()
        assertThat(savedWorkDays).hasSize(1)

        val savedDay = savedWorkDays[0]
        val dayWorkTime = calculateDayWorkTimeUseCase(savedDay.timeBlocks)
        assertThat(dayWorkTime.netMinutes).isEqualTo(120) // 2h

        // Calculate flextime with Saturday bonus rules:
        // - Full work time (120min) goes to flextime
        // - 50% bonus (60min) goes to overtime
        val flextimeBalance = calculateFlextimeUseCase(savedWorkDays, settings)
        assertThat(flextimeBalance.earnedMinutes).isEqualTo(120) // 2h to flextime
        assertThat(flextimeBalance.totalMinutes).isEqualTo(120) // 0 initial + 120 earned
        assertThat(flextimeBalance.overtimeMinutes).isEqualTo(160) // 100 initial + 60 bonus
    }

    @Test
    fun scenarioC_multipleDays_cumulative() = runTest {
        val settings = Settings(
            id = 1,
            dailyWorkMinutes = 426,
            monthlyWorkMinutes = 9266,
            officeQuotaPercent = 40,
            officeQuotaMinDays = 8,
            initialFlextimeMinutes = 120, // 2h initial flextime
            initialOvertimeMinutes = 0,
            annualVacationDays = 30,
            carryOverVacationDays = 0,
            specialVacationDays = 5,
            settingsYear = 2026
        )
        settingsRepository.saveSettings(settings)

        // Day 1: WORK with 8.5h (510min)
        val workDay1 = WorkDay(
            date = LocalDate.of(2026, 2, 10),
            location = WorkLocation.OFFICE,
            dayType = DayType.WORK,
            isPlanned = false,
            timeBlocks = listOf(
                TimeBlock(workDayId = 0, startTime = LocalTime.of(8, 0), endTime = LocalTime.of(17, 0), isDuration = true)
            )
        )
        val id1 = saveWorkDayUseCase(workDay1)
        workDay1.timeBlocks.forEach { workDayRepository.saveTimeBlock(it.copy(workDayId = id1)) }

        // Day 2: VACATION (neutral)
        val workDay2 = WorkDay(
            date = LocalDate.of(2026, 2, 11),
            location = WorkLocation.OFFICE,
            dayType = DayType.VACATION,
            isPlanned = false,
            timeBlocks = emptyList()
        )
        saveWorkDayUseCase(workDay2)

        // Day 3: SATURDAY_BONUS with 2h
        val workDay3 = WorkDay(
            date = LocalDate.of(2026, 2, 14),
            location = WorkLocation.OFFICE,
            dayType = DayType.SATURDAY_BONUS,
            isPlanned = false,
            timeBlocks = listOf(
                TimeBlock(workDayId = 0, startTime = LocalTime.of(10, 0), endTime = LocalTime.of(12, 0), isDuration = true)
            )
        )
        val id3 = saveWorkDayUseCase(workDay3)
        workDay3.timeBlocks.forEach { workDayRepository.saveTimeBlock(it.copy(workDayId = id3)) }

        // Day 4: FLEX_DAY (takes a day from flextime)
        val workDay4 = WorkDay(
            date = LocalDate.of(2026, 2, 17),
            location = WorkLocation.OFFICE,
            dayType = DayType.FLEX_DAY,
            isPlanned = false,
            timeBlocks = emptyList()
        )
        saveWorkDayUseCase(workDay4)

        // Retrieve all days
        val savedWorkDays = workDayRepository.getWorkDaysForMonth(YearMonth.of(2026, 2)).first()
        assertThat(savedWorkDays).hasSize(4)

        // Calculate cumulative flextime
        // Initial: 120min
        // Day 1 (WORK): 540min (8:00-17:00, isDuration=true) - 426 = +114min
        // Day 2 (VACATION): 0min
        // Day 3 (SATURDAY_BONUS): +120min (full time to flextime)
        // Day 4 (FLEX_DAY): -426min (full day deducted)
        // Earned: 114 + 120 - 426 = -192min
        // Total: 120 + (-192) = -72min
        val flextimeBalance = calculateFlextimeUseCase(savedWorkDays, settings, YearMonth.of(2026, 2))
        assertThat(flextimeBalance.initialMinutes).isEqualTo(120)
        assertThat(flextimeBalance.earnedMinutes).isEqualTo(114 + 120 - 426) // -192
        assertThat(flextimeBalance.totalMinutes).isEqualTo(120 + 114 + 120 - 426) // -72

        // Overtime from Saturday bonus: 120 * 0.5 = 60min
        assertThat(flextimeBalance.overtimeMinutes).isEqualTo(60)
    }

    @Test
    fun scenarioD_settingsChange_impact() = runTest {
        // Initial settings
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

        // Add work day with 8h
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
        workDay.timeBlocks.forEach { workDayRepository.saveTimeBlock(it.copy(workDayId = id)) }

        val savedWorkDays = workDayRepository.getWorkDaysForMonth(YearMonth.of(2026, 2)).first()

        // Calculate with initial settings: 540min (8:00-17:00 isDuration=true) - 426 = +114min
        val balance1 = calculateFlextimeUseCase(savedWorkDays, initialSettings)
        assertThat(balance1.totalMinutes).isEqualTo(114)

        // Update settings with different initial flextime
        val updatedSettings = initialSettings.copy(initialFlextimeMinutes = 240) // +4h
        settingsRepository.saveSettings(updatedSettings)

        val retrievedSettings = settingsRepository.getSettings().first()

        // Recalculate with new settings: 240 (initial) + 114 (earned) = 354min
        val balance2 = calculateFlextimeUseCase(savedWorkDays, retrievedSettings)
        assertThat(balance2.initialMinutes).isEqualTo(240)
        assertThat(balance2.totalMinutes).isEqualTo(354)
    }

    @Test
    fun scenarioE_quota_calculation_homeOffice_office_mix() = runTest {
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

        // Add multiple work days with different locations
        val days = listOf(
            // Office days
            WorkDay(
                date = LocalDate.of(2026, 2, 3),
                location = WorkLocation.OFFICE,
                dayType = DayType.WORK,
                isPlanned = false,
                note = null,
                timeBlocks = listOf(TimeBlock(workDayId = 0, startTime = LocalTime.of(8, 0), endTime = LocalTime.of(16, 30), isDuration = true))
            ),
            WorkDay(
                date = LocalDate.of(2026, 2, 4),
                location = WorkLocation.OFFICE,
                dayType = DayType.WORK,
                isPlanned = false,
                note = null,
                timeBlocks = listOf(TimeBlock(workDayId = 0, startTime = LocalTime.of(8, 0), endTime = LocalTime.of(16, 30), isDuration = true))
            ),
            WorkDay(
                date = LocalDate.of(2026, 2, 5),
                location = WorkLocation.OFFICE,
                dayType = DayType.WORK,
                isPlanned = false,
                note = null,
                timeBlocks = listOf(TimeBlock(workDayId = 0, startTime = LocalTime.of(8, 0), endTime = LocalTime.of(16, 30), isDuration = true))
            ),

            // Home office days
            WorkDay(
                date = LocalDate.of(2026, 2, 6),
                location = WorkLocation.HOME_OFFICE,
                dayType = DayType.WORK,
                isPlanned = false,
                note = null,
                timeBlocks = listOf(TimeBlock(workDayId = 0, startTime = LocalTime.of(9, 0), endTime = LocalTime.of(17, 30), isDuration = true))
            ),
            WorkDay(
                date = LocalDate.of(2026, 2, 7),
                location = WorkLocation.HOME_OFFICE,
                dayType = DayType.WORK,
                isPlanned = false,
                note = null,
                timeBlocks = listOf(TimeBlock(workDayId = 0, startTime = LocalTime.of(9, 0), endTime = LocalTime.of(17, 30), isDuration = true))
            ),

            // Vacation (neutral for quota)
            WorkDay(
                date = LocalDate.of(2026, 2, 10),
                location = WorkLocation.OFFICE,
                dayType = DayType.VACATION,
                isPlanned = false,
                note = null,
                timeBlocks = emptyList()
            )
        )

        days.forEach { day ->
            val id = saveWorkDayUseCase(day)
            day.timeBlocks.forEach { block ->
                workDayRepository.saveTimeBlock(block.copy(workDayId = id))
            }
        }

        val savedWorkDays = workDayRepository.getWorkDaysForMonth(YearMonth.of(2026, 2)).first()

        // Calculate quota
        val quotaStatus = calculateQuotaUseCase(savedWorkDays, settings, YearMonth.of(2026, 2))

        // 3 office days, 2 home office days (vacation excluded)
        assertThat(quotaStatus.officeDays).isEqualTo(3)
        assertThat(quotaStatus.homeOfficeDays).isEqualTo(2)

        // Each day: 8.5h = 510min
        assertThat(quotaStatus.officeMinutes).isEqualTo(1530) // 3 * 510
        assertThat(quotaStatus.homeOfficeMinutes).isEqualTo(1020) // 2 * 510

        // Office percent: 1530 / (9266 - 426) = 1530 / 8840 = 17.3%
        // Note: Quota is calculated against monthly target minus vacation days
        assertThat(quotaStatus.officePercent).isWithin(0.1).of(17.3)
        assertThat(quotaStatus.percentQuotaMet).isFalse() // 17.3% < 40%

        // Need 8 office days minimum, have 3
        assertThat(quotaStatus.daysQuotaMet).isFalse() // 3 < 8
        assertThat(quotaStatus.requiredOfficeDaysForQuota).isEqualTo(5) // Need 5 more
    }

    @Test
    fun scenarioF_running_timeBlock_notCounted() = runTest {
        val settings = Settings()
        settingsRepository.saveSettings(settings)

        // Add work day with running time block (no end time)
        val workDay = WorkDay(
            date = LocalDate.of(2026, 2, 15),
            location = WorkLocation.OFFICE,
            dayType = DayType.WORK,
            isPlanned = false,
            timeBlocks = listOf(
                TimeBlock(workDayId = 0, startTime = LocalTime.of(8, 0), endTime = null, isDuration = false)
            )
        )
        val id = saveWorkDayUseCase(workDay)
        workDay.timeBlocks.forEach { workDayRepository.saveTimeBlock(it.copy(workDayId = id)) }

        val savedWorkDays = workDayRepository.getWorkDaysForMonth(YearMonth.of(2026, 2)).first()
        val dayWorkTime = calculateDayWorkTimeUseCase(savedWorkDays[0].timeBlocks)

        // Running block should not be counted
        assertThat(dayWorkTime.netMinutes).isEqualTo(0)
        assertThat(dayWorkTime.grossMinutes).isEqualTo(0)
    }

    @Test
    fun scenarioG_multipleBlocks_breakCalculation() = runTest {
        val settings = Settings()
        settingsRepository.saveSettings(settings)

        // Work day with multiple blocks and gaps
        val workDay = WorkDay(
            date = LocalDate.of(2026, 2, 15),
            location = WorkLocation.OFFICE,
            dayType = DayType.WORK,
            isPlanned = false,
            timeBlocks = listOf(
                TimeBlock(workDayId = 0, startTime = LocalTime.of(8, 0), endTime = LocalTime.of(12, 0), isDuration = false),
                TimeBlock(workDayId = 0, startTime = LocalTime.of(12, 45), endTime = LocalTime.of(17, 15), isDuration = false)
            )
        )
        val id = saveWorkDayUseCase(workDay)
        workDay.timeBlocks.forEach { workDayRepository.saveTimeBlock(it.copy(workDayId = id)) }

        val savedWorkDays = workDayRepository.getWorkDaysForMonth(YearMonth.of(2026, 2)).first()
        val dayWorkTime = calculateDayWorkTimeUseCase(savedWorkDays[0].timeBlocks)

        // Block 1: 4h (240min), Block 2: 4.5h (270min) = 8.5h gross (510min)
        assertThat(dayWorkTime.grossMinutes).isEqualTo(510)

        // Gap: 45min manual break
        // Required break for 510min (>360 but <=540): 30min
        // effectiveBreak = maxOf(45, 30) = 45
        // netMinutes = 510 - (45 - 45).coerceAtLeast(0) = 510 - 0 = 510
        // Manual break (45min) exceeds requirement (30min), so no additional deduction
        assertThat(dayWorkTime.breakMinutes).isEqualTo(45)
        assertThat(dayWorkTime.netMinutes).isEqualTo(510) // Manual break meets/exceeds requirement
    }

    @Test
    fun scenarioH_exceeds_maxWorkHours() = runTest {
        val settings = Settings()
        settingsRepository.saveSettings(settings)

        // Work day with 11h work (exceeds 10h max)
        val workDay = WorkDay(
            date = LocalDate.of(2026, 2, 15),
            location = WorkLocation.OFFICE,
            dayType = DayType.WORK,
            isPlanned = false,
            timeBlocks = listOf(
                TimeBlock(workDayId = 0, startTime = LocalTime.of(7, 0), endTime = LocalTime.of(19, 0), isDuration = true)
            )
        )
        val id = saveWorkDayUseCase(workDay)
        workDay.timeBlocks.forEach { workDayRepository.saveTimeBlock(it.copy(workDayId = id)) }

        val savedWorkDays = workDayRepository.getWorkDaysForMonth(YearMonth.of(2026, 2)).first()
        val dayWorkTime = calculateDayWorkTimeUseCase(savedWorkDays[0].timeBlocks)

        // 12h work, but capped at 10h max
        assertThat(dayWorkTime.grossMinutes).isEqualTo(720) // 12h
        assertThat(dayWorkTime.netMinutes).isEqualTo(600) // Capped at 10h
        assertThat(dayWorkTime.exceedsMaxHours).isTrue()
    }

    @Test
    fun scenarioI_vacation_specialVacation_neutralForFlextime() = runTest {
        val settings = Settings(
            id = 1,
            dailyWorkMinutes = 426,
            monthlyWorkMinutes = 9266,
            officeQuotaPercent = 40,
            officeQuotaMinDays = 8,
            initialFlextimeMinutes = 100,
            initialOvertimeMinutes = 0,
            annualVacationDays = 30,
            carryOverVacationDays = 0,
            specialVacationDays = 5,
            settingsYear = 2026
        )
        settingsRepository.saveSettings(settings)

        // Add vacation and special vacation days
        val days = listOf(
            WorkDay(
                date = LocalDate.of(2026, 2, 10),
                location = WorkLocation.OFFICE,
                dayType = DayType.VACATION,
                isPlanned = false,
                note = null,
                timeBlocks = emptyList()
            ),
            WorkDay(
                date = LocalDate.of(2026, 2, 11),
                location = WorkLocation.OFFICE,
                dayType = DayType.SPECIAL_VACATION,
                isPlanned = false,
                note = null,
                timeBlocks = emptyList()
            ),
            WorkDay(
                date = LocalDate.of(2026, 2, 12),
                location = WorkLocation.OFFICE,
                dayType = DayType.WORK,
                isPlanned = false,
                note = null,
                timeBlocks = listOf(TimeBlock(workDayId = 0, startTime = LocalTime.of(8, 0), endTime = LocalTime.of(16, 6), isDuration = true))
            )
        )

        days.forEach { day ->
            val id = saveWorkDayUseCase(day)
            day.timeBlocks.forEach { block ->
                workDayRepository.saveTimeBlock(block.copy(workDayId = id))
            }
        }

        val savedWorkDays = workDayRepository.getWorkDaysForMonth(YearMonth.of(2026, 2)).first()
        val balance = calculateFlextimeUseCase(savedWorkDays, settings)

        // Vacation and special vacation should not affect flextime
        // Work day: 8:00-16:06 = 486min, earned = 486 - 426 = 60min
        assertThat(balance.earnedMinutes).isEqualTo(60)
        assertThat(balance.totalMinutes).isEqualTo(160) // 100 initial + 60 earned
    }
}
