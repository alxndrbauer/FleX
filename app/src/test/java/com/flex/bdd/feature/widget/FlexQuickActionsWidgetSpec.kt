package com.flex.bdd.feature.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import com.flex.data.local.PausePreferences
import com.flex.domain.events.DataChangeEventBus
import com.flex.domain.model.DayType
import com.flex.domain.model.Settings
import com.flex.domain.model.TimeBlock
import com.flex.domain.model.WorkDay
import com.flex.domain.model.WorkLocation
import com.flex.domain.repository.SettingsRepository
import com.flex.domain.usecase.AutoClockOutUseCase
import com.flex.domain.usecase.ClockInUseCase
import com.flex.domain.usecase.PauseWorkUseCase
import com.flex.domain.usecase.SwitchLocationUseCase
import com.flex.widget.ClockInAction
import com.flex.widget.ClockOutAction
import com.flex.widget.FlexQuickActionsWidget
import com.flex.widget.FlexQuickActionsWidgetReceiver
import com.flex.widget.FlexWidget
import com.flex.widget.FlexWidgetDataProvider
import com.flex.widget.FlexWidgetEntryPoint
import com.flex.widget.FlexWidgetReceiver
import com.flex.widget.FlexWidgetUpdater
import com.flex.widget.PauseAction
import com.flex.widget.QuickLocationAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flex.widget.ActionButtonColorRole
import com.flex.widget.QuickActionType
import com.flex.widget.QuickActionsHorizontalTier
import com.flex.widget.QuickActionsLayoutMode
import com.flex.widget.StatusChipColorRole
import com.flex.widget.WidgetState
import dagger.hilt.android.EntryPointAccessors
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.flow.flowOf
import java.time.LocalDate
import java.time.LocalTime

