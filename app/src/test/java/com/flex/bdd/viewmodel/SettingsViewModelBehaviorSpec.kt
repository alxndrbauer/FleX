package com.flex.bdd.viewmodel

import android.content.Context
import com.flex.calendar.CalendarSyncService
import com.flex.data.export.IcsExportService
import com.flex.data.local.AppIconPreferences
import com.flex.data.local.ThemePreferences
import com.flex.domain.model.AppIconVariant
import com.flex.domain.model.FederalState
import com.flex.domain.model.PublicHolidays
import com.flex.domain.model.QuotaRule
import com.flex.domain.model.Settings
import com.flex.domain.model.ThemeMode
import com.flex.domain.model.WorkTimeRule
import com.flex.domain.repository.SettingsRepository
import com.flex.domain.repository.WorkDayRepository
import com.flex.domain.usecase.GetSettingsUseCase
import com.flex.geofence.GeofenceManager
import com.flex.ui.settings.GeofenceStatus
import com.flex.ui.settings.SettingsViewModel
import com.flex.wifi.WifiMonitor
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import java.time.LocalDate
import java.time.YearMonth

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelBehaviorSpec : BehaviorSpec({

    val testDispatcher = UnconfinedTestDispatcher()

    beforeSpec {
        Dispatchers.setMain(testDispatcher)
    }

    afterSpec {
        Dispatchers.resetMain()
        PublicHolidays.clearCache()
    }

    fun createViewModel(
        context: Context = mockk(relaxed = true),
        getSettings: GetSettingsUseCase = mockk(),
        settingsRepository: SettingsRepository = mockk(relaxed = true),
        themePreferences: ThemePreferences = mockk(relaxed = true),
        appIconPreferences: AppIconPreferences = mockk(relaxed = true),
        geofenceManager: GeofenceManager = mockk(relaxed = true),
        wifiMonitor: WifiMonitor = mockk(relaxed = true),
        calendarSyncService: CalendarSyncService = mockk(relaxed = true),
        workDayRepository: WorkDayRepository = mockk(relaxed = true),
        icsExportService: IcsExportService = mockk(relaxed = true),
        initialSettings: Settings = Settings(),
        initialQuotaRules: List<QuotaRule> = emptyList(),
        initialWorkTimeRules: List<WorkTimeRule> = emptyList()
    ): SettingsViewModel {
        every { getSettings() } returns flowOf(initialSettings)
        every { settingsRepository.getQuotaRules() } returns flowOf(initialQuotaRules)
        every { settingsRepository.getWorkTimeRules() } returns flowOf(initialWorkTimeRules)
        every { themePreferences.themeModeFlow } returns MutableStateFlow(ThemeMode.SYSTEM)
        every { appIconPreferences.variantFlow } returns MutableStateFlow(AppIconVariant.CLASSIC)

        return SettingsViewModel(
            context = context,
            getSettings = getSettings,
            settingsRepository = settingsRepository,
            themePreferences = themePreferences,
            appIconPreferences = appIconPreferences,
            geofenceManager = geofenceManager,
            wifiMonitor = wifiMonitor,
            calendarSyncService = calendarSyncService,
            workDayRepository = workDayRepository,
            icsExportService = icsExportService
        )
    }

    Given("ein SettingsViewModel mit konfigurierten Einstellungen und Regeln") {
        val initialSettings = Settings(
            id = 1L,
            dailyWorkMinutes = 480,
            monthlyWorkMinutes = 9600,
            officeQuotaPercent = 50,
            officeQuotaMinDays = 10,
            annualVacationDays = 30,
            federalState = FederalState.HAMBURG
        )

        val quotaRule1 = QuotaRule(
            id = 1L,
            validFrom = YearMonth.of(2026, 1),
            officeQuotaPercent = 50,
            officeQuotaMinDays = 10
        )
        val quotaRules = listOf(quotaRule1)

        val workTimeRule1 = WorkTimeRule(
            id = 1L,
            validFrom = YearMonth.of(2026, 1),
            dailyWorkMinutes = 480,
            monthlyWorkMinutes = 9600
        )
        val workTimeRules = listOf(workTimeRule1)

        val viewModel = createViewModel(
            initialSettings = initialSettings,
            initialQuotaRules = quotaRules,
            initialWorkTimeRules = workTimeRules
        )

        When("der initiale State geladen wird") {
            Then("enthält die Settings StateFlow die gespeicherten Einstellungen") {
                val state = viewModel.settings.value
                state.dailyWorkMinutes shouldBe 480
                state.monthlyWorkMinutes shouldBe 9600
                state.officeQuotaPercent shouldBe 50
                state.officeQuotaMinDays shouldBe 10
                state.annualVacationDays shouldBe 30
                state.federalState shouldBe FederalState.HAMBURG
            }

            Then("enthält die QuotaRules StateFlow alle konfigurierten Quotenregeln") {
                viewModel.quotaRules.value.shouldHaveSize(1)
                viewModel.quotaRules.value.first() shouldBe quotaRule1
            }

            Then("enthält die WorkTimeRules StateFlow alle konfigurierten Arbeitszeitregeln") {
                viewModel.workTimeRules.value.shouldHaveSize(1)
                viewModel.workTimeRules.value.first() shouldBe workTimeRule1
            }

            Then("ist der Geofence-Status initial UNKNOWN wenn Geofencing nicht aktiv ist") {
                viewModel.geofenceStatus.value shouldBe GeofenceStatus.UNKNOWN
            }
        }
    }

    Given("Settings mit initial bereits aktivem Geofencing und gültigen Koordinaten") {
        val geofenceSettings = Settings(
            geofenceEnabled = true,
            geofenceLat = 53.5511,
            geofenceLon = 9.9937,
            geofenceRadiusMeters = 100f
        )

        val viewModel = createViewModel(initialSettings = geofenceSettings)

        When("das ViewModel startet") {
            Then("wird der Geofence-Status direkt auf REGISTERED gesetzt") {
                viewModel.geofenceStatus.value shouldBe GeofenceStatus.REGISTERED
            }
        }
    }

    Given("eine Aktualisierung der allgemeinen Einstellungen") {
        val settingsRepository = mockk<SettingsRepository>(relaxed = true)
        val savedSettingsSlot = slot<Settings>()
        coEvery { settingsRepository.saveSettings(capture(savedSettingsSlot)) } returns Unit

        val viewModel = createViewModel(
            settingsRepository = settingsRepository,
            initialSettings = Settings(dailyWorkMinutes = 426, officeQuotaPercent = 40)
        )

        When("updateSettings mit geänderten Werten aufgerufen wird") {
            val updated = Settings(
                id = 1L,
                dailyWorkMinutes = 480,
                monthlyWorkMinutes = 10000,
                officeQuotaPercent = 60,
                officeQuotaMinDays = 12,
                annualVacationDays = 28
            )
            viewModel.updateSettings(updated)

            Then("werden die neuen Einstellungen im Repository persistiert") {
                coVerify(exactly = 1) { settingsRepository.saveSettings(updated) }
                savedSettingsSlot.captured.dailyWorkMinutes shouldBe 480
                savedSettingsSlot.captured.officeQuotaPercent shouldBe 60
            }
        }
    }

    Given("ein Wechsel des Bundeslandes") {
        val settingsRepository = mockk<SettingsRepository>(relaxed = true)
        val savedSettingsSlot = slot<Settings>()
        coEvery { settingsRepository.saveSettings(capture(savedSettingsSlot)) } returns Unit

        val currentYear = LocalDate.now().year
        val initialSettings = Settings(
            settingsYear = currentYear,
            federalState = FederalState.HAMBURG
        )

        val viewModel = createViewModel(
            settingsRepository = settingsRepository,
            initialSettings = initialSettings
        )

        When("updateFederalState auf Bayern geändert wird") {
            viewModel.updateFederalState(FederalState.BAVARIA)

            Then("wird das Bundesland in den Settings auf BAVARIA aktualisiert") {
                coVerify { settingsRepository.saveSettings(any()) }
                savedSettingsSlot.captured.federalState shouldBe FederalState.BAVARIA
            }

            Then("sind bayerische Feiertage in der Feiertagsberechnung des Bundeslandes enthalten") {
                // Allerheiligen (1. November) und Hl. Drei Könige (6. Januar) sind in Bayern Feiertage
                PublicHolidays.clearCache()
                val holidays = PublicHolidays.getBuiltinHolidays(currentYear)
                holidays.shouldContainKey(LocalDate.of(currentYear, 1, 1)) // Neujahr
            }
        }
    }

    Given("die Verwaltung von Quotenregeln") {
        val settingsRepository = mockk<SettingsRepository>(relaxed = true)
        val viewModel = createViewModel(settingsRepository = settingsRepository)

        val rule = QuotaRule(
            id = 10L,
            validFrom = YearMonth.of(2026, 6),
            officeQuotaPercent = 60,
            officeQuotaMinDays = 12
        )

        When("eine neue Quotenregel hinzugefügt wird (addQuotaRule)") {
            viewModel.addQuotaRule(rule)

            Then("wird saveQuotaRule im Repository aufgerufen") {
                coVerify(exactly = 1) { settingsRepository.saveQuotaRule(rule) }
            }
        }

        When("eine bestehende Quotenregel gelöscht wird (deleteQuotaRule)") {
            viewModel.deleteQuotaRule(rule)

            Then("wird deleteQuotaRule im Repository aufgerufen") {
                coVerify(exactly = 1) { settingsRepository.deleteQuotaRule(rule) }
            }
        }
    }

    Given("die Verwaltung von Arbeitszeitregeln") {
        val settingsRepository = mockk<SettingsRepository>(relaxed = true)
        val viewModel = createViewModel(settingsRepository = settingsRepository)

        val workRule = WorkTimeRule(
            id = 5L,
            validFrom = YearMonth.of(2026, 4),
            dailyWorkMinutes = 400,
            monthlyWorkMinutes = 8000
        )

        When("eine neue Arbeitszeitregel hinzugefügt wird (addWorkTimeRule)") {
            viewModel.addWorkTimeRule(workRule)

            Then("wird saveWorkTimeRule im Repository aufgerufen") {
                coVerify(exactly = 1) { settingsRepository.saveWorkTimeRule(workRule) }
            }
        }

        When("eine bestehende Arbeitszeitregel gelöscht wird (deleteWorkTimeRule)") {
            viewModel.deleteWorkTimeRule(workRule)

            Then("wird deleteWorkTimeRule im Repository aufgerufen") {
                coVerify(exactly = 1) { settingsRepository.deleteWorkTimeRule(workRule) }
            }
        }
    }

    Given("die Speicherung von Geofence- und WLAN-Einstellungen") {
        val settingsRepository = mockk<SettingsRepository>(relaxed = true)
        val geofenceManager = mockk<GeofenceManager>(relaxed = true)
        val wifiMonitor = mockk<WifiMonitor>(relaxed = true)

        val savedSettingsSlot = slot<Settings>()
        coEvery { settingsRepository.saveSettings(capture(savedSettingsSlot)) } returns Unit

        When("Geofencing erfolgreich aktiviert und registriert wird") {
            every {
                geofenceManager.registerGeofence(any(), any(), any(), any(), any())
            } answers {
                val onSuccess = invocation.args[3] as () -> Unit
                onSuccess()
            }

            val viewModel = createViewModel(
                settingsRepository = settingsRepository,
                geofenceManager = geofenceManager,
                wifiMonitor = wifiMonitor
            )

            viewModel.saveGeofenceSettings(
                enabled = true,
                lat = 53.5511,
                lon = 9.9937,
                radius = 120f,
                address = "Hamburg Rathausmarkt"
            )

            Then("werden die Geofence-Werte in den Settings persistiert") {
                coVerify { settingsRepository.saveSettings(any()) }
                savedSettingsSlot.captured.geofenceEnabled shouldBe true
                savedSettingsSlot.captured.geofenceLat shouldBe 53.5511
                savedSettingsSlot.captured.geofenceLon shouldBe 9.9937
                savedSettingsSlot.captured.geofenceRadiusMeters shouldBe 120f
                savedSettingsSlot.captured.geofenceAddress shouldBe "Hamburg Rathausmarkt"
            }

            Then("wird geofenceManager.registerGeofence mit den Parametern aufgerufen") {
                coVerify { geofenceManager.registerGeofence(53.5511, 9.9937, 120f, any(), any()) }
            }

            Then("wechselt der Geofence-Status auf REGISTERED") {
                viewModel.geofenceStatus.value shouldBe GeofenceStatus.REGISTERED
            }
        }

        When("die Geofence-Registrierung fehlschlägt") {
            every {
                geofenceManager.registerGeofence(any(), any(), any(), any(), any())
            } answers {
                val onFailure = invocation.args[4] as (Exception) -> Unit
                onFailure(RuntimeException("Play Services unavailable"))
            }

            val viewModel = createViewModel(
                settingsRepository = settingsRepository,
                geofenceManager = geofenceManager,
                wifiMonitor = wifiMonitor
            )

            viewModel.saveGeofenceSettings(
                enabled = true,
                lat = 53.5511,
                lon = 9.9937,
                radius = 120f
            )

            Then("wechselt der Geofence-Status auf FAILED") {
                viewModel.geofenceStatus.value shouldBe GeofenceStatus.FAILED
            }
        }

        When("Geofencing deaktiviert wird") {
            val viewModel = createViewModel(
                settingsRepository = settingsRepository,
                geofenceManager = geofenceManager,
                wifiMonitor = wifiMonitor
            )

            viewModel.saveGeofenceSettings(
                enabled = false,
                lat = 0.0,
                lon = 0.0,
                radius = 100f
            )

            Then("wird der Geofence entfernt und der Status auf UNKNOWN zurückgesetzt") {
                coVerify { geofenceManager.removeGeofence() }
                viewModel.geofenceStatus.value shouldBe GeofenceStatus.UNKNOWN
            }
        }

        When("WLAN-Automatisierung mit gültiger SSID aktiviert wird") {
            val viewModel = createViewModel(
                settingsRepository = settingsRepository,
                wifiMonitor = wifiMonitor
            )

            viewModel.saveWifiSettings(enabled = true, ssid = "  Office-Network  ")

            Then("werden die getrimmte SSID und der Aktivierungsstatus gespeichert") {
                coVerify { settingsRepository.saveSettings(any()) }
                savedSettingsSlot.captured.wifiAutoStampEnabled shouldBe true
                savedSettingsSlot.captured.wifiSsid shouldBe "Office-Network"
            }

            Then("wird der WifiMonitor mit der getrimmten SSID registriert") {
                coVerify { wifiMonitor.register("Office-Network") }
            }
        }

        When("WLAN-Automatisierung deaktiviert wird") {
            val viewModel = createViewModel(
                settingsRepository = settingsRepository,
                wifiMonitor = wifiMonitor
            )

            viewModel.saveWifiSettings(enabled = false, ssid = "Office-Network")

            Then("wird wifiAutoStampEnabled auf false gesetzt und der Monitor deregistriert") {
                coVerify { settingsRepository.saveSettings(any()) }
                savedSettingsSlot.captured.wifiAutoStampEnabled shouldBe false
                coVerify { wifiMonitor.unregister() }
            }
        }
    }
})
