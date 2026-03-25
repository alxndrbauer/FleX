package com.flex.ui.month

import com.google.common.truth.Truth.assertThat
import com.flex.domain.model.DayType
import com.flex.domain.model.TimeBlock
import com.flex.domain.model.WorkDay
import com.flex.domain.model.WorkLocation
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalTime

class EditDayDialogStateTest {

    @Test
    fun newWorkDay_noTimeBlocks_returnsDefaults() {
        // New work day with no time blocks should show default times
        val workDay = WorkDay(
            id = 0,
            date = LocalDate.of(2026, 2, 15),
            location = WorkLocation.OFFICE,
            dayType = DayType.WORK,
            isPlanned = false,
            timeBlocks = emptyList()
        )

        val state = calculateEditDayDialogState(workDay, dailyWorkMinutes = 426)

        assertThat(state.selectedTab).isEqualTo(0)  // Start/Ende
        assertThat(state.blocks).hasSize(1)
        assertThat(state.blocks[0].startText).isEqualTo("08:00")
        assertThat(state.blocks[0].durationHours).isEqualTo("7")  // 426 / 60
        assertThat(state.blocks[0].durationMinutes).isEqualTo("6")  // 426 % 60
    }

    @Test
    fun existingWorkDay_withStartEndBlock_loadsCorrectly() {
        // Work day with Start/Ende time block (isDuration=false)
        val workDay = WorkDay(
            id = 1,
            date = LocalDate.of(2026, 2, 15),
            location = WorkLocation.OFFICE,
            dayType = DayType.WORK,
            isPlanned = false,
            timeBlocks = listOf(
                TimeBlock(
                    id = 1,
                    workDayId = 1,
                    startTime = LocalTime.of(9, 30),
                    endTime = LocalTime.of(17, 15),
                    isDuration = false
                )
            )
        )

        val state = calculateEditDayDialogState(workDay)

        assertThat(state.selectedTab).isEqualTo(0)  // Start/Ende tab
        assertThat(state.blocks).hasSize(1)
        assertThat(state.blocks[0].startText).isEqualTo("09:30")
        assertThat(state.blocks[0].endText).isEqualTo("17:15")
        // Duration values should still be initialized with defaults
        assertThat(state.blocks[0].durationHours).isEqualTo("7")
        assertThat(state.blocks[0].durationMinutes).isEqualTo("6")
    }

    @Test
    fun existingWorkDay_withDurationBlock_loadsCorrectly() {
        // Work day with Gesamtzeit duration block (isDuration=true)
        // 10 hours = 8:00 to 18:00
        val workDay = WorkDay(
            id = 2,
            date = LocalDate.of(2026, 2, 14),
            location = WorkLocation.OFFICE,
            dayType = DayType.WORK,
            isPlanned = false,
            timeBlocks = listOf(
                TimeBlock(
                    id = 2,
                    workDayId = 2,
                    startTime = LocalTime.of(8, 0),
                    endTime = LocalTime.of(18, 0),
                    isDuration = true
                )
            )
        )

        val state = calculateEditDayDialogState(workDay)

        assertThat(state.selectedTab).isEqualTo(1)  // Gesamtzeit tab
        assertThat(state.blocks).hasSize(1)
        assertThat(state.blocks[0].durationHours).isEqualTo("10")
        assertThat(state.blocks[0].durationMinutes).isEqualTo("0")
        // Start/End values should still be initialized with defaults
        assertThat(state.blocks[0].startText).isEqualTo("08:00")
    }

    @Test
    fun workDay_withMultipleBlocks_loadsAllBlocks() {
        // Work day with multiple blocks — all blocks should be returned
        val workDay = WorkDay(
            id = 3,
            date = LocalDate.of(2026, 2, 15),
            location = WorkLocation.HOME_OFFICE,
            dayType = DayType.WORK,
            isPlanned = false,
            timeBlocks = listOf(
                TimeBlock(
                    id = 1,
                    workDayId = 3,
                    startTime = LocalTime.of(8, 0),
                    endTime = LocalTime.of(12, 0),
                    isDuration = false
                ),
                TimeBlock(
                    id = 2,
                    workDayId = 3,
                    startTime = LocalTime.of(13, 0),
                    endTime = LocalTime.of(17, 0),
                    isDuration = false
                )
            )
        )

        val state = calculateEditDayDialogState(workDay)

        assertThat(state.selectedTab).isEqualTo(0)
        assertThat(state.blocks).hasSize(2)
        assertThat(state.blocks[0].startText).isEqualTo("08:00")
        assertThat(state.blocks[0].endText).isEqualTo("12:00")
        assertThat(state.blocks[1].startText).isEqualTo("13:00")
        assertThat(state.blocks[1].endText).isEqualTo("17:00")
    }

