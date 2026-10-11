package com.flex.bdd.feature.worktimemodel

import com.flex.domain.model.DEFAULT_WORK_DAYS
import com.flex.domain.model.DayType
import com.flex.domain.model.Settings
import com.flex.domain.model.TimeBlock
import com.flex.domain.model.WorkDay
import com.flex.domain.model.WorkLocation
import com.flex.domain.model.WorkTimeRule
import com.flex.domain.model.getRuleForDate
import com.flex.domain.repository.SettingsRepository
import com.flex.domain.repository.WorkDayRepository
import com.flex.domain.usecase.CalculateDayWorkTimeUseCase
import com.flex.domain.usecase.GetSettingsUseCase
import com.flex.domain.usecase.SyncPlannedDaysWithWorkTimeRuleUseCase
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

/**
 * BDD-Spezifikation für die Synchronisation geplanter Tage mit Arbeitszeit-Zeiträumen.
 *
 * Behandelt:
 * 1. Geplante Arbeitstage auf neue Soll-Arbeitszeit aktualisieren
 * 2. Manuell angepasste Tage mit individuellen Stunden bleiben unberührt
 * 3. Arbeitstage-Änderung: 4-Tage-Woche mit removeNonWorkDays = true
 * 4. Arbeitstage-Änderung: 4-Tage-Woche mit removeNonWorkDays = false
 * 5. Echte / bestätigte Arbeitstage (isPlanned = false) werden NIEMALS angetastet
 * 6. Bereichs-Synchronisation (syncRange) über mehrere zukünftige Monate ab validFrom
 * 7. Vorab-Zählung (countAffectedPlannedDays) als read-only Prüfung
 */
