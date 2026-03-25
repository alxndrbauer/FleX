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

class CalculateFlextimeUseCaseTest {

    private lateinit var calculateDayWorkTime: CalculateDayWorkTimeUseCase
    private lateinit var useCase: CalculateFlextimeUseCase
    private lateinit var settings: Settings

    @BeforeEach
    fun setUp() {
        calculateDayWorkTime = mock()
        useCase = CalculateFlextimeUseCase(calculateDayWorkTime)
        settings = Settings(
            dailyWorkMinutes = 426, // 7h 6min
            monthlyWorkMinutes = 9266,
            initialFlextimeMinutes = 0,
            initialOvertimeMinutes = 0
        )
    }

    // WORK day type tests

    @Test
    fun testWorkDayWhenExactlyDailyWorkMinutesExpectNeutralFlextime() {
        // Worked exactly daily work minutes: delta = 0
        val workDay = createWorkDay(
            date = LocalDate.of(2026, 2, 3),
            dayType = DayType.WORK
        )

        whenever(calculateDayWorkTime.invoke(any())).thenReturn(
            DayWorkTimeResult(
                grossMinutes = 426,
                netMinutes = 426,
                breakMinutes = 0,
                exceedsMaxHours = false
            )
        )

        val result = useCase(listOf(workDay), settings)

        assertThat(result.earnedMinutes).isEqualTo(0) // 426 - 426 = 0
        assertThat(result.totalMinutes).isEqualTo(0)
        assertThat(result.overtimeMinutes).isEqualTo(0)
    }

    @Test
    fun testWorkDayWhenMoreThanDailyWorkMinutesExpectPositiveFlextime() {
        // Worked 8 hours (480 min) instead of 7h 6min (426 min)
        val workDay = createWorkDay(
            date = LocalDate.of(2026, 2, 3),
            dayType = DayType.WORK
        )

        whenever(calculateDayWorkTime.invoke(any())).thenReturn(
            DayWorkTimeResult(
                grossMinutes = 480,
                netMinutes = 480,
                breakMinutes = 0,
                exceedsMaxHours = false
            )
        )

        val result = useCase(listOf(workDay), settings)

        assertThat(result.earnedMinutes).isEqualTo(54) // 480 - 426 = 54
        assertThat(result.totalMinutes).isEqualTo(54)
        assertThat(result.overtimeMinutes).isEqualTo(0) // no overtime for regular WORK days
    }

    @Test
    fun testWorkDayWhenLessThanDailyWorkMinutesExpectNegativeFlextime() {
        // Worked 6 hours (360 min) instead of 7h 6min (426 min)
        val workDay = createWorkDay(
            date = LocalDate.of(2026, 2, 3),
            dayType = DayType.WORK
        )

        whenever(calculateDayWorkTime.invoke(any())).thenReturn(
            DayWorkTimeResult(
                grossMinutes = 360,
                netMinutes = 360,
                breakMinutes = 0,
                exceedsMaxHours = false
            )
        )

        val result = useCase(listOf(workDay), settings)

        assertThat(result.earnedMinutes).isEqualTo(-66) // 360 - 426 = -66
        assertThat(result.totalMinutes).isEqualTo(-66)
        assertThat(result.overtimeMinutes).isEqualTo(0)
    }

    // SATURDAY_BONUS day type tests

    @Test
    fun testSaturdayBonusWhenWorkedExpectFullFlextimeAnd50PercentOvertime() {
        // Saturday work: 8 hours (480 min)
        // Flextime gets full 480, overtime gets 50% bonus = 240
        val workDay = createWorkDay(
            date = LocalDate.of(2026, 2, 7), // Saturday
            dayType = DayType.SATURDAY_BONUS
        )

        whenever(calculateDayWorkTime.invoke(any())).thenReturn(
            DayWorkTimeResult(
                grossMinutes = 480,
                netMinutes = 480,
                breakMinutes = 30,
                exceedsMaxHours = false
            )
        )

        val result = useCase(listOf(workDay), settings)

        assertThat(result.earnedMinutes).isEqualTo(480) // full work time
        assertThat(result.totalMinutes).isEqualTo(480)
        assertThat(result.overtimeMinutes).isEqualTo(240) // 480 * 0.5 = 240
    }

