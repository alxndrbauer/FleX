package com.flex.domain.usecase

import com.flex.domain.model.DEFAULT_WORK_DAYS
import com.flex.domain.model.DayType
import com.flex.domain.model.Settings
import com.flex.domain.model.TimeBlock
import com.flex.domain.model.WorkDay
import com.flex.domain.model.WorkLocation
import com.flex.domain.model.WorkTimeRule
import com.flex.domain.repository.SettingsRepository
import com.flex.domain.repository.WorkDayRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

class SyncPlannedDaysWithWorkTimeRuleUseCaseTest {

    private val workDayRepository: WorkDayRepository = mock()
    private val settingsRepository: SettingsRepository = mock()
    private val getSettings: GetSettingsUseCase = mock()
    private val calculateDayWorkTime: CalculateDayWorkTimeUseCase = CalculateDayWorkTimeUseCase()

    private lateinit var useCase: SyncPlannedDaysWithWorkTimeRuleUseCase

    private val defaultSettings = Settings(
        dailyWorkMinutes = 420,
        defaultStartTime = LocalTime.of(8, 0)
    )

    @BeforeEach
    fun setUp() {
        useCase = SyncPlannedDaysWithWorkTimeRuleUseCase(
            workDayRepository = workDayRepository,
            settingsRepository = settingsRepository,
            getSettings = getSettings,
            calculateDayWorkTime = calculateDayWorkTime
        )
        whenever(getSettings()).thenReturn(flowOf(defaultSettings))
        whenever(settingsRepository.getWorkTimeRules()).thenReturn(flowOf(emptyList()))
    }

    @Test
    fun `syncMonth updates time blocks when target daily work minutes differ`() = runTest {
        val month = YearMonth.of(2026, 10)
        val date = month.atDay(5) // Monday
        val existingBlock = TimeBlock(
            id = 10L,
            workDayId = 1L,
            startTime = LocalTime.of(8, 0),
            endTime = LocalTime.of(15, 0), // 7h = 420min
            isDuration = true
        )
        val plannedDay = WorkDay(
            id = 1L,
            date = date,
            dayType = DayType.WORK,
            isPlanned = true,
            location = WorkLocation.HOME_OFFICE,
            timeBlocks = listOf(existingBlock)
        )

        // New rule with 480 minutes (8h)
        val rule = WorkTimeRule(
            id = 1L,
            validFrom = month,
            dailyWorkMinutes = 480,
            monthlyWorkMinutes = 0
        )
        whenever(settingsRepository.getWorkTimeRules()).thenReturn(flowOf(listOf(rule)))
        whenever(workDayRepository.getWorkDaysForMonth(month)).thenReturn(flowOf(listOf(plannedDay)))

        val result = useCase.syncMonth(month)

        assertThat(result.updatedDaysCount).isEqualTo(1)
        assertThat(result.removedDaysCount).isEqualTo(0)
        assertThat(result.untouchedCustomDaysCount).isEqualTo(0)

        verify(workDayRepository).deleteTimeBlock(existingBlock)
        val captor = argumentCaptor<TimeBlock>()
        verify(workDayRepository).saveTimeBlock(captor.capture())

        val savedBlock = captor.firstValue
        assertThat(savedBlock.workDayId).isEqualTo(1L)
        assertThat(savedBlock.startTime).isEqualTo(LocalTime.of(8, 0))
        assertThat(savedBlock.endTime).isEqualTo(LocalTime.of(16, 0)) // 8:00 + 480 min
        assertThat(savedBlock.isDuration).isTrue()
        assertThat(savedBlock.location).isEqualTo(WorkLocation.HOME_OFFICE)
    }

    @Test
    fun `syncMonth does not update when current minutes already match target minutes`() = runTest {
        val month = YearMonth.of(2026, 10)
        val date = month.atDay(5)
        val existingBlock = TimeBlock(
            id = 10L,
            workDayId = 1L,
            startTime = LocalTime.of(8, 0),
            endTime = LocalTime.of(15, 0), // 420 min
            isDuration = true
        )
        val plannedDay = WorkDay(
            id = 1L,
            date = date,
            dayType = DayType.WORK,
            isPlanned = true,
            timeBlocks = listOf(existingBlock)
        )

        val rule = WorkTimeRule(
            id = 1L,
            validFrom = month,
            dailyWorkMinutes = 420,
            monthlyWorkMinutes = 0
        )
        whenever(settingsRepository.getWorkTimeRules()).thenReturn(flowOf(listOf(rule)))
        whenever(workDayRepository.getWorkDaysForMonth(month)).thenReturn(flowOf(listOf(plannedDay)))

        val result = useCase.syncMonth(month)

        assertThat(result.updatedDaysCount).isEqualTo(0)
        assertThat(result.removedDaysCount).isEqualTo(0)
        assertThat(result.untouchedCustomDaysCount).isEqualTo(0)

        verify(workDayRepository, never()).deleteTimeBlock(any())
        verify(workDayRepository, never()).saveTimeBlock(any())
    }

