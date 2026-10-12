package com.flex.domain.usecase

import com.flex.BaseUnitTest
import com.flex.domain.model.DayType
import com.flex.domain.model.PublicHolidays
import com.flex.domain.model.QuotaStatus
import com.flex.domain.model.Settings
import com.flex.domain.model.TimeBlock
import com.flex.domain.model.UpcomingEventType
import com.flex.domain.model.WorkDay
import com.flex.domain.model.WorkLocation
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

class CalculateDashboardMetricsUseCaseTest : BaseUnitTest() {

    private lateinit var calculateDayWorkTime: CalculateDayWorkTimeUseCase
    private lateinit var useCase: CalculateDashboardMetricsUseCase

    @BeforeEach
    override fun setUp() {
        super.setUp()
        calculateDayWorkTime = CalculateDayWorkTimeUseCase()
        useCase = CalculateDashboardMetricsUseCase(calculateDayWorkTime)
    }

    @Test
    fun `calculateForecast returns success message when quota is met`() {
        val quotaStatus = QuotaStatus(
            officeDays = 10,
            remainingWorkDays = 10,
            daysQuotaMet = true,
            percentQuotaMet = true
        )

        val forecast = useCase.calculateForecast(
            quotaStatus = quotaStatus,
            effectiveQuotaMinDays = 8,
            monthWorkDays = emptyList(),
            targetMonthlyMinutes = 9600L
        )

        assertThat(forecast.isQuotaMet).isTrue()
        assertThat(forecast.quotaPacingText).contains("bereits erfüllt")
    }

    @Test
    fun `calculateForecast includes planned office days and shows projected quota success`() {
        val actualQuota = QuotaStatus(
            officeDays = 3,
            remainingWorkDays = 15,
            daysQuotaMet = false,
            percentQuotaMet = false
        )
        val plannedDay1 = WorkDay(
            date = LocalDate.of(2026, 10, 20),
            dayType = DayType.WORK,
            location = WorkLocation.OFFICE,
            isPlanned = true
        )
        val plannedDay2 = WorkDay(
            date = LocalDate.of(2026, 10, 21),
            dayType = DayType.WORK,
            location = WorkLocation.OFFICE,
            isPlanned = true
        )
        val plannedDay3 = WorkDay(
            date = LocalDate.of(2026, 10, 22),
            dayType = DayType.WORK,
            location = WorkLocation.HOME_OFFICE,
            isPlanned = true
        )
        val projectedQuota = QuotaStatus(
            officeDays = 5,
            homeOfficeDays = 1,
            remainingWorkDays = 15,
            daysQuotaMet = true, // Quota min days is 5
            percentQuotaMet = false
        )

        val forecast = useCase.calculateForecast(
            actualQuota = actualQuota,
            projectedQuota = projectedQuota,
            effectiveQuotaMinDays = 5,
            effectiveQuotaPercent = 40,
            monthWorkDays = listOf(plannedDay1, plannedDay2, plannedDay3),
            targetMonthlyMinutes = 9600L
        )

        assertThat(forecast.isQuotaMet).isFalse()
        assertThat(forecast.isProjectedQuotaMet).isTrue()
        assertThat(forecast.plannedOfficeDays).isEqualTo(2)
        assertThat(forecast.plannedHomeOfficeDays).isEqualTo(1)
        assertThat(forecast.projectedOfficeDays).isEqualTo(5)
        assertThat(forecast.projectedQuotaText).contains("Mit 2 geplanten Büro-Tagen wird das Ziel am Monatsende erreicht")
    }

    @Test
    fun `calculateForecast includes planned office days when projected quota is still not met`() {
        val actualQuota = QuotaStatus(
            officeDays = 2,
            remainingWorkDays = 10,
            daysQuotaMet = false
        )
        val plannedDay1 = WorkDay(
            date = LocalDate.of(2026, 10, 20),
            dayType = DayType.WORK,
            location = WorkLocation.OFFICE,
            isPlanned = true
        )
        val projectedQuota = QuotaStatus(
            officeDays = 3,
            remainingWorkDays = 10,
            daysQuotaMet = false
        )

        val forecast = useCase.calculateForecast(
            actualQuota = actualQuota,
            projectedQuota = projectedQuota,
            effectiveQuotaMinDays = 8,
            effectiveQuotaPercent = 40,
            monthWorkDays = listOf(plannedDay1),
            targetMonthlyMinutes = 9600L
        )

        assertThat(forecast.isQuotaMet).isFalse()
        assertThat(forecast.isProjectedQuotaMet).isFalse()
        assertThat(forecast.plannedOfficeDays).isEqualTo(1)
        assertThat(forecast.projectedOfficeDays).isEqualTo(3)
        assertThat(forecast.projectedQuotaText).contains("Inkl. 1 geplanten Tagen: 3 / 8 Tage. Es fehlen noch 5 Büro-Tage bis zum Ziel.")
    }

