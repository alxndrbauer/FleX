package com.flex.bdd.viewmodel

import android.content.Context
import com.flex.data.backup.BackupPreferences
import com.flex.data.local.WhatsNewPreferences
import com.flex.domain.events.DataChangeEventBus
import com.flex.domain.events.UndoEvent
import com.flex.domain.model.BreakCheckResult
import com.flex.domain.model.DayType
import com.flex.domain.model.FlextimeBalance
import com.flex.domain.model.QuotaStatus
import com.flex.domain.model.Settings
import com.flex.domain.model.TimeBlock
import com.flex.domain.model.WorkDay
import com.flex.domain.model.WorkLocation
import com.flex.domain.repository.SettingsRepository
import com.flex.domain.repository.WorkDayRepository
import com.flex.domain.usecase.AutoBookPlannedDaysUseCase
import com.flex.domain.usecase.CalculateDayWorkTimeUseCase
import com.flex.domain.usecase.CalculateFlextimeUseCase
import com.flex.domain.usecase.CalculateQuotaUseCase
import com.flex.domain.usecase.CheckBreakViolationUseCase
import com.flex.domain.usecase.CheckTimeBlockOverlapUseCase
import com.flex.domain.usecase.DayWorkTimeResult
import com.flex.domain.usecase.GetMonthWorkDaysUseCase
import com.flex.domain.usecase.GetSettingsUseCase
import com.flex.notification.BreakWarningScheduler
import com.flex.ui.home.HomeViewModel
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import java.time.LocalDate
import java.time.LocalTime

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelBehaviorSpec : BehaviorSpec({

    val testDispatcher = UnconfinedTestDispatcher()

    beforeSpec {
        Dispatchers.setMain(testDispatcher)
    }

    afterSpec {
        Dispatchers.resetMain()
    }

    fun setupDependencies(): Pair<WorkDayRepository, HomeViewModel> {
        val context = mockk<Context>(relaxed = true)
        val workDayRepository = mockk<WorkDayRepository>(relaxed = true)
        val settingsRepository = mockk<SettingsRepository>(relaxed = true)
        val getMonthWorkDays = mockk<GetMonthWorkDaysUseCase>(relaxed = true)
        val getSettings = mockk<GetSettingsUseCase>(relaxed = true)
        val calculateDayWorkTime = mockk<CalculateDayWorkTimeUseCase>(relaxed = true)
        val calculateFlextime = mockk<CalculateFlextimeUseCase>(relaxed = true)
        val calculateQuota = mockk<CalculateQuotaUseCase>(relaxed = true)
        val dataChangeEventBus = mockk<DataChangeEventBus>(relaxed = true)
        val checkBreakViolation = mockk<CheckBreakViolationUseCase>(relaxed = true)
        val breakWarningScheduler = mockk<BreakWarningScheduler>(relaxed = true)
        val whatsNewPreferences = mockk<WhatsNewPreferences>(relaxed = true)
        val backupPreferences = mockk<BackupPreferences>(relaxed = true)
        val autoBookPlannedDays = mockk<AutoBookPlannedDaysUseCase>(relaxed = true)
        val checkTimeBlockOverlap = CheckTimeBlockOverlapUseCase()

        every { getSettings() } returns flowOf(Settings())
        every { getMonthWorkDays(any()) } returns flowOf(emptyList())
        every { settingsRepository.getQuotaRules() } returns flowOf(emptyList())
        every { settingsRepository.getWorkTimeRules() } returns flowOf(emptyList())
        every { workDayRepository.getWorkDaysForYear(any()) } returns flowOf(emptyList())
        every { calculateDayWorkTime(any()) } returns DayWorkTimeResult(0, 0, 0, false)
        every { calculateDayWorkTime(any(), any()) } returns DayWorkTimeResult(0, 0, 0, false)
        every { calculateFlextime(any(), any(), any(), any()) } returns FlextimeBalance()
        every { calculateQuota(any(), any(), any(), any(), any(), any()) } returns QuotaStatus()
        every { dataChangeEventBus.events } returns MutableSharedFlow()
        every { checkBreakViolation(any(), any()) } returns BreakCheckResult(emptyList(), skipped = false)
        every { checkBreakViolation(any(), any(), any()) } returns BreakCheckResult(emptyList(), skipped = false)
        every { whatsNewPreferences.getLastSeenVersionCode() } returns 0
        every { backupPreferences.isAutoBackupEnabled } returns false

        val vm = HomeViewModel(
            context = context,
            workDayRepository = workDayRepository,
            settingsRepository = settingsRepository,
            getMonthWorkDays = getMonthWorkDays,
            getSettings = getSettings,
            calculateDayWorkTime = calculateDayWorkTime,
            calculateFlextime = calculateFlextime,
            calculateQuota = calculateQuota,
            dataChangeEventBus = dataChangeEventBus,
            checkBreakViolation = checkBreakViolation,
            breakWarningScheduler = breakWarningScheduler,
            whatsNewPreferences = whatsNewPreferences,
            backupPreferences = backupPreferences,
            autoBookPlannedDaysUseCase = autoBookPlannedDays,
            checkTimeBlockOverlap = checkTimeBlockOverlap
        )
        return Pair(workDayRepository, vm)
    }

    Given("ein noch nicht gestarteter Arbeitstag") {
        val today = LocalDate.now()
        val workDayFlow = MutableStateFlow<WorkDay?>(null)

        val context = mockk<Context>(relaxed = true)
        val workDayRepository = mockk<WorkDayRepository>(relaxed = true)
        val settingsRepository = mockk<SettingsRepository>(relaxed = true)
        val getMonthWorkDays = mockk<GetMonthWorkDaysUseCase>(relaxed = true)
        val getSettings = mockk<GetSettingsUseCase>(relaxed = true)
        val calculateDayWorkTime = mockk<CalculateDayWorkTimeUseCase>(relaxed = true)
        val calculateFlextime = mockk<CalculateFlextimeUseCase>(relaxed = true)
        val calculateQuota = mockk<CalculateQuotaUseCase>(relaxed = true)
        val dataChangeEventBus = mockk<DataChangeEventBus>(relaxed = true)
        val checkBreakViolation = mockk<CheckBreakViolationUseCase>(relaxed = true)
        val breakWarningScheduler = mockk<BreakWarningScheduler>(relaxed = true)
        val whatsNewPreferences = mockk<WhatsNewPreferences>(relaxed = true)
        val backupPreferences = mockk<BackupPreferences>(relaxed = true)
        val autoBookPlannedDays = mockk<AutoBookPlannedDaysUseCase>(relaxed = true)

        coEvery { workDayRepository.getWorkDay(today) } returns workDayFlow
        every { getSettings() } returns flowOf(Settings())
        every { getMonthWorkDays(any()) } returns flowOf(emptyList())
        every { settingsRepository.getQuotaRules() } returns flowOf(emptyList())
        every { settingsRepository.getWorkTimeRules() } returns flowOf(emptyList())
        every { workDayRepository.getWorkDaysForYear(any()) } returns flowOf(emptyList())
        every { calculateDayWorkTime(any()) } returns DayWorkTimeResult(0, 0, 0, false)
        every { calculateDayWorkTime(any(), any()) } returns DayWorkTimeResult(0, 0, 0, false)
        every { calculateFlextime(any(), any(), any(), any()) } returns FlextimeBalance()
        every { calculateQuota(any(), any(), any(), any(), any(), any()) } returns QuotaStatus()
        every { dataChangeEventBus.events } returns MutableSharedFlow()
        every { checkBreakViolation(any(), any()) } returns BreakCheckResult(emptyList(), skipped = false)
        every { whatsNewPreferences.getLastSeenVersionCode() } returns 0
        every { backupPreferences.isAutoBackupEnabled } returns false

        val viewModel = HomeViewModel(
            context, workDayRepository, settingsRepository, getMonthWorkDays,
            getSettings, calculateDayWorkTime, calculateFlextime, calculateQuota,
            dataChangeEventBus, checkBreakViolation, breakWarningScheduler,
            whatsNewPreferences, backupPreferences, autoBookPlannedDays
        )

        When("der initiale State geladen ist") {
            Then("zeigt der State den heutigen Tag") {
                viewModel.uiState.value.today shouldBe today
            }

            Then("ist die Stempeluhr nicht aktiv") {
                viewModel.uiState.value.isClockRunning.shouldBeFalse()
            }

            Then("sind keine Zeitblöcke vorhanden") {
                viewModel.uiState.value.timeBlocks.shouldBeEmpty()
            }
        }
    }

    Given("ein Nutzer stempelt sich im Büro ein") {
        val today = LocalDate.now()
        val workDayFlow = MutableStateFlow<WorkDay?>(null)
        var currentWorkDay: WorkDay? = null

        val context = mockk<Context>(relaxed = true)
        val workDayRepository = mockk<WorkDayRepository>(relaxed = true)
        val settingsRepository = mockk<SettingsRepository>(relaxed = true)
        val getMonthWorkDays = mockk<GetMonthWorkDaysUseCase>(relaxed = true)
        val getSettings = mockk<GetSettingsUseCase>(relaxed = true)
        val calculateDayWorkTime = mockk<CalculateDayWorkTimeUseCase>(relaxed = true)
        val calculateFlextime = mockk<CalculateFlextimeUseCase>(relaxed = true)
        val calculateQuota = mockk<CalculateQuotaUseCase>(relaxed = true)
        val dataChangeEventBus = mockk<DataChangeEventBus>(relaxed = true)
        val checkBreakViolation = mockk<CheckBreakViolationUseCase>(relaxed = true)
        val breakWarningScheduler = mockk<BreakWarningScheduler>(relaxed = true)
        val whatsNewPreferences = mockk<WhatsNewPreferences>(relaxed = true)
        val backupPreferences = mockk<BackupPreferences>(relaxed = true)
        val autoBookPlannedDays = mockk<AutoBookPlannedDaysUseCase>(relaxed = true)

        coEvery { workDayRepository.getWorkDay(today) } returns workDayFlow
        coEvery { workDayRepository.saveWorkDay(any()) } answers {
            val wd = firstArg<WorkDay>()
            val saved = if (wd.id == 0L) wd.copy(id = 1L) else wd
            currentWorkDay = saved
            workDayFlow.value = saved
            saved.id
        }
        coEvery { workDayRepository.saveTimeBlock(any()) } answers {
            val tb = firstArg<TimeBlock>()
            val saved = if (tb.id == 0L) tb.copy(id = 10L) else tb
            val existing = currentWorkDay ?: WorkDay(id = 1L, date = today, location = tb.location)
            val updated = existing.copy(timeBlocks = listOf(saved))
            currentWorkDay = updated
            workDayFlow.value = updated
            saved.id
        }

        every { getSettings() } returns flowOf(Settings())
        every { getMonthWorkDays(any()) } returns flowOf(emptyList())
        every { settingsRepository.getQuotaRules() } returns flowOf(emptyList())
        every { settingsRepository.getWorkTimeRules() } returns flowOf(emptyList())
        every { workDayRepository.getWorkDaysForYear(any()) } returns flowOf(emptyList())
        every { calculateDayWorkTime(any()) } returns DayWorkTimeResult(0, 0, 0, false)
        every { calculateDayWorkTime(any(), any()) } returns DayWorkTimeResult(0, 0, 0, false)
        every { calculateFlextime(any(), any(), any(), any()) } returns FlextimeBalance()
        every { calculateQuota(any(), any(), any(), any(), any(), any()) } returns QuotaStatus()
        every { dataChangeEventBus.events } returns MutableSharedFlow()
        every { checkBreakViolation(any(), any()) } returns BreakCheckResult(emptyList(), skipped = false)
        every { whatsNewPreferences.getLastSeenVersionCode() } returns 0
        every { backupPreferences.isAutoBackupEnabled } returns false

        val viewModel = HomeViewModel(
            context, workDayRepository, settingsRepository, getMonthWorkDays,
            getSettings, calculateDayWorkTime, calculateFlextime, calculateQuota,
            dataChangeEventBus, checkBreakViolation, breakWarningScheduler,
            whatsNewPreferences, backupPreferences, autoBookPlannedDays
        )

        When("clockIn mit OFFICE ausgeführt wird") {
            viewModel.setLocation(WorkLocation.OFFICE)
            viewModel.clockIn()

            Then("wird isClockRunning true") {
                viewModel.uiState.value.isClockRunning.shouldBeTrue()
            }

            Then("erscheint ein offener Block mit WorkLocation OFFICE") {
                viewModel.uiState.value.timeBlocks.shouldHaveSize(1)
                val block = viewModel.uiState.value.timeBlocks.first()
                block.endTime.shouldBeNull()
                block.location shouldBe WorkLocation.OFFICE
            }

            Then("wurde der WorkDay im Repository persistiert") {
                coVerify { workDayRepository.saveWorkDay(match { it.location == WorkLocation.OFFICE }) }
            }
        }
    }

    Given("ein laufender Arbeitsblock beim Ausstempeln") {
        val today = LocalDate.now()
        val runningBlock = TimeBlock(id = 10L, workDayId = 1L, startTime = LocalTime.of(9, 0), endTime = null, location = WorkLocation.OFFICE)
        val initialDay = WorkDay(id = 1L, date = today, location = WorkLocation.OFFICE, timeBlocks = listOf(runningBlock))
        val workDayFlow = MutableStateFlow<WorkDay?>(initialDay)
        var currentWorkDay: WorkDay? = initialDay

        val context = mockk<Context>(relaxed = true)
        val workDayRepository = mockk<WorkDayRepository>(relaxed = true)
        val settingsRepository = mockk<SettingsRepository>(relaxed = true)
        val getMonthWorkDays = mockk<GetMonthWorkDaysUseCase>(relaxed = true)
        val getSettings = mockk<GetSettingsUseCase>(relaxed = true)
        val calculateDayWorkTime = mockk<CalculateDayWorkTimeUseCase>(relaxed = true)
        val calculateFlextime = mockk<CalculateFlextimeUseCase>(relaxed = true)
        val calculateQuota = mockk<CalculateQuotaUseCase>(relaxed = true)
        val dataChangeEventBus = mockk<DataChangeEventBus>(relaxed = true)
        val checkBreakViolation = mockk<CheckBreakViolationUseCase>(relaxed = true)
        val breakWarningScheduler = mockk<BreakWarningScheduler>(relaxed = true)
        val whatsNewPreferences = mockk<WhatsNewPreferences>(relaxed = true)
        val backupPreferences = mockk<BackupPreferences>(relaxed = true)
        val autoBookPlannedDays = mockk<AutoBookPlannedDaysUseCase>(relaxed = true)

        coEvery { workDayRepository.getWorkDay(today) } returns workDayFlow
        coEvery { workDayRepository.saveTimeBlock(any()) } answers {
            val tb = firstArg<TimeBlock>()
            val updated = currentWorkDay!!.copy(timeBlocks = listOf(tb))
            currentWorkDay = updated
            workDayFlow.value = updated
            tb.id
        }

        every { getSettings() } returns flowOf(Settings())
        every { getMonthWorkDays(any()) } returns flowOf(emptyList())
        every { settingsRepository.getQuotaRules() } returns flowOf(emptyList())
        every { settingsRepository.getWorkTimeRules() } returns flowOf(emptyList())
        every { workDayRepository.getWorkDaysForYear(any()) } returns flowOf(emptyList())
        every { calculateDayWorkTime(any()) } returns DayWorkTimeResult(0, 0, 0, false)
        every { calculateDayWorkTime(any(), any()) } returns DayWorkTimeResult(0, 0, 0, false)
        every { calculateFlextime(any(), any(), any(), any()) } returns FlextimeBalance()
        every { calculateQuota(any(), any(), any(), any(), any(), any()) } returns QuotaStatus()
        every { dataChangeEventBus.events } returns MutableSharedFlow()
        every { checkBreakViolation(any(), any()) } returns BreakCheckResult(emptyList(), skipped = false)
        every { whatsNewPreferences.getLastSeenVersionCode() } returns 0
        every { backupPreferences.isAutoBackupEnabled } returns false

        val viewModel = HomeViewModel(
            context, workDayRepository, settingsRepository, getMonthWorkDays,
            getSettings, calculateDayWorkTime, calculateFlextime, calculateQuota,
            dataChangeEventBus, checkBreakViolation, breakWarningScheduler,
            whatsNewPreferences, backupPreferences, autoBookPlannedDays
        )

        When("clockOut aufgerufen wird") {
            viewModel.clockOut()

            Then("wird der Block abgeschlossen und isClockRunning wird false") {
                viewModel.uiState.value.isClockRunning.shouldBeFalse()
                val block = viewModel.uiState.value.timeBlocks.first()
                block.endTime.shouldNotBeNull()
            }
        }
    }

    Given("die Erfassung eines manuellen Eintrags") {
        val (workDayRepository, viewModel) = setupDependencies()
        val start = LocalTime.of(8, 30)
        val end = LocalTime.of(16, 30)

        When("saveManualEntry aufgerufen wird") {
            viewModel.saveManualEntry(start, end, WorkLocation.OFFICE)

            Then("wird ein neuer TimeBlock im Repository persistiert") {
                val blockSlot = slot<TimeBlock>()
                coVerify { workDayRepository.saveTimeBlock(capture(blockSlot)) }
                blockSlot.captured.startTime shouldBe start
                blockSlot.captured.endTime shouldBe end
                blockSlot.captured.location shouldBe WorkLocation.OFFICE
                blockSlot.captured.isDuration.shouldBeFalse()
            }
        }
    }

    Given("die Erfassung eines Dauer-Eintrags") {
        val (workDayRepository, viewModel) = setupDependencies()

        When("saveDurationEntry mit 480 Minuten gespeichert wird") {
            viewModel.saveDurationEntry(480, WorkLocation.HOME_OFFICE)

            Then("wird ein TimeBlock mit isDuration = true persistiert") {
                val blockSlot = slot<TimeBlock>()
                coVerify { workDayRepository.saveTimeBlock(capture(blockSlot)) }
                blockSlot.captured.isDuration.shouldBeTrue()
                blockSlot.captured.location shouldBe WorkLocation.HOME_OFFICE
                blockSlot.captured.endTime shouldBe blockSlot.captured.startTime.plusMinutes(480)
            }
        }
    }

    Given("das Löschen eines Zeitblocks mit UndoEvent") {
        val (workDayRepository, viewModel) = setupDependencies()
        val block = TimeBlock(id = 55L, workDayId = 1L, startTime = LocalTime.of(9, 0), endTime = LocalTime.of(12, 0))

        When("deleteTimeBlock ausgeführt wird") {
            var receivedUndoEvent: UndoEvent? = null
            val collectJob = launch(UnconfinedTestDispatcher()) {
                viewModel.undoEvent.collect { receivedUndoEvent = it }
            }

            viewModel.deleteTimeBlock(block)

            Then("wird der Block aus dem Repository gelöscht") {
                coVerify { workDayRepository.deleteTimeBlock(block) }
            }

            Then("wird ein UndoEvent emittiert") {
                receivedUndoEvent.shouldNotBeNull()
                receivedUndoEvent.message shouldBe "Block gelöscht"
            }

            collectJob.cancel()
        }
    }

    Given("das Umschalten des Standorts eines Blocks") {
        val (workDayRepository, viewModel) = setupDependencies()
        val officeBlock = TimeBlock(id = 22L, workDayId = 1L, startTime = LocalTime.of(10, 0), endTime = LocalTime.of(14, 0), location = WorkLocation.OFFICE)

        When("toggleTimeBlockLocation aufgerufen wird") {
            viewModel.toggleTimeBlockLocation(officeBlock)

            Then("wechselt der Standort des Blocks zu HOME_OFFICE") {
                coVerify { workDayRepository.saveTimeBlock(match { it.id == 22L && it.location == WorkLocation.HOME_OFFICE }) }
            }
        }

        When("toggleTimeBlockLocation für HOME_OFFICE aufgerufen wird") {
            val hoBlock = officeBlock.copy(location = WorkLocation.HOME_OFFICE)
            viewModel.toggleTimeBlockLocation(hoBlock)

            Then("wechselt der Standort zurück zu OFFICE") {
                coVerify { workDayRepository.saveTimeBlock(match { it.id == 22L && it.location == WorkLocation.OFFICE }) }
            }
        }
    }

    Given("das Löschen eines Tages") {
        val today = LocalDate.now()
        val block1 = TimeBlock(id = 1L, workDayId = 10L, startTime = LocalTime.of(8, 0), endTime = LocalTime.of(12, 0))
        val block2 = TimeBlock(id = 2L, workDayId = 10L, startTime = LocalTime.of(13, 0), endTime = LocalTime.of(17, 0))
        val workDay = WorkDay(id = 10L, date = today, timeBlocks = listOf(block1, block2))

        val context = mockk<Context>(relaxed = true)
        val workDayRepository = mockk<WorkDayRepository>(relaxed = true)
        val settingsRepository = mockk<SettingsRepository>(relaxed = true)
        val getMonthWorkDays = mockk<GetMonthWorkDaysUseCase>(relaxed = true)
        val getSettings = mockk<GetSettingsUseCase>(relaxed = true)
        val calculateDayWorkTime = mockk<CalculateDayWorkTimeUseCase>(relaxed = true)
        val calculateFlextime = mockk<CalculateFlextimeUseCase>(relaxed = true)
        val calculateQuota = mockk<CalculateQuotaUseCase>(relaxed = true)
        val dataChangeEventBus = mockk<DataChangeEventBus>(relaxed = true)
        val checkBreakViolation = mockk<CheckBreakViolationUseCase>(relaxed = true)
        val breakWarningScheduler = mockk<BreakWarningScheduler>(relaxed = true)
        val whatsNewPreferences = mockk<WhatsNewPreferences>(relaxed = true)
        val backupPreferences = mockk<BackupPreferences>(relaxed = true)
        val autoBookPlannedDays = mockk<AutoBookPlannedDaysUseCase>(relaxed = true)

        coEvery { workDayRepository.getWorkDay(today) } returns flowOf(workDay)
        every { getSettings() } returns flowOf(Settings())
        every { getMonthWorkDays(any()) } returns flowOf(emptyList())
        every { settingsRepository.getQuotaRules() } returns flowOf(emptyList())
        every { settingsRepository.getWorkTimeRules() } returns flowOf(emptyList())
        every { workDayRepository.getWorkDaysForYear(any()) } returns flowOf(emptyList())
        every { calculateDayWorkTime(any()) } returns DayWorkTimeResult(0, 0, 0, false)
        every { calculateDayWorkTime(any(), any()) } returns DayWorkTimeResult(0, 0, 0, false)
        every { calculateFlextime(any(), any(), any(), any()) } returns FlextimeBalance()
        every { calculateQuota(any(), any(), any(), any(), any(), any()) } returns QuotaStatus()
        every { dataChangeEventBus.events } returns MutableSharedFlow()
        every { checkBreakViolation(any(), any()) } returns BreakCheckResult(emptyList(), skipped = false)
        every { whatsNewPreferences.getLastSeenVersionCode() } returns 0
        every { backupPreferences.isAutoBackupEnabled } returns false

        val viewModel = HomeViewModel(
            context, workDayRepository, settingsRepository, getMonthWorkDays,
            getSettings, calculateDayWorkTime, calculateFlextime, calculateQuota,
            dataChangeEventBus, checkBreakViolation, breakWarningScheduler,
            whatsNewPreferences, backupPreferences, autoBookPlannedDays
        )

        When("deleteDay aufgerufen wird") {
            viewModel.deleteDay()

            Then("werden alle Zeitblöcke des Tages gelöscht") {
                coVerify { workDayRepository.deleteTimeBlock(block1) }
                coVerify { workDayRepository.deleteTimeBlock(block2) }
            }

            Then("wird der WorkDay gelöscht") {
                coVerify { workDayRepository.deleteWorkDay(workDay) }
            }
        }
    }
})