    @Test
    fun testSaturdayBonusWhenPartialHoursExpectRoundedBonus() {
        // Saturday work: 5.5 hours (330 min)
        // Overtime bonus: 330 * 0.5 = 165
        val workDay = createWorkDay(
            date = LocalDate.of(2026, 2, 7),
            dayType = DayType.SATURDAY_BONUS
        )

        whenever(calculateDayWorkTime.invoke(any())).thenReturn(
            DayWorkTimeResult(
                grossMinutes = 330,
                netMinutes = 330,
                breakMinutes = 0,
                exceedsMaxHours = false
            )
        )

        val result = useCase(listOf(workDay), settings)

        assertThat(result.earnedMinutes).isEqualTo(330)
        assertThat(result.overtimeMinutes).isEqualTo(165) // 330 * 0.5 = 165
    }

    @Test
    fun testSaturdayBonusWhenNoWorkExpectNoChange() {
        // Saturday with no time blocks
        val workDay = createWorkDay(
            date = LocalDate.of(2026, 2, 7),
            dayType = DayType.SATURDAY_BONUS
        )

        whenever(calculateDayWorkTime.invoke(any())).thenReturn(
            DayWorkTimeResult(
                grossMinutes = 0,
                netMinutes = 0,
                breakMinutes = 0,
                exceedsMaxHours = false
            )
        )

        val result = useCase(listOf(workDay), settings)

        assertThat(result.earnedMinutes).isEqualTo(0)
        assertThat(result.overtimeMinutes).isEqualTo(0)
    }

    // FLEX_DAY type tests

    @Test
    fun testFlexDayExpectNegativeDailyWorkMinutes() {
        // Flex day: deduct full daily work time
        val workDay = createWorkDay(
            date = LocalDate.of(2026, 2, 5),
            dayType = DayType.FLEX_DAY
        )

        val result = useCase(listOf(workDay), settings)

        assertThat(result.earnedMinutes).isEqualTo(-426) // -dailyWorkMinutes
        assertThat(result.totalMinutes).isEqualTo(-426)
        assertThat(result.overtimeMinutes).isEqualTo(0)
    }

    // VACATION and SPECIAL_VACATION tests

    @Test
    fun testVacationExpectNeutralFlextime() {
        val workDay = createWorkDay(
            date = LocalDate.of(2026, 2, 4),
            dayType = DayType.VACATION
        )

        val result = useCase(listOf(workDay), settings)

        assertThat(result.earnedMinutes).isEqualTo(0) // neutral
        assertThat(result.totalMinutes).isEqualTo(0)
        assertThat(result.overtimeMinutes).isEqualTo(0)
    }

    @Test
    fun testSpecialVacationExpectNeutralFlextime() {
        val workDay = createWorkDay(
            date = LocalDate.of(2026, 2, 4),
            dayType = DayType.SPECIAL_VACATION
        )

        val result = useCase(listOf(workDay), settings)

        assertThat(result.earnedMinutes).isEqualTo(0) // neutral
        assertThat(result.totalMinutes).isEqualTo(0)
        assertThat(result.overtimeMinutes).isEqualTo(0)
    }

    // Initial balance tests

    @Test
    fun testInitialFlextimeWhenPositiveExpectAddedToTotal() {
        val settingsWithInitial = settings.copy(initialFlextimeMinutes = 120)
        val workDay = createWorkDay(
            date = LocalDate.of(2026, 2, 3),
            dayType = DayType.WORK
        )

        whenever(calculateDayWorkTime.invoke(any())).thenReturn(
            DayWorkTimeResult(netMinutes = 426, grossMinutes = 426, breakMinutes = 0, exceedsMaxHours = false)
        )

        val result = useCase(listOf(workDay), settingsWithInitial)

        assertThat(result.initialMinutes).isEqualTo(120)
        assertThat(result.earnedMinutes).isEqualTo(0)
        assertThat(result.totalMinutes).isEqualTo(120) // initial + earned
    }

    @Test
    fun testInitialOvertimeWhenPositiveExpectAddedToOvertime() {
        val settingsWithInitial = settings.copy(initialOvertimeMinutes = 60)
        val workDay = createWorkDay(
            date = LocalDate.of(2026, 2, 7),
            dayType = DayType.SATURDAY_BONUS
        )

        whenever(calculateDayWorkTime.invoke(any())).thenReturn(
            DayWorkTimeResult(netMinutes = 480, grossMinutes = 480, breakMinutes = 0, exceedsMaxHours = false)
        )

        val result = useCase(listOf(workDay), settingsWithInitial)

        assertThat(result.overtimeMinutes).isEqualTo(300) // 60 + (480 * 0.5)
    }