    @Test
    fun `calculateForecast calculates pacing when quota is not yet met`() {
        val quotaStatus = QuotaStatus(
            officeDays = 4,
            remainingWorkDays = 12,
            daysQuotaMet = false,
            percentQuotaMet = false
        )

        val forecast = useCase.calculateForecast(
            quotaStatus = quotaStatus,
            effectiveQuotaMinDays = 8,
            monthWorkDays = emptyList(),
            targetMonthlyMinutes = 9600L
        )

        assertThat(forecast.isQuotaMet).isFalse()
        assertThat(forecast.quotaPacingText).contains("Noch 4 Büro-Tage")
        assertThat(forecast.quotaPacingText).contains("12 offenen Arbeitstagen")
        assertThat(forecast.quotaPacingText).contains("alle 3 Tage")
    }

    @Test
    fun `calculateForecast warns when remaining days are insufficient`() {
        val quotaStatus = QuotaStatus(
            officeDays = 2,
            remainingWorkDays = 3,
            daysQuotaMet = false,
            percentQuotaMet = false
        )

        val forecast = useCase.calculateForecast(
            quotaStatus = quotaStatus,
            effectiveQuotaMinDays = 8,
            monthWorkDays = emptyList(),
            targetMonthlyMinutes = 9600L
        )

        assertThat(forecast.isQuotaMet).isFalse()
        assertThat(forecast.quotaPacingText).contains("Achtung")
        assertThat(forecast.quotaPacingText).contains("Noch 6 Büro-Tage nötig")
        assertThat(forecast.quotaPacingText).contains("nur noch 3 Arbeitstage")
    }

    @Test
    fun `calculateForecast calculates projected flextime based on daily average`() {
        val ym = YearMonth.of(2026, 10)
        // 2 worked days with 9h gross, 8h 15m (495m) net each
        val day1 = WorkDay(
            date = LocalDate.of(2026, 10, 1),
            dayType = DayType.WORK,
            timeBlocks = listOf(TimeBlock(startTime = LocalTime.of(8, 0), endTime = LocalTime.of(17, 0)))
        )
        val day2 = WorkDay(
            date = LocalDate.of(2026, 10, 2),
            dayType = DayType.WORK,
            timeBlocks = listOf(TimeBlock(startTime = LocalTime.of(8, 0), endTime = LocalTime.of(17, 0)))
        )
        val quotaStatus = QuotaStatus(
            officeDays = 2,
            remainingWorkDays = 18,
            daysQuotaMet = false
        )

        // Target: 20 days * 8h = 160h (9600 min)
        // 2 days worked with 495m = 990m. Remaining 18 days * 495m = 8910m. Total = 9900m.
        // Delta = 9900 - 9600 = +300m (+5h)
        val forecast = useCase.calculateForecast(
            quotaStatus = quotaStatus,
            effectiveQuotaMinDays = 8,
            monthWorkDays = listOf(day1, day2),
            targetMonthlyMinutes = 9600L
        )

        assertThat(forecast.projectedFlextimeText).isNotNull()
        assertThat(forecast.projectedFlextimeText).contains("+10h 0min")
    }

    @Test
    fun `calculateUpcomingEvents finds future vacation, flex days, and holidays`() {
        val today = LocalDate.of(2026, 10, 12)
        val year = 2026

        val pastVacation = WorkDay(date = LocalDate.of(2026, 10, 5), dayType = DayType.VACATION)
        val futureVacation1 = WorkDay(date = LocalDate.of(2026, 10, 26), dayType = DayType.VACATION, isPlanned = true)
        val futureVacation2 = WorkDay(date = LocalDate.of(2026, 10, 27), dayType = DayType.VACATION, isPlanned = true)
        val futureVacation3 = WorkDay(date = LocalDate.of(2026, 10, 28), dayType = DayType.VACATION, isPlanned = true)
        val futureFlexDay = WorkDay(date = LocalDate.of(2026, 10, 16), dayType = DayType.FLEX_DAY, isPlanned = true)

        val events = useCase.calculateUpcomingEvents(
            today = today,
            yearDays = listOf(pastVacation, futureVacation1, futureVacation2, futureVacation3, futureFlexDay),
            year = year
        )

        assertThat(events).isNotEmpty()
        // First should be FlexDay on 16. Oct
        val flexEvent = events.find { it.eventType == UpcomingEventType.FLEX_DAY }
        assertThat(flexEvent).isNotNull()
        assertThat(flexEvent?.date).isEqualTo(LocalDate.of(2026, 10, 16))

        // Vacation block should be grouped
        val vacationEvent = events.find { it.eventType == UpcomingEventType.VACATION }
        assertThat(vacationEvent).isNotNull()
        assertThat(vacationEvent?.date).isEqualTo(LocalDate.of(2026, 10, 26))
        assertThat(vacationEvent?.endDate).isEqualTo(LocalDate.of(2026, 10, 28))
        assertThat(vacationEvent?.daysCount).isEqualTo(3)

        // Holiday (e.g. Reformationstag on 31. Oct)
        val holidayEvent = events.find { it.eventType == UpcomingEventType.PUBLIC_HOLIDAY }
        assertThat(holidayEvent).isNotNull()
    }