class FlexQuickActionsWidgetSpec : BehaviorSpec({

    Given("die Widget-Receiver für Dashboard und Schnellaktionen") {
        When("der FlexWidgetReceiver instanziiert wird") {
            val receiver = FlexWidgetReceiver()
            Then("liefert er eine FlexWidget Instanz") {
                receiver.glanceAppWidget.shouldBeInstanceOf<FlexWidget>()
            }
        }

        When("der FlexQuickActionsWidgetReceiver instanziiert wird") {
            val receiver = FlexQuickActionsWidgetReceiver()
            Then("liefert er eine FlexQuickActionsWidget Instanz") {
                receiver.glanceAppWidget.shouldBeInstanceOf<FlexQuickActionsWidget>()
            }
        }
    }

    Given("der FlexWidgetUpdater") {
        val mockContext = mockk<Context>(relaxed = true)

        When("updateAll aufgerufen wird") {
            Then("läuft der Aufruf ohne Exception durch") {
                FlexWidgetUpdater.updateAll(mockContext)
            }
        }
    }

    Given("die Widget Actions (QuickLocationAction, PauseAction, ClockOutAction)") {
        val mockContext = mockk<Context>(relaxed = true)
        val mockGlanceId = mockk<GlanceId>(relaxed = true)
        val entryPoint = mockk<FlexWidgetEntryPoint>(relaxed = true)
        val dataProvider = mockk<FlexWidgetDataProvider>(relaxed = true)
        val clockInUseCase = mockk<ClockInUseCase>(relaxed = true)
        val autoClockOutUseCase = mockk<AutoClockOutUseCase>(relaxed = true)
        val pauseWorkUseCase = mockk<PauseWorkUseCase>(relaxed = true)
        val switchLocationUseCase = mockk<SwitchLocationUseCase>(relaxed = true)
        val settingsRepo = mockk<SettingsRepository>(relaxed = true)
        val pausePrefs = mockk<PausePreferences>(relaxed = true)
        val eventBus = mockk<DataChangeEventBus>(relaxed = true)

        mockkStatic(EntryPointAccessors::class)
        every {
            EntryPointAccessors.fromApplication(any<Context>(), FlexWidgetEntryPoint::class.java)
        } returns entryPoint

        every { entryPoint.flexWidgetDataProvider() } returns dataProvider
        every { entryPoint.clockInUseCase() } returns clockInUseCase
        every { entryPoint.autoClockOutUseCase() } returns autoClockOutUseCase
        every { entryPoint.pauseWorkUseCase() } returns pauseWorkUseCase
        every { entryPoint.switchLocationUseCase() } returns switchLocationUseCase
        every { entryPoint.settingsRepository() } returns settingsRepo
        every { entryPoint.pausePreferences() } returns pausePrefs
        every { entryPoint.dataChangeEventBus() } returns eventBus
        every { settingsRepo.getSettings() } returns flowOf(Settings(workTimerNotificationEnabled = false))

        When("QuickLocationAction bei ausgestempeltem Zustand aufgerufen wird") {
            coEvery { dataProvider.getWidgetState() } returns WidgetState(
                isClockRunning = false,
                isPaused = false,
                currentLocation = WorkLocation.OFFICE,
                netMinutesToday = 0,
                targetMinutesToday = 480,
                flextimeBalanceMinutes = 0
            )
            coEvery { clockInUseCase.invoke(WorkLocation.HOME_OFFICE) } returns true

            val paramKey = ActionParameters.Key<String>(QuickLocationAction.KEY_LOCATION)
            val params = actionParametersOf(paramKey to WorkLocation.HOME_OFFICE.name)
            QuickLocationAction().onAction(mockContext, mockGlanceId, params)

            Then("wird clockInUseCase mit der Ziel-Location aufgerufen") {
                coVerify { clockInUseCase.invoke(WorkLocation.HOME_OFFICE) }
            }
        }

        When("QuickLocationAction bei laufender Zeiterfassung im Büro für Home-Office aufgerufen wird") {
            coEvery { dataProvider.getWidgetState() } returns WidgetState(
                isClockRunning = true,
                isPaused = false,
                currentLocation = WorkLocation.OFFICE,
                netMinutesToday = 120,
                targetMinutesToday = 480,
                flextimeBalanceMinutes = 0
            )
            coEvery { switchLocationUseCase.invoke(WorkLocation.HOME_OFFICE) } returns true

            val paramKey = ActionParameters.Key<String>(QuickLocationAction.KEY_LOCATION)
            val params = actionParametersOf(paramKey to WorkLocation.HOME_OFFICE.name)
            QuickLocationAction().onAction(mockContext, mockGlanceId, params)

            Then("wird switchLocationUseCase mit HOME_OFFICE aufgerufen") {
                coVerify { switchLocationUseCase.invoke(WorkLocation.HOME_OFFICE) }
            }
        }

        When("QuickLocationAction bei laufender Zeiterfassung für denselben Standort aufgerufen wird") {
            coEvery { dataProvider.getWidgetState() } returns WidgetState(
                isClockRunning = true,
                isPaused = false,
                currentLocation = WorkLocation.OFFICE,
                netMinutesToday = 120,
                targetMinutesToday = 480,
                flextimeBalanceMinutes = 0
            )

            val paramKey = ActionParameters.Key<String>(QuickLocationAction.KEY_LOCATION)
            val params = actionParametersOf(paramKey to WorkLocation.OFFICE.name)
            QuickLocationAction().onAction(mockContext, mockGlanceId, params)

            Then("ist es ein No-Op (kein erneuter Aufruf von switchLocationUseCase oder clockInUseCase)") {
                coVerify(exactly = 0) { switchLocationUseCase.invoke(WorkLocation.OFFICE) }
            }
        }

        When("PauseAction während der Arbeitszeit aufgerufen wird") {
            every { pausePrefs.isPaused } returns false
            coEvery { pauseWorkUseCase.invoke() } returns true

            PauseAction().onAction(mockContext, mockGlanceId, actionParametersOf())

            Then("wird pauseWorkUseCase aufgerufen") {
                coVerify { pauseWorkUseCase.invoke() }
            }
        }

        When("PauseAction während einer Pause aufgerufen wird") {
            every { pausePrefs.isPaused } returns true
            coEvery { clockInUseCase.invoke() } returns true

            PauseAction().onAction(mockContext, mockGlanceId, actionParametersOf())

            Then("wird clockInUseCase zum Fortsetzen aufgerufen") {
                coVerify { clockInUseCase.invoke() }
            }
        }

        When("PauseAction im ausgestempelten Zustand aufgerufen wird") {
            every { pausePrefs.isPaused } returns false
            coEvery { pauseWorkUseCase.invoke() } returns false

            PauseAction().onAction(mockContext, mockGlanceId, actionParametersOf())

            Then("bricht PauseAction sicher ab und startet keine Services") {
                coVerify(exactly = 0) { mockContext.startForegroundService(any()) }
            }
        }

        When("ClockOutAction im ausgestempelten Zustand aufgerufen wird") {
            coEvery { dataProvider.getWidgetState() } returns WidgetState(
                isClockRunning = false,
                isPaused = false,
                currentLocation = WorkLocation.OFFICE,
                netMinutesToday = 0,
                targetMinutesToday = 480,
                flextimeBalanceMinutes = 0
            )

            ClockOutAction().onAction(mockContext, mockGlanceId, actionParametersOf())

            Then("ist es ein No-Op und autoClockOutUseCase wird nicht aufgerufen") {
                coVerify(exactly = 0) { autoClockOutUseCase.invoke() }
            }
        }

        When("ClockOutAction während der Arbeitszeit aufgerufen wird") {
            coEvery { dataProvider.getWidgetState() } returns WidgetState(
                isClockRunning = true,
                isPaused = false,
                currentLocation = WorkLocation.OFFICE,
                netMinutesToday = 200,
                targetMinutesToday = 480,
                flextimeBalanceMinutes = 0
            )
            coEvery { autoClockOutUseCase.invoke() } returns true

            ClockOutAction().onAction(mockContext, mockGlanceId, actionParametersOf())

            Then("wird autoClockOutUseCase ausgeführt") {
                coVerify { autoClockOutUseCase.invoke() }
            }
        }
    }

    Given("Dynamische Button-Anpassung nach Breite und Höhe (Google Widget Muster)") {
        When("die Breite horizontal kleiner als 180dp ist (z.B. 2x1 Widget)") {
            Then("werden genau 2 Buttons gerendert") {
                FlexQuickActionsWidget.resolveHorizontalButtonCount(140.dp) shouldBe 2
                FlexQuickActionsWidget.resolveHorizontalButtonCount(179.dp) shouldBe 2
            }
        }

        When("die Breite horizontal zwischen 180dp und 259dp liegt (z.B. 3x1 Widget)") {
            Then("werden genau 3 Buttons gerendert") {
                FlexQuickActionsWidget.resolveHorizontalButtonCount(180.dp) shouldBe 3
                FlexQuickActionsWidget.resolveHorizontalButtonCount(250.dp) shouldBe 3
            }
        }

        When("die Breite horizontal ab 260dp liegt (z.B. 4x1 oder 5x1 Widget)") {
            Then("werden 4 Buttons gerendert") {
                FlexQuickActionsWidget.resolveHorizontalButtonCount(260.dp) shouldBe 4
                FlexQuickActionsWidget.resolveHorizontalButtonCount(380.dp) shouldBe 4
            }
        }

        When("die Höhe vertikal kleiner als 160dp ist") {
            Then("werden vertikal 2 Buttons gerendert") {
                FlexQuickActionsWidget.resolveVerticalButtonCount(140.dp) shouldBe 2
            }
        }

        When("die Höhe vertikal zwischen 160dp und 229dp liegt") {
            Then("werden vertikal 3 Buttons gerendert") {
                FlexQuickActionsWidget.resolveVerticalButtonCount(180.dp) shouldBe 3
            }
        }

        When("die Höhe vertikal ab 230dp liegt") {
            Then("werden vertikal 4 Buttons gerendert") {
                FlexQuickActionsWidget.resolveVerticalButtonCount(240.dp) shouldBe 4
            }
        }

        When("die Widget-Größe für verschiedene Layout-Modi ausgewertet wird") {
            Then("wird der 1x1 Kompakt-Modus bei kleinen Abmessungen gewählt") {
                FlexQuickActionsWidget.resolveLayoutMode(100.dp, 60.dp) shouldBe QuickActionsLayoutMode.COMPACT_1X1
                FlexQuickActionsWidget.resolveLayoutMode(115.dp, 50.dp) shouldBe QuickActionsLayoutMode.COMPACT_1X1
                FlexQuickActionsWidget.resolveLayoutMode(90.dp, 90.dp) shouldBe QuickActionsLayoutMode.COMPACT_1X1
            }

            Then("wird der vertikale Modus bei schmalen und hohen Widgets gewählt") {
                FlexQuickActionsWidget.resolveLayoutMode(100.dp, 180.dp) shouldBe QuickActionsLayoutMode.VERTICAL
                FlexQuickActionsWidget.resolveLayoutMode(120.dp, 240.dp) shouldBe QuickActionsLayoutMode.VERTICAL
            }

            Then("wird der 2x2 Grid-Modus bei mehrzeiligen Widgets gewählt") {
                FlexQuickActionsWidget.resolveLayoutMode(180.dp, 140.dp) shouldBe QuickActionsLayoutMode.GRID_2X2
                FlexQuickActionsWidget.resolveLayoutMode(250.dp, 180.dp) shouldBe QuickActionsLayoutMode.GRID_2X2
            }

            Then("wird der horizontale Leisten-Modus bei breiten 1-Zeilen-Widgets gewählt") {
                FlexQuickActionsWidget.resolveLayoutMode(140.dp, 60.dp) shouldBe QuickActionsLayoutMode.HORIZONTAL
                FlexQuickActionsWidget.resolveLayoutMode(250.dp, 60.dp) shouldBe QuickActionsLayoutMode.HORIZONTAL
                FlexQuickActionsWidget.resolveLayoutMode(380.dp, 60.dp) shouldBe QuickActionsLayoutMode.HORIZONTAL
            }
        }
    }

    Given("Kompakt-Kachel 1x1 (Google Keep Single-Action Style)") {
        val clockedOutState = WidgetState(
            isClockRunning = false,
            isPaused = false,
            currentLocation = WorkLocation.OFFICE,
            netMinutesToday = 0,
            targetMinutesToday = 480,
            flextimeBalanceMinutes = 0
        )
        val runningState = WidgetState(
            isClockRunning = true,
            isPaused = false,
            currentLocation = WorkLocation.OFFICE,
            netMinutesToday = 60,
            targetMinutesToday = 480,
            flextimeBalanceMinutes = 0
        )
        val pausedState = WidgetState(
            isClockRunning = false,
            isPaused = true,
            currentLocation = WorkLocation.OFFICE,
            netMinutesToday = 60,
            targetMinutesToday = 480,
            flextimeBalanceMinutes = 0
        )

        When("der Benutzer ausgestempelt ist") {
            val spec = FlexQuickActionsWidget.resolveCompactTileSpec(clockedOutState)
            Then("zeigt 1x1 Büro im neutralen DEFAULT-Design (kein Primary-Akzent)") {
                spec.label shouldBe "Büro"
                spec.iconRes shouldBe com.flex.R.drawable.ic_widget_office
                spec.colorRole shouldBe ActionButtonColorRole.DEFAULT
            }
        }

        When("die Zeiterfassung aktiv läuft") {
            val spec = FlexQuickActionsWidget.resolveCompactTileSpec(runningState)
            Then("zeigt 1x1 Pause mit Primary-Akzent zum Pausieren") {
                spec.label shouldBe "Pause"
                spec.iconRes shouldBe com.flex.R.drawable.ic_widget_pause
                spec.colorRole shouldBe ActionButtonColorRole.PRIMARY
            }
        }

        When("die Pause aktiv ist") {
            val spec = FlexQuickActionsWidget.resolveCompactTileSpec(pausedState)
            Then("zeigt 1x1 Weiter mit Tertiary-Akzent zum Fortsetzen") {
                spec.label shouldBe "Weiter"
                spec.iconRes shouldBe com.flex.R.drawable.ic_widget_play
                spec.colorRole shouldBe ActionButtonColorRole.TERTIARY
            }
        }
    }

    Given("Stabile Button-Reihenfolge (Büro, Home-Office, Pause/Weiter, Stopp) und Zustandskonfiguration") {
        val clockedOutState = WidgetState(
            isClockRunning = false,
            isPaused = false,
            currentLocation = WorkLocation.OFFICE,
            netMinutesToday = 0,
            targetMinutesToday = 480,
            flextimeBalanceMinutes = 0
        )

        When("im ausgestempelten Zustand 2 Buttons gerendert werden") {
            val buttons = FlexQuickActionsWidget.resolveActionButtons(2, clockedOutState)
            Then("sind beide Buttons (Büro und Home-Office) neutral und kein Lead-Button aktiv") {
                buttons.size shouldBe 2
                buttons[0].type shouldBe QuickActionType.OFFICE
                buttons[0].isLead shouldBe false
                buttons[0].label shouldBe "Büro"
                buttons[1].type shouldBe QuickActionType.HOME_OFFICE
                buttons[1].isLead shouldBe false
                FlexQuickActionsWidget.resolveButtonColorRole(buttons[0]) shouldBe ActionButtonColorRole.DEFAULT
                FlexQuickActionsWidget.resolveButtonColorRole(buttons[1]) shouldBe ActionButtonColorRole.DEFAULT
            }
        }

        When("im ausgestempelten Zustand 3 Buttons gerendert werden") {
            val buttons = FlexQuickActionsWidget.resolveActionButtons(3, clockedOutState)
            Then("sind Büro und HO neutral aktivierbar, Pause inaktiv (Disabled) und kein Lead aktiv") {
                buttons.size shouldBe 3
                buttons[0].type shouldBe QuickActionType.OFFICE
                buttons[0].isLead shouldBe false
                buttons[1].type shouldBe QuickActionType.HOME_OFFICE
                buttons[1].isLead shouldBe false
                buttons[2].type shouldBe QuickActionType.PAUSE
                buttons[2].isLead shouldBe false
                buttons[2].isEnabled shouldBe false
                buttons.count { it.isLead } shouldBe 0
                FlexQuickActionsWidget.resolveButtonColorRole(buttons[0]) shouldBe ActionButtonColorRole.DEFAULT
                FlexQuickActionsWidget.resolveButtonColorRole(buttons[1]) shouldBe ActionButtonColorRole.DEFAULT
                FlexQuickActionsWidget.resolveButtonColorRole(buttons[2]) shouldBe ActionButtonColorRole.DISABLED
            }
        }

        When("im ausgestempelten Zustand 4 Buttons gerendert werden") {
            val buttons = FlexQuickActionsWidget.resolveActionButtons(4, clockedOutState)
            Then("sind Büro und Home-Office neutral aktivierbar (kein Lead), während Pause und Stopp inaktiv sind") {
                buttons.size shouldBe 4
                buttons[0].type shouldBe QuickActionType.OFFICE
                buttons[0].isLead shouldBe false
                buttons[0].isEnabled shouldBe true
                buttons[1].type shouldBe QuickActionType.HOME_OFFICE
                buttons[1].isLead shouldBe false
                buttons[1].isEnabled shouldBe true
                buttons[2].type shouldBe QuickActionType.PAUSE
                buttons[2].isEnabled shouldBe false
                buttons[2].isLead shouldBe false
                buttons[3].type shouldBe QuickActionType.STOP
                buttons[3].isEnabled shouldBe false
                buttons[3].isLead shouldBe false
                buttons.count { it.isLead } shouldBe 0
            }
        }

        val runningOfficeState = WidgetState(
            isClockRunning = true,
            isPaused = false,
            currentLocation = WorkLocation.OFFICE,
            netMinutesToday = 60,
            targetMinutesToday = 480,
            flextimeBalanceMinutes = 0
        )

        val runningHomeState = WidgetState(
            isClockRunning = true,
            isPaused = false,
            currentLocation = WorkLocation.HOME_OFFICE,
            netMinutesToday = 60,
            targetMinutesToday = 480,
            flextimeBalanceMinutes = 0
        )

        val pausedState = WidgetState(
            isClockRunning = false,
            isPaused = true,
            currentLocation = WorkLocation.OFFICE,
            netMinutesToday = 120,
            targetMinutesToday = 480,
            flextimeBalanceMinutes = 0
        )

        When("im Büro 4 Buttons gerendert werden") {
            val buttons = FlexQuickActionsWidget.resolveActionButtons(4, runningOfficeState)
            Then("bleiben die 4 Positionen stabil und Büro ist als aktiv hervorgehoben") {
                buttons.size shouldBe 4
                buttons[0].type shouldBe QuickActionType.OFFICE
                buttons[0].isLead shouldBe true
                buttons[0].isEnabled shouldBe true
                buttons[1].type shouldBe QuickActionType.HOME_OFFICE
                buttons[1].isLead shouldBe false
                buttons[1].isEnabled shouldBe true
                buttons[2].type shouldBe QuickActionType.PAUSE
                buttons[2].label shouldBe "Pause"
                buttons[2].iconRes shouldBe com.flex.R.drawable.ic_widget_pause
                buttons[2].isLead shouldBe false
                buttons[2].isEnabled shouldBe true
                buttons[3].type shouldBe QuickActionType.STOP
                buttons[3].label shouldBe "Stopp"
                buttons[3].iconRes shouldBe com.flex.R.drawable.ic_widget_stop
                buttons[3].isLead shouldBe false
                buttons[3].isEnabled shouldBe true
                buttons.count { it.isLead } shouldBe 1
            }
        }

        When("im Home-Office 4 Buttons gerendert werden") {
            val buttons = FlexQuickActionsWidget.resolveActionButtons(4, runningHomeState)
            Then("bleiben die Positionen stabil und Home-Office ist als aktiv hervorgehoben") {
                buttons.size shouldBe 4
                buttons[0].type shouldBe QuickActionType.OFFICE
                buttons[0].isLead shouldBe false
                buttons[0].isEnabled shouldBe true
                buttons[1].type shouldBe QuickActionType.HOME_OFFICE
                buttons[1].isLead shouldBe true
                buttons[1].isEnabled shouldBe true
                buttons[2].type shouldBe QuickActionType.PAUSE
                buttons[2].label shouldBe "Pause"
                buttons[2].isLead shouldBe false
                buttons[2].isEnabled shouldBe true
                buttons[3].type shouldBe QuickActionType.STOP
                buttons[3].label shouldBe "Stopp"
                buttons[3].isLead shouldBe false
                buttons[3].isEnabled shouldBe true
                buttons.count { it.isLead } shouldBe 1
            }
        }

        When("in der Pause 4 Buttons gerendert werden") {
            val buttons = FlexQuickActionsWidget.resolveActionButtons(4, pausedState)
            Then("bleibt Pause/Weiter an Position 2 mit Weiter-Icon und ist als Lead hervorgehoben") {
                buttons.size shouldBe 4
                buttons[0].type shouldBe QuickActionType.OFFICE
                buttons[0].isLead shouldBe false
                buttons[0].isEnabled shouldBe true
                buttons[1].type shouldBe QuickActionType.HOME_OFFICE
                buttons[1].isLead shouldBe false
                buttons[1].isEnabled shouldBe true
                buttons[2].type shouldBe QuickActionType.RESUME
                buttons[2].label shouldBe "Weiter"
                buttons[2].iconRes shouldBe com.flex.R.drawable.ic_widget_play
                buttons[2].isLead shouldBe true
                buttons[2].isEnabled shouldBe true
                buttons[3].type shouldBe QuickActionType.STOP
                buttons[3].label shouldBe "Stopp"
                buttons[3].isLead shouldBe false
                buttons[3].isEnabled shouldBe true
                buttons.count { it.isLead } shouldBe 1
            }
        }

        When("die Button-Positionen über alle Zustände verglichen werden") {
            val states = listOf(clockedOutState, runningOfficeState, runningHomeState, pausedState)
            Then("sind die 4 Positionen 100% fix und unverändert") {
                states.forEach { state ->
                    val buttons = FlexQuickActionsWidget.resolveActionButtons(4, state)
                    buttons.size shouldBe 4
                    buttons[0].type shouldBe QuickActionType.OFFICE
                    buttons[1].type shouldBe QuickActionType.HOME_OFFICE
                    if (state.isPaused) {
                        buttons[2].type shouldBe QuickActionType.RESUME
                    } else {
                        buttons[2].type shouldBe QuickActionType.PAUSE
                    }
                    buttons[3].type shouldBe QuickActionType.STOP
                }
            }
        }

        When("Grenzwerte für buttonCount übergeben werden") {
            Then("wird buttonCount sicher zwischen 1 und 4 begrenzt") {
                FlexQuickActionsWidget.resolveActionButtons(-1, clockedOutState).size shouldBe 1
                FlexQuickActionsWidget.resolveActionButtons(0, clockedOutState).size shouldBe 1
                FlexQuickActionsWidget.resolveActionButtons(1, clockedOutState).size shouldBe 1
                FlexQuickActionsWidget.resolveActionButtons(2, clockedOutState).size shouldBe 2
                FlexQuickActionsWidget.resolveActionButtons(3, clockedOutState).size shouldBe 3
                FlexQuickActionsWidget.resolveActionButtons(4, clockedOutState).size shouldBe 4
                FlexQuickActionsWidget.resolveActionButtons(5, clockedOutState).size shouldBe 4
                FlexQuickActionsWidget.resolveActionButtons(100, clockedOutState).size shouldBe 4
            }
        }

        When("isWideLabel als true übergeben wird") {
            Then("heißt der Home-Office-Button voll ausgeschrieben Home-Office statt HO") {
                val shortButtons = FlexQuickActionsWidget.resolveActionButtons(4, clockedOutState, isWideLabel = false)
                val wideButtons = FlexQuickActionsWidget.resolveActionButtons(4, clockedOutState, isWideLabel = true)
                shortButtons[1].label shouldBe "HO"
                wideButtons[1].label shouldBe "Home-Office"
            }
        }

        When("die Farbrollen (ActionButtonColorRole) der Buttons über alle Zustände ausgewertet werden") {
            Then("sind im ausgestempelten Zustand Büro und Home-Office Default (neutral) und Pause/Stopp Disabled") {
                val buttons = FlexQuickActionsWidget.resolveActionButtons(4, clockedOutState)
                FlexQuickActionsWidget.resolveButtonColorRole(buttons[0]) shouldBe ActionButtonColorRole.DEFAULT
                FlexQuickActionsWidget.resolveButtonColorRole(buttons[1]) shouldBe ActionButtonColorRole.DEFAULT
                FlexQuickActionsWidget.resolveButtonColorRole(buttons[2]) shouldBe ActionButtonColorRole.DISABLED
                FlexQuickActionsWidget.resolveButtonColorRole(buttons[3]) shouldBe ActionButtonColorRole.DISABLED
            }

            Then("hat bei Büro-Arbeit Büro Primary, Pause Default und Stopp ErrorContainer") {
                val buttons = FlexQuickActionsWidget.resolveActionButtons(4, runningOfficeState)
                FlexQuickActionsWidget.resolveButtonColorRole(buttons[0]) shouldBe ActionButtonColorRole.PRIMARY
                FlexQuickActionsWidget.resolveButtonColorRole(buttons[1]) shouldBe ActionButtonColorRole.DEFAULT
                FlexQuickActionsWidget.resolveButtonColorRole(buttons[2]) shouldBe ActionButtonColorRole.DEFAULT
                FlexQuickActionsWidget.resolveButtonColorRole(buttons[3]) shouldBe ActionButtonColorRole.ERROR_CONTAINER
            }

            Then("hat bei Home-Office-Arbeit Home-Office Secondary und Stopp ErrorContainer") {
                val buttons = FlexQuickActionsWidget.resolveActionButtons(4, runningHomeState)
                FlexQuickActionsWidget.resolveButtonColorRole(buttons[0]) shouldBe ActionButtonColorRole.DEFAULT
                FlexQuickActionsWidget.resolveButtonColorRole(buttons[1]) shouldBe ActionButtonColorRole.SECONDARY
                FlexQuickActionsWidget.resolveButtonColorRole(buttons[2]) shouldBe ActionButtonColorRole.DEFAULT
                FlexQuickActionsWidget.resolveButtonColorRole(buttons[3]) shouldBe ActionButtonColorRole.ERROR_CONTAINER
            }

            Then("hat in der Pause Weiter Tertiary, Stopp ErrorContainer und Standorte sind Default unhervorgehoben") {
                val buttonsFromOffice = FlexQuickActionsWidget.resolveActionButtons(4, pausedState)
                FlexQuickActionsWidget.resolveButtonColorRole(buttonsFromOffice[0]) shouldBe ActionButtonColorRole.DEFAULT
                FlexQuickActionsWidget.resolveButtonColorRole(buttonsFromOffice[1]) shouldBe ActionButtonColorRole.DEFAULT
                FlexQuickActionsWidget.resolveButtonColorRole(buttonsFromOffice[2]) shouldBe ActionButtonColorRole.TERTIARY
                FlexQuickActionsWidget.resolveButtonColorRole(buttonsFromOffice[3]) shouldBe ActionButtonColorRole.ERROR_CONTAINER

                val pausedFromHome = pausedState.copy(currentLocation = WorkLocation.HOME_OFFICE)
                val buttonsFromHome = FlexQuickActionsWidget.resolveActionButtons(4, pausedFromHome)
                FlexQuickActionsWidget.resolveButtonColorRole(buttonsFromHome[0]) shouldBe ActionButtonColorRole.DEFAULT
                FlexQuickActionsWidget.resolveButtonColorRole(buttonsFromHome[1]) shouldBe ActionButtonColorRole.DEFAULT
                FlexQuickActionsWidget.resolveButtonColorRole(buttonsFromHome[2]) shouldBe ActionButtonColorRole.TERTIARY
                FlexQuickActionsWidget.resolveButtonColorRole(buttonsFromHome[3]) shouldBe ActionButtonColorRole.ERROR_CONTAINER
            }

            Then("gewinnt isPaused auch bei widersprüchlichem State (isClockRunning = true && isPaused = true)") {
                val inconsistentState = WidgetState(
                    isClockRunning = true,
                    isPaused = true,
                    currentLocation = WorkLocation.OFFICE,
                    netMinutesToday = 60,
                    targetMinutesToday = 480,
                    flextimeBalanceMinutes = 0
                )
                val buttons = FlexQuickActionsWidget.resolveActionButtons(4, inconsistentState)
                FlexQuickActionsWidget.resolveButtonColorRole(buttons[0]) shouldBe ActionButtonColorRole.DEFAULT
                FlexQuickActionsWidget.resolveButtonColorRole(buttons[1]) shouldBe ActionButtonColorRole.DEFAULT
                FlexQuickActionsWidget.resolveButtonColorRole(buttons[2]) shouldBe ActionButtonColorRole.TERTIARY
                FlexQuickActionsWidget.resolveButtonColorRole(buttons[3]) shouldBe ActionButtonColorRole.ERROR_CONTAINER
            }
        }
    }

    Given("Google Keep Widget-Struktur (Kompakte Pille vs. Breite Leiste)") {
        When("die Breite kleiner als 260dp ist (z.B. 2x1 oder 3x1 Widget)") {
            Then("wird der Kompakt-Pillen-Modus mit App-Branding und Plus-Icon-Button aktiv") {
                FlexQuickActionsWidget.isCompactPill(120.dp) shouldBe true
                FlexQuickActionsWidget.isCompactPill(140.dp) shouldBe true
                FlexQuickActionsWidget.isCompactPill(180.dp) shouldBe true
                FlexQuickActionsWidget.isCompactPill(250.dp) shouldBe true
                FlexQuickActionsWidget.isCompactPill(259.dp) shouldBe true
            }
        }

        When("die Breite ab 260dp liegt (z.B. 4x1 oder 5x1 Widget)") {
            Then("wird der Leisten-Modus (nicht mehr Kompakt-Pille) aktiv") {
                FlexQuickActionsWidget.isCompactPill(260.dp) shouldBe false
                FlexQuickActionsWidget.isCompactPill(300.dp) shouldBe false
                FlexQuickActionsWidget.isCompactPill(360.dp) shouldBe false
                FlexQuickActionsWidget.isCompactPill(420.dp) shouldBe false
            }
        }

        When("der Inhalt des Kompakt-Pillen-Widgets für verschiedene Zustände ermittelt wird") {
            Then("wird im ausgestempelten Zustand FleX mit App-Icon angezeigt") {
                val state = WidgetState(
                    isClockRunning = false,
                    isPaused = false,
                    currentLocation = WorkLocation.OFFICE,
                    netMinutesToday = 0,
                    targetMinutesToday = 480,
                    flextimeBalanceMinutes = 0
                )
                val (icon, text) = FlexQuickActionsWidget.resolvePillContent(state)
                icon shouldBe com.flex.R.drawable.ic_notification
                text shouldBe "FleX"
            }

            Then("wird bei Büro-Arbeit das Büro-Icon und Büro angezeigt") {
                val state = WidgetState(
                    isClockRunning = true,
                    isPaused = false,
                    currentLocation = WorkLocation.OFFICE,
                    netMinutesToday = 120,
                    targetMinutesToday = 480,
                    flextimeBalanceMinutes = 0
                )
                val (icon, text) = FlexQuickActionsWidget.resolvePillContent(state)
                icon shouldBe com.flex.R.drawable.ic_widget_office
                text shouldBe "Büro"
            }

            Then("wird bei Home-Office das Home-Office-Icon und Home-Office angezeigt") {
                val state = WidgetState(
                    isClockRunning = true,
                    isPaused = false,
                    currentLocation = WorkLocation.HOME_OFFICE,
                    netMinutesToday = 120,
                    targetMinutesToday = 480,
                    flextimeBalanceMinutes = 0
                )
                val (icon, text) = FlexQuickActionsWidget.resolvePillContent(state)
                icon shouldBe com.flex.R.drawable.ic_widget_home
                text shouldBe "Home-Office"
            }

            Then("wird bei Pause das Pause-Icon und Pause angezeigt") {
                val state = WidgetState(
                    isClockRunning = false,
                    isPaused = true,
                    currentLocation = WorkLocation.OFFICE,
                    netMinutesToday = 120,
                    targetMinutesToday = 480,
                    flextimeBalanceMinutes = 0
                )
                val (icon, text) = FlexQuickActionsWidget.resolvePillContent(state)
                icon shouldBe com.flex.R.drawable.ic_widget_pause
                text shouldBe "Pause"
            }
        }

        When("die Beschriftung des Flextime-Badges im Dashboard-Widget ermittelt wird") {
            Then("wird bei schmaler Breite Saldo verwendet") {
                FlexWidget.resolveBadgeLabel(isNarrow = true) shouldBe "Saldo"
            }

            Then("wird bei voller Breite Gleitzeit verwendet (keine redundante FleX-Wiederholung)") {
                FlexWidget.resolveBadgeLabel(isNarrow = false) shouldBe "Gleitzeit"
            }
        }

        When("das Header-Datum für volle Breite formatiert wird") {
            Then("wird Wochentag und Tag. Monat formatiert") {
                val formatted = FlexWidget.formatHeaderDate(LocalDate.of(2026, 10, 17))
                formatted.contains("17. Okt") shouldBe true
            }
        }
    }

    Given("Horizontale Breiten-Tiers (2. größte Breite vs. größte Reihe / 5-Zellen)") {
        val testClockedOutState = WidgetState(
            isClockRunning = false,
            isPaused = false,
            currentLocation = WorkLocation.OFFICE,
            netMinutesToday = 0,
            targetMinutesToday = 480,
            flextimeBalanceMinutes = 0
        )
        val testRunningState = WidgetState(
            isClockRunning = true,
            isPaused = false,
            currentLocation = WorkLocation.OFFICE,
            netMinutesToday = 260,
            targetMinutesToday = 480,
            flextimeBalanceMinutes = 30
        )
        val testPausedState = WidgetState(
            isClockRunning = false,
            isPaused = true,
            currentLocation = WorkLocation.HOME_OFFICE,
            netMinutesToday = 120,
            targetMinutesToday = 480,
            flextimeBalanceMinutes = 0
        )

        When("die Breite im 2. größten Tier liegt (z.B. 4-Zellen-Breite von 260dp bis 339dp)") {
            Then("wird der FOUR_ACTIONS Tier ausgewählt") {
                FlexQuickActionsWidget.resolveHorizontalTier(260.dp) shouldBe QuickActionsHorizontalTier.FOUR_ACTIONS
                FlexQuickActionsWidget.resolveHorizontalTier(300.dp) shouldBe QuickActionsHorizontalTier.FOUR_ACTIONS
                FlexQuickActionsWidget.resolveHorizontalTier(339.dp) shouldBe QuickActionsHorizontalTier.FOUR_ACTIONS
            }

            Then("ist der Plus-Button komplett entfernt") {
                FlexQuickActionsWidget.hasPlusButton(260.dp) shouldBe false
                FlexQuickActionsWidget.hasPlusButton(300.dp) shouldBe false
                FlexQuickActionsWidget.hasPlusButton(339.dp) shouldBe false
            }

            Then("wird kein Status-Chip gerendert") {
                FlexQuickActionsWidget.hasStatusChip(260.dp) shouldBe false
                FlexQuickActionsWidget.hasStatusChip(300.dp) shouldBe false
                FlexQuickActionsWidget.hasStatusChip(339.dp) shouldBe false
            }

            Then("bleiben genau die 4 Aktions-Buttons in stabiler Reihenfolge erhalten und reflektieren den Status") {
                // 1. Ausgestempelt -> kein Button ist Lead (alles neutral / Feierabend)
                val clockedOut = FlexQuickActionsWidget.resolveActionButtons(4, testClockedOutState)
                clockedOut.size shouldBe 4
                clockedOut[0].type shouldBe QuickActionType.OFFICE
                clockedOut[0].isLead shouldBe false
                clockedOut[1].type shouldBe QuickActionType.HOME_OFFICE
                clockedOut[1].isLead shouldBe false
                clockedOut[2].type shouldBe QuickActionType.PAUSE
                clockedOut[2].isLead shouldBe false
                clockedOut[3].type shouldBe QuickActionType.STOP
                clockedOut[3].isLead shouldBe false
                clockedOut.count { it.isLead } shouldBe 0

                // 2. Laufende Arbeitszeit Büro -> Büro bleibt an Index 0 und ist Lead (Primary)
                val running = FlexQuickActionsWidget.resolveActionButtons(4, testRunningState)
                running.size shouldBe 4
                running[0].type shouldBe QuickActionType.OFFICE
                running[0].isLead shouldBe true
                running[1].type shouldBe QuickActionType.HOME_OFFICE
                running[1].isLead shouldBe false
                running[2].type shouldBe QuickActionType.PAUSE
                running[2].isLead shouldBe false
                running[3].type shouldBe QuickActionType.STOP
                running[3].isLead shouldBe false
                running.count { it.isLead } shouldBe 1

                // 3. Pause -> Weiter bleibt an Index 2 und ist Lead (Tertiary)
                val paused = FlexQuickActionsWidget.resolveActionButtons(4, testPausedState)
                paused.size shouldBe 4
                paused[0].type shouldBe QuickActionType.OFFICE
                paused[0].isLead shouldBe false
                paused[1].type shouldBe QuickActionType.HOME_OFFICE
                paused[1].isLead shouldBe false
                paused[2].type shouldBe QuickActionType.RESUME
                paused[2].isLead shouldBe true
                paused[3].type shouldBe QuickActionType.STOP
                paused[3].isLead shouldBe false
                paused.count { it.isLead } shouldBe 1
            }
        }

        When("die Breite im größten Tier liegt (z.B. 5-Zellen-Breite ab 340dp)") {
            Then("wird der FULL_BAR_WITH_TIME Tier ausgewählt") {
                FlexQuickActionsWidget.resolveHorizontalTier(340.dp) shouldBe QuickActionsHorizontalTier.FULL_BAR_WITH_TIME
                FlexQuickActionsWidget.resolveHorizontalTier(360.dp) shouldBe QuickActionsHorizontalTier.FULL_BAR_WITH_TIME
                FlexQuickActionsWidget.resolveHorizontalTier(420.dp) shouldBe QuickActionsHorizontalTier.FULL_BAR_WITH_TIME
            }

            Then("ist der Plus-Button weiterhin entfernt und stattdessen der Status-Chip aktiv") {
                FlexQuickActionsWidget.hasPlusButton(340.dp) shouldBe false
                FlexQuickActionsWidget.hasPlusButton(380.dp) shouldBe false
                FlexQuickActionsWidget.hasStatusChip(340.dp) shouldBe true
                FlexQuickActionsWidget.hasStatusChip(380.dp) shouldBe true
            }

            Then("werden die geleisteten Arbeitsstunden für den Status-Chip korrekt formatiert") {
                FlexQuickActionsWidget.formatWorkedHours(0) shouldBe "00:00 Std."
                FlexQuickActionsWidget.formatWorkedHours(59) shouldBe "00:59 Std."
                FlexQuickActionsWidget.formatWorkedHours(60) shouldBe "01:00 Std."
                FlexQuickActionsWidget.formatWorkedHours(65) shouldBe "01:05 Std."
                FlexQuickActionsWidget.formatWorkedHours(260) shouldBe "04:20 Std."
                FlexQuickActionsWidget.formatWorkedHours(480) shouldBe "08:00 Std."
                FlexQuickActionsWidget.formatWorkedHours(600) shouldBe "10:00 Std."
                FlexQuickActionsWidget.formatWorkedHours(1440) shouldBe "24:00 Std."
                FlexQuickActionsWidget.formatWorkedHours(-15) shouldBe "00:00 Std."
            }

            Then("wird die digitale Zeit für den Status-Chip 2-zeilig mit Stunden und Minuten formatiert") {
                FlexQuickActionsWidget.formatDigitalTime(0) shouldBe ("00" to "00")
                FlexQuickActionsWidget.formatDigitalTime(59) shouldBe ("00" to "59")
                FlexQuickActionsWidget.formatDigitalTime(60) shouldBe ("01" to "00")
                FlexQuickActionsWidget.formatDigitalTime(65) shouldBe ("01" to "05")
                FlexQuickActionsWidget.formatDigitalTime(260) shouldBe ("04" to "20")
                FlexQuickActionsWidget.formatDigitalTime(480) shouldBe ("08" to "00")
                FlexQuickActionsWidget.formatDigitalTime(600) shouldBe ("10" to "00")
                FlexQuickActionsWidget.formatDigitalTime(1440) shouldBe ("24" to "00")
                FlexQuickActionsWidget.formatDigitalTime(6000L) shouldBe ("100" to "00")
                FlexQuickActionsWidget.formatDigitalTime(-15) shouldBe ("00" to "00")
                // Int overload
                FlexQuickActionsWidget.formatDigitalTime(480.toInt()) shouldBe ("08" to "00")
                FlexQuickActionsWidget.formatDigitalTime((-10).toInt()) shouldBe ("00" to "00")
            }

            Then("wird der Tagesfortschritt für die Chip-Füllung korrekt berechnet") {
                // Normaler Fortschritt
                FlexQuickActionsWidget.resolveDayProgress(0, 480) shouldBe 0f
                FlexQuickActionsWidget.resolveDayProgress(120, 480) shouldBe 0.25f
                FlexQuickActionsWidget.resolveDayProgress(240, 480) shouldBe 0.5f
                FlexQuickActionsWidget.resolveDayProgress(360, 480) shouldBe 0.75f
                FlexQuickActionsWidget.resolveDayProgress(480, 480) shouldBe 1.0f

                // Edge Cases: Über 100% gekappt auf 1.0f
                FlexQuickActionsWidget.resolveDayProgress(540, 480) shouldBe 1.0f
                FlexQuickActionsWidget.resolveDayProgress(960, 480) shouldBe 1.0f

                // Edge Cases: Kein Tagessoll / freier Tag (targetMinutesToday <= 0)
                FlexQuickActionsWidget.resolveDayProgress(120, 0) shouldBe 0f
                FlexQuickActionsWidget.resolveDayProgress(120, -10) shouldBe 0f

                // Edge Cases: Negative gearbeitete Minuten
                FlexQuickActionsWidget.resolveDayProgress(-30, 480) shouldBe 0f

                // Edge Cases: Überstunden / über 100% (geclamped auf 1.0f)
                FlexQuickActionsWidget.resolveDayProgress(600, 480) shouldBe 1.0f
                FlexQuickActionsWidget.resolveDayProgress(Long.MAX_VALUE, 480) shouldBe 1.0f

                // Int overloads
                FlexQuickActionsWidget.resolveDayProgress(240.toInt(), 480.toInt()) shouldBe 0.5f
                FlexQuickActionsWidget.resolveDayProgress(120.toInt(), 0.toInt()) shouldBe 0f
            }

            Then("wird die Füllbreite des Status-Chips proportional zur Chipbreite berechnet") {
                FlexQuickActionsWidget.resolveProgressFillWidth(0f, 92.dp) shouldBe 0.dp
                FlexQuickActionsWidget.resolveProgressFillWidth(0.5f, 92.dp) shouldBe 46.dp
                FlexQuickActionsWidget.resolveProgressFillWidth(1.0f, 92.dp) shouldBe 92.dp
                FlexQuickActionsWidget.resolveProgressFillWidth(1.5f, 92.dp) shouldBe 92.dp
                FlexQuickActionsWidget.resolveProgressFillWidth(-0.2f, 92.dp) shouldBe 0.dp
                FlexQuickActionsWidget.resolveProgressFillWidth(0.5f, (-20).dp) shouldBe 0.dp
                FlexQuickActionsWidget.resolveProgressFillWidth(Float.NaN, 92.dp) shouldBe 0.dp
                FlexQuickActionsWidget.resolveProgressFillWidth(1.0f) shouldBe FlexQuickActionsWidget.STATUS_CHIP_WIDTH
            }

            Then("wird die Farbrolle für den Status-Chip anhand des Arbeitsstatus bestimmt") {
                FlexQuickActionsWidget.resolveStatusChipColorRole(testRunningState) shouldBe StatusChipColorRole.RUNNING
                FlexQuickActionsWidget.resolveStatusChipColorRole(testPausedState) shouldBe StatusChipColorRole.PAUSED
                FlexQuickActionsWidget.resolveStatusChipColorRole(testClockedOutState) shouldBe StatusChipColorRole.CLOCKED_OUT

                FlexQuickActionsWidget.shouldInvertChipText(0.49f) shouldBe false
                FlexQuickActionsWidget.shouldInvertChipText(0.5f) shouldBe true
                FlexQuickActionsWidget.shouldInvertChipText(0.8f) shouldBe true
            }

            Then("bleiben die 4 Aktions-Buttons daneben vollständig in stabiler Reihenfolge erhalten") {
                val buttons = FlexQuickActionsWidget.resolveActionButtons(4, testRunningState)
                buttons.size shouldBe 4
                buttons[0].type shouldBe QuickActionType.OFFICE
                buttons[0].isLead shouldBe true
                buttons[1].type shouldBe QuickActionType.HOME_OFFICE
                buttons[2].type shouldBe QuickActionType.PAUSE
                buttons[3].type shouldBe QuickActionType.STOP
                buttons.drop(1).all { !it.isLead } shouldBe true
            }
        }

        When("die Breite schmaler als 260dp ist") {
            Then("wird der COMPACT_PILL Tier gewählt und der Plus-Button ist vorhanden") {
                FlexQuickActionsWidget.resolveHorizontalTier(120.dp) shouldBe QuickActionsHorizontalTier.COMPACT_PILL
                FlexQuickActionsWidget.resolveHorizontalTier(259.dp) shouldBe QuickActionsHorizontalTier.COMPACT_PILL
                FlexQuickActionsWidget.hasPlusButton(140.dp) shouldBe true
                FlexQuickActionsWidget.hasPlusButton(250.dp) shouldBe true
                FlexQuickActionsWidget.hasStatusChip(140.dp) shouldBe false
                FlexQuickActionsWidget.hasStatusChip(250.dp) shouldBe false
            }
        }
    }

    Given("Prominente Icon-Größen und subtiler Hintergrund-Track für Fortschritts-Chip") {
        When("die Icon-Größen für die Aktions-Buttons abgefragt werden") {
            Then("sind die Wide-Action-Icons prominent auf 26dp vergrößert") {
                FlexQuickActionsWidget.WIDE_ACTION_ICON_SIZE shouldBe 26.dp
            }

            Then("sind die Action-Button-Icons mit Labels auf 24dp vergrößert") {
                FlexQuickActionsWidget.ACTION_BUTTON_ICON_SIZE shouldBe 24.dp
            }
        }

        When("der Status-Chip bei Arbeitsbeginn oder ohne Soll geprüft wird") {
            Then("ist der Fortschritt bei 0 Minuten exakt 0f und die Füllbreite 0dp") {
                FlexQuickActionsWidget.resolveDayProgress(0L, 480L) shouldBe 0f
                FlexQuickActionsWidget.resolveProgressFillWidth(0f) shouldBe 0.dp
            }

            Then("bleibt der Fortschritt an Wochenenden oder Tagen ohne Soll (targetMinutesToday <= 0) immer bei 0f") {
                FlexQuickActionsWidget.resolveDayProgress(60L, 0L) shouldBe 0f
                FlexQuickActionsWidget.resolveDayProgress(120L, -1L) shouldBe 0f
                FlexQuickActionsWidget.resolveProgressFillWidth(FlexQuickActionsWidget.resolveDayProgress(60L, 0L)) shouldBe 0.dp
            }

            Then("wächst der Fortschritt bei fortschreitender Dauer kontinuierlich bis zum Ziel") {
                val p1 = FlexQuickActionsWidget.resolveDayProgress(60L, 480L)
                val p2 = FlexQuickActionsWidget.resolveDayProgress(120L, 480L)
                val p3 = FlexQuickActionsWidget.resolveDayProgress(240L, 480L)
                val p4 = FlexQuickActionsWidget.resolveDayProgress(480L, 480L)

                p1 shouldBe 0.125f
                p2 shouldBe 0.25f
                p3 shouldBe 0.5f
                p4 shouldBe 1.0f

                FlexQuickActionsWidget.resolveProgressFillWidth(p1) shouldBe 11.5.dp
                FlexQuickActionsWidget.resolveProgressFillWidth(p2) shouldBe 23.dp
                FlexQuickActionsWidget.resolveProgressFillWidth(p3) shouldBe 46.dp
                FlexQuickActionsWidget.resolveProgressFillWidth(p4) shouldBe 92.dp
            }
        }
    }

    Given("Skalierbare und vergrößerte Schriftgrößen für den Status-Chip der 5er-Reihe") {
        When("die Schrifthöhe für kompakte Höhen (<56dp) abgefragt wird") {
            Then("ist die Zeitanzeige mit 15sp deutlich größer als zuvor und Std mit 10sp lesbar") {
                FlexQuickActionsWidget.resolveTimeFontSize(48.dp) shouldBe 15.sp
                FlexQuickActionsWidget.resolveTimeFontSize(55.dp) shouldBe 15.sp
                FlexQuickActionsWidget.resolveStdFontSize(48.dp) shouldBe 10.sp
                FlexQuickActionsWidget.resolveStdFontSize(55.dp) shouldBe 10.sp
            }
        }

        When("die Schrifthöhe für Standard-Höhen (56dp bis 69dp) abgefragt wird") {
            Then("ist die Zeitanzeige mit 17sp prominent groß und Std mit 11sp gut lesbar") {
                FlexQuickActionsWidget.resolveTimeFontSize(56.dp) shouldBe 17.sp
                FlexQuickActionsWidget.resolveTimeFontSize(64.dp) shouldBe 17.sp
                FlexQuickActionsWidget.resolveStdFontSize(56.dp) shouldBe 11.sp
                FlexQuickActionsWidget.resolveStdFontSize(64.dp) shouldBe 11.sp
            }
        }

        When("die Schrifthöhe für größere Höhen (>=70dp) abgefragt wird") {
            Then("skaliert die Zeitanzeige auf 19sp und Std auf 12sp") {
                FlexQuickActionsWidget.resolveTimeFontSize(70.dp) shouldBe 19.sp
                FlexQuickActionsWidget.resolveTimeFontSize(80.dp) shouldBe 19.sp
                FlexQuickActionsWidget.resolveStdFontSize(70.dp) shouldBe 12.sp
                FlexQuickActionsWidget.resolveStdFontSize(80.dp) shouldBe 12.sp
            }
        }
    }
})

