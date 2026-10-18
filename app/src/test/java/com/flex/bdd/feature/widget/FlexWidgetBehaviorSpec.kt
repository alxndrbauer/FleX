package com.flex.bdd.feature.widget

import com.flex.domain.model.DayType
import com.flex.domain.model.FlextimeBalance
import com.flex.domain.model.ProjectedEndTime
import com.flex.domain.model.Settings
import com.flex.domain.model.TimeBlock
import com.flex.domain.model.WorkDay
import com.flex.domain.model.WorkLocation
import com.flex.domain.repository.SettingsRepository
import com.flex.domain.repository.WorkDayRepository
import com.flex.domain.usecase.CalculateDayWorkTimeUseCase
import com.flex.domain.usecase.CalculateFlextimeUseCase
import com.flex.domain.usecase.CalculateProjectedEndTimeUseCase
import com.flex.domain.usecase.DayWorkTimeResult
import com.flex.widget.FlexWidgetDataProvider
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import java.time.LocalDate
import java.time.LocalTime

class FlexWidgetBehaviorSpec : BehaviorSpec({

    val defaultSettings = Settings(
        dailyWorkMinutes = 480, // 8h
        monthlyWorkMinutes = 9600,
        initialFlextimeMinutes = 60,
        initialOvertimeMinutes = 0
    )

    Given("ein Benutzer ist aktuell ausgestempelt") {
        val workDayRepository = mockk<WorkDayRepository>(relaxed = true)
        val settingsRepository = mockk<SettingsRepository>(relaxed = true)
        val calculateDayWorkTime = mockk<CalculateDayWorkTimeUseCase>()
        val calculateFlextime = mockk<CalculateFlextimeUseCase>()

        every { settingsRepository.getSettings() } returns flowOf(defaultSettings)
        every { settingsRepository.getWorkTimeRules() } returns flowOf(emptyList())
        every { settingsRepository.getWorkTimeRuleForDate(any(), any()) } returns null
        every { workDayRepository.getWorkDaysForYear(any()) } returns flowOf(emptyList())

        val dataProvider = FlexWidgetDataProvider(
            workDayRepository = workDayRepository,
            settingsRepository = settingsRepository,
            calculateDayWorkTime = calculateDayWorkTime,
            calculateFlextime = calculateFlextime
        )

        val today = LocalDate.now()
        val workDay = WorkDay(
            id = 1L,
            date = today,
            location = WorkLocation.OFFICE,
            dayType = DayType.WORK,
            timeBlocks = listOf(
                TimeBlock(
                    id = 10L,
                    workDayId = 1L,
                    startTime = LocalTime.of(8, 0),
                    endTime = LocalTime.of(12, 0),
                    location = WorkLocation.OFFICE
                )
            )
        )

        every { workDayRepository.getWorkDay(today) } returns flowOf(workDay)
        every { calculateDayWorkTime(any(), any()) } returns DayWorkTimeResult(
            grossMinutes = 240,
            netMinutes = 240,
            breakMinutes = 0,
            exceedsMaxHours = false
        )
        every { calculateFlextime(any(), any(), any(), any()) } returns FlextimeBalance(
            initialMinutes = 60,
            earnedMinutes = 15,
            totalMinutes = 75,
            targetMinutes = 480
        )

        When("der FlexWidgetDataProvider den Widget-State berechnet") {
            val state = dataProvider.getWidgetState()

            Then("ist isClockRunning false") {
                state.isClockRunning shouldBe false
            }
            Then("entspricht netMinutesToday der abgeschlossenen Arbeitszeit (4h = 240m)") {
                state.netMinutesToday shouldBe 240L
            }
            Then("ist targetMinutesToday das Tagessoll (480m)") {
                state.targetMinutesToday shouldBe 480L
            }
            Then("wird der aktuelle Gleitzeitsaldo geliefert (75m)") {
                state.flextimeBalanceMinutes shouldBe 75L
            }
            Then("entspricht der Standort dem des Arbeitstags") {
                state.currentLocation shouldBe WorkLocation.OFFICE
            }
        }
    }

    Given("ein Benutzer ist aktuell im Home-Office eingestempelt mit laufendem Block") {
        val workDayRepository = mockk<WorkDayRepository>(relaxed = true)
        val settingsRepository = mockk<SettingsRepository>(relaxed = true)
        val calculateDayWorkTime = mockk<CalculateDayWorkTimeUseCase>()
        val calculateFlextime = mockk<CalculateFlextimeUseCase>()

        every { settingsRepository.getSettings() } returns flowOf(defaultSettings)
        every { settingsRepository.getWorkTimeRules() } returns flowOf(emptyList())
        every { settingsRepository.getWorkTimeRuleForDate(any(), any()) } returns null
        every { workDayRepository.getWorkDaysForYear(any()) } returns flowOf(emptyList())

        val dataProvider = FlexWidgetDataProvider(
            workDayRepository = workDayRepository,
            settingsRepository = settingsRepository,
            calculateDayWorkTime = calculateDayWorkTime,
            calculateFlextime = calculateFlextime
        )

        val today = LocalDate.now()
        val runningBlock = TimeBlock(
            id = 20L,
            workDayId = 2L,
            startTime = LocalTime.now().minusHours(2),
            endTime = null,
            location = WorkLocation.HOME_OFFICE
        )
        val workDay = WorkDay(
            id = 2L,
            date = today,
            location = WorkLocation.HOME_OFFICE,
            dayType = DayType.WORK,
            timeBlocks = listOf(runningBlock)
        )

        every { workDayRepository.getWorkDay(today) } returns flowOf(workDay)
        every { calculateDayWorkTime(any(), any()) } returns DayWorkTimeResult(
            grossMinutes = 120,
            netMinutes = 120,
            breakMinutes = 0,
            exceedsMaxHours = false
        )
        every { calculateFlextime(any(), any(), any(), any()) } returns FlextimeBalance(
            initialMinutes = 0,
            earnedMinutes = -360,
            totalMinutes = -30,
            targetMinutes = 480
        )

        When("der FlexWidgetDataProvider den Widget-State berechnet") {
            val state = dataProvider.getWidgetState()

            Then("ist isClockRunning true") {
                state.isClockRunning shouldBe true
            }
            Then("ist der aktuelle Standort HOME_OFFICE") {
                state.currentLocation shouldBe WorkLocation.HOME_OFFICE
            }
            Then("beinhaltet die Arbeitszeit den laufenden Block (120m)") {
                state.netMinutesToday shouldBe 120L
            }
            Then("wird ein negativer Gleitzeitsaldo korrekt übergeben (-30m)") {
                state.flextimeBalanceMinutes shouldBe -30L
            }
        }
    }

    Given("noch kein Arbeitstag für heute existiert") {
        val workDayRepository = mockk<WorkDayRepository>(relaxed = true)
        val settingsRepository = mockk<SettingsRepository>(relaxed = true)
        val calculateDayWorkTime = mockk<CalculateDayWorkTimeUseCase>()
        val calculateFlextime = mockk<CalculateFlextimeUseCase>()

        every { settingsRepository.getSettings() } returns flowOf(defaultSettings)
        every { settingsRepository.getWorkTimeRules() } returns flowOf(emptyList())
        every { settingsRepository.getWorkTimeRuleForDate(any(), any()) } returns null
        every { workDayRepository.getWorkDaysForYear(any()) } returns flowOf(emptyList())

        val dataProvider = FlexWidgetDataProvider(
            workDayRepository = workDayRepository,
            settingsRepository = settingsRepository,
            calculateDayWorkTime = calculateDayWorkTime,
            calculateFlextime = calculateFlextime
        )

        val today = LocalDate.now()
        every { workDayRepository.getWorkDay(today) } returns flowOf(null)
        every { calculateFlextime(any(), any(), any(), any()) } returns FlextimeBalance(
            initialMinutes = 0,
            earnedMinutes = 0,
            totalMinutes = 0,
            targetMinutes = 480
        )

        When("der FlexWidgetDataProvider den Widget-State abruft") {
            val state = dataProvider.getWidgetState()

            Then("ist isClockRunning false") {
                state.isClockRunning shouldBe false
            }
            Then("ist die Arbeitszeit heute 0") {
                state.netMinutesToday shouldBe 0L
            }
            Then("ist das Tagessoll 480 Minuten") {
                state.targetMinutesToday shouldBe 480L
            }
            Then("ist der Default-Standort OFFICE") {
                state.currentLocation shouldBe WorkLocation.OFFICE
            }
        }
    }

    Given("ein Benutzer befindet sich in Pause") {
        val workDayRepository = mockk<WorkDayRepository>(relaxed = true)
        val settingsRepository = mockk<SettingsRepository>(relaxed = true)
        val calculateDayWorkTime = mockk<CalculateDayWorkTimeUseCase>()
        val calculateFlextime = mockk<CalculateFlextimeUseCase>()
        val pausePreferences = mockk<com.flex.data.local.PausePreferences>()

        every { settingsRepository.getSettings() } returns flowOf(defaultSettings)
        every { settingsRepository.getWorkTimeRules() } returns flowOf(emptyList())
        every { settingsRepository.getWorkTimeRuleForDate(any(), any()) } returns null
        every { workDayRepository.getWorkDaysForYear(any()) } returns flowOf(emptyList())
        every { pausePreferences.isPaused } returns true

        val dataProvider = FlexWidgetDataProvider(
            workDayRepository = workDayRepository,
            settingsRepository = settingsRepository,
            calculateDayWorkTime = calculateDayWorkTime,
            calculateFlextime = calculateFlextime,
            pausePreferences = pausePreferences
        )

        val today = LocalDate.now()
        val workDay = WorkDay(
            id = 1L,
            date = today,
            location = WorkLocation.OFFICE,
            dayType = DayType.WORK,
            timeBlocks = listOf(
                TimeBlock(
                    id = 10L,
                    workDayId = 1L,
                    startTime = LocalTime.of(8, 0),
                    endTime = LocalTime.of(12, 0),
                    location = WorkLocation.OFFICE
                )
            )
        )
        every { workDayRepository.getWorkDay(today) } returns flowOf(workDay)
        every { calculateDayWorkTime(any(), any()) } returns DayWorkTimeResult(
            grossMinutes = 240,
            netMinutes = 240,
            breakMinutes = 0,
            exceedsMaxHours = false
        )
        every { calculateFlextime(any(), any(), any(), any()) } returns FlextimeBalance(
            totalMinutes = 0,
            targetMinutes = 480
        )

        When("der FlexWidgetDataProvider den Widget-State abruft") {
            val state = dataProvider.getWidgetState()

            Then("ist isClockRunning false") {
                state.isClockRunning shouldBe false
            }
            Then("ist isPaused true") {
                state.isPaused shouldBe true
            }
        }
    }

    Given("ein Benutzer hat eine laufende Zeiterfassung mit Feierabend-Prognose") {
        val workDayRepository = mockk<WorkDayRepository>(relaxed = true)
        val settingsRepository = mockk<SettingsRepository>(relaxed = true)
        val calculateDayWorkTime = mockk<CalculateDayWorkTimeUseCase>()
        val calculateFlextime = mockk<CalculateFlextimeUseCase>()
        val calculateProjectedEndTime = mockk<CalculateProjectedEndTimeUseCase>()

        every { settingsRepository.getSettings() } returns flowOf(defaultSettings)
        every { settingsRepository.getWorkTimeRules() } returns flowOf(emptyList())
        every { settingsRepository.getWorkTimeRuleForDate(any(), any()) } returns null
        every { workDayRepository.getWorkDaysForYear(any()) } returns flowOf(emptyList())

        val dataProvider = FlexWidgetDataProvider(
            workDayRepository = workDayRepository,
            settingsRepository = settingsRepository,
            calculateDayWorkTime = calculateDayWorkTime,
            calculateFlextime = calculateFlextime,
            calculateProjectedEndTime = calculateProjectedEndTime
        )

        val today = LocalDate.now()
        val runningBlock = TimeBlock(
            id = 30L,
            workDayId = 3L,
            startTime = LocalTime.of(8, 0),
            endTime = null,
            location = WorkLocation.OFFICE
        )
        val runningWorkDay = WorkDay(
            id = 3L,
            date = today,
            location = WorkLocation.OFFICE,
            dayType = DayType.WORK,
            timeBlocks = listOf(runningBlock)
        )

        every { workDayRepository.getWorkDay(today) } returns flowOf(runningWorkDay)
        every { calculateDayWorkTime(any(), any()) } returns DayWorkTimeResult(
            grossMinutes = 180,
            netMinutes = 180,
            breakMinutes = 0,
            exceedsMaxHours = false
        )
        every { calculateFlextime(any(), any(), any(), any()) } returns FlextimeBalance(
            totalMinutes = 0,
            targetMinutes = 480
        )
        every { calculateProjectedEndTime(any(), any(), any(), any()) } returns ProjectedEndTime(
            projectedEndTime = LocalTime.of(16, 30),
            projectedEndTimeWithoutBreak = LocalTime.of(16, 0),
            pendingBreakMinutes = 30L,
            takenBreakMinutes = 0L,
            requiredBreakMinutes = 30L,
            remainingNetMinutes = 300L
        )

        When("der FlexWidgetDataProvider den Widget-State bei laufender Stoppuhr berechnet") {
            val state = dataProvider.getWidgetState()

            Then("ist isClockRunning true") {
                state.isClockRunning shouldBe true
            }
            Then("ist projectedEndTime mit 16:30 befüllt") {
                state.projectedEndTime shouldBe LocalTime.of(16, 30)
            }
        }

        When("der Benutzer ausgestempelt ist") {
            val stoppedWorkDay = WorkDay(
                id = 3L,
                date = today,
                location = WorkLocation.OFFICE,
                dayType = DayType.WORK,
                timeBlocks = listOf(
                    TimeBlock(
                        id = 30L,
                        workDayId = 3L,
                        startTime = LocalTime.of(8, 0),
                        endTime = LocalTime.of(12, 0),
                        location = WorkLocation.OFFICE
                    )
                )
            )
            every { workDayRepository.getWorkDay(today) } returns flowOf(stoppedWorkDay)

            val state = dataProvider.getWidgetState()

            Then("ist isClockRunning false") {
                state.isClockRunning shouldBe false
            }
            Then("ist projectedEndTime null") {
                state.projectedEndTime shouldBe null
            }
        }

        When("das Tagessoll bereits erreicht ist") {
            every { workDayRepository.getWorkDay(today) } returns flowOf(runningWorkDay)
            every { calculateDayWorkTime(any(), any()) } returns DayWorkTimeResult(
                grossMinutes = 510,
                netMinutes = 480, // Tagessoll 480 erreicht
                breakMinutes = 30,
                exceedsMaxHours = false
            )

            val state = dataProvider.getWidgetState()

            Then("ist netMinutesToday >= targetMinutesToday") {
                (state.netMinutesToday >= state.targetMinutesToday) shouldBe true
            }
            Then("ist projectedEndTime trotz laufender Stoppuhr null") {
                state.projectedEndTime shouldBe null
            }
        }
    }

    Given("die FlexWidget Tagesfortschritts- und Formatierungs-Berechnung") {
        fun formatMinutes(minutes: Long): String {
            val h = minutes / 60
            val m = minutes % 60
            return "%02d:%02d".format(h, m)
        }

        fun calculateProgressDetail(
            netMinutesToday: Long,
            targetMinutesToday: Long,
            isNarrow: Boolean,
            isClockRunning: Boolean = false,
            projectedEndTime: LocalTime? = null
        ): Pair<Float, String> {
            val progress = if (targetMinutesToday > 0) {
                (netMinutesToday.toFloat() / targetMinutesToday.toFloat()).coerceIn(0f, 1f)
            } else 0f
            val progressPercent = (progress * 100).toInt()
            val progressDetail = when {
                targetMinutesToday <= 0 -> {
                    if (netMinutesToday > 0) {
                        "${formatMinutes(netMinutesToday)} Std. (kein Soll)"
                    } else {
                        "Kein Tagessoll"
                    }
                }
                netMinutesToday >= targetMinutesToday -> "$progressPercent% (Ziel erreicht)"
                else -> {
                    val remaining = targetMinutesToday - netMinutesToday
                    val endSuffix = if (isClockRunning && projectedEndTime != null) {
                        " · Ende %02d:%02d".format(projectedEndTime.hour, projectedEndTime.minute)
                    } else {
                        ""
                    }
                    "$progressPercent% (noch ${formatMinutes(remaining)} Std.$endSuffix)"
                }
            }
            val displayText = if (isNarrow) {
                if (targetMinutesToday <= 0) {
                    if (netMinutesToday > 0) "${formatMinutes(netMinutesToday)} Std." else "Kein Soll"
                } else {
                    "$progressPercent%"
                }
            } else {
                progressDetail
            }
            return progress to displayText
        }

        When("das Tagesziel noch nicht erreicht ist (z.B. 300 von 480 Min)") {
            val (progress, textWide) = calculateProgressDetail(300L, 480L, isNarrow = false)
            val (_, textNarrow) = calculateProgressDetail(300L, 480L, isNarrow = true)

            Then("ist der Fortschritt ca. 62% und Restzeit 03:00 Std.") {
                progress shouldBe (300f / 480f)
                textWide shouldBe "62% (noch 03:00 Std.)"
                textNarrow shouldBe "62%"
            }
        }

        When("die Stoppuhr läuft und eine Feierabend-Prognose vorhanden ist (z.B. 300 von 480 Min, Ende 16:30)") {
            val (progress, textWide) = calculateProgressDetail(300L, 480L, isNarrow = false, isClockRunning = true, projectedEndTime = LocalTime.of(16, 30))
            val (_, textNarrow) = calculateProgressDetail(300L, 480L, isNarrow = true, isClockRunning = true, projectedEndTime = LocalTime.of(16, 30))

            Then("enthält die Detail-Ansicht ' · Ende 16:30'") {
                progress shouldBe (300f / 480f)
                textWide shouldBe "62% (noch 03:00 Std. · Ende 16:30)"
                textNarrow shouldBe "62%"
            }
        }

        When("der Benutzer ausgestempelt ist trotz übergebenem Endzeit-Wert") {
            val (progress, textWide) = calculateProgressDetail(300L, 480L, isNarrow = false, isClockRunning = false, projectedEndTime = LocalTime.of(16, 30))
            val (_, textNarrow) = calculateProgressDetail(300L, 480L, isNarrow = true, isClockRunning = false, projectedEndTime = LocalTime.of(16, 30))

            Then("wird kein Ende-Suffix angehängt") {
                progress shouldBe (300f / 480f)
                textWide shouldBe "62% (noch 03:00 Std.)"
                textNarrow shouldBe "62%"
            }
        }

        When("das Tagesziel erreicht ist trotz laufender Stoppuhr und übergebenem Endzeit-Wert") {
            val (progress, textWide) = calculateProgressDetail(480L, 480L, isNarrow = false, isClockRunning = true, projectedEndTime = LocalTime.of(16, 30))
            val (_, textNarrow) = calculateProgressDetail(480L, 480L, isNarrow = true, isClockRunning = true, projectedEndTime = LocalTime.of(16, 30))

            Then("wird 'Ziel erreicht' gemeldet und kein Ende-Suffix angehängt") {
                progress shouldBe 1f
                textWide shouldBe "100% (Ziel erreicht)"
                textNarrow shouldBe "100%"
            }
        }

        When("das Tagesziel exakt erreicht ist (480 von 480 Min)") {
            val (progress, textWide) = calculateProgressDetail(480L, 480L, isNarrow = false)
            val (_, textNarrow) = calculateProgressDetail(480L, 480L, isNarrow = true)

            Then("wird Ziel erreicht gemeldet") {
                progress shouldBe 1f
                textWide shouldBe "100% (Ziel erreicht)"
                textNarrow shouldBe "100%"
            }
        }

        When("das Tagesziel überschritten ist (520 von 480 Min)") {
            val (progress, textWide) = calculateProgressDetail(520L, 480L, isNarrow = false)
            val (_, textNarrow) = calculateProgressDetail(520L, 480L, isNarrow = true)

            Then("wird der Fortschritt auf 100% gedeckelt und Ziel erreicht gemeldet") {
                progress shouldBe 1f
                textWide shouldBe "100% (Ziel erreicht)"
                textNarrow shouldBe "100%"
            }
        }

        When("kein Tagessoll definiert ist und keine Arbeitszeit vorliegt (0 Min Soll, 0 Min gearbeitet)") {
            val (progress, textWide) = calculateProgressDetail(0L, 0L, isNarrow = false)
            val (_, textNarrow) = calculateProgressDetail(0L, 0L, isNarrow = true)

            Then("wird Kein Tagessoll bzw. Kein Soll gemeldet ohne 0% Fehler") {
                progress shouldBe 0f
                textWide shouldBe "Kein Tagessoll"
                textNarrow shouldBe "Kein Soll"
            }
        }

        When("an einem Tag ohne Soll gearbeitet wurde (0 Min Soll, 150 Min gearbeitet)") {
            val (progress, textWide) = calculateProgressDetail(150L, 0L, isNarrow = false)
            val (_, textNarrow) = calculateProgressDetail(150L, 0L, isNarrow = true)

            Then("wird die gearbeitete Zeit angezeigt statt irreführender 0%") {
                progress shouldBe 0f
                textWide shouldBe "02:30 Std. (kein Soll)"
                textNarrow shouldBe "02:30 Std."
            }
        }
    }
})
