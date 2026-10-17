package com.flex.bdd.feature.worktimemodel

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.flex.data.backup.BackupPreferences
import com.flex.data.export.ExportService
import com.flex.data.local.WhatsNewPreferences
import com.flex.domain.events.DataChangeEventBus
import com.flex.domain.model.DEFAULT_WORK_DAYS
import com.flex.domain.model.DayType
import com.flex.domain.model.Settings
import com.flex.domain.model.TimeBlock
import com.flex.domain.model.WorkDay
import com.flex.domain.model.WorkLocation
import com.flex.domain.model.WorkTimeRule
import com.flex.domain.model.getRuleForDate
import com.flex.domain.model.getRuleForMonth
import com.flex.domain.repository.SettingsRepository
import com.flex.domain.repository.WorkDayRepository
import com.flex.domain.usecase.AutoBookPlannedDaysUseCase
import com.flex.domain.usecase.CalculateDashboardMetricsUseCase
import com.flex.domain.usecase.CalculateDayWorkTimeUseCase
import com.flex.domain.usecase.CalculateFlextimeUseCase
import com.flex.domain.usecase.CalculateQuotaUseCase
import com.flex.domain.usecase.CheckBreakViolationUseCase
import com.flex.domain.usecase.CheckTimeBlockOverlapUseCase
import com.flex.domain.usecase.GetMonthWorkDaysUseCase
import com.flex.domain.usecase.GetSettingsUseCase
import com.flex.domain.usecase.PrepareExportDataUseCase
import com.flex.notification.BreakWarningScheduler
import androidx.compose.ui.graphics.Color
import com.flex.domain.usecase.BuildPrognosisDaysUseCase
import com.flex.ui.home.HomeViewModel
import com.flex.ui.month.MonthViewModel
import com.flex.ui.month.getMonthDayCellColor
import com.flex.ui.year.YearOverviewViewModel
import com.flex.widget.FlexWidgetDataProvider
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import java.io.ByteArrayOutputStream
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

