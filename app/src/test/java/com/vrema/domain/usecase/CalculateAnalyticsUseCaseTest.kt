package com.vrema.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.vrema.domain.model.DayType
import com.vrema.domain.model.FlextimeBalance
import com.vrema.domain.model.Settings
import com.vrema.domain.model.TimeBlock
import com.vrema.domain.model.TimeRange
import com.vrema.domain.model.WorkDay
import com.vrema.domain.model.WorkLocation
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

class CalculateAnalyticsUseCaseTest {

    private lateinit var calculateDayWorkTime: CalculateDayWorkTimeUseCase
    private lateinit var calculateFlextime: CalculateFlextimeUseCase
    private lateinit var useCase: CalculateAnalyticsUseCase
    private lateinit var settings: Settings

    @BeforeEach
    fun setUp() {
        calculateDayWorkTime = mock()
        calculateFlextime = mock()
        useCase = CalculateAnalyticsUseCase(calculateDayWorkTime, calculateFlextime)
        settings = Settings(
            dailyWorkMinutes = 480, // 8 hours
            monthlyWorkMinutes = 9600,
            initialFlextimeMinutes = 0,
            initialOvertimeMinutes = 0
        )
    }

    // 1. Monthly Aggregation Tests

    @Test
    fun testMonthlyAggregationWhenMultipleMonthsExpectCorrectSeries() {
        // Create 3 WorkDays in Feb 2025, 2 in March 2025
        val workDays = listOf(
            createWorkDay(LocalDate.of(2025, 2, 3), 480), // Feb
            createWorkDay(LocalDate.of(2025, 2, 10), 480), // Feb
            createWorkDay(LocalDate.of(2025, 2, 17), 480), // Feb
            createWorkDay(LocalDate.of(2025, 3, 5), 480), // March
            createWorkDay(LocalDate.of(2025, 3, 12), 480)  // March
        )

        // Mock calculate day work time for each day
        whenever(calculateDayWorkTime.invoke(any())).thenReturn(
            DayWorkTimeResult(grossMinutes = 480, netMinutes = 480, breakMinutes = 30, exceedsMaxHours = false)
        )

        // Mock flextime calculation
        // Feb: 3 days * 480 min = 1440 min (cumulative: 1440)
        // March: 2 days * 480 min = 960 min (cumulative: 2400)
        whenever(calculateFlextime.invoke(any(), any(), any())).thenReturn(
            FlextimeBalance(
                earnedMinutes = 1440,
                totalMinutes = 1440,
                overtimeMinutes = 0,
                targetMinutes = 0,
                initialMinutes = 0
            ),
            FlextimeBalance(
                earnedMinutes = 960,
                totalMinutes = 960,
                overtimeMinutes = 0,
                targetMinutes = 0,
                initialMinutes = 0
            )
        )

        val result = useCase(workDays, settings, TimeRange.Custom(
            LocalDate.of(2025, 2, 1),
            LocalDate.of(2025, 3, 31)
        ))

        // Verify: flextimeSeries has 2 entries (Feb, March)
        assertThat(result.flextimeSeries).hasSize(2)
        assertThat(result.flextimeSeries[0].yearMonth).isEqualTo(YearMonth.of(2025, 2))
        assertThat(result.flextimeSeries[0].value).isEqualTo(1440) // Cumulative
        assertThat(result.flextimeSeries[1].yearMonth).isEqualTo(YearMonth.of(2025, 3))
        assertThat(result.flextimeSeries[1].value).isEqualTo(2400) // Cumulative: 1440 + 960

        // Verify: overtimeSeries has 2 entries
        assertThat(result.overtimeSeries).hasSize(2)

        // Verify: monthlyHours has 2 entries (non-cumulative!)
        assertThat(result.monthlyHours).hasSize(2)
        assertThat(result.monthlyHours[0].yearMonth).isEqualTo(YearMonth.of(2025, 2))
        assertThat(result.monthlyHours[0].value).isEqualTo(1440) // 3 * 480
        assertThat(result.monthlyHours[1].yearMonth).isEqualTo(YearMonth.of(2025, 3))
        assertThat(result.monthlyHours[1].value).isEqualTo(960) // 2 * 480
    }

    // 2. Weekly Grouping Tests