class WorkTimeRuleSyncBehaviorSpec : BehaviorSpec({

    val calculateDayWorkTime = CalculateDayWorkTimeUseCase()

    val baseSettings = Settings(
        dailyWorkMinutes = 462,
        monthlyWorkMinutes = 9372,
        defaultStartTime = LocalTime.of(8, 0),
        initialFlextimeMinutes = 0,
        initialOvertimeMinutes = 0
    )

    fun createPlannedDay(
        date: LocalDate,
        minutes: Long,
        dayType: DayType = DayType.WORK,
        location: WorkLocation = WorkLocation.OFFICE,
        isPlanned: Boolean = true
    ): WorkDay {
        val start = LocalTime.of(8, 0)
        return WorkDay(
            id = date.toEpochDay(),
            date = date,
            dayType = dayType,
            location = location,
            isPlanned = isPlanned,
            timeBlocks = listOf(
                TimeBlock(
                    id = date.toEpochDay(),
                    workDayId = date.toEpochDay(),
                    startTime = start,
                    endTime = start.plusMinutes(minutes),
                    isDuration = true,
                    location = location
                )
            )
        )
    }

    // =========================================================================
    // Szenario 1: Geplante Arbeitstage auf neue Soll-Arbeitszeit aktualisieren
    // =========================================================================
    Given("geplante Arbeitstage im Monat mit alter Arbeitszeit (462 Min) und neue WorkTimeRule mit 480 Min") {
        val workDayRepository = mockk<WorkDayRepository>(relaxed = true)
        val settingsRepository = mockk<SettingsRepository>(relaxed = true)
        val getSettings = mockk<GetSettingsUseCase>()
        val syncUseCase = SyncPlannedDaysWithWorkTimeRuleUseCase(
            workDayRepository = workDayRepository,
            settingsRepository = settingsRepository,
            getSettings = getSettings,
            calculateDayWorkTime = calculateDayWorkTime
        )

        val month = YearMonth.of(2026, 10)
        val ruleNew = WorkTimeRule(
            id = 1L,
            validFrom = month,
            dailyWorkMinutes = 480,
            monthlyWorkMinutes = 10560,
            workDays = DEFAULT_WORK_DAYS
        )
        val rules = listOf(ruleNew)

        val plannedDays = (5..9).map { day ->
            createPlannedDay(
                date = LocalDate.of(2026, 10, day),
                minutes = 462L,
                isPlanned = true
            )
        }

        coEvery { workDayRepository.getWorkDaysForMonth(month) } returns flowOf(plannedDays)
        coEvery { settingsRepository.getWorkTimeRules() } returns flowOf(rules)
        every { getSettings() } returns flowOf(baseSettings.copy(dailyWorkMinutes = 480))
        coEvery { workDayRepository.saveTimeBlock(any()) } returns 1L

        When("syncMonth für den Monat mit previousDailyMinutes = 462 aufgerufen wird") {
            val result = syncUseCase.syncMonth(
                month = month,
                removeNonWorkDays = false,
                previousDailyMinutes = 462
            )

            Then("werden alle 5 geplanten Tage auf 480 Minuten aktualisiert") {
                result.updatedDaysCount shouldBe 5
                result.removedDaysCount shouldBe 0
                result.untouchedCustomDaysCount shouldBe 0

                val savedBlocksSlot = mutableListOf<TimeBlock>()
                coVerify(exactly = 5) { workDayRepository.saveTimeBlock(capture(savedBlocksSlot)) }

                savedBlocksSlot.forEach { savedBlock ->
                    savedBlock.isDuration shouldBe true
                    Duration.between(savedBlock.startTime, savedBlock.endTime).toMinutes() shouldBe 480L
                }
            }
        }
    }

    // =========================================================================
    // Szenario 2: Manuell angepasste Tage mit individuellen Stunden bleiben unberührt
    // =========================================================================
    Given("ein geplanter Standard-Tag (462 Min) und ein manuell angepasster Tag (240 Min) bei neuer Regel mit 480 Min") {
        val workDayRepository = mockk<WorkDayRepository>(relaxed = true)
        val settingsRepository = mockk<SettingsRepository>(relaxed = true)
        val getSettings = mockk<GetSettingsUseCase>()
        val syncUseCase = SyncPlannedDaysWithWorkTimeRuleUseCase(
            workDayRepository = workDayRepository,
            settingsRepository = settingsRepository,
            getSettings = getSettings,
            calculateDayWorkTime = calculateDayWorkTime
        )

        val month = YearMonth.of(2026, 10)
        val ruleNew = WorkTimeRule(
            id = 1L,
            validFrom = month,
            dailyWorkMinutes = 480,
            monthlyWorkMinutes = 10560,
            workDays = DEFAULT_WORK_DAYS
        )
        val rules = listOf(ruleNew)

        val standardDay = createPlannedDay(
            date = LocalDate.of(2026, 10, 5),
            minutes = 462L,
            isPlanned = true
        )
        val customDay = createPlannedDay(
            date = LocalDate.of(2026, 10, 6),
            minutes = 240L,
            isPlanned = true
        )
        val plannedDays = listOf(standardDay, customDay)

        coEvery { workDayRepository.getWorkDaysForMonth(month) } returns flowOf(plannedDays)
        coEvery { settingsRepository.getWorkTimeRules() } returns flowOf(rules)
        every { getSettings() } returns flowOf(baseSettings.copy(dailyWorkMinutes = 480))
        coEvery { workDayRepository.saveTimeBlock(any()) } returns 1L

        When("syncMonth mit previousDailyMinutes = 462 aufgerufen wird") {
            val result = syncUseCase.syncMonth(
                month = month,
                previousDailyMinutes = 462
            )

            Then("wird der Standard-Tag auf 480 Min aktualisiert und der 240-Min-Tag bleibt unberührt") {
                result.updatedDaysCount shouldBe 1
                result.untouchedCustomDaysCount shouldBe 1
                result.removedDaysCount shouldBe 0

                val savedBlocks = mutableListOf<TimeBlock>()
                coVerify(exactly = 1) { workDayRepository.saveTimeBlock(capture(savedBlocks)) }

                val updatedBlock = savedBlocks.first()
                Duration.between(updatedBlock.startTime, updatedBlock.endTime).toMinutes() shouldBe 480L

                // Der benutzerdefinierte Tag (06.10, id = 20733) wurde nicht überschrieben
                coVerify(exactly = 0) {
                    workDayRepository.saveTimeBlock(match { it.workDayId == customDay.id })
                }
            }
        }
    }

    // =========================================================================
    // Szenario 3: Arbeitstage-Änderung: 4-Tage-Woche mit removeNonWorkDays = true
    // =========================================================================
    Given("geplante Tage von Mo bis Fr und Wechsel auf 4-Tage-Woche (Mo–Do) mit removeNonWorkDays = true") {
        val workDayRepository = mockk<WorkDayRepository>(relaxed = true)
        val settingsRepository = mockk<SettingsRepository>(relaxed = true)
        val getSettings = mockk<GetSettingsUseCase>()
        val syncUseCase = SyncPlannedDaysWithWorkTimeRuleUseCase(
            workDayRepository = workDayRepository,
            settingsRepository = settingsRepository,
            getSettings = getSettings,
            calculateDayWorkTime = calculateDayWorkTime
        )

        val month = YearMonth.of(2026, 10)
        val fourDays = setOf(
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY
        )
        val ruleFourDays = WorkTimeRule(
            id = 2L,
            validFrom = month,
            dailyWorkMinutes = 533,
            monthlyWorkMinutes = 18 * 533,
            workDays = fourDays
        )
        val rules = listOf(ruleFourDays)

        // Mo 05.10 bis Fr 09.10
        val plannedDays = (5..9).map { day ->
            createPlannedDay(
                date = LocalDate.of(2026, 10, day),
                minutes = 462L,
                isPlanned = true
            )
        }

        coEvery { workDayRepository.getWorkDaysForMonth(month) } returns flowOf(plannedDays)
        coEvery { settingsRepository.getWorkTimeRules() } returns flowOf(rules)
        every { getSettings() } returns flowOf(baseSettings.copy(dailyWorkMinutes = 533))
        coEvery { workDayRepository.saveTimeBlock(any()) } returns 1L
        coEvery { workDayRepository.deleteWorkDay(any()) } returns Unit

        When("syncMonth mit removeNonWorkDays = true und previousDailyMinutes = 462 aufgerufen wird") {
            val result = syncUseCase.syncMonth(
                month = month,
                removeNonWorkDays = true,
                previousDailyMinutes = 462
            )

            Then("werden geplante Freitage gelöscht und Mo–Do auf 533 Min aktualisiert") {
                result.removedDaysCount shouldBe 1
                result.updatedDaysCount shouldBe 4
                result.untouchedCustomDaysCount shouldBe 0

                val deletedDaySlot = slot<WorkDay>()
                coVerify(exactly = 1) { workDayRepository.deleteWorkDay(capture(deletedDaySlot)) }
                deletedDaySlot.captured.date shouldBe LocalDate.of(2026, 10, 9) // Freitag
                deletedDaySlot.captured.isPlanned shouldBe true

                val savedBlocks = mutableListOf<TimeBlock>()
                coVerify(exactly = 4) { workDayRepository.saveTimeBlock(capture(savedBlocks)) }
                savedBlocks.forEach { savedBlock ->
                    Duration.between(savedBlock.startTime, savedBlock.endTime).toMinutes() shouldBe 533L
                }
            }
        }
    }

    // =========================================================================
    // Szenario 4: Arbeitstage-Änderung: 4-Tage-Woche mit removeNonWorkDays = false
    // =========================================================================
    Given("geplante Tage von Mo bis Fr und Wechsel auf 4-Tage-Woche (Mo–Do) mit removeNonWorkDays = false") {
        val workDayRepository = mockk<WorkDayRepository>(relaxed = true)
        val settingsRepository = mockk<SettingsRepository>(relaxed = true)
        val getSettings = mockk<GetSettingsUseCase>()
        val syncUseCase = SyncPlannedDaysWithWorkTimeRuleUseCase(
            workDayRepository = workDayRepository,
            settingsRepository = settingsRepository,
            getSettings = getSettings,
            calculateDayWorkTime = calculateDayWorkTime
        )

        val month = YearMonth.of(2026, 10)
        val fourDays = setOf(
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY
        )
        val ruleFourDays = WorkTimeRule(
            id = 2L,
            validFrom = month,
            dailyWorkMinutes = 533,
            monthlyWorkMinutes = 18 * 533,
            workDays = fourDays
        )
        val rules = listOf(ruleFourDays)

        val plannedDays = (5..9).map { day ->
            createPlannedDay(
                date = LocalDate.of(2026, 10, day),
                minutes = 462L,
                isPlanned = true
            )
        }

        coEvery { workDayRepository.getWorkDaysForMonth(month) } returns flowOf(plannedDays)
        coEvery { settingsRepository.getWorkTimeRules() } returns flowOf(rules)
        every { getSettings() } returns flowOf(baseSettings.copy(dailyWorkMinutes = 533))
        coEvery { workDayRepository.saveTimeBlock(any()) } returns 1L

        When("syncMonth mit removeNonWorkDays = false und previousDailyMinutes = 462 aufgerufen wird") {
            val result = syncUseCase.syncMonth(
                month = month,
                removeNonWorkDays = false,
                previousDailyMinutes = 462
            )

            Then("wird kein Tag gelöscht (removedDaysCount == 0)") {
                result.removedDaysCount shouldBe 0
                coVerify(exactly = 0) { workDayRepository.deleteWorkDay(any()) }
            }
        }
    }

    // =========================================================================
    // Szenario 5: Echte / bestätigte Arbeitstage (isPlanned = false) werden NIEMALS angetastet
    // =========================================================================
    Given("ein Monat mit sowohl geplanten Tagen (isPlanned = true) als auch bestätigten Tagen (isPlanned = false)") {
        val workDayRepository = mockk<WorkDayRepository>(relaxed = true)
        val settingsRepository = mockk<SettingsRepository>(relaxed = true)
        val getSettings = mockk<GetSettingsUseCase>()
        val syncUseCase = SyncPlannedDaysWithWorkTimeRuleUseCase(
            workDayRepository = workDayRepository,
            settingsRepository = settingsRepository,
            getSettings = getSettings,
            calculateDayWorkTime = calculateDayWorkTime
        )

        val month = YearMonth.of(2026, 10)
        val fourDays = setOf(
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY
        )
        val ruleFourDays = WorkTimeRule(
            id = 2L,
            validFrom = month,
            dailyWorkMinutes = 533,
            monthlyWorkMinutes = 18 * 533,
            workDays = fourDays
        )
        val rules = listOf(ruleFourDays)

        val confirmedFriday = createPlannedDay(
            date = LocalDate.of(2026, 10, 2),
            minutes = 462L,
            isPlanned = false
        )
        val confirmedMonday = createPlannedDay(
            date = LocalDate.of(2026, 10, 5),
            minutes = 420L,
            isPlanned = false
        )
        val plannedFriday = createPlannedDay(
            date = LocalDate.of(2026, 10, 9),
            minutes = 462L,
            isPlanned = true
        )
        val plannedMonday = createPlannedDay(
            date = LocalDate.of(2026, 10, 12),
            minutes = 462L,
            isPlanned = true
        )

        val allMonthDays = listOf(confirmedFriday, confirmedMonday, plannedFriday, plannedMonday)

        coEvery { workDayRepository.getWorkDaysForMonth(month) } returns flowOf(allMonthDays)
        coEvery { settingsRepository.getWorkTimeRules() } returns flowOf(rules)
        every { getSettings() } returns flowOf(baseSettings.copy(dailyWorkMinutes = 533))
        coEvery { workDayRepository.saveTimeBlock(any()) } returns 1L
        coEvery { workDayRepository.deleteWorkDay(any()) } returns Unit

        When("syncMonth aufgerufen wird (selbst mit removeNonWorkDays = true)") {
            syncUseCase.syncMonth(
                month = month,
                removeNonWorkDays = true,
                previousDailyMinutes = 462
            )

            Then("werden bestätigte Tage (isPlanned = false) weder gelöscht noch geändert") {
                coVerify(exactly = 0) {
                    workDayRepository.deleteWorkDay(match { !it.isPlanned })
                }
                coVerify(exactly = 0) {
                    workDayRepository.saveTimeBlock(match { it.workDayId == confirmedFriday.id || it.workDayId == confirmedMonday.id })
                }

                // Nur der geplante Freitag wurde gelöscht
                val deletedSlot = slot<WorkDay>()
                coVerify(exactly = 1) { workDayRepository.deleteWorkDay(capture(deletedSlot)) }
                deletedSlot.captured.date shouldBe LocalDate.of(2026, 10, 9)
                deletedSlot.captured.isPlanned shouldBe true

                // Nur der geplante Montag wurde aktualisiert
                val savedSlot = slot<TimeBlock>()
                coVerify(exactly = 1) { workDayRepository.saveTimeBlock(capture(savedSlot)) }
                savedSlot.captured.workDayId shouldBe plannedMonday.id
            }
        }
    }

    // =========================================================================
    // Szenario 6: Bereichs-Synchronisation (syncRange) über mehrere zukünftige Monate
    // =========================================================================
    Given("geplante Tage in zwei aufeinanderfolgenden Monaten ab validFrom (Nov & Dez 2026)") {
        val workDayRepository = mockk<WorkDayRepository>(relaxed = true)
        val settingsRepository = mockk<SettingsRepository>(relaxed = true)
        val getSettings = mockk<GetSettingsUseCase>()
        val syncUseCase = SyncPlannedDaysWithWorkTimeRuleUseCase(
            workDayRepository = workDayRepository,
            settingsRepository = settingsRepository,
            getSettings = getSettings,
            calculateDayWorkTime = calculateDayWorkTime
        )

        val monthNov = YearMonth.of(2026, 11)
        val monthDec = YearMonth.of(2026, 12)

        val ruleNew = WorkTimeRule(
            id = 3L,
            validFrom = monthNov,
            dailyWorkMinutes = 480,
            monthlyWorkMinutes = 10560,
            workDays = DEFAULT_WORK_DAYS
        )
        val rules = listOf(ruleNew)

        val novDays = listOf(
            createPlannedDay(LocalDate.of(2026, 11, 2), minutes = 462L, isPlanned = true),
            createPlannedDay(LocalDate.of(2026, 11, 3), minutes = 462L, isPlanned = true),
            createPlannedDay(LocalDate.of(2026, 11, 4), minutes = 462L, isPlanned = true)
        )
        val decDays = listOf(
            createPlannedDay(LocalDate.of(2026, 12, 1), minutes = 462L, isPlanned = true),
            createPlannedDay(LocalDate.of(2026, 12, 2), minutes = 462L, isPlanned = true),
            createPlannedDay(LocalDate.of(2026, 12, 3), minutes = 462L, isPlanned = true),
            createPlannedDay(LocalDate.of(2026, 12, 4), minutes = 462L, isPlanned = true)
        )

        coEvery { workDayRepository.getWorkDaysForMonth(monthNov) } returns flowOf(novDays)
        coEvery { workDayRepository.getWorkDaysForMonth(monthDec) } returns flowOf(decDays)
        // For other months in default 12-month range:
        coEvery { workDayRepository.getWorkDaysForMonth(match { it != monthNov && it != monthDec }) } returns flowOf(emptyList())

        coEvery { settingsRepository.getWorkTimeRules() } returns flowOf(rules)
        every { getSettings() } returns flowOf(baseSettings.copy(dailyWorkMinutes = 480))
        coEvery { workDayRepository.saveTimeBlock(any()) } returns 1L

        When("syncRange von Nov bis Dez aufgerufen wird") {
            val result = syncUseCase.syncRange(
                fromMonth = monthNov,
                toMonth = monthDec,
                previousDailyMinutes = 462
            )

            Then("werden alle 7 Tage über beide Monate hinweg synchronisiert und aggregiert") {
                result.updatedDaysCount shouldBe 7
                result.removedDaysCount shouldBe 0
                result.untouchedCustomDaysCount shouldBe 0

                val savedBlocks = mutableListOf<TimeBlock>()
                coVerify(exactly = 7) { workDayRepository.saveTimeBlock(capture(savedBlocks)) }
                savedBlocks.forEach { savedBlock ->
                    Duration.between(savedBlock.startTime, savedBlock.endTime).toMinutes() shouldBe 480L
                }
            }
        }

        When("syncRange mit toMonth = null aufgerufen wird (offener Bereich ab fromMonth)") {
            val result = syncUseCase.syncRange(
                fromMonth = monthNov,
                toMonth = null,
                previousDailyMinutes = 462
            )

            Then("werden die Tage ab fromMonth erfolgreich synchronisiert") {
                result.updatedDaysCount shouldBe 7
                result.removedDaysCount shouldBe 0
                result.untouchedCustomDaysCount shouldBe 0
            }
        }
    }

    // =========================================================================
    // Szenario 7: Vorab-Zählung (countAffectedPlannedDays)
    // =========================================================================
    Given("geplante und echte Tage über mehrere Monate hinweg") {
        val workDayRepository = mockk<WorkDayRepository>(relaxed = true)
        val settingsRepository = mockk<SettingsRepository>(relaxed = true)
        val getSettings = mockk<GetSettingsUseCase>()
        val syncUseCase = SyncPlannedDaysWithWorkTimeRuleUseCase(
            workDayRepository = workDayRepository,
            settingsRepository = settingsRepository,
            getSettings = getSettings,
            calculateDayWorkTime = calculateDayWorkTime
        )

        val monthNov = YearMonth.of(2026, 11)
        val monthDec = YearMonth.of(2026, 12)

        val novDays = listOf(
            createPlannedDay(LocalDate.of(2026, 11, 2), minutes = 462L, isPlanned = true),
            createPlannedDay(LocalDate.of(2026, 11, 3), minutes = 462L, isPlanned = true),
            createPlannedDay(LocalDate.of(2026, 11, 4), minutes = 462L, isPlanned = true),
            createPlannedDay(LocalDate.of(2026, 11, 5), minutes = 420L, isPlanned = false) // echt!
        )
        val decDays = listOf(
            createPlannedDay(LocalDate.of(2026, 12, 1), minutes = 462L, isPlanned = true),
            createPlannedDay(LocalDate.of(2026, 12, 2), minutes = 462L, isPlanned = true)
        )

        coEvery { workDayRepository.getWorkDaysForMonth(monthNov) } returns flowOf(novDays)
        coEvery { workDayRepository.getWorkDaysForMonth(monthDec) } returns flowOf(decDays)
        coEvery { workDayRepository.getWorkDaysForMonth(match { it != monthNov && it != monthDec }) } returns flowOf(emptyList())

        When("countAffectedPlannedDays für den Zeitraum Nov bis Dez aufgerufen wird") {
            val count = syncUseCase.countAffectedPlannedDays(
                fromMonth = monthNov,
                toMonth = monthDec
            )

            Then("wird exakt die Anzahl betroffener geplanter Tage (5) zurückgegeben") {
                count shouldBe 5
            }

            Then("erfolgen keinerlei schreibende Zugriffe auf das Repository (Read-only)") {
                coVerify(exactly = 0) { workDayRepository.saveTimeBlock(any()) }
                coVerify(exactly = 0) { workDayRepository.deleteWorkDay(any()) }
            }
        }
    }

    // =========================================================================
    // Szenario 8: Tage, die bereits der Ziel-Regel entsprechen, werden nicht als custom gezählt
    // =========================================================================
    Given("ein Monat, in dem geplante Tage bereits die Ziel-Sollzeit haben, plus ein manuell angepasster Tag") {
        val workDayRepository = mockk<WorkDayRepository>(relaxed = true)
        val settingsRepository = mockk<SettingsRepository>(relaxed = true)
        val getSettings = mockk<GetSettingsUseCase>()
        val syncUseCase = SyncPlannedDaysWithWorkTimeRuleUseCase(
            workDayRepository = workDayRepository,
            settingsRepository = settingsRepository,
            getSettings = getSettings,
            calculateDayWorkTime = calculateDayWorkTime
        )

        val month = YearMonth.of(2026, 11)
        val ruleNew = WorkTimeRule(
            id = 1L,
            validFrom = month,
            dailyWorkMinutes = 480,
            monthlyWorkMinutes = 10560,
            workDays = DEFAULT_WORK_DAYS
        )

        // 3 Tage bereits mit 480 Min (Ziel)
        val matchingDays = (2..4).map { day ->
            createPlannedDay(LocalDate.of(2026, 11, day), minutes = 480L, isPlanned = true)
        }
        // 1 Tag mit 240 Min (individuell angepasst)
        val customDay = createPlannedDay(LocalDate.of(2026, 11, 5), minutes = 240L, isPlanned = true)
        val allDays = matchingDays + customDay

        coEvery { workDayRepository.getWorkDaysForMonth(month) } returns flowOf(allDays)
        coEvery { settingsRepository.getWorkTimeRules() } returns flowOf(listOf(ruleNew))
        every { getSettings() } returns flowOf(baseSettings.copy(dailyWorkMinutes = 480))

        When("syncMonth mit previousDailyMinutes = 462 aufgerufen wird") {
            val result = syncUseCase.syncMonth(
                month = month,
                previousDailyMinutes = 462
            )

            Then("werden 0 Tage aktualisiert und genau 1 individueller Tag als unberührt gemeldet") {
                result.updatedDaysCount shouldBe 0
                result.removedDaysCount shouldBe 0
                result.untouchedCustomDaysCount shouldBe 1 // Nur der 240-Min-Tag, NICHT die 480-Min-Tage!
                coVerify(exactly = 0) { workDayRepository.saveTimeBlock(any()) }
            }
        }
    }

    // =========================================================================
    // Szenario 9: Regel-Bearbeitung ohne Stundenänderung (previousDailyMinutes = null)
    // =========================================================================
    Given("ein Monat mit älterer Standard-Arbeitszeit (462 Min) und einem individuellen Tag (240 Min)") {
        val workDayRepository = mockk<WorkDayRepository>(relaxed = true)
        val settingsRepository = mockk<SettingsRepository>(relaxed = true)
        val getSettings = mockk<GetSettingsUseCase>()
        val syncUseCase = SyncPlannedDaysWithWorkTimeRuleUseCase(
            workDayRepository = workDayRepository,
            settingsRepository = settingsRepository,
            getSettings = getSettings,
            calculateDayWorkTime = calculateDayWorkTime
        )

        val month = YearMonth.of(2026, 11)
        val ruleOld = WorkTimeRule(
            id = 1L,
            validFrom = YearMonth.of(2026, 1),
            dailyWorkMinutes = 462,
            monthlyWorkMinutes = 10164,
            workDays = DEFAULT_WORK_DAYS
        )
        val ruleNew = WorkTimeRule(
            id = 2L,
            validFrom = YearMonth.of(2026, 7),
            dailyWorkMinutes = 480,
            monthlyWorkMinutes = 10560,
            workDays = DEFAULT_WORK_DAYS
        )

        val standardDay = createPlannedDay(LocalDate.of(2026, 11, 2), minutes = 462L, isPlanned = true)
        val customDay = createPlannedDay(LocalDate.of(2026, 11, 3), minutes = 240L, isPlanned = true)
        val allDays = listOf(standardDay, customDay)

        coEvery { workDayRepository.getWorkDaysForMonth(month) } returns flowOf(allDays)
        coEvery { settingsRepository.getWorkTimeRules() } returns flowOf(listOf(ruleOld, ruleNew))
        every { getSettings() } returns flowOf(baseSettings.copy(dailyWorkMinutes = 462))
        coEvery { workDayRepository.saveTimeBlock(any()) } returns 1L

        When("syncMonth mit previousDailyMinutes = null aufgerufen wird") {
            val result = syncUseCase.syncMonth(
                month = month,
                previousDailyMinutes = null
            )

            Then("wird der 462-Min-Tag auf 480 Min aktualisiert und der 240-Min-Tag bleibt unberührt") {
                result.updatedDaysCount shouldBe 1
                result.removedDaysCount shouldBe 0
                result.untouchedCustomDaysCount shouldBe 1

                val savedBlocks = mutableListOf<TimeBlock>()
                coVerify(exactly = 1) { workDayRepository.saveTimeBlock(capture(savedBlocks)) }
                Duration.between(savedBlocks.first().startTime, savedBlocks.first().endTime).toMinutes() shouldBe 480L
                coVerify(exactly = 0) {
                    workDayRepository.saveTimeBlock(match { it.workDayId == customDay.id })
                }
            }
        }
    }
})