    @Test
    fun workDayType_vacation_returnsDurationTab() {
        // VACATION should use duration tab (1)
        val workDay = WorkDay(
            id = 4,
            date = LocalDate.of(2026, 2, 15),
            location = WorkLocation.OFFICE,
            dayType = DayType.VACATION,
            isPlanned = false,
            timeBlocks = emptyList()
        )

        val state = calculateEditDayDialogState(workDay)

        assertThat(state.selectedTab).isEqualTo(1)  // Gesamtzeit for vacation
    }

    @Test
    fun workDayType_specialVacation_returnsDurationTab() {
        // SPECIAL_VACATION should use duration tab (1)
        val workDay = WorkDay(
            id = 5,
            date = LocalDate.of(2026, 2, 15),
            location = WorkLocation.OFFICE,
            dayType = DayType.SPECIAL_VACATION,
            isPlanned = false,
            timeBlocks = emptyList()
        )

        val state = calculateEditDayDialogState(workDay)

        assertThat(state.selectedTab).isEqualTo(1)  // Gesamtzeit for special vacation
    }

    @Test
    fun workDayType_flexDay_returnsDurationTab() {
        // FLEX_DAY should use duration tab (1)
        val workDay = WorkDay(
            id = 6,
            date = LocalDate.of(2026, 2, 15),
            location = WorkLocation.OFFICE,
            dayType = DayType.FLEX_DAY,
            isPlanned = false,
            timeBlocks = emptyList()
        )

        val state = calculateEditDayDialogState(workDay)

        assertThat(state.selectedTab).isEqualTo(1)  // Gesamtzeit for flex day
    }

    @Test
    fun workDayType_saturdayBonus_work_returnStartEndTab() {
        // SATURDAY_BONUS is a work day, so uses start/end tab
        val workDay = WorkDay(
            id = 7,
            date = LocalDate.of(2026, 2, 15),
            location = WorkLocation.OFFICE,
            dayType = DayType.SATURDAY_BONUS,
            isPlanned = false,
            timeBlocks = emptyList()
        )

        val state = calculateEditDayDialogState(workDay)

        assertThat(state.selectedTab).isEqualTo(0)  // Start/Ende for Saturday Bonus
        assertThat(state.blocks[0].startText).isEqualTo("08:00")
    }

    @Test
    fun durationBlock_withCustomMinutes_extractsCorrectly() {
        // Duration block with 8 hours 45 minutes
        val workDay = WorkDay(
            id = 8,
            date = LocalDate.of(2026, 2, 15),
            location = WorkLocation.OFFICE,
            dayType = DayType.WORK,
            isPlanned = false,
            timeBlocks = listOf(
                TimeBlock(
                    id = 3,
                    workDayId = 8,
                    startTime = LocalTime.of(8, 0),
                    endTime = LocalTime.of(16, 45),  // 8h 45m
                    isDuration = true
                )
            )
        )

        val state = calculateEditDayDialogState(workDay)

        assertThat(state.selectedTab).isEqualTo(1)
        assertThat(state.blocks[0].durationHours).isEqualTo("8")
        assertThat(state.blocks[0].durationMinutes).isEqualTo("45")
    }

    @Test
    fun startEndBlock_withVariousEndTime_extractsCorrectly() {
        // Start/End block with specific times
        val workDay = WorkDay(
            id = 9,
            date = LocalDate.of(2026, 2, 15),
            location = WorkLocation.HOME_OFFICE,
            dayType = DayType.WORK,
            isPlanned = false,
            timeBlocks = listOf(
                TimeBlock(
                    id = 4,
                    workDayId = 9,
                    startTime = LocalTime.of(7, 30),
                    endTime = LocalTime.of(16, 30),
                    isDuration = false
                )
            )
        )

        val state = calculateEditDayDialogState(workDay)

        assertThat(state.selectedTab).isEqualTo(0)
        assertThat(state.blocks[0].startText).isEqualTo("07:30")
        assertThat(state.blocks[0].endText).isEqualTo("16:30")
    }

    @Test
    fun customDailyWorkMinutes_affectsDefaultDuration() {
        // When dailyWorkMinutes is different, defaults should change
        val workDay = WorkDay(
            id = 10,
            date = LocalDate.of(2026, 2, 15),
            location = WorkLocation.OFFICE,
            dayType = DayType.WORK,
            isPlanned = false,
            timeBlocks = emptyList()
        )

        val state = calculateEditDayDialogState(workDay, dailyWorkMinutes = 480)  // 8 hours

        assertThat(state.blocks[0].durationHours).isEqualTo("8")
        assertThat(state.blocks[0].durationMinutes).isEqualTo("0")
    }
}
