package com.flex.domain.usecase

import com.flex.domain.model.DayType
import com.flex.domain.model.Settings
import com.flex.domain.model.TimeBlock
import com.flex.domain.model.WorkDay
import com.flex.domain.model.WorkLocation
import com.flex.domain.model.YearClosing
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalTime

class YearContextResolverTest {

    private lateinit var calculateFlextime: CalculateFlextimeUseCase
    private lateinit var resolver: YearContextResolver

    private val baseSettings = Settings(
        dailyWorkMinutes = 420,
        monthlyWorkMinutes = 8820,
        initialFlextimeMinutes = 360,
        initialOvertimeMinutes = 120,
        annualVacationDays = 30,
        carryOverVacationDays = 4,
        specialVacationDays = 5,
        specialVacationPaidOut = false,
        settingsYear = 2026
    )

    @BeforeEach
    fun setUp() {
        calculateFlextime = CalculateFlextimeUseCase(CalculateDayWorkTimeUseCase())
        resolver = YearContextResolver(calculateFlextime)
    }

    private fun workDay(date: LocalDate, netWorkMinutes: Long): WorkDay {
        val start = LocalTime.of(8, 0)
        // Use business trip to avoid automatic break deduction so netMinutes == duration
        return WorkDay(
            id = date.toEpochDay(),
            date = date,
            location = WorkLocation.OFFICE,
            dayType = DayType.BUSINESS_TRIP,
            isPlanned = false,
            timeBlocks = listOf(
                TimeBlock(
                    workDayId = date.toEpochDay(),
                    startTime = start,
                    endTime = start.plusMinutes(netWorkMinutes),
                    isDuration = true,
                    location = WorkLocation.OFFICE
                )
            )
        )
    }

    @Test
    fun `case A - targetYear equals settingsYear returns settings unchanged and only targetYearWorkDays`() {
        val days2026 = listOf(workDay(LocalDate.of(2026, 2, 2), 480))
        val otherDays = listOf(workDay(LocalDate.of(2025, 5, 5), 480))

        val resolved = resolver.resolveSettingsForYear(
            targetYear = 2026,
            settings = baseSettings,
            closingsByYear = emptyMap(),
            workDaysLookup = { if (it == 2025) otherDays else days2026 },
            workTimeRules = emptyList()
        )
        assertThat(resolved).isEqualTo(baseSettings)

        val (effectiveSettings, flexDays) = resolver.buildFlextimeInputForYear(
            targetYear = 2026,
            settings = baseSettings,
            closingsByYear = emptyMap(),
            targetYearWorkDays = days2026,
            otherYearsWorkDaysLookup = { otherDays },
            workTimeRules = emptyList()
        )
        assertThat(effectiveSettings).isEqualTo(baseSettings)
        assertThat(flexDays).isEqualTo(days2026)
    }

    @Test
    fun `case B - targetYear before settingsYear with YearClosing uses snapshot and avoids double counting`() {
        // 2025 started at 60 flex, earned 300 flex -> ended at 360 flex (which became 2026 initialFlextimeMinutes)
        val closing2025 = YearClosing(
            year = 2025,
            annualVacationDays = 28,
            carryOverVacationDays = 3,
            specialVacationDays = 5,
            specialVacationPaidOut = true,
            startFlextimeMinutes = 60,
            startOvertimeMinutes = 30,
            endFlextimeMinutes = 360,
            endOvertimeMinutes = 120,
            usedVacationDays = 27
        )
        // +300 min earned in 2025 (420 target + 300 = 720)
        val days2025 = listOf(workDay(LocalDate.of(2025, 3, 3), 720))

        val (effectiveSettings, flexDays) = resolver.buildFlextimeInputForYear(
            targetYear = 2025,
            settings = baseSettings,
            closingsByYear = mapOf(2025 to closing2025),
            targetYearWorkDays = days2025,
            otherYearsWorkDaysLookup = { emptyList() },
            workTimeRules = emptyList()
        )

        assertThat(effectiveSettings.settingsYear).isEqualTo(2025)
        assertThat(effectiveSettings.initialFlextimeMinutes).isEqualTo(60)
        assertThat(effectiveSettings.initialOvertimeMinutes).isEqualTo(30)
        assertThat(effectiveSettings.annualVacationDays).isEqualTo(28)
        assertThat(effectiveSettings.carryOverVacationDays).isEqualTo(3)
        assertThat(effectiveSettings.specialVacationDays).isEqualTo(5)
        assertThat(effectiveSettings.specialVacationPaidOut).isTrue()
        assertThat(flexDays).isEqualTo(days2025)

        val balance = calculateFlextime(flexDays, effectiveSettings, null, emptyList())
        // 60 + 300 = 360 (NOT 360 + 300 = 660)
        assertThat(balance.totalMinutes).isEqualTo(360L)
    }