    @Test
    fun `calculateWorkHabits averages start time, end time, net minutes and break`() {
        val day1 = WorkDay(
            date = LocalDate.of(2026, 10, 1),
            dayType = DayType.WORK,
            timeBlocks = listOf(TimeBlock(startTime = LocalTime.of(8, 0), endTime = LocalTime.of(16, 30)))
        )
        val day2 = WorkDay(
            date = LocalDate.of(2026, 10, 2),
            dayType = DayType.WORK,
            timeBlocks = listOf(TimeBlock(startTime = LocalTime.of(9, 0), endTime = LocalTime.of(17, 30)))
        )

        val habits = useCase.calculateWorkHabits(listOf(day1, day2))

        // Avg start: (8:00 + 9:00) / 2 = 8:30
        assertThat(habits.avgStartTime).isEqualTo(LocalTime.of(8, 30))
        // Avg end: (16:30 + 17:30) / 2 = 17:00
        assertThat(habits.avgEndTime).isEqualTo(LocalTime.of(17, 0))
        // Net: 8h = 480m
        assertThat(habits.avgNetMinutes).isEqualTo(480L)
        // Break: 30m
        assertThat(habits.avgBreakMinutes).isEqualTo(30L)
    }

    @Test
    fun `calculateAbsenceSummary counts all absence types in year`() {
        val days = listOf(
            WorkDay(date = LocalDate.of(2026, 1, 5), dayType = DayType.VACATION),
            WorkDay(date = LocalDate.of(2026, 1, 6), dayType = DayType.VACATION),
            WorkDay(date = LocalDate.of(2026, 2, 10), dayType = DayType.SPECIAL_VACATION),
            WorkDay(date = LocalDate.of(2026, 3, 15), dayType = DayType.FLEX_DAY),
            WorkDay(date = LocalDate.of(2026, 4, 20), dayType = DayType.SICK_DAY),
            WorkDay(date = LocalDate.of(2026, 5, 25), dayType = DayType.BUSINESS_TRIP)
        )

        val summary = useCase.calculateAbsenceSummary(days)

        assertThat(summary.vacationDays).isEqualTo(2)
        assertThat(summary.specialVacationDays).isEqualTo(1)
        assertThat(summary.flexDays).isEqualTo(1)
        assertThat(summary.sickDays).isEqualTo(1)
        assertThat(summary.businessTripDays).isEqualTo(1)
    }

    @Test
    fun `calculateCurrentWeekStatus computes worked hours and office vs home office days`() {
        val today = LocalDate.of(2026, 10, 14) // Wednesday
        val mon = WorkDay(
            date = LocalDate.of(2026, 10, 12),
            dayType = DayType.WORK,
            location = WorkLocation.OFFICE,
            timeBlocks = listOf(TimeBlock(startTime = LocalTime.of(8, 0), endTime = LocalTime.of(16, 30), location = WorkLocation.OFFICE))
        )
        val tue = WorkDay(
            date = LocalDate.of(2026, 10, 13),
            dayType = DayType.WORK,
            location = WorkLocation.HOME_OFFICE,
            timeBlocks = listOf(TimeBlock(startTime = LocalTime.of(8, 0), endTime = LocalTime.of(16, 30), location = WorkLocation.HOME_OFFICE))
        )
        val wed = WorkDay(
            date = LocalDate.of(2026, 10, 14),
            dayType = DayType.WORK,
            location = WorkLocation.OFFICE,
            timeBlocks = listOf(TimeBlock(startTime = LocalTime.of(8, 0), endTime = LocalTime.of(16, 30), location = WorkLocation.OFFICE))
        )

        val status = useCase.calculateCurrentWeekStatus(
            today = today,
            yearDays = listOf(mon, tue, wed),
            settings = Settings(dailyWorkMinutes = 480)
        )

        assertThat(status.workedMinutes).isEqualTo(480L * 3) // 24h = 1440m
        assertThat(status.targetMinutes).isEqualTo(2400L) // 40h = 2400m
        assertThat(status.officeDays).isEqualTo(2)
        assertThat(status.homeOfficeDays).isEqualTo(1)
    }
}