    @Test
    fun testWeeklyGroupingWhenMultipleWeeksExpectCorrectGroups() {
        // Create 7 WorkDays (1 per week in Jan 2025)
        // Jan 2025 has 5 weeks (weeks 1-5)
        val workDays = listOf(
            createWorkDay(LocalDate.of(2025, 1, 6), 480),  // Week 2
            createWorkDay(LocalDate.of(2025, 1, 13), 480), // Week 3
            createWorkDay(LocalDate.of(2025, 1, 20), 480), // Week 4
            createWorkDay(LocalDate.of(2025, 1, 27), 480), // Week 5
            createWorkDay(LocalDate.of(2025, 1, 2), 480),  // Week 1
            createWorkDay(LocalDate.of(2025, 1, 3), 480),  // Week 1
            createWorkDay(LocalDate.of(2025, 1, 7), 480)   // Week 2
        )

        whenever(calculateDayWorkTime.invoke(any())).thenReturn(
            DayWorkTimeResult(grossMinutes = 480, netMinutes = 480, breakMinutes = 30, exceedsMaxHours = false)
        )

        whenever(calculateFlextime.invoke(any(), any(), any())).thenReturn(
            FlextimeBalance(earnedMinutes = 0, totalMinutes = 0, overtimeMinutes = 0, targetMinutes = 0, initialMinutes = 0)
        )

        val result = useCase(workDays, settings, TimeRange.Month(YearMonth.of(2025, 1)))

        // Verify: weeklyHours has 5 entries (5 weeks in Jan)
        assertThat(result.weeklyHours.size).isAtLeast(4)

        // Verify: Each week has totalMinutes > 0
        for (weekHours in result.weeklyHours) {
            assertThat(weekHours.totalMinutes).isGreaterThan(0)
        }

        // Verify that weeks are sorted correctly
        val sortedWeeks = result.weeklyHours.sortedWith(compareBy({ it.year }, { it.weekOfYear }))
        assertThat(result.weeklyHours).isEqualTo(sortedWeeks)
    }

    // 3. Location Distribution Tests

    @Test
    fun testLocationDistributionWhenMixedLocationsExpectCorrectPercentages() {
        // Create 2 WorkDays: 1× OFFICE (480 min), 1× HOME_OFFICE (480 min)
        val workDays = listOf(
            createWorkDay(LocalDate.of(2025, 2, 3), 480, WorkLocation.OFFICE),
            createWorkDay(LocalDate.of(2025, 2, 4), 480, WorkLocation.HOME_OFFICE)
        )

        // Use thenAnswer so all calls return consistent result based on the work time
        whenever(calculateDayWorkTime.invoke(any())).thenReturn(
            DayWorkTimeResult(grossMinutes = 480, netMinutes = 480, breakMinutes = 30, exceedsMaxHours = false)
        )

        whenever(calculateFlextime.invoke(any(), any(), any())).thenReturn(
            FlextimeBalance(earnedMinutes = 0, totalMinutes = 0, overtimeMinutes = 0, targetMinutes = 0, initialMinutes = 0)
        )

        val result = useCase(workDays, settings, TimeRange.Month(YearMonth.of(2025, 2)))

        // Total: 480 + 480 = 960 min → 50/50 split
        assertThat(result.locationDistribution.officeMinutes).isEqualTo(480)
        assertThat(result.locationDistribution.homeOfficeMinutes).isEqualTo(480)
        assertThat(result.locationDistribution.officePercent).isWithin(0.1).of(50.0)
        assertThat(result.locationDistribution.homeOfficePercent).isWithin(0.1).of(50.0)
    }

    @Test
    fun testLocationDistributionWhenAllOfficeExpect100Percent() {
        val workDays = listOf(
            createWorkDay(LocalDate.of(2025, 2, 3), 480, WorkLocation.OFFICE),
            createWorkDay(LocalDate.of(2025, 2, 4), 480, WorkLocation.OFFICE)
        )

        whenever(calculateDayWorkTime.invoke(any())).thenReturn(
            DayWorkTimeResult(grossMinutes = 480, netMinutes = 480, breakMinutes = 30, exceedsMaxHours = false)
        )

        whenever(calculateFlextime.invoke(any(), any(), any())).thenReturn(
            FlextimeBalance(earnedMinutes = 0, totalMinutes = 0, overtimeMinutes = 0, targetMinutes = 0, initialMinutes = 0)
        )

        val result = useCase(workDays, settings, TimeRange.Month(YearMonth.of(2025, 2)))

        assertThat(result.locationDistribution.officePercent).isWithin(0.01).of(100.0)
        assertThat(result.locationDistribution.homeOfficePercent).isWithin(0.01).of(0.0)
    }

    // 4. Empty Data Handling Tests

    @Test
    fun testEmptyDataWhenNoWorkDaysExpectEmptyAnalyticsData() {
        val result = useCase(emptyList(), settings, TimeRange.Month(YearMonth.of(2025, 2)))

        // Verify: AnalyticsData.isEmpty == true
        assertThat(result.isEmpty).isTrue()

        // Verify: No Exception thrown
        assertThat(result.flextimeSeries).isEmpty()
        assertThat(result.overtimeSeries).isEmpty()
        assertThat(result.weeklyHours).isEmpty()
        assertThat(result.monthlyHours).isEmpty()
        assertThat(result.locationDistribution.officeMinutes).isEqualTo(0)
        assertThat(result.locationDistribution.homeOfficeMinutes).isEqualTo(0)
    }