    @Test
    fun `case C - back_derive without snapshot derives start balances across 1 and 2 years including OVERTIME_DAY`() {
        // Anchor is 2026 with initialFlextime = 360, initialOvertime = 120
        // In 2025 (no snapshot): +120 flex, +60 overtime (Saturday 120 min -> +120 flex, +60 overtime), then 0 overtime day
        // Wait: on Saturday (non-working day), SATURDAY_BONUS adds netMinutes to flex and 0.5*netMinutes to overtime.
        val sat2025 = WorkDay(
            id = 1L,
            date = LocalDate.of(2025, 6, 7),
            location = WorkLocation.OFFICE,
            dayType = DayType.SATURDAY_BONUS,
            isPlanned = false,
            timeBlocks = listOf(TimeBlock(workDayId = 1L, startTime = LocalTime.of(8, 0), endTime = LocalTime.of(10, 0)))
        )
        // Also an OVERTIME_DAY in 2025 on a regular Monday (target 420) when overtime is available -> consumes 420 overtime, 0 flex
        val otDay2025 = WorkDay(
            id = 2L,
            date = LocalDate.of(2025, 6, 16),
            location = WorkLocation.OFFICE,
            dayType = DayType.OVERTIME_DAY,
            isPlanned = false
        )
        // In 2024 (no snapshot): +60 flex on Monday (480 min vs 420 target)
        val mon2024 = workDay(LocalDate.of(2024, 5, 6), 480)

        // Expected forward:
        // Start 2024: flex=180, overtime=480
        // After mon2024: flex=240, overtime=480
        // After sat2025: flex=240+120=360, overtime=480+60=540
        // After otDay2025: flex=360, overtime=540-420=120 -> Anchor 2026!
        val lookup: (Int) -> List<WorkDay> = { year ->
            when (year) {
                2025 -> listOf(sat2025, otDay2025)
                2024 -> listOf(mon2024)
                else -> emptyList()
            }
        }

        val resolved2025 = resolver.resolveSettingsForYear(
            targetYear = 2025,
            settings = baseSettings,
            closingsByYear = emptyMap(),
            workDaysLookup = lookup,
            workTimeRules = emptyList()
        )
        assertThat(resolved2025.settingsYear).isEqualTo(2025)
        assertThat(resolved2025.initialFlextimeMinutes).isEqualTo(240)
        assertThat(resolved2025.initialOvertimeMinutes).isEqualTo(480)
        assertThat(resolved2025.specialVacationPaidOut).isFalse()

        val resolved2024 = resolver.resolveSettingsForYear(
            targetYear = 2024,
            settings = baseSettings,
            closingsByYear = emptyMap(),
            workDaysLookup = lookup,
            workTimeRules = emptyList()
        )
        assertThat(resolved2024.settingsYear).isEqualTo(2024)
        assertThat(resolved2024.initialFlextimeMinutes).isEqualTo(180)
        assertThat(resolved2024.initialOvertimeMinutes).isEqualTo(480)
    }

