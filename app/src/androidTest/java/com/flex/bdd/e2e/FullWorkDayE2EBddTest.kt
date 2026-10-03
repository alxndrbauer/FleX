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
import com.flex.domain.model.DayType
import com.flex.domain.model.Settings
import com.flex.domain.model.TimeBlock
import com.flex.domain.model.WorkDay
import com.flex.domain.model.WorkLocation
import com.flex.domain.usecase.CalculateDayWorkTimeUseCase
import com.flex.domain.usecase.CalculateFlextimeUseCase
import com.flex.domain.usecase.CalculateQuotaUseCase
import com.flex.domain.usecase.CheckBreakViolationUseCase
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
class FullWorkDayE2EBddTest {

    private lateinit var database: FlexDatabase
    private lateinit var workDayRepository: WorkDayRepositoryImpl
    private lateinit var settingsRepository: SettingsRepositoryImpl
    private lateinit var calculateDayWorkTimeUseCase: CalculateDayWorkTimeUseCase
    private lateinit var calculateFlextimeUseCase: CalculateFlextimeUseCase
    private lateinit var calculateQuotaUseCase: CalculateQuotaUseCase
    private lateinit var checkBreakViolationUseCase: CheckBreakViolationUseCase
    private lateinit var saveWorkDayUseCase: SaveWorkDayUseCase

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, FlexDatabase::class.java).build()
        settingsRepository = SettingsRepositoryImpl(database.settingsDao(), database.quotaRuleDao(), database.workTimeRuleDao())
        val calendarSyncService = CalendarSyncService(context, database.calendarEventDao(), CalendarEventMapper())
        workDayRepository = WorkDayRepositoryImpl(database.workDayDao(), database.timeBlockDao(), calendarSyncService, settingsRepository)

        calculateDayWorkTimeUseCase = CalculateDayWorkTimeUseCase()
        calculateFlextimeUseCase = CalculateFlextimeUseCase(calculateDayWorkTimeUseCase)
        calculateQuotaUseCase = CalculateQuotaUseCase(calculateDayWorkTimeUseCase)
        checkBreakViolationUseCase = CheckBreakViolationUseCase()
        saveWorkDayUseCase = SaveWorkDayUseCase(workDayRepository)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun completeOfficeWorkDayCycleWithBreakAndQuotaCalculation() = runTest {
        bddScenario("Vollständiger Tageszyklus mit Einstempeln, Pause, Ausstempeln und Salden") {
            val date = LocalDate.of(2026, 9, 2) // Mittwoch
            val yearMonth = YearMonth.of(2026, 9)

            Given("ein Standard-Setup mit 426 Min Tagessoll und 40% Büroquote") {
                val settings = Settings(
                    dailyWorkMinutes = 426,
                    monthlyWorkMinutes = 8520,
                    officeQuotaPercent = 40,
                    officeQuotaMinDays = 8,
                    settingsYear = 2026
                )
                settingsRepository.saveSettings(settings)
            }

            When("der Nutzer morgens von 08:00 bis 12:00 im Büro arbeitet und Pause macht") {
                val morningBlock = TimeBlock(
                    startTime = LocalTime.of(8, 0),
                    endTime = LocalTime.of(12, 0),
                    location = WorkLocation.OFFICE
                )
                val day = WorkDay(
                    date = date,
                    location = WorkLocation.OFFICE,
                    dayType = DayType.WORK,
                    isPlanned = false,
                    timeBlocks = listOf(morningBlock)
                )
                val workDayId = saveWorkDayUseCase(day)
                workDayRepository.saveTimeBlock(morningBlock.copy(workDayId = workDayId))
            }

            And("nach einer 30-Minuten Pause von 12:30 bis 16:30 weiterarbeitet und ausstempelt") {
                val afternoonBlock = TimeBlock(
                    startTime = LocalTime.of(12, 30),
                    endTime = LocalTime.of(16, 30),
                    location = WorkLocation.OFFICE
                )
                val savedDay = workDayRepository.getWorkDay(date).first()!!
                workDayRepository.saveTimeBlock(afternoonBlock.copy(workDayId = savedDay.id))
            }

            Then("ist die Nettoarbeitszeit exakt 480 Minuten (8h 00m) ohne zusätzlichen Pausenabzug") {
                val workDay = workDayRepository.getWorkDay(date).first()!!
                assertThat(workDay.timeBlocks).hasSize(2)

                val dayResult = calculateDayWorkTimeUseCase(workDay.timeBlocks)
                assertThat(dayResult.grossMinutes).isEqualTo(480L)
                assertThat(dayResult.breakMinutes).isEqualTo(30L)
                assertThat(dayResult.netMinutes).isEqualTo(480L)
                assertThat(dayResult.exceedsMaxHours).isFalse()
            }

            And("die gesetzliche ArbZG-Pausenprüfung meldet keinerlei Verstöße") {
                val workDay = workDayRepository.getWorkDay(date).first()!!
                val breakResult = checkBreakViolationUseCase(workDay.timeBlocks)
                assertThat(breakResult.violations).isEmpty()
                assertThat(breakResult.skipped).isFalse()
            }

            And("der Gleitzeitbeitrag beträgt exakt +54 Minuten gegenüber dem Tagessoll") {
                val workDay = workDayRepository.getWorkDay(date).first()!!
                val settings = settingsRepository.getSettings().first()
                val flextime = calculateFlextimeUseCase(listOf(workDay), settings, yearMonth)
                assertThat(flextime.earnedMinutes).isEqualTo(54L)
                assertThat(flextime.totalMinutes).isEqualTo(54L)
            }

            And("die Quotenberechnung wertet den Tag als 1 voll erfüllten Bürotag") {
                val workDays = workDayRepository.getWorkDaysForMonth(yearMonth).first()
                val settings = settingsRepository.getSettings().first()
                val quota = calculateQuotaUseCase(workDays, settings, yearMonth)
                assertThat(quota.officeDays).isEqualTo(1)
                assertThat(quota.homeOfficeDays).isEqualTo(0)
                assertThat(quota.officeMinutes).isEqualTo(480L)
            }
        }
    }
}
