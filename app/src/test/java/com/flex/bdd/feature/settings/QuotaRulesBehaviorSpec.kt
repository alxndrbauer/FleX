package com.flex.bdd.feature.settings

import com.flex.data.local.dao.QuotaRuleDao
import com.flex.data.local.dao.SettingsDao
import com.flex.data.local.dao.WorkTimeRuleDao
import com.flex.data.local.entity.QuotaRuleEntity
import com.flex.data.repository.SettingsRepositoryImpl
import com.flex.domain.model.QuotaRule
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
import java.time.YearMonth

class QuotaRulesBehaviorSpec : BehaviorSpec({

    Given("ein SettingsRepository für dynamische Quotenregeln") {
        val settingsDao = mockk<SettingsDao>(relaxed = true)
        val quotaRuleDao = mockk<QuotaRuleDao>(relaxed = true)
        val workTimeRuleDao = mockk<WorkTimeRuleDao>(relaxed = true)

        val storedRules = mutableListOf<QuotaRuleEntity>()
        val rulesFlow = MutableStateFlow<List<QuotaRuleEntity>>(emptyList())
        every { quotaRuleDao.getAllRules() } returns rulesFlow

        coEvery { quotaRuleDao.insert(any()) } answers {
            val entity = firstArg<QuotaRuleEntity>()
            storedRules.removeAll { it.id == entity.id }
            storedRules.add(entity)
            rulesFlow.value = storedRules.toList()
            entity.id
        }

        coEvery { quotaRuleDao.delete(any()) } answers {
            val entity = firstArg<QuotaRuleEntity>()
            storedRules.removeAll { it.id == entity.id }
            rulesFlow.value = storedRules.toList()
        }

        val repository: SettingsRepository = SettingsRepositoryImpl(
            settingsDao,
            quotaRuleDao,
            workTimeRuleDao
        )

        val initialRule = QuotaRule(
            id = 1L,
            validFrom = YearMonth.of(2025, 1),
            officeQuotaPercent = 40,
            officeQuotaMinDays = 8
        )
        val newerRule = QuotaRule(
            id = 2L,
            validFrom = YearMonth.of(2026, 6),
            officeQuotaPercent = 50,
            officeQuotaMinDays = 10
        )

        When("mehrere QuotaRules mit unterschiedlichem Gültigkeitsbeginn gespeichert werden") {
            repository.saveQuotaRule(initialRule)
            repository.saveQuotaRule(newerRule)

            Then("stellt der Flow alle gespeicherten Quotenregeln bereit") {
                val rules = repository.getQuotaRules().first()
                rules.shouldHaveSize(2)
            }

            Then("liefert der Lookup für einen Monat vor der zweiten Regel (März 2025) die erste Regel") {
                val rules = repository.getQuotaRules().first()
                val ruleForMarch2025 = repository.getQuotaRuleForMonth(YearMonth.of(2025, 3), rules)
                ruleForMarch2025.shouldNotBeNull()
                ruleForMarch2025.id shouldBe 1L
                ruleForMarch2025.officeQuotaPercent shouldBe 40
                ruleForMarch2025.officeQuotaMinDays shouldBe 8
            }

            Then("liefert der Lookup für den Monat direkt vor Inkrafttreten der zweiten Regel (Mai 2026) weiterhin die erste Regel") {
                val rules = repository.getQuotaRules().first()
                val ruleForMay2026 = repository.getQuotaRuleForMonth(YearMonth.of(2026, 5), rules)
                ruleForMay2026.shouldNotBeNull()
                ruleForMay2026.id shouldBe 1L
                ruleForMay2026.officeQuotaPercent shouldBe 40
                ruleForMay2026.officeQuotaMinDays shouldBe 8
            }

            Then("liefert der Lookup ab dem Stichtag (Juni 2026) die neuere Regel") {
                val rules = repository.getQuotaRules().first()
                val ruleForJune2026 = repository.getQuotaRuleForMonth(YearMonth.of(2026, 6), rules)
                ruleForJune2026.shouldNotBeNull()
                ruleForJune2026.id shouldBe 2L
                ruleForJune2026.officeQuotaPercent shouldBe 50
                ruleForJune2026.officeQuotaMinDays shouldBe 10
            }

            Then("liefert der Lookup für spätere Monate (Dezember 2026) weiterhin die aktuellste Regel") {
                val rules = repository.getQuotaRules().first()
                val ruleForDec2026 = repository.getQuotaRuleForMonth(YearMonth.of(2026, 12), rules)
                ruleForDec2026.shouldNotBeNull()
                ruleForDec2026.id shouldBe 2L
                ruleForDec2026.officeQuotaPercent shouldBe 50
            }

            Then("liefert der Lookup für einen Monat vor jeglicher hinterlegten Regel (Dezember 2024) null") {
                val rules = repository.getQuotaRules().first()
                val ruleBeforeAll = repository.getQuotaRuleForMonth(YearMonth.of(2024, 12), rules)
                ruleBeforeAll.shouldBeNull()
            }
        }

        When("die neuere QuotaRule gelöscht wird") {
            repository.deleteQuotaRule(newerRule)

            Then("enthält die Liste nur noch die ursprüngliche Regel") {
                val rules = repository.getQuotaRules().first()
                rules.shouldHaveSize(1)
                rules.first().id shouldBe 1L
            }

            Then("fällt der Lookup für Juni 2026 auf die historisch gültige Regel von Januar 2025 zurück") {
                val rules = repository.getQuotaRules().first()
                val ruleForJuneAfterDelete = repository.getQuotaRuleForMonth(YearMonth.of(2026, 6), rules)
                ruleForJuneAfterDelete.shouldNotBeNull()
                ruleForJuneAfterDelete.id shouldBe 1L
                ruleForJuneAfterDelete.officeQuotaPercent shouldBe 40
            }
        }

        When("auch die verbleibende Regel gelöscht wird") {
            repository.deleteQuotaRule(initialRule)

            Then("ist die Regelliste leer") {
                val rules = repository.getQuotaRules().first()
                rules.shouldBeEmpty()
            }

            Then("liefert der Lookup für jeden Monat null") {
                val rules = repository.getQuotaRules().first()
                repository.getQuotaRuleForMonth(YearMonth.of(2025, 1), rules).shouldBeNull()
                repository.getQuotaRuleForMonth(YearMonth.of(2026, 6), rules).shouldBeNull()
            }
        }
    }
})