    @Test
    fun `case C - back_derive handles empty intermediate year without calling calculateFlextime`() {
        val days2023 = listOf(workDay(LocalDate.of(2023, 4, 3), 480)) // +60 flex
        val resolved2023 = resolver.resolveSettingsForYear(
            targetYear = 2023,
            settings = baseSettings,
            closingsByYear = emptyMap(),
            workDaysLookup = { if (it == 2023) days2023 else emptyList() },
            workTimeRules = emptyList()
        )
        assertThat(resolved2023.initialFlextimeMinutes).isEqualTo(300)
        assertThat(resolved2023.initialOvertimeMinutes).isEqualTo(120)
    }

    @Test
    fun `case D - targetYear after settingsYear chains balances and vacation carryOver over 1 and 2 years`() {
        // 2026: 30 annual + 4 carryOver = 34; 20 used -> 14 remaining
        // 2026: +120 flex -> 360 + 120 = 480
        val days2026 = (1..20).map { d ->
            WorkDay(id = d.toLong(), date = LocalDate.of(2026, 7, d), dayType = DayType.VACATION, isPlanned = false)
        } + listOf(workDay(LocalDate.of(2026, 8, 3), 540)) // +120 flex

        // 2027: 30 annual + 14 carryOver = 44; 10 used -> 34 remaining
        // 2027: +60 flex -> 480 + 60 = 540
        val days2027 = (1..10).map { d ->
            WorkDay(id = 100L + d, date = LocalDate.of(2027, 7, d), dayType = DayType.VACATION, isPlanned = false)
        } + listOf(workDay(LocalDate.of(2027, 8, 2), 480)) // +60 flex

        val days2028 = listOf(workDay(LocalDate.of(2028, 1, 3), 480)) // +60 flex

        val lookup: (Int) -> List<WorkDay> = { year ->
            when (year) {
                2026 -> days2026
                2027 -> days2027
                2028 -> days2028
                else -> emptyList()
            }
        }

        val resolved2027 = resolver.resolveSettingsForYear(
            targetYear = 2027,
            settings = baseSettings.copy(specialVacationPaidOut = true),
            closingsByYear = emptyMap(),
            workDaysLookup = lookup,
            workTimeRules = emptyList(),
            includeForwardBalances = true
        )
        assertThat(resolved2027.settingsYear).isEqualTo(2027)
        assertThat(resolved2027.carryOverVacationDays).isEqualTo(14)
        assertThat(resolved2027.specialVacationDays).isEqualTo(0)
        assertThat(resolved2027.specialVacationPaidOut).isFalse()
        assertThat(resolved2027.initialFlextimeMinutes).isEqualTo(480)
        assertThat(resolved2027.initialOvertimeMinutes).isEqualTo(120)

        val resolved2028 = resolver.resolveSettingsForYear(
            targetYear = 2028,
            settings = baseSettings,
            closingsByYear = emptyMap(),
            workDaysLookup = lookup,
            workTimeRules = emptyList(),
            includeForwardBalances = true
        )
        assertThat(resolved2028.settingsYear).isEqualTo(2028)
        assertThat(resolved2028.carryOverVacationDays).isEqualTo(34)
        assertThat(resolved2028.initialFlextimeMinutes).isEqualTo(540)

        val (flexSettings, flexDays) = resolver.buildFlextimeInputForYear(
            targetYear = 2028,
            settings = baseSettings,
            closingsByYear = emptyMap(),
            targetYearWorkDays = days2028,
            otherYearsWorkDaysLookup = lookup,
            workTimeRules = emptyList()
        )
        assertThat(flexSettings).isEqualTo(baseSettings)
        assertThat(flexDays).isEqualTo(days2026 + days2027 + days2028)
        val totalBalance = calculateFlextime(flexDays, flexSettings, null, emptyList())
        assertThat(totalBalance.totalMinutes).isEqualTo(600L)
    }
}
