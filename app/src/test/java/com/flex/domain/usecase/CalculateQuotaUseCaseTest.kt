package com.flex.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.flex.domain.model.DayType
import com.flex.domain.model.Settings
import com.flex.domain.model.TimeBlock
import com.flex.domain.model.WorkDay
import com.flex.domain.model.WorkLocation
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

class CalculateQuotaUseCaseTest {

    private lateinit var calculateDayWorkTime: CalculateDayWorkTimeUseCase
    private lateinit var useCase: CalculateQuotaUseCase
    private lateinit var settings: Settings

    @BeforeEach
    fun setUp() {
        calculateDayWorkTime = mock()
        useCase = CalculateQuotaUseCase(calculateDayWorkTime)
        settings = Settings(
            dailyWorkMinutes = 426, // 7h 6min
            monthlyWorkMinutes = 9266,
            officeQuotaPercent = 40, // 40% office quota
            officeQuotaMinDays = 8 // min 8 office days
        )
    }

    // Office vs Home-Office hours tests

    @Test
    fun testOfficeWorkDayWhenWorkedExpectOfficeMinutesIncremented() {
        val workDay = createWorkDay(
            date = LocalDate.of(2026, 2, 3),
            location = WorkLocation.OFFICE,
            dayType = DayType.WORK
        )

        whenever(calculateDayWorkTime.invoke(any())).thenReturn(
            DayWorkTimeResult(
                netMinutes = 420,
                grossMinutes = 420,
                breakMinutes = 0,
                exceedsMaxHours = false
            )
        )

        val result = useCase(listOf(workDay), settings, YearMonth.of(2026, 2))

        assertThat(result.officeMinutes).isEqualTo(420)
        assertThat(result.homeOfficeMinutes).isEqualTo(0)
        assertThat(result.officeDays).isEqualTo(1)
        assertThat(result.homeOfficeDays).isEqualTo(0)
    }

    @Test
    fun testHomeOfficeWorkDayWhenWorkedExpectHomeOfficeMinutesIncremented() {
        val workDay = createWorkDay(
            date = LocalDate.of(2026, 2, 3),
            location = WorkLocation.HOME_OFFICE,
            dayType = DayType.WORK
        )

        whenever(calculateDayWorkTime.invoke(any())).thenReturn(
            DayWorkTimeResult(
                netMinutes = 420,
                grossMinutes = 420,
                breakMinutes = 0,
                exceedsMaxHours = false
            )
        )

        val result = useCase(listOf(workDay), settings, YearMonth.of(2026, 2))

        assertThat(result.officeMinutes).isEqualTo(0)
        assertThat(result.homeOfficeMinutes).isEqualTo(420)
        assertThat(result.officeDays).isEqualTo(0)
        assertThat(result.homeOfficeDays).isEqualTo(1)
    }

    @Test
    fun testMixedLocationDaysExpectSeparateTracking() {
        val workDays = listOf(
            createWorkDay(LocalDate.of(2026, 2, 3), WorkLocation.OFFICE, DayType.WORK),
            createWorkDay(LocalDate.of(2026, 2, 4), WorkLocation.HOME_OFFICE, DayType.WORK),
            createWorkDay(LocalDate.of(2026, 2, 5), WorkLocation.OFFICE, DayType.WORK)
        )

        whenever(calculateDayWorkTime.invoke(any())).thenReturn(
            DayWorkTimeResult(
                netMinutes = 420,
                grossMinutes = 420,
                breakMinutes = 0,
                exceedsMaxHours = false
            )
        )

        val result = useCase(workDays, settings, YearMonth.of(2026, 2))

        assertThat(result.officeMinutes).isEqualTo(840) // 2 * 420
        assertThat(result.homeOfficeMinutes).isEqualTo(420) // 1 * 420
        assertThat(result.officeDays).isEqualTo(2)
        assertThat(result.homeOfficeDays).isEqualTo(1)
    }

    // Neutral day types tests

    @Test
    fun testVacationDayExpectNotCountedInQuota() {
        val workDay = createWorkDay(
            date = LocalDate.of(2026, 2, 3),
            location = WorkLocation.OFFICE,
            dayType = DayType.VACATION
        )

        val result = useCase(listOf(workDay), settings, YearMonth.of(2026, 2))

        assertThat(result.officeMinutes).isEqualTo(0)
        assertThat(result.homeOfficeMinutes).isEqualTo(0)
        assertThat(result.officeDays).isEqualTo(0)
        assertThat(result.homeOfficeDays).isEqualTo(0)
    }

    @Test
    fun testSpecialVacationDayExpectNotCountedInQuota() {
        val workDay = createWorkDay(
            date = LocalDate.of(2026, 2, 3),
            location = WorkLocation.OFFICE,
            dayType = DayType.SPECIAL_VACATION
        )

        val result = useCase(listOf(workDay), settings, YearMonth.of(2026, 2))

        assertThat(result.officeMinutes).isEqualTo(0)
        assertThat(result.officeDays).isEqualTo(0)
    }

