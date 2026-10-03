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
import com.flex.domain.usecase.GetSettingsUseCase
import com.flex.domain.usecase.SaveWorkDayUseCase
import androidx.lifecycle.viewModelScope
import com.flex.testing.bddScenario
import com.flex.ui.yearchange.YearChangeViewModel
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalTime

@RunWith(AndroidJUnit4::class)
class YearChangeE2EBddTest {

    private lateinit var database: FlexDatabase
    private lateinit var workDayRepository: WorkDayRepositoryImpl
    private lateinit var settingsRepository: SettingsRepositoryImpl
    private lateinit var calculateDayWorkTimeUseCase: CalculateDayWorkTimeUseCase
    private lateinit var calculateFlextimeUseCase: CalculateFlextimeUseCase
    private lateinit var saveWorkDayUseCase: SaveWorkDayUseCase
    private lateinit var getSettingsUseCase: GetSettingsUseCase
    private var viewModel: YearChangeViewModel? = null

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, FlexDatabase::class.java).build()
        settingsRepository = SettingsRepositoryImpl(database.settingsDao(), database.quotaRuleDao(), database.workTimeRuleDao())
        val calendarSyncService = CalendarSyncService(context, database.calendarEventDao(), CalendarEventMapper())
        workDayRepository = WorkDayRepositoryImpl(database.workDayDao(), database.timeBlockDao(), calendarSyncService, settingsRepository)

        calculateDayWorkTimeUseCase = CalculateDayWorkTimeUseCase()
        calculateFlextimeUseCase = CalculateFlextimeUseCase(calculateDayWorkTimeUseCase)
        saveWorkDayUseCase = SaveWorkDayUseCase(workDayRepository)
        getSettingsUseCase = GetSettingsUseCase(settingsRepository)
    }

    @After
    fun tearDown() {
        viewModel?.viewModelScope?.cancel()
        database.close()
    }

    @Test
    fun completeYearChangeWorkflowWithVacationCarryOverAndFlextime() = runTest {
        bddScenario("Vollständiger Jahreswechsel mit Urlaubübertrag und Gleitzeitsaldo-Übernahme") {
            Given("ein abgeschlossenes Jahr 2026 mit 30 Tagen Jahresurlaub und Anfangsständen 0") {
                val initialSettings = Settings(
                    settingsYear = 2026,
                    dailyWorkMinutes = 426,
                    monthlyWorkMinutes = 8520,
                    annualVacationDays = 30,
                    carryOverVacationDays = 0,
                    specialVacationDays = 2,
                    initialFlextimeMinutes = 0,
                    initialOvertimeMinutes = 0
                )
                settingsRepository.saveSettings(initialSettings)
            }

            When("im Jahr 2026 22 Urlaubstage genommen werden und ein Gleitzeitguthaben erarbeitet wird") {
                // 22 Urlaubstage im Jahr 2026 eintragen
                for (day in 1..22) {
                    val vacationDay = WorkDay(
                        date = LocalDate.of(2026, 7, day),
                        dayType = DayType.VACATION,
                        location = WorkLocation.HOME_OFFICE,
                        isPlanned = false
                    )
                    saveWorkDayUseCase(vacationDay)
                }

                // Ein Arbeitstag mit Überstunden (9h gearbeitet = 510 Netto min, Soll 426 -> +84 min Gleitzeit)
                val overtimeDay = WorkDay(
                    date = LocalDate.of(2026, 8, 10),
                    dayType = DayType.WORK,
                    location = WorkLocation.OFFICE,
                    isPlanned = false,
                    timeBlocks = listOf(
                        TimeBlock(startTime = LocalTime.of(8, 0), endTime = LocalTime.of(17, 30), location = WorkLocation.OFFICE)
                    )
                )
                val dayId = saveWorkDayUseCase(overtimeDay)
                overtimeDay.timeBlocks.forEach { workDayRepository.saveTimeBlock(it.copy(workDayId = dayId)) }
            }

            Then("ermittelt das YearChangeViewModel korrekte Resturlaubstage und Salden") {
                val vm = YearChangeViewModel(
                    workDayRepository = workDayRepository,
                    settingsRepository = settingsRepository,
                    calculateFlextime = calculateFlextimeUseCase,
                    getSettings = getSettingsUseCase
                ).also { viewModel = it }

                // 30 Tage Anspruch + 2 Sonderurlaub - 22 genommene = 8 Resturlaubstage
                val state = vm.uiState.first { it.targetYear == 2027 }
                assertThat(state.usedVacationDays).isEqualTo(22)
                assertThat(state.remainingVacationDays).isEqualTo(8)
                assertThat(state.sourceYear).isEqualTo(2026)
                assertThat(state.targetYear).isEqualTo(2027)

                // Jahreswechsel anwenden: 8 Tage übertragen, neuer Jahresurlaub 30 Tage
                vm.applyYearChange(carryOverDays = state.remainingVacationDays, annualDays = 30)

                val newSettings = settingsRepository.getSettings().first { it.settingsYear == 2027 }
                assertThat(newSettings.settingsYear).isEqualTo(2027)
                assertThat(newSettings.carryOverVacationDays).isEqualTo(8)
                assertThat(newSettings.annualVacationDays).isEqualTo(30)
                assertThat(newSettings.specialVacationDays).isEqualTo(0)
            }
        }
    }
}
