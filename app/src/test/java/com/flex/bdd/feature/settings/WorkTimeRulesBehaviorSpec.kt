package com.flex.bdd.feature.settings

import com.flex.data.local.dao.QuotaRuleDao
import com.flex.data.local.dao.SettingsDao
import com.flex.data.local.dao.WorkTimeRuleDao
import com.flex.data.local.entity.WorkTimeRuleEntity
import com.flex.data.repository.SettingsRepositoryImpl
import com.flex.domain.model.DEFAULT_WORK_DAYS
import com.flex.domain.model.WorkTimeRule
import com.flex.domain.model.getRuleForDate
import com.flex.domain.model.getRuleForMonth
import com.flex.domain.repository.SettingsRepository
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

class WorkTimeRulesBehaviorSpec : BehaviorSpec({

    Given("ein SettingsRepository für flexible Arbeitszeitregeln") {
        val settingsDao = mockk<SettingsDao>(relaxed = true)
        val quotaRuleDao = mockk<QuotaRuleDao>(relaxed = true)
        val workTimeRuleDao = mockk<WorkTimeRuleDao>(relaxed = true)

        val storedRules = mutableListOf<WorkTimeRuleEntity>()
        val rulesFlow = MutableStateFlow<List<WorkTimeRuleEntity>>(emptyList())
        every { workTimeRuleDao.getAllRules() } returns rulesFlow

        coEvery { workTimeRuleDao.insert(any()) } answers {
            val entity = firstArg<WorkTimeRuleEntity>()
            storedRules.removeAll { it.id == entity.id }
            storedRules.add(entity)
            rulesFlow.value = storedRules.toList()
            entity.id
        }

        coEvery { workTimeRuleDao.delete(any()) } answers {
            val entity = firstArg<WorkTimeRuleEntity>()
            storedRules.removeAll { it.id == entity.id }
            rulesFlow.value = storedRules.toList()
        }

        val repository: SettingsRepository = SettingsRepositoryImpl(
            settingsDao,
            quotaRuleDao,
            workTimeRuleDao
        )

        // Regel 1: 5-Tage-Woche ab Jan 2025 mit 426 Min / Tag (7h 06m)
        val fiveDayRule = WorkTimeRule(
            id = 1L,
            validFrom = YearMonth.of(2025, 1),
            dailyWorkMinutes = 426,
            monthlyWorkMinutes = 8520,
            workDays = DEFAULT_WORK_DAYS
        )

        // Regel 2: 4-Tage-Woche ab Juli 2026 mit 480 Min / Tag (8h, Mo-Do)
        val fourDayWorkDays = setOf(
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY
        )
        val fourDayRule = WorkTimeRule(
            id = 2L,
            validFrom = YearMonth.of(2026, 7),
            dailyWorkMinutes = 480,
            monthlyWorkMinutes = 7680,
            workDays = fourDayWorkDays
        )

        When("mehrere WorkTimeRules mit unterschiedlichem Gültigkeitsbeginn gespeichert werden") {
            repository.saveWorkTimeRule(fiveDayRule)
            repository.saveWorkTimeRule(fourDayRule)

            Then("stellt der Flow alle gespeicherten Arbeitszeitregeln bereit") {
                val rules = repository.getWorkTimeRules().first()
                rules.shouldHaveSize(2)
            }

            Then("liefert der Lookup nach Stichtag für ein Datum vor Juli 2026 die erste Regel (5-Tage-Woche)") {
                val rules = repository.getWorkTimeRules().first()
                val dateBeforeNewRule = LocalDate.of(2025, 5, 15)
                val activeRule = repository.getWorkTimeRuleForDate(dateBeforeNewRule, rules)

                activeRule.shouldNotBeNull()
                activeRule.id shouldBe 1L
                activeRule.dailyWorkMinutes shouldBe 426
                activeRule.monthlyWorkMinutes shouldBe 8520
                activeRule.workDays shouldBe DEFAULT_WORK_DAYS

                // Extension-Funktion getRuleForDate
                val extRule = rules.getRuleForDate(dateBeforeNewRule)
                extRule shouldBe activeRule
            }

            Then("liefert der Lookup für den Tag vor dem neuen Stichtag (30. Juni 2026) weiterhin die 5-Tage-Regel") {
                val rules = repository.getWorkTimeRules().first()
                val lastDayOfJune = LocalDate.of(2026, 6, 30)
                val activeRule = repository.getWorkTimeRuleForDate(lastDayOfJune, rules)

                activeRule.shouldNotBeNull()
                activeRule.id shouldBe 1L
                activeRule.dailyWorkMinutes shouldBe 426
            }

            Then("liefert der Lookup exakt ab Stichtag (1. Juli 2026) die neue 4-Tage-Woche-Regel") {
                val rules = repository.getWorkTimeRules().first()
                val firstDayOfJuly = LocalDate.of(2026, 7, 1)
                val activeRule = repository.getWorkTimeRuleForDate(firstDayOfJuly, rules)

                activeRule.shouldNotBeNull()
                activeRule.id shouldBe 2L
                activeRule.dailyWorkMinutes shouldBe 480
                activeRule.monthlyWorkMinutes shouldBe 7680
                activeRule.workDays shouldBe fourDayWorkDays
            }

            Then("liefert der Lookup für ein Datum weit in der Zukunft (10. Januar 2027) die 4-Tage-Regel") {
                val rules = repository.getWorkTimeRules().first()
                val futureDate = LocalDate.of(2027, 1, 10)
                val activeRule = repository.getWorkTimeRuleForDate(futureDate, rules)

                activeRule.shouldNotBeNull()
                activeRule.id shouldBe 2L
                activeRule.dailyWorkMinutes shouldBe 480
            }

            Then("liefert der Lookup für ein Datum vor allen Regeln (31. Dezember 2024) null") {
                val rules = repository.getWorkTimeRules().first()
                val dateBeforeAll = LocalDate.of(2024, 12, 31)
                val activeRule = repository.getWorkTimeRuleForDate(dateBeforeAll, rules)

                activeRule.shouldBeNull()
                rules.getRuleForDate(dateBeforeAll).shouldBeNull()
                rules.getRuleForMonth(YearMonth.of(2024, 12)).shouldBeNull()
            }
        }

        When("die neuere 4-Tage-Regel gelöscht wird") {
            repository.deleteWorkTimeRule(fourDayRule)

            Then("enthält die Regelliste nur noch die 5-Tage-Regel") {
                val rules = repository.getWorkTimeRules().first()
                rules.shouldHaveSize(1)
                rules.first().id shouldBe 1L
            }

            Then("fällt der Lookup für den 1. Juli 2026 auf die 5-Tage-Regel zurück") {
                val rules = repository.getWorkTimeRules().first()
                val activeRule = repository.getWorkTimeRuleForDate(LocalDate.of(2026, 7, 1), rules)

                activeRule.shouldNotBeNull()
                activeRule.id shouldBe 1L
                activeRule.dailyWorkMinutes shouldBe 426
                activeRule.workDays shouldBe DEFAULT_WORK_DAYS
            }
        }

        When("auch die verbleibende Arbeitszeitregel gelöscht wird") {
            repository.deleteWorkTimeRule(fiveDayRule)

            Then("ist die Liste leer und der Lookup liefert null") {
                val rules = repository.getWorkTimeRules().first()
                rules.shouldBeEmpty()
                repository.getWorkTimeRuleForDate(LocalDate.of(2025, 5, 15), rules).shouldBeNull()
                rules.getRuleForDate(LocalDate.of(2025, 5, 15)).shouldBeNull()
            }
        }
    }
})