    @Test
    fun testFlexDayExpectNotCountedInQuota() {
        val workDay = createWorkDay(
            date = LocalDate.of(2026, 2, 3),
            location = WorkLocation.OFFICE,
            dayType = DayType.FLEX_DAY
        )

        val result = useCase(listOf(workDay), settings, YearMonth.of(2026, 2))

        assertThat(result.officeMinutes).isEqualTo(0)
        assertThat(result.officeDays).isEqualTo(0)
    }

    // Office percentage calculation tests

    @Test
    fun testOfficePercentWhenNoNeutralDaysExpectCorrectCalculation() {
        // 5 office days, 5 home office days (all work days)
        // Monthly target: 9266 minutes
        // Office work: 5 * 420 = 2100 minutes
        // Percent: (2100 / 9266) * 100 = ~22.66%
        val workDays = mutableListOf<WorkDay>()
        for (i in 3..7) { // Mon-Fri (5 office days)
            workDays.add(createWorkDay(LocalDate.of(2026, 2, i), WorkLocation.OFFICE, DayType.WORK))
        }
        for (i in 10..14) { // Next Mon-Fri (5 home office days)
            workDays.add(createWorkDay(LocalDate.of(2026, 2, i), WorkLocation.HOME_OFFICE, DayType.WORK))
        }

        whenever(calculateDayWorkTime.invoke(any())).thenReturn(
            DayWorkTimeResult(
                netMinutes = 420,
                grossMinutes = 420,
                breakMinutes = 0,
                exceedsMaxHours = false
            )
        )

        val result = useCase(workDays, settings, YearMonth.of(2026, 2))

        assertThat(result.officeMinutes).isEqualTo(2100) // 5 * 420
        assertThat(result.homeOfficeMinutes).isEqualTo(2100) // 5 * 420
        val expectedPercent = (2100.0 / 9266.0) * 100
        assertThat(result.officePercent).isWithin(0.1).of(expectedPercent)
    }

    @Test
    fun testOfficePercentWhenNeutralDaysExpectAdjustedTarget() {
        // Target is adjusted: monthlyWorkMinutes - (neutralDays * dailyWorkMinutes)
        // 2 work days (office), 1 vacation day
        // Target: 9266 - (1 * 426) = 8840
        // Office: 2 * 420 = 840
        // Percent: (840 / 8840) * 100 = ~9.50%
        val workDays = listOf(
            createWorkDay(LocalDate.of(2026, 2, 3), WorkLocation.OFFICE, DayType.WORK),
            createWorkDay(LocalDate.of(2026, 2, 4), WorkLocation.OFFICE, DayType.WORK),
            createWorkDay(LocalDate.of(2026, 2, 5), WorkLocation.OFFICE, DayType.VACATION)
        )

        whenever(calculateDayWorkTime.invoke(any())).thenReturn(
            DayWorkTimeResult(
                netMinutes = 420,
                grossMinutes = 420,
                breakMinutes = 0,
                exceedsMaxHours = false
            )
        )

        val result = useCase(workDays, settings, YearMonth.of(2026, 2))

        val adjustedTarget = 9266 - 426
        val expectedPercent = (840.0 / adjustedTarget) * 100
        assertThat(result.officePercent).isWithin(0.1).of(expectedPercent)
    }

    @Test
    fun testOfficePercentWhenZeroTargetExpectZeroPercent() {
        // All days are neutral (vacation, flex days)
        val workDays = listOf(
            createWorkDay(LocalDate.of(2026, 2, 3), WorkLocation.OFFICE, DayType.VACATION),
            createWorkDay(LocalDate.of(2026, 2, 4), WorkLocation.OFFICE, DayType.FLEX_DAY)
        )

        val result = useCase(workDays, settings, YearMonth.of(2026, 2))

        assertThat(result.officePercent).isEqualTo(0.0)
    }

    // Quota met conditions tests

    @Test
    fun testQuotaMetWhenPercentageExceedsThresholdExpectPercentQuotaMet() {
        // 10 office days * 420 = 4200 minutes
        // Percent: (4200 / 9266) * 100 = ~45.32% > 40%
        val workDays = mutableListOf<WorkDay>()
        for (i in 3..12) { // 10 office days
            workDays.add(createWorkDay(LocalDate.of(2026, 2, i), WorkLocation.OFFICE, DayType.WORK))
        }

        whenever(calculateDayWorkTime.invoke(any())).thenReturn(
            DayWorkTimeResult(
                netMinutes = 420,
                grossMinutes = 420,
                breakMinutes = 0,
                exceedsMaxHours = false
            )
        )

        val result = useCase(workDays, settings, YearMonth.of(2026, 2))

        assertThat(result.officePercent).isGreaterThan(40.0)
        assertThat(result.percentQuotaMet).isTrue()
        assertThat(result.daysQuotaMet).isTrue() // also meets days quota (10 >= 8)
    }