    // Cumulative calculation tests

    @Test
    fun testMultipleDaysWhenMixedTypesExpectCumulativeCalculation() {
        val workDays = listOf(
            createWorkDay(LocalDate.of(2026, 2, 3), DayType.WORK),
            createWorkDay(LocalDate.of(2026, 2, 4), DayType.WORK),
            createWorkDay(LocalDate.of(2026, 2, 5), DayType.FLEX_DAY),
            createWorkDay(LocalDate.of(2026, 2, 6), DayType.VACATION),
            createWorkDay(LocalDate.of(2026, 2, 7), DayType.SATURDAY_BONUS)
        )

        // Day 1: WORK - worked 480 min (54 min flextime = 480 - 426)
        // Day 2: WORK - worked 360 min (-66 min flextime = 360 - 426)
        // Day 3: FLEX_DAY - (-426 min flextime, doesn't call calculateDayWorkTime)
        // Day 4: VACATION - (0 min flextime, doesn't call calculateDayWorkTime)
        // Day 5: SATURDAY_BONUS - 300 min (300 min flextime + 150 overtime)

        // calculateDayWorkTime is called only for days 1, 2, and 5
        whenever(calculateDayWorkTime.invoke(any())).thenReturn(
            DayWorkTimeResult(netMinutes = 480, grossMinutes = 480, breakMinutes = 0, exceedsMaxHours = false),
            DayWorkTimeResult(netMinutes = 360, grossMinutes = 360, breakMinutes = 0, exceedsMaxHours = false),
            DayWorkTimeResult(netMinutes = 300, grossMinutes = 300, breakMinutes = 0, exceedsMaxHours = false)
        )

        val result = useCase(workDays, settings)

        // earned = 54 - 66 - 426 + 0 + 300 = -138
        assertThat(result.earnedMinutes).isEqualTo(-138)
        assertThat(result.totalMinutes).isEqualTo(-138)
        assertThat(result.overtimeMinutes).isEqualTo(150) // only from Saturday
    }

    // Target minutes calculation tests

    @Test
    fun testTargetMinutesWhenYearMonthProvidedExpectWorkingDaysCalculation() {
        // February 2026: 28 days
        // Saturdays: 7, 14, 21, 28
        // Sundays: 1, 8, 15, 22
        // Working days (Mon-Fri): 28 - 8 = 20 days
        // But we need to check public holidays - for this test assume no holidays in Hamburg
        val yearMonth = YearMonth.of(2026, 2)

        val result = useCase(emptyList(), settings, yearMonth)

        // Feb 2026: 20 working days (Mon-Fri) * 426 = 8520 minutes
        // Note: actual calculation excludes public holidays
        assertThat(result.targetMinutes).isGreaterThan(0)
    }

    @Test
    fun testTargetMinutesWhenNoYearMonthExpectZero() {
        val result = useCase(emptyList(), settings, yearMonth = null)

        assertThat(result.targetMinutes).isEqualTo(0)
    }

    // Weekend work day type tests (Saturday/Sunday WORK days count full time as flextime)

    @Test
    fun testSaturdayWorkDay_shouldCountFullTimeAsFlextime() {
        // Saturday work day with exactly daily work minutes
        val saturdayWorkDay = createWorkDay(
            date = LocalDate.of(2026, 2, 14),  // Saturday
            dayType = DayType.WORK
        )

        whenever(calculateDayWorkTime.invoke(any())).thenReturn(
            DayWorkTimeResult(
                grossMinutes = 426,
                netMinutes = 426,
                breakMinutes = 0,
                exceedsMaxHours = false
            )
        )

        val balance = useCase(listOf(saturdayWorkDay), settings)

        // Saturday work should count FULL time as flextime (not 426 - 426 = 0)
        assertThat(balance.earnedMinutes).isEqualTo(426)  // Full time counts!
        assertThat(balance.totalMinutes).isEqualTo(426)
    }

    @Test
    fun testSaturdayWorkDay_withExtraHours_shouldCountFullTimeAsFlextime() {
        // Saturday work day with extra hours
        val saturdayWorkDay = createWorkDay(
            date = LocalDate.of(2026, 2, 14),  // Saturday
            dayType = DayType.WORK
        )

        whenever(calculateDayWorkTime.invoke(any())).thenReturn(
            DayWorkTimeResult(
                grossMinutes = 540,
                netMinutes = 540,
                breakMinutes = 0,
                exceedsMaxHours = false
            )
        )

        val balance = useCase(listOf(saturdayWorkDay), settings)

        // Saturday work should count FULL 9 hours as flextime
        assertThat(balance.earnedMinutes).isEqualTo(540)
        assertThat(balance.totalMinutes).isEqualTo(540)
    }

