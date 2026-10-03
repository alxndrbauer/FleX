package com.flex.bdd.feature.holidays

import com.flex.data.holidays.HolidayApiService
import com.flex.data.local.dao.HolidayCacheDao
import com.flex.data.local.entity.HolidayCacheEntity
import com.flex.data.repository.HolidayRepositoryImpl
import com.flex.domain.model.DayType
import com.flex.domain.model.FederalState
import com.flex.domain.model.PublicHolidays
import com.flex.domain.model.Settings
import com.flex.domain.model.TimeBlock
import com.flex.domain.model.WorkDay
import com.flex.domain.model.WorkLocation
import com.flex.domain.usecase.CalculateDayWorkTimeUseCase
import com.flex.domain.usecase.CalculateFlextimeUseCase
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.maps.shouldNotContainKey
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

class PublicHolidayBehaviorSpec : BehaviorSpec({

    afterSpec {
        PublicHolidays.clearCache()
    }

    Given("ein HolidayApiService mit Daten für verschiedene Bundesländer") {
        val apiService = HolidayApiService()

        val sampleHolidaysJson = """
            [
              {
                "date": "2026-10-03",
                "localName": "Tag der Deutschen Einheit",
                "counties": null
              },
              {
                "date": "2026-10-31",
                "localName": "Reformationstag",
                "counties": ["DE-BB", "DE-HB", "DE-HH", "DE-MV", "DE-NI", "DE-SN", "DE-ST", "DE-SH", "DE-TH"]
              },
              {
                "date": "2026-11-01",
                "localName": "Allerheiligen",
                "counties": ["DE-BW", "DE-BY", "DE-NW", "DE-RP", "DE-SL"]
              },
              {
                "date": "2026-01-06",
                "localName": "Heilige Drei Könige",
                "counties": ["DE-BW", "DE-BY", "DE-ST"]
              },
              {
                "date": "2026-06-04",
                "localName": "Fronleichnam",
                "counties": ["DE-BW", "DE-BY", "DE-HE", "DE-NW", "DE-RP", "DE-SL"]
              }
            ]
        """.trimIndent()

        When("die Feiertage für Hamburg (HH) geparst werden") {
            val hamburgHolidays = apiService.parseHolidays(sampleHolidaysJson, FederalState.HAMBURG)

            Then("ist der Reformationstag (31.10.) in Hamburg ein Feiertag") {
                hamburgHolidays shouldContainKey LocalDate.of(2026, 10, 31)
                hamburgHolidays[LocalDate.of(2026, 10, 31)] shouldBe "Reformationstag"
            }

            Then("ist Allerheiligen (01.11.) in Hamburg KEIN Feiertag") {
                hamburgHolidays shouldNotContainKey LocalDate.of(2026, 11, 1)
            }

            Then("sind Heilige Drei Könige und Fronleichnam in Hamburg KEINE Feiertage") {
                hamburgHolidays shouldNotContainKey LocalDate.of(2026, 1, 6)
                hamburgHolidays shouldNotContainKey LocalDate.of(2026, 6, 4)
            }

            Then("ist der bundesweite Feiertag (Tag der Deutschen Einheit) enthalten") {
                hamburgHolidays shouldContainKey LocalDate.of(2026, 10, 3)
            }
        }

        When("die Feiertage für Bayern (BY) geparst werden") {
            val bavariaHolidays = apiService.parseHolidays(sampleHolidaysJson, FederalState.BAVARIA)

            Then("sind Allerheiligen, Heilige Drei Könige und Fronleichnam in Bayern Feiertage") {
                bavariaHolidays shouldContainKey LocalDate.of(2026, 11, 1)
                bavariaHolidays shouldContainKey LocalDate.of(2026, 1, 6)
                bavariaHolidays shouldContainKey LocalDate.of(2026, 6, 4)
            }

            Then("ist der Reformationstag (31.10.) in Bayern KEIN Feiertag") {
                bavariaHolidays shouldNotContainKey LocalDate.of(2026, 10, 31)
            }

            Then("ist der Tag der Deutschen Einheit auch in Bayern enthalten") {
                bavariaHolidays shouldContainKey LocalDate.of(2026, 10, 3)
            }
        }

        When("die Feiertage für Nordrhein-Westfalen (NRW / NW) geparst werden") {
            val nrwHolidays = apiService.parseHolidays(sampleHolidaysJson, FederalState.NORTH_RHINE_WESTPHALIA)

            Then("sind Allerheiligen und Fronleichnam in NRW Feiertage") {
                nrwHolidays shouldContainKey LocalDate.of(2026, 11, 1)
                nrwHolidays shouldContainKey LocalDate.of(2026, 6, 4)
            }

            Then("sind Heilige Drei Könige und Reformationstag in NRW KEINE Feiertage") {
                nrwHolidays shouldNotContainKey LocalDate.of(2026, 1, 6)
                nrwHolidays shouldNotContainKey LocalDate.of(2026, 10, 31)
            }
        }
    }

    Given("die Arbeitszeitberechnung bei Feiertagen an Werktagen vs. Wochenenden") {
        val calculateDayWorkTime = CalculateDayWorkTimeUseCase()
        val calculateFlextime = CalculateFlextimeUseCase(calculateDayWorkTime)
        val defaultSettings = Settings(dailyWorkMinutes = 480) // 8h pro Werktag
        val ym = YearMonth.of(2026, 7) // Juli 2026: 31 Tage, 23 Werktage (Mo-Fr), keine eingebauten Feiertage

        When("ein Feiertag auf einen Werktag fällt (z.B. Mittwoch, 15. Juli 2026)") {
            PublicHolidays.updateCache(
                2026,
                mapOf(LocalDate.of(2026, 7, 15) to "Sommerfeiertag")
            )

            Then("reduziert der Werktags-Feiertag die Monatssollzeit um genau einen Arbeitstag (480 Min)") {
                // 23 Werktage minus 1 Feiertag = 22 Soll-Arbeitstage * 480 = 10560 Min
                val balance = calculateFlextime(emptyList(), defaultSettings, ym)
                balance.targetMinutes shouldBe 22L * 480L // 10560 Min
            }
        }

        When("ein Feiertag auf ein Wochenende fällt (z.B. Sonntag, 19. Juli 2026)") {
            PublicHolidays.updateCache(
                2026,
                mapOf(LocalDate.of(2026, 7, 19) to "Wochenendfeiertag")
            )

            Then("beeinflusst der Wochenend-Feiertag die monatliche Sollzeit nicht, da Wochenenden ohnehin frei sind") {
                // 23 Werktage bleiben 23 Soll-Arbeitstage * 480 = 11040 Min
                val balance = calculateFlextime(emptyList(), defaultSettings, ym)
                balance.targetMinutes shouldBe 23L * 480L // 11040 Min
            }
        }

        When("an einem Wochenend-Feiertag tatsächlich gearbeitet wird") {
            val sunday = LocalDate.of(2026, 7, 19)
            val workDayOnSunday = WorkDay(
                id = 10L,
                date = sunday,
                dayType = DayType.WORK,
                location = WorkLocation.OFFICE,
                timeBlocks = listOf(
                    TimeBlock(
                        id = 10L,
                        workDayId = 10L,
                        startTime = LocalTime.of(9, 0),
                        endTime = LocalTime.of(13, 0), // 4 Stunden (240 Min)
                        isDuration = true,
                        location = WorkLocation.OFFICE
                    )
                )
            )

            val balance = calculateFlextime(listOf(workDayOnSunday), defaultSettings)

            Then("wird die gesamte Arbeitszeit voll als Gleitzeit gutgeschrieben (ohne Tagessoll-Abzug)") {
                balance.earnedMinutes shouldBe 240L
                balance.totalMinutes shouldBe 240L
            }
        }
    }

    Given("das Caching und die Fallback-Berechnung im HolidayRepository") {
        val mockApiService = mockk<HolidayApiService>()
        val mockHolidayCacheDao = mockk<HolidayCacheDao>(relaxed = true)
        val holidayRepository = HolidayRepositoryImpl(mockApiService, mockHolidayCacheDao)

        val apiHolidays = mapOf(
            LocalDate.of(2026, 1, 1) to "Neujahr",
            LocalDate.of(2026, 10, 3) to "Tag der Deutschen Einheit"
        )

        When("Feiertage das erste Mal über das Repository angefragt werden") {
            coEvery { mockApiService.fetchHolidays(2026, FederalState.HAMBURG) } returns apiHolidays

            val result = holidayRepository.getHolidays(2026, FederalState.HAMBURG)

            Then("werden die Feiertage von der API abgerufen und mit kulturellen Tagen angereichert") {
                result shouldContainKey LocalDate.of(2026, 1, 1)
                result shouldContainKey LocalDate.of(2026, 10, 3)
                // Kulturelle Tage (z.B. Heiligabend, Silvester)
                result shouldContainKey LocalDate.of(2026, 12, 24)
                result shouldContainKey LocalDate.of(2026, 12, 31)
            }

            Then("werden die Feiertage in Room persistiert") {
                coVerify { mockHolidayCacheDao.insertAll(any()) }
            }
        }

        When("dieselbe Anfrage ein zweites Mal ausgeführt wird") {
            val secondResult = holidayRepository.getHolidays(2026, FederalState.HAMBURG)

            Then("wird das Ergebnis direkt aus dem Memory-Cache bedient, ohne die API erneut aufzurufen") {
                secondResult shouldContainKey LocalDate.of(2026, 10, 3)
                coVerify(exactly = 1) { mockApiService.fetchHolidays(2026, FederalState.HAMBURG) }
            }
        }

        When("die API fehlschlägt, aber Room gecachte Daten enthält") {
            val offlineRepo = HolidayRepositoryImpl(mockApiService, mockHolidayCacheDao)
            coEvery { mockApiService.fetchHolidays(2027, FederalState.HAMBURG) } throws RuntimeException("Network down")
            coEvery { mockHolidayCacheDao.getHolidays(2027, "HH") } returns listOf(
                HolidayCacheEntity(
                    id = "2027-01-01_HH",
                    date = "2027-01-01",
                    name = "Neujahr",
                    federalState = "HH",
                    year = 2027
                )
            )

            val roomResult = offlineRepo.getHolidays(2027, FederalState.HAMBURG)

            Then("werden die gecachten Feiertage aus Room geladen") {
                roomResult shouldContainKey LocalDate.of(2027, 1, 1)
                roomResult[LocalDate.of(2027, 1, 1)] shouldBe "Neujahr"
            }
        }

        When("weder API noch Room Daten liefern") {
            val fallbackRepo = HolidayRepositoryImpl(mockApiService, mockHolidayCacheDao)
            coEvery { mockApiService.fetchHolidays(2028, FederalState.HAMBURG) } throws RuntimeException("Network down")
            coEvery { mockHolidayCacheDao.getHolidays(2028, "HH") } returns emptyList()

            val fallbackResult = fallbackRepo.getHolidays(2028, FederalState.HAMBURG)

            Then("greift der eingebaute Fallback mit den gesetzlichen Feiertagen") {
                fallbackResult shouldContainKey LocalDate.of(2028, 1, 1) // Neujahr
                fallbackResult shouldContainKey LocalDate.of(2028, 5, 1) // Tag der Arbeit
                fallbackResult shouldContainKey LocalDate.of(2028, 10, 3) // Tag der Dt. Einheit
                fallbackResult shouldContainKey LocalDate.of(2028, 12, 25) // 1. Weihnachtstag
            }
        }

        When("die Osterformel nach Gauß für ein bestimmtes Jahr berechnet wird") {
            val easter2026 = PublicHolidays.calculateEasterPublic(2026)
            val builtin2026 = PublicHolidays.getBuiltinHolidays(2026)

            Then("ist Ostersonntag 2026 am 5. April") {
                easter2026 shouldBe LocalDate.of(2026, 4, 5)
            }

            Then("werden die davon abhängigen beweglichen Feiertage exakt abgeleitet") {
                builtin2026[LocalDate.of(2026, 4, 3)] shouldBe "Karfreitag" // Ostern - 2
                builtin2026[LocalDate.of(2026, 4, 6)] shouldBe "Ostermontag" // Ostern + 1
                builtin2026[LocalDate.of(2026, 5, 14)] shouldBe "Christi Himmelfahrt" // Ostern + 39
                builtin2026[LocalDate.of(2026, 5, 25)] shouldBe "Pfingstmontag" // Ostern + 50
            }
        }
    }
})