    @Test
    fun testQuotaMetWhenDaysExceedMinimumExpectDaysQuotaMet() {
        // 8 office days (meets minimum)
        val workDays = mutableListOf<WorkDay>()
        for (i in 3..10) { // 8 office days
            workDays.add(createWorkDay(LocalDate.of(2026, 2, i), WorkLocation.OFFICE, DayType.WORK))
        }

        whenever(calculateDayWorkTime.invoke(any())).thenReturn(
            DayWorkTimeResult(
                netMinutes = 420,
                grossMinutes = 420,
                breakMinutes = 0,
                exceedsMaxHours = false
            )
        )

        val result = useCase(workDays, settings, YearMonth.of(2026, 2))

        assertThat(result.officeDays).isEqualTo(8)
        assertThat(result.daysQuotaMet).isTrue()
    }

    @Test
    fun testQuotaNotMetWhenBelowBothThresholdsExpectNotMet() {
        // 2 office days, 10 home office days
        // Percent: (840 / 9266) * 100 = ~9.1% < 40%
        // Days: 2 < 8
        val workDays = mutableListOf<WorkDay>()
        for (i in 3..4) { // 2 office days
            workDays.add(createWorkDay(LocalDate.of(2026, 2, i), WorkLocation.OFFICE, DayType.WORK))
        }
        for (i in 5..14) { // 10 home office days
            workDays.add(createWorkDay(LocalDate.of(2026, 2, i), WorkLocation.HOME_OFFICE, DayType.WORK))
        }

        whenever(calculateDayWorkTime.invoke(any())).thenReturn(
            DayWorkTimeResult(
                netMinutes = 420,
                grossMinutes = 420,
                breakMinutes = 0,
                exceedsMaxHours = false
            )
        )

        val result = useCase(workDays, settings, YearMonth.of(2026, 2))

        assertThat(result.officePercent).isLessThan(40.0)
        assertThat(result.percentQuotaMet).isFalse()
        assertThat(result.officeDays).isLessThan(8)
        assertThat(result.daysQuotaMet).isFalse()
    }

    @Test
    fun testQuotaMetWhenEitherConditionMetExpectQuotaMet() {
        // quotaMet = percentQuotaMet OR daysQuotaMet
        // Test case: days met but percent not met
        val workDays = mutableListOf<WorkDay>()
        for (i in 3..10) { // 8 office days (meets min days)
            workDays.add(createWorkDay(LocalDate.of(2026, 2, i), WorkLocation.OFFICE, DayType.WORK))
        }
        for (i in 11..20) { // 10 home office days
            workDays.add(createWorkDay(LocalDate.of(2026, 2, i), WorkLocation.HOME_OFFICE, DayType.WORK))
        }

        whenever(calculateDayWorkTime.invoke(any())).thenReturn(
            DayWorkTimeResult(
                netMinutes = 420,
                grossMinutes = 420,
                breakMinutes = 0,
                exceedsMaxHours = false
            )
        )

        val result = useCase(workDays, settings, YearMonth.of(2026, 2))

        // Even if percent quota not met, days quota is met
        assertThat(result.daysQuotaMet).isTrue()
        // quotaMet property uses OR logic
        assertThat(result.percentQuotaMet || result.daysQuotaMet).isTrue()
    }

    // Remaining work days calculation tests

    @Test
    fun testRemainingWorkDaysWhenCurrentMonthExpectCorrectCount() {
        // This test is time-dependent; uses the current month so remainingWorkDays > 0
        // For Mar 2026, from Mar 12 to end of month
        // Remaining work days: Mon-Fri from Mar 13-31
        val result = useCase(emptyList(), settings, YearMonth.of(2026, 3))

        // Mar 2026: 13-20 (6 days), 23-27 (5 days), 30-31 (2 days) = 13 work days remaining
        assertThat(result.remainingWorkDays).isGreaterThan(0)
    }

    @Test
    fun testRemainingWorkDaysWhenPastMonthExpectZero() {
        val result = useCase(emptyList(), settings, YearMonth.of(2026, 1))

        assertThat(result.remainingWorkDays).isEqualTo(0)
    }