    @Test
    fun testMondayWorkDay_stillCountsOnlyExtraAsFlextime() {
        // Monday work day - should use old logic (only extra over 426 min)
        val mondayWorkDay = createWorkDay(
            date = LocalDate.of(2026, 2, 10),  // Monday
            dayType = DayType.WORK
        )

        whenever(calculateDayWorkTime.invoke(any())).thenReturn(
            DayWorkTimeResult(
                grossMinutes = 426,
                netMinutes = 426,
                breakMinutes = 0,
                exceedsMaxHours = false
            )
        )

        val balance = useCase(listOf(mondayWorkDay), settings)

        // Monday work with exactly 426 min should be 0 flextime
        assertThat(balance.earnedMinutes).isEqualTo(0)
        assertThat(balance.totalMinutes).isEqualTo(0)
    }

    @Test
    fun testSundayWorkDay_shouldCountFullTimeAsFlextime() {
        // Sunday is also a weekend day - should count full time
        val sundayWorkDay = createWorkDay(
            date = LocalDate.of(2026, 2, 15),  // Sunday
            dayType = DayType.WORK
        )

        whenever(calculateDayWorkTime.invoke(any())).thenReturn(
            DayWorkTimeResult(
                grossMinutes = 426,
                netMinutes = 426,
                breakMinutes = 0,
                exceedsMaxHours = false
            )
        )

        val balance = useCase(listOf(sundayWorkDay), settings)

        // Sunday work should count FULL time as flextime
        assertThat(balance.earnedMinutes).isEqualTo(426)
        assertThat(balance.totalMinutes).isEqualTo(426)
    }

    @Test
    fun testSaturdayBonusDay_stillUses50Split() {
        // SATURDAY_BONUS should still use 50/50 split (not change behavior)
        val saturdayBonus = createWorkDay(
            date = LocalDate.of(2026, 2, 14),  // Saturday
            dayType = DayType.SATURDAY_BONUS
        )

        whenever(calculateDayWorkTime.invoke(any())).thenReturn(
            DayWorkTimeResult(
                grossMinutes = 426,
                netMinutes = 426,
                breakMinutes = 0,
                exceedsMaxHours = false
            )
        )

        val balance = useCase(listOf(saturdayBonus), settings)

        // SATURDAY_BONUS: 100% to flextime, 50% to overtime
        assertThat(balance.earnedMinutes).isEqualTo(426)  // Full to flextime
        assertThat(balance.overtimeMinutes).isEqualTo(213)  // 50% to overtime (426 * 0.5 = 213)
    }

    // Edge cases

    @Test
    fun testEmptyWorkDaysListExpectZeroBalance() {
        val result = useCase(emptyList(), settings)

        assertThat(result.earnedMinutes).isEqualTo(0)
        assertThat(result.totalMinutes).isEqualTo(0)
        assertThat(result.overtimeMinutes).isEqualTo(0)
    }

    @Test
    fun testNegativeInitialFlextimeExpectCorrectCalculation() {
        val settingsWithNegative = settings.copy(initialFlextimeMinutes = -180)
        val workDay = createWorkDay(
            date = LocalDate.of(2026, 2, 3),
            dayType = DayType.WORK
        )

        whenever(calculateDayWorkTime.invoke(any())).thenReturn(
            DayWorkTimeResult(netMinutes = 480, grossMinutes = 480, breakMinutes = 0, exceedsMaxHours = false)
        )

        val result = useCase(listOf(workDay), settingsWithNegative)

        assertThat(result.initialMinutes).isEqualTo(-180)
        assertThat(result.earnedMinutes).isEqualTo(54) // 480 - 426
        assertThat(result.totalMinutes).isEqualTo(-126) // -180 + 54
    }

    // Helper function
    private fun createWorkDay(
        date: LocalDate,
        dayType: DayType,
        location: WorkLocation = WorkLocation.OFFICE
    ): WorkDay {
        return WorkDay(
            id = 1,
            date = date,
            location = location,
            dayType = dayType,
            timeBlocks = listOf(
                TimeBlock(
                    id = 1,
                    workDayId = 1,
                    startTime = LocalTime.of(9, 0),
                    endTime = LocalTime.of(17, 0)
                )
            )
        )
    }
}