    @Test
    fun `syncMonth leaves manually customized days untouched when previousDailyMinutes does not match`() = runTest {
        val month = YearMonth.of(2026, 10)
        val date = month.atDay(5)
        // Day was manually planned with 300 minutes (5h) instead of the previous standard 420 min
        val customBlock = TimeBlock(
            id = 10L,
            workDayId = 1L,
            startTime = LocalTime.of(8, 0),
            endTime = LocalTime.of(13, 0),
            isDuration = true
        )
        val plannedDay = WorkDay(
            id = 1L,
            date = date,
            dayType = DayType.WORK,
            isPlanned = true,
            timeBlocks = listOf(customBlock)
        )

        val newRule = WorkTimeRule(
            id = 1L,
            validFrom = month,
            dailyWorkMinutes = 480,
            monthlyWorkMinutes = 0
        )
        whenever(settingsRepository.getWorkTimeRules()).thenReturn(flowOf(listOf(newRule)))
        whenever(workDayRepository.getWorkDaysForMonth(month)).thenReturn(flowOf(listOf(plannedDay)))

        val result = useCase.syncMonth(
            month = month,
            previousDailyMinutes = 420
        )

        assertThat(result.updatedDaysCount).isEqualTo(0)
        assertThat(result.removedDaysCount).isEqualTo(0)
        assertThat(result.untouchedCustomDaysCount).isEqualTo(1)

        verify(workDayRepository, never()).deleteTimeBlock(any())
        verify(workDayRepository, never()).saveTimeBlock(any())
    }

    @Test
    fun `syncMonth removes non-work days when removeNonWorkDays is true`() = runTest {
        val month = YearMonth.of(2026, 10)
        val friday = month.atDay(9) // Friday
        assertThat(friday.dayOfWeek).isEqualTo(DayOfWeek.FRIDAY)

        val plannedDay = WorkDay(
            id = 2L,
            date = friday,
            dayType = DayType.WORK,
            isPlanned = true,
            timeBlocks = listOf(
                TimeBlock(id = 20L, workDayId = 2L, startTime = LocalTime.of(8, 0), endTime = LocalTime.of(15, 0), isDuration = true)
            )
        )

        // 4-day week: Mo-Th (Friday is not an active workday)
        val rule = WorkTimeRule(
            id = 1L,
            validFrom = month,
            dailyWorkMinutes = 480,
            monthlyWorkMinutes = 0,
            workDays = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY)
        )
        whenever(settingsRepository.getWorkTimeRules()).thenReturn(flowOf(listOf(rule)))
        whenever(workDayRepository.getWorkDaysForMonth(month)).thenReturn(flowOf(listOf(plannedDay)))

        val result = useCase.syncMonth(month, removeNonWorkDays = true)

        assertThat(result.removedDaysCount).isEqualTo(1)
        assertThat(result.updatedDaysCount).isEqualTo(0)
        assertThat(result.untouchedCustomDaysCount).isEqualTo(0)