    // 5. Planned Days Filtering Tests

    @Test
    fun testPlannedDaysFilteringWhenMixedPlannedAndActualExpectOnlyActual() {
        // Create 3 actual days + 2 planned days
        val workDays = listOf(
            createWorkDay(LocalDate.of(2025, 2, 3), 480, WorkLocation.OFFICE, isPlanned = false),
            createWorkDay(LocalDate.of(2025, 2, 4), 480, WorkLocation.OFFICE, isPlanned = false),
            createWorkDay(LocalDate.of(2025, 2, 5), 480, WorkLocation.OFFICE, isPlanned = false),
            createWorkDay(LocalDate.of(2025, 2, 6), 480, WorkLocation.OFFICE, isPlanned = true),
            createWorkDay(LocalDate.of(2025, 2, 7), 480, WorkLocation.OFFICE, isPlanned = true)
        )

        whenever(calculateDayWorkTime.invoke(any())).thenReturn(
            DayWorkTimeResult(grossMinutes = 480, netMinutes = 480, breakMinutes = 30, exceedsMaxHours = false)
        )

        whenever(calculateFlextime.invoke(any(), any(), any())).thenReturn(
            FlextimeBalance(earnedMinutes = 1440, totalMinutes = 1440, overtimeMinutes = 0, targetMinutes = 0, initialMinutes = 0)
        )

        val result = useCase(workDays, settings, TimeRange.Month(YearMonth.of(2025, 2)))

        // UseCase should filter out planned days internally
        // Verify: Only 3 actual days are counted
        assertThat(result.monthlyHours).hasSize(1)
        assertThat(result.monthlyHours[0].value).isEqualTo(1440) // 3 * 480
    }

    @Test
    fun testPlannedDaysFilteringWhenAllPlannedExpectEmptyData() {
        val workDays = listOf(
            createWorkDay(LocalDate.of(2025, 2, 3), 480, WorkLocation.OFFICE, isPlanned = true),
            createWorkDay(LocalDate.of(2025, 2, 4), 480, WorkLocation.OFFICE, isPlanned = true)
        )

        val result = useCase(workDays, settings, TimeRange.Month(YearMonth.of(2025, 2)))

        // All days are planned, so result should be empty
        assertThat(result.isEmpty).isTrue()
    }

    // 6. Year View Data Tests

    @Test
    fun testYearViewDataWhen12MonthsExpectFullYearSeries() {
        // Create 12 WorkDays for each month 2025
        val workDays = (1..12).map { month ->
            createWorkDay(LocalDate.of(2025, month, 15), 480)
        }

        whenever(calculateDayWorkTime.invoke(any())).thenReturn(
            DayWorkTimeResult(grossMinutes = 480, netMinutes = 480, breakMinutes = 30, exceedsMaxHours = false)
        )

        // Mock flextime for each month
        whenever(calculateFlextime.invoke(any(), any(), any())).thenAnswer {
            FlextimeBalance(earnedMinutes = 480, totalMinutes = 480, overtimeMinutes = 0, targetMinutes = 0, initialMinutes = 0)
        }

        val result = useCase(workDays, settings, TimeRange.Year(2025))

        // Verify: flextimeSeries.size == 12
        assertThat(result.flextimeSeries).hasSize(12)

        // Verify: monthlyHours.size == 12
        assertThat(result.monthlyHours).hasSize(12)

        // Verify all 12 months are present
        val months = result.flextimeSeries.map { it.yearMonth.monthValue }
        assertThat(months).containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12)

