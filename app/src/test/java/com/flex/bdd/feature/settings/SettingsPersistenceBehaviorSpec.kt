package com.flex.bdd.feature.settings

import com.flex.data.local.dao.QuotaRuleDao
import com.flex.data.local.dao.SettingsDao
import com.flex.data.local.dao.WorkTimeRuleDao
import com.flex.data.local.entity.SettingsEntity
import com.flex.data.repository.SettingsRepositoryImpl
import com.flex.domain.model.DayType
import com.flex.domain.model.FederalState
import com.flex.domain.model.Settings
import com.flex.domain.model.TimeBlock
import com.flex.domain.model.WorkDay
import com.flex.domain.model.WorkLocation
import com.flex.domain.repository.SettingsRepository
import com.flex.domain.usecase.CalculateDayWorkTimeUseCase
import com.flex.domain.usecase.CalculateFlextimeUseCase
import com.flex.domain.usecase.CalculateQuotaUseCase
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

class SettingsPersistenceBehaviorSpec : BehaviorSpec({

    Given("ein SettingsRepository mit angebundenem Room-DAO Flow") {
        val settingsDao = mockk<SettingsDao>(relaxed = true)
        val quotaRuleDao = mockk<QuotaRuleDao>(relaxed = true)
        val workTimeRuleDao = mockk<WorkTimeRuleDao>(relaxed = true)

        val settingsEntityFlow = MutableStateFlow<SettingsEntity?>(null)
        every { settingsDao.getSettings() } returns settingsEntityFlow

        val insertedSlot = slot<SettingsEntity>()
        coEvery { settingsDao.insert(capture(insertedSlot)) } answers {
            settingsEntityFlow.value = insertedSlot.captured
        }

        val repository: SettingsRepository = SettingsRepositoryImpl(
            settingsDao,
            quotaRuleDao,
            workTimeRuleDao
        )

        When("noch keine Einstellungen in der Datenbank existieren") {
            settingsEntityFlow.value = null
            val defaultSettings = repository.getSettings().first()

            Then("liefert der Flow die Standard-Einstellungen") {
                defaultSettings.dailyWorkMinutes shouldBe 426
                defaultSettings.monthlyWorkMinutes shouldBe 9266
                defaultSettings.officeQuotaPercent shouldBe 40
                defaultSettings.officeQuotaMinDays shouldBe 8
                defaultSettings.federalState shouldBe FederalState.HAMBURG
            }
        }

        When("benutzerdefinierte Einstellungen gespeichert werden") {
            val customSettings = Settings(
                id = 1L,
                dailyWorkMinutes = 480,
                monthlyWorkMinutes = 9600,
                officeQuotaPercent = 50,
                officeQuotaMinDays = 10,
                annualVacationDays = 28,
                carryOverVacationDays = 3,
                federalState = FederalState.BAVARIA
            )
            repository.saveSettings(customSettings)

            Then("emittiert der getSettings Flow die aktualisierten Werte") {
                val loaded = repository.getSettings().first()
                loaded.dailyWorkMinutes shouldBe 480
                loaded.monthlyWorkMinutes shouldBe 9600
                loaded.officeQuotaPercent shouldBe 50
                loaded.officeQuotaMinDays shouldBe 10
                loaded.annualVacationDays shouldBe 28
                loaded.carryOverVacationDays shouldBe 3
                loaded.federalState shouldBe FederalState.BAVARIA
            }
        }
    }

    Given("die Auswirkung von täglicher Arbeitszeit auf die Gleitzeitberechnung") {
        val calculateDayWorkTime = CalculateDayWorkTimeUseCase()
        val calculateFlextime = CalculateFlextimeUseCase(calculateDayWorkTime)

        val testDate = LocalDate.of(2026, 9, 2) // Mittwoch (Werktag)
        val eightHourWorkDay = WorkDay(
            id = 1L,
            date = testDate,
            dayType = DayType.WORK,
            location = WorkLocation.OFFICE,
            timeBlocks = listOf(
                TimeBlock(
                    id = 1L,
                    workDayId = 1L,
                    startTime = LocalTime.of(8, 0),
                    endTime = LocalTime.of(16, 0), // 480 min (8h)
                    isDuration = true,
                    location = WorkLocation.OFFICE
                )
            )
        )

        When("das Standard-Tagessoll von 426 Minuten (7h 06min) konfiguriert ist") {
            val settingsStandard = Settings(dailyWorkMinutes = 426)
            val balance = calculateFlextime(listOf(eightHourWorkDay), settingsStandard)

            Then("ergibt sich ein Gleitzeitgewinn von +54 Minuten") {
                balance.earnedMinutes shouldBe 54L
                balance.totalMinutes shouldBe 54L
            }
        }

        When("die tägliche Arbeitszeit in den Einstellungen auf 480 Minuten (8h) erhöht wird") {
            val settingsUpdated = Settings(dailyWorkMinutes = 480)
            val balance = calculateFlextime(listOf(eightHourWorkDay), settingsUpdated)

            Then("ist das Tagessoll exakt erfüllt und der Gleitzeitsaldo beträgt 0 Minuten") {
                balance.earnedMinutes shouldBe 0L
                balance.totalMinutes shouldBe 0L
            }
        }
    }

    Given("die Auswirkung von monatlicher Arbeitszeit und Quoten auf die Quotenberechnung") {
        val calculateDayWorkTime = CalculateDayWorkTimeUseCase()
        val calculateQuota = CalculateQuotaUseCase(calculateDayWorkTime)
        val ym = YearMonth.of(2026, 9)

        fun createOfficeDay(dayOfMonth: Int, netMinutes: Long): WorkDay {
            val date = ym.atDay(dayOfMonth)
            val start = LocalTime.of(8, 0)
            return WorkDay(
                id = dayOfMonth.toLong(),
                date = date,
                dayType = DayType.WORK,
                location = WorkLocation.OFFICE,
                timeBlocks = listOf(
                    TimeBlock(
                        id = dayOfMonth.toLong(),
                        workDayId = dayOfMonth.toLong(),
                        startTime = start,
                        endTime = start.plusMinutes(netMinutes),
                        isDuration = true,
                        location = WorkLocation.OFFICE
                    )
                )
            )
        }

        // 4 Tage à 480 Minuten = 1920 Minuten Präsenzzeit
        val monthDays = (1..4).map { createOfficeDay(it, 480) }

        When("die Einstellungen ein hohes Monatssoll von 9600 Min und 8 Mindesttage verlangen (40% Quote)") {
            val strictSettings = Settings(
                monthlyWorkMinutes = 9600,
                officeQuotaPercent = 40,
                officeQuotaMinDays = 8
            )
            val status = calculateQuota(monthDays, strictSettings, ym)

            Then("sind weder Prozentquote noch Mindesttagequote erfüllt") {
                status.officeMinutes shouldBe 1920L
                status.officeDays shouldBe 4
                // 1920 / 9600 = 20.0% < 40%
                status.percentQuotaMet.shouldBeFalse()
                status.daysQuotaMet.shouldBeFalse()
            }
        }

        When("die Einstellungen auf 4800 Min Monatssoll und 4 Mindesttage angepasst werden") {
            val adjustedSettings = Settings(
                monthlyWorkMinutes = 4800,
                officeQuotaPercent = 40,
                officeQuotaMinDays = 4
            )
            val status = calculateQuota(monthDays, adjustedSettings, ym)

            Then("sind beide Quoten erfüllt") {
                // 1920 / 4800 = 40.0% >= 40%
                status.percentQuotaMet.shouldBeTrue()
                // 4 Tage >= 4 Tage
                status.daysQuotaMet.shouldBeTrue()
            }
        }
    }
})