        verify(workDayRepository).deleteWorkDay(plannedDay)
        verify(workDayRepository, never()).saveTimeBlock(any())
    }

    @Test
    fun `syncMonth does not remove non-work days when removeNonWorkDays is false, but syncs them`() = runTest {
        val month = YearMonth.of(2026, 10)
        val friday = month.atDay(9) // Friday

        val existingBlock = TimeBlock(id = 20L, workDayId = 2L, startTime = LocalTime.of(8, 0), endTime = LocalTime.of(15, 0), isDuration = true)
        val plannedDay = WorkDay(
            id = 2L,
            date = friday,
            dayType = DayType.WORK,
            isPlanned = true,
            timeBlocks = listOf(existingBlock)
        )

        val rule = WorkTimeRule(
            id = 1L,
            validFrom = month,
            dailyWorkMinutes = 480,
            monthlyWorkMinutes = 0,
            workDays = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY)
        )
        whenever(settingsRepository.getWorkTimeRules()).thenReturn(flowOf(listOf(rule)))
        whenever(workDayRepository.getWorkDaysForMonth(month)).thenReturn(flowOf(listOf(plannedDay)))

        val result = useCase.syncMonth(month, removeNonWorkDays = false)

        assertThat(result.removedDaysCount).isEqualTo(0)
        assertThat(result.updatedDaysCount).isEqualTo(1)
        verify(workDayRepository, never()).deleteWorkDay(any())
        verify(workDayRepository).deleteTimeBlock(existingBlock)
        verify(workDayRepository).saveTimeBlock(any())
    }

    @Test
    fun `syncMonth ignores non-planned days and non-work-day types like VACATION`() = runTest {
        val month = YearMonth.of(2026, 10)
        val realDay = WorkDay(
            id = 1L,
            date = month.atDay(5),
            dayType = DayType.WORK,
            isPlanned = false,
            timeBlocks = listOf(TimeBlock(id = 10L, workDayId = 1L, startTime = LocalTime.of(8, 0), endTime = LocalTime.of(15, 0)))
        )
        val plannedVacation = WorkDay(
            id = 2L,
            date = month.atDay(6),
            dayType = DayType.VACATION,
            isPlanned = true
        )

        whenever(workDayRepository.getWorkDaysForMonth(month)).thenReturn(flowOf(listOf(realDay, plannedVacation)))

        val result = useCase.syncMonth(month)

        assertThat(result.updatedDaysCount).isEqualTo(0)
        assertThat(result.removedDaysCount).isEqualTo(0)
        assertThat(result.untouchedCustomDaysCount).isEqualTo(0)
        verify(workDayRepository, never()).deleteWorkDay(any())
        verify(workDayRepository, never()).saveTimeBlock(any())
    }

    @Test
    fun `syncMonth processes BUSINESS_TRIP and SATURDAY_BONUS planned days`() = runTest {
        val month = YearMonth.of(2026, 10)
        val tripDay = WorkDay(
            id = 1L,
            date = month.atDay(5),
            dayType = DayType.BUSINESS_TRIP,
            isPlanned = true,
            timeBlocks = listOf(TimeBlock(id = 10L, workDayId = 1L, startTime = LocalTime.of(8, 0), endTime = LocalTime.of(15, 0), isDuration = true))
        )
        val saturdayBonusDay = WorkDay(
            id = 2L,
            date = month.atDay(10), // Saturday
            dayType = DayType.SATURDAY_BONUS,
            isPlanned = true,
            timeBlocks = listOf(TimeBlock(id = 20L, workDayId = 2L, startTime = LocalTime.of(8, 0), endTime = LocalTime.of(15, 0), isDuration = true))
        )

        val rule = WorkTimeRule(
            id = 1L,
            validFrom = month,
            dailyWorkMinutes = 480,
            monthlyWorkMinutes = 0,
            workDays = DEFAULT_WORK_DAYS + DayOfWeek.SATURDAY
        )
        whenever(settingsRepository.getWorkTimeRules()).thenReturn(flowOf(listOf(rule)))
        whenever(workDayRepository.getWorkDaysForMonth(month)).thenReturn(flowOf(listOf(tripDay, saturdayBonusDay)))

        val result = useCase.syncMonth(month)

        assertThat(result.updatedDaysCount).isEqualTo(2)
        verify(workDayRepository, times(2)).saveTimeBlock(any())
    }

    @Test
    fun `syncRange defaults to 12 months horizon when toMonth is null`() = runTest {
        val fromMonth = YearMonth.of(2026, 1)

        whenever(workDayRepository.getWorkDaysForMonth(any())).thenReturn(flowOf(emptyList()))

        val result = useCase.syncRange(fromMonth)

        assertThat(result.updatedDaysCount).isEqualTo(0)
        // Verify 12 months checked (2026-01 to 2026-12)
        verify(workDayRepository, times(12)).getWorkDaysForMonth(any())
    }

    @Test
    fun `syncRange iterates given range and aggregates counts`() = runTest {
        val fromMonth = YearMonth.of(2026, 1)
        val toMonth = YearMonth.of(2026, 2)

        val day1 = WorkDay(
            id = 1L,
            date = fromMonth.atDay(5),
            dayType = DayType.WORK,
            isPlanned = true,
            timeBlocks = listOf(TimeBlock(id = 10L, workDayId = 1L, startTime = LocalTime.of(8, 0), endTime = LocalTime.of(15, 0), isDuration = true))
        )
        val day2 = WorkDay(
            id = 2L,
            date = toMonth.atDay(2),
            dayType = DayType.WORK,
            isPlanned = true,
            timeBlocks = listOf(TimeBlock(id = 20L, workDayId = 2L, startTime = LocalTime.of(8, 0), endTime = LocalTime.of(15, 0), isDuration = true))
        )

        whenever(workDayRepository.getWorkDaysForMonth(fromMonth)).thenReturn(flowOf(listOf(day1)))
        whenever(workDayRepository.getWorkDaysForMonth(toMonth)).thenReturn(flowOf(listOf(day2)))

        val rule = WorkTimeRule(
            id = 1L,
            validFrom = fromMonth,
            dailyWorkMinutes = 480,
            monthlyWorkMinutes = 0
        )
        whenever(settingsRepository.getWorkTimeRules()).thenReturn(flowOf(listOf(rule)))

        val result = useCase.syncRange(fromMonth, toMonth)

        assertThat(result.updatedDaysCount).isEqualTo(2)
        verify(workDayRepository, times(2)).saveTimeBlock(any())
    }

    @Test
    fun `countAffectedPlannedDays counts planned work days in range`() = runTest {
        val fromMonth = YearMonth.of(2026, 1)
        val toMonth = YearMonth.of(2026, 2)

        val day1 = WorkDay(id = 1L, date = fromMonth.atDay(5), dayType = DayType.WORK, isPlanned = true)
        val day2 = WorkDay(id = 2L, date = fromMonth.atDay(6), dayType = DayType.VACATION, isPlanned = true) // Ignored
        val day3 = WorkDay(id = 3L, date = toMonth.atDay(2), dayType = DayType.BUSINESS_TRIP, isPlanned = true)
        val day4 = WorkDay(id = 4L, date = toMonth.atDay(3), dayType = DayType.WORK, isPlanned = false) // Ignored

        whenever(workDayRepository.getWorkDaysForMonth(fromMonth)).thenReturn(flowOf(listOf(day1, day2)))
        whenever(workDayRepository.getWorkDaysForMonth(toMonth)).thenReturn(flowOf(listOf(day3, day4)))

        val count = useCase.countAffectedPlannedDays(fromMonth, toMonth)

        assertThat(count).isEqualTo(2) // day1 and day3
    }

    @Test
    fun `syncMonth does not count days as untouchedCustomDays when they already match target even if previousDailyMinutes is set`() = runTest {
        val month = YearMonth.of(2026, 10)
        val date1 = month.atDay(5) // already matching target (480 min)
        val date2 = month.atDay(6) // custom day (240 min)
        val dayAlreadyTarget = WorkDay(
            id = 1L,
            date = date1,
            dayType = DayType.WORK,
            isPlanned = true,
            timeBlocks = listOf(
                TimeBlock(id = 10L, workDayId = 1L, startTime = LocalTime.of(8, 0), endTime = LocalTime.of(16, 0), isDuration = true)
            )
        )
        val dayCustom = WorkDay(
            id = 2L,
            date = date2,
            dayType = DayType.WORK,
            isPlanned = true,
            timeBlocks = listOf(
                TimeBlock(id = 20L, workDayId = 2L, startTime = LocalTime.of(8, 0), endTime = LocalTime.of(12, 0), isDuration = true)
            )
        )

        val rule = WorkTimeRule(
            id = 1L,
            validFrom = month,
            dailyWorkMinutes = 480,
            monthlyWorkMinutes = 0
        )
        whenever(settingsRepository.getWorkTimeRules()).thenReturn(flowOf(listOf(rule)))
        whenever(workDayRepository.getWorkDaysForMonth(month)).thenReturn(flowOf(listOf(dayAlreadyTarget, dayCustom)))

        val result = useCase.syncMonth(month, previousDailyMinutes = 462)

        assertThat(result.updatedDaysCount).isEqualTo(0)
        assertThat(result.removedDaysCount).isEqualTo(0)
        // Only dayCustom (240 min) should be counted as untouchedCustom, NOT dayAlreadyTarget (480 min)!
        assertThat(result.untouchedCustomDaysCount).isEqualTo(1)
        verify(workDayRepository, never()).saveTimeBlock(any())
    }

    @Test
    fun `syncMonth updates days with known standard duration even when previousDailyMinutes is null, while keeping custom days untouched`() = runTest {
        val month = YearMonth.of(2026, 10)
        val date1 = month.atDay(5) // older standard duration (420 min from defaultSettings)
        val date2 = month.atDay(6) // custom day (240 min)
        val dayStandard = WorkDay(
            id = 1L,
            date = date1,
            dayType = DayType.WORK,
            isPlanned = true,
            timeBlocks = listOf(
                TimeBlock(id = 10L, workDayId = 1L, startTime = LocalTime.of(8, 0), endTime = LocalTime.of(15, 0), isDuration = true)
            )
        )
        val dayCustom = WorkDay(
            id = 2L,
            date = date2,
            dayType = DayType.WORK,
            isPlanned = true,
            timeBlocks = listOf(
                TimeBlock(id = 20L, workDayId = 2L, startTime = LocalTime.of(8, 0), endTime = LocalTime.of(12, 0), isDuration = true)
            )
        )

        val rule = WorkTimeRule(
            id = 1L,
            validFrom = month,
            dailyWorkMinutes = 480,
            monthlyWorkMinutes = 0
        )
        whenever(settingsRepository.getWorkTimeRules()).thenReturn(flowOf(listOf(rule)))
        whenever(workDayRepository.getWorkDaysForMonth(month)).thenReturn(flowOf(listOf(dayStandard, dayCustom)))

        val result = useCase.syncMonth(month, previousDailyMinutes = null)

        assertThat(result.updatedDaysCount).isEqualTo(1)
        assertThat(result.removedDaysCount).isEqualTo(0)
        assertThat(result.untouchedCustomDaysCount).isEqualTo(1)

        val captor = argumentCaptor<TimeBlock>()
        verify(workDayRepository).saveTimeBlock(captor.capture())
        assertThat(captor.firstValue.workDayId).isEqualTo(1L)
        assertThat(captor.firstValue.endTime).isEqualTo(LocalTime.of(16, 0))
    }

    @Test
    fun `syncMonth updates days with known standard duration when previousDailyMinutes matches target (rule edited without hour change)`() = runTest {
        val month = YearMonth.of(2026, 10)
        val date1 = month.atDay(5) // older standard duration (420 min from defaultSettings)
        val date2 = month.atDay(6) // custom day (240 min)
        val dayStandard = WorkDay(
            id = 1L,
            date = date1,
            dayType = DayType.WORK,
            isPlanned = true,
            timeBlocks = listOf(
                TimeBlock(id = 10L, workDayId = 1L, startTime = LocalTime.of(8, 0), endTime = LocalTime.of(15, 0), isDuration = true)
            )
        )
        val dayCustom = WorkDay(
            id = 2L,
            date = date2,
            dayType = DayType.WORK,
            isPlanned = true,
            timeBlocks = listOf(
                TimeBlock(id = 20L, workDayId = 2L, startTime = LocalTime.of(8, 0), endTime = LocalTime.of(12, 0), isDuration = true)
            )
        )

        val rule = WorkTimeRule(
            id = 1L,
            validFrom = month,
            dailyWorkMinutes = 480,
            monthlyWorkMinutes = 0
        )
        whenever(settingsRepository.getWorkTimeRules()).thenReturn(flowOf(listOf(rule)))
        whenever(workDayRepository.getWorkDaysForMonth(month)).thenReturn(flowOf(listOf(dayStandard, dayCustom)))

        val result = useCase.syncMonth(month, previousDailyMinutes = 480)

        assertThat(result.updatedDaysCount).isEqualTo(1)
        assertThat(result.removedDaysCount).isEqualTo(0)
        assertThat(result.untouchedCustomDaysCount).isEqualTo(1)

        val captor = argumentCaptor<TimeBlock>()
        verify(workDayRepository).saveTimeBlock(captor.capture())
        assertThat(captor.firstValue.workDayId).isEqualTo(1L)
        assertThat(captor.firstValue.endTime).isEqualTo(LocalTime.of(16, 0))
    }
}
