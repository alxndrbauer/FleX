package com.flex.bdd.e2e

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
import com.flex.domain.events.DataChangeEventBus
import com.flex.domain.model.DayType
import com.flex.domain.model.Settings
import com.flex.domain.model.TimeBlock
import com.flex.domain.model.WorkDay
import com.flex.domain.model.WorkLocation
import com.flex.domain.usecase.AutoBookPlannedDaysUseCase
import com.flex.domain.usecase.CalculateDayWorkTimeUseCase
import com.flex.domain.usecase.PrepareExportDataUseCase
import com.flex.domain.usecase.SaveWorkDayUseCase
import com.flex.testing.bddScenario
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
class MonthExportE2EBddTest {

    private lateinit var database: FlexDatabase
    private lateinit var workDayRepository: WorkDayRepositoryImpl
    private lateinit var settingsRepository: SettingsRepositoryImpl
    private lateinit var calculateDayWorkTimeUseCase: CalculateDayWorkTimeUseCase
    private lateinit var saveWorkDayUseCase: SaveWorkDayUseCase
    private lateinit var autoBookPlannedDaysUseCase: AutoBookPlannedDaysUseCase
    private lateinit var prepareExportDataUseCase: PrepareExportDataUseCase

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, FlexDatabase::class.java).build()
        settingsRepository = SettingsRepositoryImpl(database.settingsDao(), database.quotaRuleDao(), database.workTimeRuleDao())
        val calendarSyncService = CalendarSyncService(context, database.calendarEventDao(), CalendarEventMapper())
        workDayRepository = WorkDayRepositoryImpl(database.workDayDao(), database.timeBlockDao(), calendarSyncService, settingsRepository)

        calculateDayWorkTimeUseCase = CalculateDayWorkTimeUseCase()
        saveWorkDayUseCase = SaveWorkDayUseCase(workDayRepository)
        autoBookPlannedDaysUseCase = AutoBookPlannedDaysUseCase(workDayRepository, DataChangeEventBus())
        prepareExportDataUseCase = PrepareExportDataUseCase(
            workDayRepository = workDayRepository,
            settingsRepository = settingsRepository,
            calculateDayWorkTime = calculateDayWorkTimeUseCase
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun completeMonthTrackingAutoBookingAndCsvExportWorkflow() = runTest {
        bddScenario("Monatszyklus mit verschiedenen Arbeitsorten, Auto-Booking und CSV-Export") {
            val month = YearMonth.of(2026, 9)

            Given("Standard-Settings für September 2026") {
                val settings = Settings(
                    dailyWorkMinutes = 426,
                    monthlyWorkMinutes = 8520,
                    officeQuotaPercent = 40,
                    officeQuotaMinDays = 8,
                    settingsYear = 2026
                )
                settingsRepository.saveSettings(settings)
            }

            When("Arbeitstage mit Büro, Home-Office und Mischtag erfasst werden") {
                // Tag 1: Reiner Bürotag
                val day1 = WorkDay(
                    date = LocalDate.of(2026, 9, 1),
                    location = WorkLocation.OFFICE,
                    dayType = DayType.WORK,
                    isPlanned = false,
                    timeBlocks = listOf(
                        TimeBlock(startTime = LocalTime.of(8, 0), endTime = LocalTime.of(16, 30), location = WorkLocation.OFFICE)
                    )
                )
                val id1 = saveWorkDayUseCase(day1)
                day1.timeBlocks.forEach { workDayRepository.saveTimeBlock(it.copy(workDayId = id1)) }

                // Tag 2: Reiner Home-Office-Tag
                val day2 = WorkDay(
                    date = LocalDate.of(2026, 9, 2),
                    location = WorkLocation.HOME_OFFICE,
                    dayType = DayType.WORK,
                    isPlanned = false,
                    timeBlocks = listOf(
                        TimeBlock(startTime = LocalTime.of(9, 0), endTime = LocalTime.of(17, 0), location = WorkLocation.HOME_OFFICE)
                    )
                )
                val id2 = saveWorkDayUseCase(day2)
                day2.timeBlocks.forEach { workDayRepository.saveTimeBlock(it.copy(workDayId = id2)) }

                // Tag 3: Mischtag (Vormittag Büro, Nachmittag HO)
                val day3Blocks = listOf(
                    TimeBlock(startTime = LocalTime.of(8, 0), endTime = LocalTime.of(12, 0), location = WorkLocation.OFFICE),
                    TimeBlock(startTime = LocalTime.of(13, 0), endTime = LocalTime.of(17, 0), location = WorkLocation.HOME_OFFICE)
                )
                val day3 = WorkDay(
                    date = LocalDate.of(2026, 9, 3),
                    location = WorkLocation.OFFICE,
                    dayType = DayType.WORK,
                    isPlanned = false,
                    timeBlocks = day3Blocks
                )
                val id3 = saveWorkDayUseCase(day3)
                day3Blocks.forEach { workDayRepository.saveTimeBlock(it.copy(workDayId = id3)) }

                // Tag 4: Geplanter Bürotag
                val day4 = WorkDay(
                    date = LocalDate.of(2026, 9, 4),
                    location = WorkLocation.OFFICE,
                    dayType = DayType.WORK,
                    isPlanned = true,
                    timeBlocks = listOf(
                        TimeBlock(startTime = LocalTime.of(0, 0), endTime = LocalTime.of(7, 6), isDuration = true, location = WorkLocation.OFFICE)
                    )
                )
                val id4 = saveWorkDayUseCase(day4)
                day4.timeBlocks.forEach { workDayRepository.saveTimeBlock(it.copy(workDayId = id4)) }
            }

            And("der geplante Tag 4 per AutoBookUseCase bestätigt wird") {
                val booked = autoBookPlannedDaysUseCase(LocalDate.of(2026, 9, 10))
                assertThat(booked).isEqualTo(1)

                val day4Updated = workDayRepository.getWorkDay(LocalDate.of(2026, 9, 4)).first()!!
                assertThat(day4Updated.isPlanned).isFalse()
            }

            Then("bereitet PrepareExportDataUseCase alle Tage für den Monatsbericht auf") {
                val exportData = prepareExportDataUseCase(month)
                assertThat(exportData.rows).hasSize(30) // September hat 30 Tage

                val row1 = exportData.rows.first { it.date == LocalDate.of(2026, 9, 1) }
                assertThat(row1.location).isEqualTo(WorkLocation.OFFICE)

                val row3 = exportData.rows.first { it.date == LocalDate.of(2026, 9, 3) }
                assertThat(row3.hasMultipleLocations).isTrue()
                assertThat(row3.blocks).hasSize(2)
            }

            And("die aufbereiteten Exportdaten enthalten korrekte Standortzuordnungen und Summen") {
                val exportData = prepareExportDataUseCase(month)
                val row2 = exportData.rows.first { it.date == LocalDate.of(2026, 9, 2) }
                assertThat(row2.location).isEqualTo(WorkLocation.HOME_OFFICE)
                assertThat(row2.netMinutes).isEqualTo(450L) // 8h - 30m

                val row4 = exportData.rows.first { it.date == LocalDate.of(2026, 9, 4) }
                assertThat(row4.location).isEqualTo(WorkLocation.OFFICE)
                assertThat(row4.netMinutes).isEqualTo(426L)

                // Monatssummen prüfen
                assertThat(exportData.totalNetMinutes).isGreaterThan(0L)
                assertThat(exportData.totalTargetMinutes).isGreaterThan(0L)
            }
        }
    }
}