/**
 * Übergreifende BDD-Feature-Spezifikation für das Verhalten von Arbeitszeitregeln (WorkTimeRule).
 *
 * Deckt ab:
 * 1. Export mit 4-Tage-Woche (PrepareExportDataUseCase & ExportService)
 * 2. Dashboard-Wochenmetriken mit 4-Tage-Woche (CalculateDashboardMetricsUseCase)
 * 3. HomeView mit wechselnden Arbeitszeitregeln (HomeViewModel)
 * 4. Jahresübersicht mit 4-Tage-Woche und unterjährigem Wechsel (YearOverviewViewModel)
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WorkTimeRuleIntegrationBehaviorSpec : BehaviorSpec({

    val testDispatcher = UnconfinedTestDispatcher()

    beforeSpec {
        Dispatchers.setMain(testDispatcher)
    }

    afterSpec {
        Dispatchers.resetMain()
    }

    val fourDays = setOf(
        DayOfWeek.MONDAY,
        DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY
    )

    val baseSettings = Settings(
        dailyWorkMinutes = 426, // 7h 06min Fallback
        monthlyWorkMinutes = 9372,
        officeQuotaPercent = 40,
        officeQuotaMinDays = 8
    )

    val calculateDayWorkTime = CalculateDayWorkTimeUseCase()

    fun createDurationBlock(minutes: Long, location: WorkLocation = WorkLocation.OFFICE): TimeBlock {
        val start = LocalTime.of(8, 0)
        return TimeBlock(
            startTime = start,
            endTime = start.plusMinutes(minutes),
            location = location,
            isDuration = true
        )
    }

    // =========================================================================
    // Feature 1: Export mit 4-Tage-Woche und Stichtagswechsel
    // =========================================================================
    Given("ein Mitarbeiter im 4-Tage-Woche-Modell für den Monats-Export") {
        val testMonth = YearMonth.of(2026, 9) // 30 Tage, 18 Mo-Do Tage, 4 Freitage
        val fourDayRule = WorkTimeRule(
            id = 1L,
            validFrom = testMonth,
            dailyWorkMinutes = 533, // 8h 53min
            monthlyWorkMinutes = 18 * 533, // 9594 Min
            workDays = fourDays
        )
        val rules = listOf(fourDayRule)

        val workDayRepo = mockk<WorkDayRepository>()
        val settingsRepo = mockk<SettingsRepository>()

        // 1 regulärer Arbeitstag (Donnerstag 03.09., 533 Min)
        val thu = WorkDay(
            id = 3L,
            date = LocalDate.of(2026, 9, 3),
            dayType = DayType.WORK,
            location = WorkLocation.OFFICE,
            timeBlocks = listOf(createDurationBlock(533))
        )
        // 1 freier Freitag mit freiwilligem Einsatz (Freitag 04.09., 240 Min)
        val fri = WorkDay(
            id = 4L,
            date = LocalDate.of(2026, 9, 4),
            dayType = DayType.WORK,
            location = WorkLocation.HOME_OFFICE,
            timeBlocks = listOf(createDurationBlock(240, WorkLocation.HOME_OFFICE))
        )

        every { workDayRepo.getWorkDaysForMonth(testMonth) } returns flowOf(listOf(thu, fri))
        every { settingsRepo.getSettings() } returns flowOf(baseSettings)
        every { settingsRepo.getWorkTimeRules() } returns flowOf(rules)
        every { settingsRepo.getWorkTimeRuleForDate(any(), any()) } answers {
            val d = firstArg<LocalDate>()
            rules.getRuleForDate(d)
        }

        val prepareExportData = PrepareExportDataUseCase(workDayRepo, settingsRepo, calculateDayWorkTime)
        val exportService = ExportService()

        When("die Exportdaten für September 2026 aufbereitet werden") {
            val exportData = prepareExportData(testMonth)

            Then("haben reguläre Arbeitstage (Mo-Do) das konfigurierte Tagessoll von 533 Minuten") {
                // Donnerstag 03.09.2026: regulärer Arbeitstag
                val rowThu = exportData.rows.first { it.date == LocalDate.of(2026, 9, 3) }
                rowThu.targetMinutes shouldBe 533
                rowThu.netMinutes shouldBe 533L
                (rowThu.netMinutes - rowThu.targetMinutes) shouldBe 0L
            }

            Then("haben freie Freitage ein Tagessoll von 0 Minuten") {
                // Freitag 04.09.2026: gearbeiteter Freitag -> Soll 0, 100% Netto als Plus
                val rowFri = exportData.rows.first { it.date == LocalDate.of(2026, 9, 4) }
                rowFri.targetMinutes shouldBe 0
                rowFri.netMinutes shouldBe 240L
                (rowFri.netMinutes - rowFri.targetMinutes) shouldBe 240L

                // Freitag 11.09.2026: ungearbeiteter Freitag -> Soll 0
                val rowEmptyFri = exportData.rows.first { it.date == LocalDate.of(2026, 9, 11) }
                rowEmptyFri.targetMinutes shouldBe 0
                rowEmptyFri.netMinutes shouldBe 0L
            }

            Then("beträgt das Gesamtsoll des Monats exakt 18 * 533 = 9.594 Minuten") {
                exportData.totalTargetMinutes shouldBe 18L * 533L
                exportData.totalTargetMinutes shouldBe 9594L
            }
        }

        When("der CSV-Export generiert wird") {
            val exportData = prepareExportData(testMonth)
            val outputStream = ByteArrayOutputStream()
            val contentResolver = mockk<ContentResolver>()
            val uri = mockk<Uri>()

            every { contentResolver.openOutputStream(uri) } returns outputStream
            exportService.exportToCsv(exportData, uri, contentResolver)
            val lines = outputStream.toString(Charsets.UTF_8).removePrefix("\uFEFF").trim().lines()

            Then("enthält die CSV-Zeile für den freien Freitag das Soll - und die volle Arbeitszeit als Plus") {
                val friLine = lines.first { it.startsWith("04.09.2026;") }
                friLine shouldContain ";-;+4:00;"
            }

            Then("weist die Summenzeile das korrekte Gesamtsoll aus") {
                val summaryLine = lines.last()
                // 9594 Min = 159h 54m
                summaryLine shouldContain ";159:54;"
            }
        }
    }

    // =========================================================================
    // Feature 2: Dashboard Wochenstatus mit 4-Tage-Woche
    // =========================================================================
    Given("die Berechnung des aktuellen Wochenstatus (CalculateDashboardMetricsUseCase)") {
        val calculateDashboardMetrics = CalculateDashboardMetricsUseCase(calculateDayWorkTime)

        // Test-Datum: Mittwoch, 16.09.2026 (KW 38)
        val today = LocalDate.of(2026, 9, 16)
        val fourDayRule = WorkTimeRule(
            id = 1L,
            validFrom = YearMonth.of(2026, 9),
            dailyWorkMinutes = 533,
            monthlyWorkMinutes = 9594,
            workDays = fourDays
        )

        // Mo: 533 Min, Di: 533 Min, Mi: 533 Min gearbeitet
        val weekDays = listOf(
            WorkDay(date = LocalDate.of(2026, 9, 14), dayType = DayType.WORK, timeBlocks = listOf(createDurationBlock(533))),
            WorkDay(date = LocalDate.of(2026, 9, 15), dayType = DayType.WORK, timeBlocks = listOf(createDurationBlock(533))),
            WorkDay(date = LocalDate.of(2026, 9, 16), dayType = DayType.WORK, timeBlocks = listOf(createDurationBlock(533)))
        )

        When("der Wochenstatus für das 4-Tage-Modell berechnet wird") {
            val status = calculateDashboardMetrics.calculateCurrentWeekStatus(
                today = today,
                yearDays = weekDays,
                settings = baseSettings,
                workTimeRules = listOf(fourDayRule)
            )

            Then("beträgt das Wochenziel exakt 4 * 533 = 2.132 Minuten (nicht 5 * 533 = 2.665)") {
                status.targetMinutes shouldBe 2132L
            }

            Then("betragen die bisher geleisteten Minuten 3 * 533 = 1.599 Minuten") {
                status.workedMinutes shouldBe 1599L
            }
        }

        When("zusätzlich an einem freien Freitag 240 Minuten geleistet werden") {
            val weekDaysWithFriday = weekDays + listOf(
                WorkDay(date = LocalDate.of(2026, 9, 18), dayType = DayType.WORK, timeBlocks = listOf(createDurationBlock(240)))
            )
            val status = calculateDashboardMetrics.calculateCurrentWeekStatus(
                today = today,
                yearDays = weekDaysWithFriday,
                settings = baseSettings,
                workTimeRules = listOf(fourDayRule)
            )

            Then("erhöht sich die geleistete Arbeitszeit auf 1.839 Minuten, während das Wochenziel unverändert bei 2.132 Minuten bleibt") {
                status.workedMinutes shouldBe 1839L
                status.targetMinutes shouldBe 2132L
            }
        }
    }

    // =========================================================================
    // Feature 3: HomeViewModel mit wechselnden Arbeitszeitregeln
    // =========================================================================
    Given("ein HomeViewModel mit Monatsnavigation über verschiedene Arbeitszeitregeln hinweg") {
        val context = mockk<Context>(relaxed = true)
        val workDayRepo = mockk<WorkDayRepository>(relaxed = true)
        val settingsRepo = mockk<SettingsRepository>(relaxed = true)
        val getMonthWorkDays = mockk<GetMonthWorkDaysUseCase>(relaxed = true)
        val getSettings = mockk<GetSettingsUseCase>(relaxed = true)
        val calculateFlextime = CalculateFlextimeUseCase(calculateDayWorkTime)
        val calculateQuota = CalculateQuotaUseCase(calculateDayWorkTime)
        val dataChangeEventBus = mockk<DataChangeEventBus>(relaxed = true)
        val checkBreakViolation = mockk<CheckBreakViolationUseCase>(relaxed = true)
        val breakWarningScheduler = mockk<BreakWarningScheduler>(relaxed = true)
        val whatsNewPreferences = mockk<WhatsNewPreferences>(relaxed = true)
        val backupPreferences = mockk<BackupPreferences>(relaxed = true)
        val autoBookPlannedDays = mockk<AutoBookPlannedDaysUseCase>(relaxed = true)

        val ruleAugust = WorkTimeRule(
            id = 1L,
            validFrom = YearMonth.of(2026, 8),
            dailyWorkMinutes = 426,
            monthlyWorkMinutes = 9372,
            workDays = DEFAULT_WORK_DAYS
        )
        val ruleOctober = WorkTimeRule(
            id = 2L,
            validFrom = YearMonth.of(2026, 10),
            dailyWorkMinutes = 324, // Teilzeit 5h 24m
            monthlyWorkMinutes = 7047,
            workDays = DEFAULT_WORK_DAYS
        )
        val rules = listOf(ruleAugust, ruleOctober)

        every { getSettings() } returns flowOf(baseSettings)
        every { settingsRepo.getSettings() } returns flowOf(baseSettings)
        every { settingsRepo.getWorkTimeRules() } returns flowOf(rules)
        every { settingsRepo.getWorkTimeRuleForDate(any(), any()) } answers {
            val d = firstArg<LocalDate>()
            rules.getRuleForDate(d)
        }
        every { workDayRepo.getWorkDay(any()) } returns flowOf(null)
        every { workDayRepo.getWorkDaysForYear(any()) } returns flowOf(emptyList())
        every { getMonthWorkDays(any()) } returns flowOf(emptyList())
        every { settingsRepo.getQuotaRules() } returns flowOf(emptyList())
        every { settingsRepo.getQuotaRuleForMonth(any(), any()) } returns null

        val viewModel = HomeViewModel(
            context = context,
            workDayRepository = workDayRepo,
            settingsRepository = settingsRepo,
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
            checkTimeBlockOverlap = CheckTimeBlockOverlapUseCase()
        )

        When("der Nutzer den Oktober auswählt") {
            viewModel.navigateToDate(LocalDate.of(2026, 10, 15))
            testDispatcher.scheduler.advanceUntilIdle()

            Then("entspricht dailyTargetMinutes dem Tagessoll der Oktober-Regel (324 Min)") {
                viewModel.uiState.value.dailyTargetMinutes shouldBe 324
            }
        }

        When("der Nutzer zurück in den August navigiert") {
            viewModel.navigateToDate(LocalDate.of(2026, 8, 20))
            testDispatcher.scheduler.advanceUntilIdle()

            Then("wechselt dailyTargetMinutes auf das Tagessoll der August-Regel (426 Min)") {
                viewModel.uiState.value.dailyTargetMinutes shouldBe 426
            }
        }
    }

    // =========================================================================
    // Feature 4: Jahresübersicht (YearOverview) mit 4-Tage-Woche und unterjährigem Wechsel
    // =========================================================================
    Given("ein YearOverviewViewModel mit unterjährigem Wechsel auf eine 4-Tage-Woche zum 01.07.2026") {
        val workDayRepo = mockk<WorkDayRepository>()
        val getSettings = mockk<GetSettingsUseCase>()
        val settingsRepo = mockk<SettingsRepository>()

        val ruleH1 = WorkTimeRule(
            id = 1L,
            validFrom = YearMonth.of(2026, 1),
            dailyWorkMinutes = 480,
            monthlyWorkMinutes = 10560,
            workDays = DEFAULT_WORK_DAYS
        )
        val ruleH2 = WorkTimeRule(
            id = 2L,
            validFrom = YearMonth.of(2026, 7),
            dailyWorkMinutes = 533,
            monthlyWorkMinutes = 9594,
            workDays = fourDays
        )
        val rules = listOf(ruleH1, ruleH2)

        every { getSettings() } returns flowOf(baseSettings)
        every { workDayRepo.getWorkDaysForYear(2026) } returns flowOf(emptyList())
        every { settingsRepo.getWorkTimeRules() } returns flowOf(rules)
        every { settingsRepo.getWorkTimeRuleForDate(any(), any()) } answers {
            val d = firstArg<LocalDate>()
            rules.getRuleForDate(d)
        }

        val viewModel = YearOverviewViewModel(
            workDayRepository = workDayRepo,
            getSettings = getSettings,
            calculateDayWorkTime = calculateDayWorkTime,
            settingsRepository = settingsRepo
        )

        When("die Heatmap-Einträge für das Jahr 2026 generiert werden") {
            val entries = viewModel.uiState.value.heatmapEntries

            Then("ist ein Freitag im ersten Halbjahr (z.B. 12.06.2026) als regulärer Arbeitstag mit 480 Min Tagessoll vermerkt") {
                val friJune = entries[LocalDate.of(2026, 6, 12)]
                friJune.shouldNotBeNull()
                friJune.isScheduledWorkDay.shouldBeTrue()
                friJune.dailyTargetMinutes shouldBe 480
            }

            Then("ist ein Freitag im zweiten Halbjahr (z.B. 11.09.2026) als arbeitsfreier Tag mit 533 Min Tagessoll vermerkt") {
                val friSept = entries[LocalDate.of(2026, 9, 11)]
                friSept.shouldNotBeNull()
                friSept.isScheduledWorkDay.shouldBeFalse()
                friSept.dailyTargetMinutes shouldBe 533
            }

            Then("sind Samstage und Sonntage in beiden Halbjahren als arbeitsfreie Tage markiert") {
                val satJune = entries[LocalDate.of(2026, 6, 13)]
                satJune.shouldNotBeNull()
                satJune.isScheduledWorkDay.shouldBeFalse()

                val satSept = entries[LocalDate.of(2026, 9, 12)]
                satSept.shouldNotBeNull()
                satSept.isScheduledWorkDay.shouldBeFalse()
            }
        }
    }

    // =========================================================================
    // Feature 5: HomeView Tagessoll & verbleibende Zeit an arbeitsfreien Tagen (4-Tage-Woche / Wochenende)
    // =========================================================================
    Given("ein HomeViewModel im 4-Tage-Woche-Modell (Mo-Do)") {
        val context = mockk<Context>(relaxed = true)
        val workDayRepo = mockk<WorkDayRepository>(relaxed = true)
        val settingsRepo = mockk<SettingsRepository>(relaxed = true)
        val getMonthWorkDays = mockk<GetMonthWorkDaysUseCase>(relaxed = true)
        val getSettings = mockk<GetSettingsUseCase>(relaxed = true)
        val calculateFlextime = CalculateFlextimeUseCase(calculateDayWorkTime)
        val calculateQuota = CalculateQuotaUseCase(calculateDayWorkTime)
        val dataChangeEventBus = mockk<DataChangeEventBus>(relaxed = true)
        val checkBreakViolation = mockk<CheckBreakViolationUseCase>(relaxed = true)
        val breakWarningScheduler = mockk<BreakWarningScheduler>(relaxed = true)
        val whatsNewPreferences = mockk<WhatsNewPreferences>(relaxed = true)
        val backupPreferences = mockk<BackupPreferences>(relaxed = true)
        val autoBookPlannedDays = mockk<AutoBookPlannedDaysUseCase>(relaxed = true)

        val fourDayRule = WorkTimeRule(
            id = 1L,
            validFrom = YearMonth.of(2026, 9),
            dailyWorkMinutes = 533,
            monthlyWorkMinutes = 9594,
            workDays = fourDays
        )
        val rules = listOf(fourDayRule)

        every { getSettings() } returns flowOf(baseSettings)
        every { settingsRepo.getSettings() } returns flowOf(baseSettings)
        every { settingsRepo.getWorkTimeRules() } returns flowOf(rules)
        every { settingsRepo.getWorkTimeRuleForDate(any(), any()) } answers {
            rules.getRuleForDate(firstArg<LocalDate>())
        }
        every { workDayRepo.getWorkDay(any()) } returns flowOf(null)
        every { workDayRepo.getWorkDaysForYear(any()) } returns flowOf(emptyList())
        every { getMonthWorkDays(any()) } returns flowOf(emptyList())
        every { settingsRepo.getQuotaRules() } returns flowOf(emptyList())
        every { settingsRepo.getQuotaRuleForMonth(any(), any()) } returns null

        val viewModel = HomeViewModel(
            context = context,
            workDayRepository = workDayRepo,
            settingsRepository = settingsRepo,
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
            checkTimeBlockOverlap = CheckTimeBlockOverlapUseCase()
        )

        When("der Nutzer einen regulären Arbeitstag ansteuert (Donnerstag 17.09.2026)") {
            viewModel.navigateToDate(LocalDate.of(2026, 9, 17))
            testDispatcher.scheduler.advanceUntilIdle()

            Then("beträgt dailyTargetMinutes regulär 533 Minuten") {
                viewModel.uiState.value.dailyTargetMinutes shouldBe 533
            }
        }

        When("der Nutzer den freien Freitag ansteuert (Freitag 18.09.2026)") {
            viewModel.navigateToDate(LocalDate.of(2026, 9, 18))
            testDispatcher.scheduler.advanceUntilIdle()

            Then("ist dailyTargetMinutes exakt 0 Minuten") {
                viewModel.uiState.value.dailyTargetMinutes shouldBe 0
            }
        }

        When("der Nutzer das Wochenende ansteuert (Samstag 19.09.2026)") {
            viewModel.navigateToDate(LocalDate.of(2026, 9, 19))
            testDispatcher.scheduler.advanceUntilIdle()

            Then("ist dailyTargetMinutes am Wochenende ebenfalls 0 Minuten") {
                viewModel.uiState.value.dailyTargetMinutes shouldBe 0
            }
        }
    }

    // =========================================================================
    // Feature 6: MonthView & MonthGrid mit 4-Tage-Woche und neutralen Tagen
    // =========================================================================
    Given("ein MonthViewModel und MonthGrid im 4-Tage-Woche-Modell für September 2026") {
        val month = YearMonth.of(2026, 9)
        val fourDayRule = WorkTimeRule(
            id = 1L,
            validFrom = month,
            dailyWorkMinutes = 533,
            monthlyWorkMinutes = 18 * 533, // 9594 Min
            workDays = fourDays
        )
        val rules = listOf(fourDayRule)

        val workDayRepo = mockk<WorkDayRepository>(relaxed = true)
        val settingsRepo = mockk<SettingsRepository>(relaxed = true)
        val getMonthWorkDays = mockk<GetMonthWorkDaysUseCase>()
        val getSettings = mockk<GetSettingsUseCase>()
        val calculateFlextime = CalculateFlextimeUseCase(calculateDayWorkTime)
        val calculateQuota = CalculateQuotaUseCase(calculateDayWorkTime)
        val buildPrognosisDays = BuildPrognosisDaysUseCase()
        val dataChangeEventBus = mockk<DataChangeEventBus>(relaxed = true)
        val prepareExportData = mockk<PrepareExportDataUseCase>(relaxed = true)
        val exportService = mockk<ExportService>(relaxed = true)
        val checkBreakViolation = mockk<CheckBreakViolationUseCase>(relaxed = true)

        // Ein Urlaubstag an einem regulären Arbeitstag (Donnerstag, 10.09.2026)
        val thuVacation = WorkDay(
            id = 10L,
            date = LocalDate.of(2026, 9, 10),
            dayType = DayType.VACATION,
            location = WorkLocation.HOME_OFFICE
        )
        // Ein Urlaubstag an einem arbeitsfreien Freitag (11.09.2026)
        val friVacation = WorkDay(
            id = 11L,
            date = LocalDate.of(2026, 9, 11),
            dayType = DayType.VACATION,
            location = WorkLocation.HOME_OFFICE
        )

        every { getSettings() } returns flowOf(baseSettings)
        every { settingsRepo.getSettings() } returns flowOf(baseSettings)
        every { settingsRepo.getWorkTimeRules() } returns flowOf(rules)
        every { settingsRepo.getQuotaRules() } returns flowOf(emptyList())
        every { settingsRepo.getQuotaRuleForMonth(any(), any()) } returns null
        every { settingsRepo.getWorkTimeRuleForDate(any(), any()) } answers {
            rules.getRuleForDate(firstArg<LocalDate>())
        }
        every { getMonthWorkDays(any()) } returns flowOf(listOf(thuVacation, friVacation))
        every { workDayRepo.getWorkDaysForYear(any()) } returns flowOf(listOf(thuVacation, friVacation))

        val viewModel = MonthViewModel(
            getMonthWorkDays = getMonthWorkDays,
            getSettings = getSettings,
            workDayRepository = workDayRepo,
            settingsRepository = settingsRepo,
            calculateDayWorkTime = calculateDayWorkTime,
            calculateQuota = calculateQuota,
            calculateFlextime = calculateFlextime,
            dataChangeEventBus = dataChangeEventBus,
            prepareExportData = prepareExportData,
            exportService = exportService,
            checkBreakViolation = checkBreakViolation,
            buildPrognosisDays = buildPrognosisDays,
            checkTimeBlockOverlap = CheckTimeBlockOverlapUseCase()
        )

        When("die Monatsdaten berechnet werden") {
            viewModel.selectMonth(month)
            testDispatcher.scheduler.advanceUntilIdle()
            val state = viewModel.uiState.value

            Then("beträgt das Monats-Sollsoll targetMinutesMonth 18 * 533 = 9.594 Minuten") {
                state.targetMinutesMonth shouldBe 9594L
            }

            Then("reduziert nur der Urlaub am Donnerstag das Quota-Soll, während der Urlaub am freien Freitag neutral bleibt") {
                // totalWorkMinutes: Basis-Soll (9594) - neutralDaysDeduction (nur Do = 533) = 9061
                state.totalWorkMinutes shouldBe (9594L - 533L)
            }
        }

        When("das MonthGrid die Hintergrundfarbe von Zellen berechnet") {
            val emptyWorkDay: WorkDay? = null
            val friDate = LocalDate.of(2026, 9, 4) // Freitag (arbeitsfrei)
            val thuDate = LocalDate.of(2026, 9, 3) // Donnerstag (Arbeitstag)

            val friRule = rules.getRuleForDate(friDate)
            val isFriOffDay = friDate.dayOfWeek !in (friRule?.workDays ?: DEFAULT_WORK_DAYS)

            val thuRule = rules.getRuleForDate(thuDate)
            val isThuOffDay = thuDate.dayOfWeek !in (thuRule?.workDays ?: DEFAULT_WORK_DAYS)

            Then("erhält der freie Freitag eine abgedunkelte/hinterlegte Zellenfarbe") {
                isFriOffDay.shouldBeTrue()
                val friColor = getMonthDayCellColor(friDate, emptyWorkDay, isOffDay = isFriOffDay)
                friColor shouldBe Color.LightGray.copy(alpha = 0.2f)
            }

            Then("bleibt der reguläre Donnerstag transparent") {
                isThuOffDay.shouldBeFalse()
                val thuColor = getMonthDayCellColor(thuDate, emptyWorkDay, isOffDay = isThuOffDay)
                thuColor shouldBe Color.Transparent
            }
        }
    }

    // =========================================================================
    // Feature 7: FlexWidgetDataProvider mit 4-Tage-Woche (Arbeitstag vs freier Freitag)
    // =========================================================================
    Given("der FlexWidgetDataProvider mit aktiver 4-Tage-Woche (Mo-Do)") {
        val workDayRepo = mockk<WorkDayRepository>(relaxed = true)
        val settingsRepo = mockk<SettingsRepository>(relaxed = true)
        val calculateFlextime = CalculateFlextimeUseCase(calculateDayWorkTime)
        val calculateQuota = CalculateQuotaUseCase(calculateDayWorkTime)

        val fourDayRule = WorkTimeRule(
            id = 1L,
            validFrom = YearMonth.now(),
            dailyWorkMinutes = 533,
            monthlyWorkMinutes = 9594,
            workDays = fourDays
        )
        val rules = listOf(fourDayRule)

        every { settingsRepo.getSettings() } returns flowOf(baseSettings)
        every { settingsRepo.getWorkTimeRules() } returns flowOf(rules)
        every { workDayRepo.getWorkDaysForYear(any()) } returns flowOf(emptyList())

        val dataProvider = FlexWidgetDataProvider(
            workDayRepository = workDayRepo,
            settingsRepository = settingsRepo,
            calculateDayWorkTime = calculateDayWorkTime,
            calculateFlextime = calculateFlextime,
            calculateQuota = calculateQuota
        )

        When("heute ein freier Tag nach WorkTimeRule ist") {
            every { settingsRepo.getWorkTimeRuleForDate(any(), any()) } returns fourDayRule.copy(
                workDays = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY)
                    .filter { it != LocalDate.now().dayOfWeek }.toSet()
            )
            every { workDayRepo.getWorkDay(any()) } returns flowOf(null)

            val state = dataProvider.getWidgetState()

            Then("ist targetMinutesToday exakt 0L") {
                state.targetMinutesToday shouldBe 0L
            }
        }

        When("heute ein Arbeitstag nach WorkTimeRule ist") {
            every { settingsRepo.getWorkTimeRuleForDate(any(), any()) } returns fourDayRule.copy(
                workDays = setOf(LocalDate.now().dayOfWeek)
            )
            every { workDayRepo.getWorkDay(any()) } returns flowOf(null)

            val state = dataProvider.getWidgetState()

            Then("ist targetMinutesToday das Tagessoll von 533 Minuten") {
                state.targetMinutesToday shouldBe 533L
            }
        }
    }
})