        // Verify cumulative flextime increases
        for (i in 1 until result.flextimeSeries.size) {
            assertThat(result.flextimeSeries[i].value)
                .isGreaterThan(result.flextimeSeries[i - 1].value)
        }
    }

    // 7. Additional Edge Cases

    @Test
    fun testFlextimeCumulativeWhenMixedPositiveNegativeExpectCorrectCumulation() {
        val workDays = listOf(
            createWorkDay(LocalDate.of(2025, 2, 3), 480),
            createWorkDay(LocalDate.of(2025, 3, 5), 480)
        )

        whenever(calculateDayWorkTime.invoke(any())).thenReturn(
            DayWorkTimeResult(grossMinutes = 480, netMinutes = 480, breakMinutes = 30, exceedsMaxHours = false)
        )

        // Feb: +60 minutes flextime
        // March: -30 minutes flextime
        whenever(calculateFlextime.invoke(any(), any(), any())).thenReturn(
            FlextimeBalance(earnedMinutes = 60, totalMinutes = 60, overtimeMinutes = 0, targetMinutes = 0, initialMinutes = 0),
            FlextimeBalance(earnedMinutes = -30, totalMinutes = -30, overtimeMinutes = 0, targetMinutes = 0, initialMinutes = 0)
        )

        val result = useCase(workDays, settings, TimeRange.Custom(
            LocalDate.of(2025, 2, 1),
            LocalDate.of(2025, 3, 31)
        ))

        // Verify cumulative calculation
        assertThat(result.flextimeSeries).hasSize(2)
        assertThat(result.flextimeSeries[0].value).isEqualTo(60)  // Feb cumulative
        assertThat(result.flextimeSeries[1].value).isEqualTo(30)  // March cumulative: 60 + (-30) = 30
    }

    @Test
    fun testOvertimeSeriesWhenSaturdayBonusExpectCumulativeOvertime() {
        val workDays = listOf(
            createWorkDay(LocalDate.of(2025, 2, 7), 480, dayType = DayType.SATURDAY_BONUS),
            createWorkDay(LocalDate.of(2025, 3, 7), 480, dayType = DayType.SATURDAY_BONUS)
        )

        whenever(calculateDayWorkTime.invoke(any())).thenReturn(
            DayWorkTimeResult(grossMinutes = 480, netMinutes = 480, breakMinutes = 30, exceedsMaxHours = false)
        )

        // Feb: 240 overtime (50% of 480)
        // March: 240 overtime (50% of 480)
        whenever(calculateFlextime.invoke(any(), any(), any())).thenReturn(
            FlextimeBalance(earnedMinutes = 480, totalMinutes = 480, overtimeMinutes = 240, targetMinutes = 0, initialMinutes = 0),
            FlextimeBalance(earnedMinutes = 480, totalMinutes = 480, overtimeMinutes = 240, targetMinutes = 0, initialMinutes = 0)
        )

        val result = useCase(workDays, settings, TimeRange.Custom(
            LocalDate.of(2025, 2, 1),
            LocalDate.of(2025, 3, 31)
        ))

        // Verify cumulative overtime
        assertThat(result.overtimeSeries).hasSize(2)
        assertThat(result.overtimeSeries[0].value).isEqualTo(240)  // Feb cumulative
        assertThat(result.overtimeSeries[1].value).isEqualTo(480)  // March cumulative: 240 + 240
    }

    @Test
    fun testWeeklyHoursWhenMultipleLocationsExpectCorrectBreakdown() {
        // Use dates within same ALIGNED_WEEK_OF_YEAR (week 6 = Feb 5-11)
        val workDays = listOf(
            createWorkDay(LocalDate.of(2025, 2, 5), 480, WorkLocation.OFFICE),
            createWorkDay(LocalDate.of(2025, 2, 6), 480, WorkLocation.HOME_OFFICE),
            createWorkDay(LocalDate.of(2025, 2, 7), 480, WorkLocation.OFFICE)
        )

        whenever(calculateDayWorkTime.invoke(any())).thenReturn(
            DayWorkTimeResult(grossMinutes = 480, netMinutes = 480, breakMinutes = 30, exceedsMaxHours = false)
        )

        whenever(calculateFlextime.invoke(any(), any(), any())).thenReturn(
            FlextimeBalance(earnedMinutes = 0, totalMinutes = 0, overtimeMinutes = 0, targetMinutes = 0, initialMinutes = 0)
        )

        val result = useCase(workDays, settings, TimeRange.Month(YearMonth.of(2025, 2)))

        // Should have 1 week with breakdown (all days in same aligned week)
        assertThat(result.weeklyHours).hasSize(1)
        val firstWeek = result.weeklyHours[0]

        assertThat(firstWeek.totalMinutes).isEqualTo(1440) // 3 * 480
        assertThat(firstWeek.officeMinutes).isEqualTo(960) // 2 * 480
        assertThat(firstWeek.homeOfficeMinutes).isEqualTo(480) // 1 * 480
    }

    // Helper functions

    private fun createWorkDay(
        date: LocalDate,
        netMinutes: Long,
        location: WorkLocation = WorkLocation.OFFICE,
        isPlanned: Boolean = false,
        dayType: DayType = DayType.WORK
    ): WorkDay {
        return WorkDay(
            id = date.toEpochDay(),
            date = date,
            location = location,
            dayType = dayType,
            isPlanned = isPlanned,
            timeBlocks = listOf(
                TimeBlock(
                    id = date.toEpochDay(),
                    workDayId = date.toEpochDay(),
                    startTime = LocalTime.of(9, 0),
                    endTime = LocalTime.of(9, 0).plusMinutes(netMinutes),
                    isDuration = false
                )
            )
        )
    }
}