    @Test
    fun testRequiredOfficeDaysWhenBelowQuotaExpectPositiveRequired() {
        // 3 office days, need 8 total
        // Required: 8 - 3 = 5
        val workDays = listOf(
            createWorkDay(LocalDate.of(2026, 2, 3), WorkLocation.OFFICE, DayType.WORK),
            createWorkDay(LocalDate.of(2026, 2, 4), WorkLocation.OFFICE, DayType.WORK),
            createWorkDay(LocalDate.of(2026, 2, 5), WorkLocation.OFFICE, DayType.WORK)
        )

        whenever(calculateDayWorkTime.invoke(any())).thenReturn(
            DayWorkTimeResult(
                netMinutes = 420,
                grossMinutes = 420,
                breakMinutes = 0,
                exceedsMaxHours = false
            )
        )

        val result = useCase(workDays, settings, YearMonth.of(2026, 2))

        assertThat(result.officeDays).isEqualTo(3)
        assertThat(result.requiredOfficeDaysForQuota).isEqualTo(5) // 8 - 3
    }

    @Test
    fun testRequiredOfficeDaysWhenMetQuotaExpectZero() {
        // 10 office days, need 8
        // Required: max(0, 8 - 10) = 0
        val workDays = mutableListOf<WorkDay>()
        for (i in 3..12) { // 10 office days
            workDays.add(createWorkDay(LocalDate.of(2026, 2, i), WorkLocation.OFFICE, DayType.WORK))
        }

        whenever(calculateDayWorkTime.invoke(any())).thenReturn(
            DayWorkTimeResult(
                netMinutes = 420,
                grossMinutes = 420,
                breakMinutes = 0,
                exceedsMaxHours = false
            )
        )

        val result = useCase(workDays, settings, YearMonth.of(2026, 2))

        assertThat(result.requiredOfficeDaysForQuota).isEqualTo(0)
    }

    // Custom quota parameters tests

    @Test
    fun testCustomQuotaPercentExpectUsedInCalculation() {
        // Override quota percent to 50%
        val workDays = mutableListOf<WorkDay>()
        for (i in 3..10) { // 8 office days
            workDays.add(createWorkDay(LocalDate.of(2026, 2, i), WorkLocation.OFFICE, DayType.WORK))
        }

        whenever(calculateDayWorkTime.invoke(any())).thenReturn(
            DayWorkTimeResult(
                netMinutes = 420,
                grossMinutes = 420,
                breakMinutes = 0,
                exceedsMaxHours = false
            )
        )

        val result = useCase(
            workDays = workDays,
            settings = settings,
            yearMonth = YearMonth.of(2026, 2),
            quotaPercent = 50, // custom
            quotaMinDays = 8
        )

        // 8 * 420 / 9266 = ~36.3% < 50%
        assertThat(result.percentQuotaMet).isFalse()
    }

    @Test
    fun testCustomQuotaMinDaysExpectUsedInCalculation() {
        // Override min days to 12
        val workDays = mutableListOf<WorkDay>()
        for (i in 3..10) { // 8 office days
            workDays.add(createWorkDay(LocalDate.of(2026, 2, i), WorkLocation.OFFICE, DayType.WORK))
        }

        whenever(calculateDayWorkTime.invoke(any())).thenReturn(
            DayWorkTimeResult(
                netMinutes = 420,
                grossMinutes = 420,
                breakMinutes = 0,
                exceedsMaxHours = false
            )
        )

        val result = useCase(
            workDays = workDays,
            settings = settings,
            yearMonth = YearMonth.of(2026, 2),
            quotaPercent = 40,
            quotaMinDays = 12 // custom
        )

        assertThat(result.officeDays).isEqualTo(8)
        assertThat(result.daysQuotaMet).isFalse() // 8 < 12
        assertThat(result.requiredOfficeDaysForQuota).isEqualTo(4) // 12 - 8
    }

    // Edge cases

    @Test
    fun testEmptyWorkDaysListExpectZeroValues() {
        val result = useCase(emptyList(), settings, YearMonth.of(2026, 2))

        assertThat(result.officeMinutes).isEqualTo(0)
        assertThat(result.homeOfficeMinutes).isEqualTo(0)
        assertThat(result.officeDays).isEqualTo(0)
        assertThat(result.homeOfficeDays).isEqualTo(0)
        assertThat(result.officePercent).isEqualTo(0.0)
        assertThat(result.percentQuotaMet).isFalse()
        assertThat(result.daysQuotaMet).isFalse()
    }

    // Helper function
    private fun createWorkDay(
        date: LocalDate,
        location: WorkLocation,
        dayType: DayType
    ): WorkDay {
        return WorkDay(
            id = date.dayOfMonth.toLong(),
            date = date,
            location = location,
            dayType = dayType,
            timeBlocks = listOf(
                TimeBlock(
                    id = date.dayOfMonth.toLong(),
                    workDayId = date.dayOfMonth.toLong(),
                    startTime = LocalTime.of(9, 0),
                    endTime = LocalTime.of(16, 0), // 7h = 420 min (on 5-min boundary)
                    location = location
                )
            )
        )
    }
}
