package com.flex.specification

import com.flex.domain.model.DayType
import com.flex.domain.model.Settings
import com.flex.domain.model.TimeBlock
import com.flex.domain.model.WorkDay
import com.flex.domain.model.WorkLocation
import com.flex.domain.model.WorkTimeRule
import com.flex.domain.usecase.CalculateDayWorkTimeUseCase
import com.flex.domain.usecase.CalculateQuotaUseCase
import com.flex.specification.fixture.September2026ExportFixture
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

@DisplayName("Spezifikation: Hybrid-Work-Quoten Verhalten (BDD)")
class QuotaBehaviorSpecificationTest {

    private lateinit var calculateDayWorkTime: CalculateDayWorkTimeUseCase
    private lateinit var calculateQuota: CalculateQuotaUseCase

    private val testMonth = YearMonth.of(2026, 9)

    // Standardeinstellungen: 20 Arbeitstage * 426 min = 8520 min Soll, 40% Quote, 8 Mindesttage
    private val defaultSettings = Settings(
        dailyWorkMinutes = 426, // 7h 06m
        monthlyWorkMinutes = 8520, // 20 * 426 min
        officeQuotaPercent = 40, // 40% Büroquote
        officeQuotaMinDays = 8 // 8 Tage Mindest-Bürotage
    )

    @BeforeEach
    fun setUp() {
        calculateDayWorkTime = CalculateDayWorkTimeUseCase()
        calculateQuota = CalculateQuotaUseCase(calculateDayWorkTime)
    }

    // =========================================================================
    // Szenario 1: Prozent-Quote (z.B. 40% Ziel)
    // =========================================================================
    @Nested
    @DisplayName("Szenario 1: Prozent-Quote (z.B. 40% Ziel)")
    inner class PercentQuotaScenarios {

        @Test
        @DisplayName("Fall A: Übererfüllung (z.B. 50% Bürozeit) -> percentQuotaMet == true")
        fun `overachievement of percent quota marks percentQuotaMet as true`() {
            // Given: 20 Arbeitstage Soll = 8520 min, Ziel 40% Bürozeit
            // 10 Bürotage à 426 min = 4260 min (50% der Arbeitszeit)
            // 10 Home-Office Tage à 426 min = 4260 min
            val workDays = mutableListOf<WorkDay>()
            for (day in 1..10) {
                workDays.add(createFullDay(date = LocalDate.of(2026, 9, day), location = WorkLocation.OFFICE, minutes = 426))
            }
            for (day in 11..20) {
                workDays.add(createFullDay(date = LocalDate.of(2026, 9, day), location = WorkLocation.HOME_OFFICE, minutes = 426))
            }

            // When: Quotenberechnung ausgeführt wird
            val result = calculateQuota(
                workDays = workDays,
                settings = defaultSettings,
                yearMonth = testMonth
            )

            // Then: Büroanteil beträgt exakt 50% und Quote gilt als erfüllt
            assertThat(result.officeMinutes).isEqualTo(4260L)
            assertThat(result.homeOfficeMinutes).isEqualTo(4260L)
            assertThat(result.officePercent).isWithin(0.01).of(50.0)
            assertThat(result.percentQuotaMet).isTrue()
            assertThat(result.quotaMet).isTrue()
        }

        @Test
        @DisplayName("Fall B: Untererfüllung (z.B. 30% Bürozeit) -> percentQuotaMet == false")
        fun `underachievement of percent quota marks percentQuotaMet as false`() {
            // Given: 20 Arbeitstage Soll = 8520 min, Ziel 40% Bürozeit
            // 6 Bürotage à 426 min = 2556 min (30% der Arbeitszeit)
            // 14 Home-Office Tage à 426 min = 5964 min
            val workDays = mutableListOf<WorkDay>()
            for (day in 1..6) {
                workDays.add(createFullDay(date = LocalDate.of(2026, 9, day), location = WorkLocation.OFFICE, minutes = 426))
            }
            for (day in 7..20) {
                workDays.add(createFullDay(date = LocalDate.of(2026, 9, day), location = WorkLocation.HOME_OFFICE, minutes = 426))
            }

            // When: Quotenberechnung ausgeführt wird
            val result = calculateQuota(
                workDays = workDays,
                settings = defaultSettings,
                yearMonth = testMonth
            )

            // Then: Büroanteil beträgt exakt 30% und Quote gilt als nicht erfüllt
            assertThat(result.officeMinutes).isEqualTo(2556L)
            assertThat(result.homeOfficeMinutes).isEqualTo(5964L)
            assertThat(result.officePercent).isWithin(0.01).of(30.0)
            assertThat(result.percentQuotaMet).isFalse()
        }

        @Test
        @DisplayName("Toleranzgrenze: 39,96% Bürozeit erreicht 40% Quote dank 0,05% Rundungstoleranz")
        fun `rounding tolerance of 0,05 percent allows barely reaching quota`() {
            // Given: Ziel 40%
            // Soll = 10000 min, Bürozeit = 3996 min (8 Tage à 450 min + 1 Tag à 396 min) -> officePercent = 39.96%
            val settings = defaultSettings.copy(monthlyWorkMinutes = 10000)
            val workDays = (1..8).map {
                createFullDay(date = LocalDate.of(2026, 9, it), location = WorkLocation.OFFICE, minutes = 450)
            } + createFullDay(date = LocalDate.of(2026, 9, 9), location = WorkLocation.OFFICE, minutes = 396)

            // When
            val result = calculateQuota(workDays, settings, testMonth)

            // Then: 39.96 + 0.05 = 40.01 >= 40.0 -> percentQuotaMet == true
            assertThat(result.officePercent).isWithin(0.001).of(39.96)
            assertThat(result.percentQuotaMet).isTrue()
        }

        @Test
        @DisplayName("Toleranzgrenze: 39,94% Bürozeit verfehlt 40% Quote knapp")
        fun `below rounding tolerance of 0,05 percent does not reach quota`() {
            // Given: Ziel 40%
            // Soll = 10000 min, Bürozeit = 3994 min (8 Tage à 450 min + 1 Tag à 394 min) -> officePercent = 39.94%
            val settings = defaultSettings.copy(monthlyWorkMinutes = 10000)
            val workDays = (1..8).map {
                createFullDay(date = LocalDate.of(2026, 9, it), location = WorkLocation.OFFICE, minutes = 450)
            } + createFullDay(date = LocalDate.of(2026, 9, 9), location = WorkLocation.OFFICE, minutes = 394)

            // When
            val result = calculateQuota(workDays, settings, testMonth)

            // Then: 39.94 + 0.05 = 39.99 < 40.0 -> percentQuotaMet == false
            assertThat(result.officePercent).isWithin(0.001).of(39.94)
            assertThat(result.percentQuotaMet).isFalse()
        }
    }

    // =========================================================================
    // Szenario 2: Mindesttage-Quote (z.B. 8 Tage Ziel)
    // =========================================================================
    @Nested
    @DisplayName("Szenario 2: Mindesttage-Quote (z.B. 8 Tage Ziel)")
    inner class MinimumDaysQuotaScenarios {

        @Test
        @DisplayName("Fall A: Erfüllt (8 Tage Büro) -> daysQuotaMet == true und requiredOfficeDays == 0")
        fun `meeting minimum days quota marks daysQuotaMet as true and remaining required days as 0`() {
            // Given: Mindesttage-Ziel = 8 Tage
            // 8 Bürotage gebucht
            val workDays = (1..8).map { day ->
                createFullDay(date = LocalDate.of(2026, 9, day), location = WorkLocation.OFFICE, minutes = 426)
            }

            // When: Quotenberechnung ausgeführt wird
            val result = calculateQuota(
                workDays = workDays,
                settings = defaultSettings,
                yearMonth = testMonth
            )

            // Then: 8 Bürotage gezählt, Tage-Quote erfüllt, keine weiteren Tage erforderlich
            assertThat(result.officeDays).isEqualTo(8)
            assertThat(result.daysQuotaMet).isTrue()
            assertThat(result.requiredOfficeDaysForQuota).isEqualTo(0)
            assertThat(result.quotaMet).isTrue()
        }

        @Test
        @DisplayName("Fall B: Verfehlt (7 Tage Büro) -> daysQuotaMet == false und requiredOfficeDays == 1")
        fun `missing minimum days quota marks daysQuotaMet as false and indicates 1 required day remaining`() {
            // Given: Mindesttage-Ziel = 8 Tage
            // 7 Bürotage und 5 Home-Office Tage gebucht
            val workDays = mutableListOf<WorkDay>()
            for (day in 1..7) {
                workDays.add(createFullDay(date = LocalDate.of(2026, 9, day), location = WorkLocation.OFFICE, minutes = 426))
            }
            for (day in 8..12) {
                workDays.add(createFullDay(date = LocalDate.of(2026, 9, day), location = WorkLocation.HOME_OFFICE, minutes = 426))
            }

            // When: Quotenberechnung ausgeführt wird
            val result = calculateQuota(
                workDays = workDays,
                settings = defaultSettings,
                yearMonth = testMonth
            )

            // Then: 7 Bürotage gezählt, Tage-Quote verfehlt, 1 Tag fehlt zur Erfüllung
            assertThat(result.officeDays).isEqualTo(7)
            assertThat(result.homeOfficeDays).isEqualTo(5)
            assertThat(result.daysQuotaMet).isFalse()
            assertThat(result.requiredOfficeDaysForQuota).isEqualTo(1) // 8 - 7
        }

        @Test
        @DisplayName("Übererfüllung: 10 Bürotage bei 8 Tagen Ziel -> daysQuotaMet == true und requiredOfficeDays == 0")
        fun `exceeding minimum days quota does not produce negative required days`() {
            // Given: 10 Bürotage gebucht
            val workDays = (1..10).map { day ->
                createFullDay(date = LocalDate.of(2026, 9, day), location = WorkLocation.OFFICE, minutes = 426)
            }

            // When
            val result = calculateQuota(workDays, defaultSettings, testMonth)

            // Then: requiredOfficeDays ist niemals negativ (min 0)
            assertThat(result.officeDays).isEqualTo(10)
            assertThat(result.daysQuotaMet).isTrue()
            assertThat(result.requiredOfficeDaysForQuota).isEqualTo(0)
        }

        @Test
        @DisplayName("Nur Tage mit abgeschlossenen Zeitblöcken zählen als Bürotage")
        fun `uncompleted running blocks do not count as office days`() {
            // Given: Ein Tag mit nur einem laufenden Block (endTime == null)
            val runningBlockDay = WorkDay(
                id = 1L,
                date = LocalDate.of(2026, 9, 1),
                location = WorkLocation.OFFICE,
                dayType = DayType.WORK,
                timeBlocks = listOf(
                    TimeBlock(
                        id = 1L,
                        workDayId = 1L,
                        startTime = LocalTime.of(8, 0),
                        endTime = null,
                        location = WorkLocation.OFFICE
                    )
                )
            )

            // When
            val result = calculateQuota(listOf(runningBlockDay), defaultSettings, testMonth)

            // Then: Tag zählt nicht als Bürotag
            assertThat(result.officeDays).isEqualTo(0)
            assertThat(result.daysQuotaMet).isFalse()
            assertThat(result.requiredOfficeDaysForQuota).isEqualTo(8)
        }
    }

    // =========================================================================
    // Szenario 3: Misch-Tage (Split Location an einem Tag)
    // =========================================================================
    @Nested
    @DisplayName("Szenario 3: Misch-Tage (Split Location an einem Tag)")
    inner class SplitLocationScenarios {

        @Test
        @DisplayName("Fall A: Vormittags 3h HO, Nachmittags 5h Büro -> Tag zählt als Bürotag (Büro 5h >= HO 3h)")
        fun `mixed day with more office time counts as full office day and distributes net minutes proportionally`() {
            // Given: Ein Arbeitstag mit:
            // - Vormittag: 3h (180 min) Home Office (08:00–11:00)
            // - Pause: 30 min (11:00–11:30) erfüllt gesetzliche Pausenpflicht für 8h (>6h: 30 min)
            // - Nachmittag: 5h (300 min) Büro (11:30–16:30)
            val mixedDay = WorkDay(
                id = 1L,
                date = LocalDate.of(2026, 9, 1),
                location = WorkLocation.OFFICE,
                dayType = DayType.WORK,
                timeBlocks = listOf(
                    TimeBlock(
                        id = 1L,
                        workDayId = 1L,
                        startTime = LocalTime.of(8, 0),
                        endTime = LocalTime.of(11, 0),
                        location = WorkLocation.HOME_OFFICE
                    ),
                    TimeBlock(
                        id = 2L,
                        workDayId = 1L,
                        startTime = LocalTime.of(11, 30),
                        endTime = LocalTime.of(16, 30),
                        location = WorkLocation.OFFICE
                    )
                )
            )

            // When: Quotenberechnung mit echtem CalculateDayWorkTimeUseCase ausgeführt wird
            val result = calculateQuota(listOf(mixedDay), defaultSettings, testMonth)

            // Then:
            // 1. Büro-Brutto (300 min) >= HO-Brutto (180 min) -> Tag zählt als Bürotag
            assertThat(result.officeDays).isEqualTo(1)
            assertThat(result.homeOfficeDays).isEqualTo(0)

            // 2. Netto-Minuten (480 min) werden anteilig verteilt:
            //    Büro: 300 * 480 / 480 = 300 min (5h = 5/8)
            //    HO:   180 * 480 / 480 = 180 min (3h = 3/8)
            assertThat(result.officeMinutes).isEqualTo(300L)
            assertThat(result.homeOfficeMinutes).isEqualTo(180L)
        }

        @Test
        @DisplayName("Fall B: Vormittags 5h HO, Nachmittags 3h Büro -> Tag zählt als Home-Office-Tag (HO 5h > Büro 3h)")
        fun `mixed day with more home office time counts as full home office day and distributes net minutes proportionally`() {
            // Given: Ein Arbeitstag mit:
            // - Vormittag: 5h (300 min) Home Office (08:00–13:00)
            // - Pause: 30 min (13:00–13:30)
            // - Nachmittag: 3h (180 min) Büro (13:30–16:30)
            val mixedDay = WorkDay(
                id = 1L,
                date = LocalDate.of(2026, 9, 1),
                location = WorkLocation.HOME_OFFICE,
                dayType = DayType.WORK,
                timeBlocks = listOf(
                    TimeBlock(
                        id = 1L,
                        workDayId = 1L,
                        startTime = LocalTime.of(8, 0),
                        endTime = LocalTime.of(13, 0),
                        location = WorkLocation.HOME_OFFICE
                    ),
                    TimeBlock(
                        id = 2L,
                        workDayId = 1L,
                        startTime = LocalTime.of(13, 30),
                        endTime = LocalTime.of(16, 30),
                        location = WorkLocation.OFFICE
                    )
                )
            )

            // When: Quotenberechnung ausgeführt wird
            val result = calculateQuota(listOf(mixedDay), defaultSettings, testMonth)

            // Then:
            // 1. Büro-Brutto (180 min) < HO-Brutto (300 min) -> Tag zählt als Home-Office-Tag
            assertThat(result.officeDays).isEqualTo(0)
            assertThat(result.homeOfficeDays).isEqualTo(1)

            // 2. Netto-Minuten werden anteilig verteilt:
            //    Büro: 180 * 480 / 480 = 180 min (3h = 3/8)
            //    HO:   300 * 480 / 480 = 300 min (5h = 5/8)
            assertThat(result.officeMinutes).isEqualTo(180L)
            assertThat(result.homeOfficeMinutes).isEqualTo(300L)
        }

        @Test
        @DisplayName("Fall C: Exakter Gleichstand (4h HO, 4h Büro) -> Büro erhält Vorrang (officeDays = 1, homeOfficeDays = 0)")
        fun `mixed day with equal time favors office day`() {
            // Given: 4h HO (08:00–12:00) und 4h Büro (12:30–16:30)
            val mixedDay = WorkDay(
                id = 1L,
                date = LocalDate.of(2026, 9, 1),
                location = WorkLocation.OFFICE,
                dayType = DayType.WORK,
                timeBlocks = listOf(
                    TimeBlock(
                        id = 1L,
                        workDayId = 1L,
                        startTime = LocalTime.of(8, 0),
                        endTime = LocalTime.of(12, 0),
                        location = WorkLocation.HOME_OFFICE
                    ),
                    TimeBlock(
                        id = 2L,
                        workDayId = 1L,
                        startTime = LocalTime.of(12, 30),
                        endTime = LocalTime.of(16, 30),
                        location = WorkLocation.OFFICE
                    )
                )
            )

            // When: Quotenberechnung ausgeführt wird
            val result = calculateQuota(listOf(mixedDay), defaultSettings, testMonth)

            // Then: dayOfficeGross (240) >= dayHomeOfficeGross (240) -> Bürotag
            assertThat(result.officeDays).isEqualTo(1)
            assertThat(result.homeOfficeDays).isEqualTo(0)
            assertThat(result.officeMinutes).isEqualTo(240L)
            assertThat(result.homeOfficeMinutes).isEqualTo(240L)
        }

        @Test
        @DisplayName("Fall D: Misch-Tag mit automatischer Pausenabzugsberechnung verteilt Nettozeit exakt proportional")
        fun `mixed day with automatic break deduction distributes reduced net minutes proportionally`() {
            // Given: Vormittag 3h (180m) HO, Nachmittag 5h (300m) Büro direkt anschließend ohne Pause
            // Brutto = 480m. Gesetzliche Pause (>6h) = 30m -> Netto = 450m
            val mixedDay = WorkDay(
                id = 1L,
                date = LocalDate.of(2026, 9, 1),
                location = WorkLocation.OFFICE,
                dayType = DayType.WORK,
                timeBlocks = listOf(
                    TimeBlock(
                        id = 1L,
                        workDayId = 1L,
                        startTime = LocalTime.of(8, 0),
                        endTime = LocalTime.of(11, 0),
                        location = WorkLocation.HOME_OFFICE
                    ),
                    TimeBlock(
                        id = 2L,
                        workDayId = 1L,
                        startTime = LocalTime.of(11, 0),
                        endTime = LocalTime.of(16, 0),
                        location = WorkLocation.OFFICE
                    )
                )
            )

            // When
            val result = calculateQuota(listOf(mixedDay), defaultSettings, testMonth)

            // Then:
            // Büro-Brutto (300m) >= HO-Brutto (180m) -> 1 Bürotag
            assertThat(result.officeDays).isEqualTo(1)
            assertThat(result.homeOfficeDays).isEqualTo(0)
            // Büro-Netto: 300 * 450 / 480 = 281 min (5/8 von 450 min)
            // HO-Netto:   180 * 450 / 480 = 168 min (3/8 von 450 min)
            assertThat(result.officeMinutes).isEqualTo(281L)
            assertThat(result.homeOfficeMinutes).isEqualTo(168L)
        }
    }

    // =========================================================================
    // Szenario 4: Quoten-Bereinigung durch neutrale Tage (Urlaub, Gleittag, Krankheit)
    // =========================================================================
    @Nested
    @DisplayName("Szenario 4: Quoten-Bereinigung durch neutrale Tage (Urlaub, Gleittag, Krankheit)")
    inner class NeutralDayAdjustmentScenarios {

        @Test
        @DisplayName("Urlaub (DayType VACATION): 5 Tage Urlaub reduzieren Monatssoll von 8520 auf 6390 min -> 40% Quote nicht benachteiligt")
        fun `vacation days reduce monthly target so quota percentage is not penalized`() {
            // Given: Monat mit 20 Arbeitstagen (Soll = 20 * 426 = 8520 min), Ziel: 40% Büro
            // 5 Tage Urlaub (DayType.VACATION) gebucht
            // 6 Tage Büro à 426 min = 2556 min Bürozeit
            val workDays = mutableListOf<WorkDay>()
            // 5 Tage Urlaub
            for (day in 1..5) {
                workDays.add(createNeutralDay(date = LocalDate.of(2026, 9, day), dayType = DayType.VACATION))
            }
            // 6 Tage Büro
            for (day in 6..11) {
                workDays.add(createFullDay(date = LocalDate.of(2026, 9, day), location = WorkLocation.OFFICE, minutes = 426))
            }

            // When: Quotenberechnung ausgeführt wird
            val result = calculateQuota(
                workDays = workDays,
                settings = defaultSettings,
                yearMonth = testMonth
            )

            // Then:
            // Bereinigtes Monatssoll: 8520 min - (5 * 426 min) = 6390 min (15 Tage)
            // Büroanteil: 2556 min / 6390 min * 100 = 40.0%
            // Ohne Urlaubsbereinigung wäre es: 2556 / 8520 * 100 = 30.0% (Quote verfehlt)!
            // Mit Bereinigung ist die 40%-Quote exakt erfüllt:
            assertThat(result.officeMinutes).isEqualTo(2556L)
            assertThat(result.officePercent).isWithin(0.01).of(40.0)
            assertThat(result.percentQuotaMet).isTrue()

            // Urlaubstage zählen weder als Büro- noch als HO-Tage
            assertThat(result.officeDays).isEqualTo(6)
            assertThat(result.homeOfficeDays).isEqualTo(0)
        }

        @Test
        @DisplayName("Krankheit (DayType SICK_DAY): 5 Krankheitstage reduzieren Monatssoll gleichermaßen")
        fun `sick days reduce monthly target identically to vacation`() {
            // Given: 5 Krankheitstage und 6 Bürotage (2556 min Büro)
            val workDays = mutableListOf<WorkDay>()
            for (day in 1..5) {
                workDays.add(createNeutralDay(date = LocalDate.of(2026, 9, day), dayType = DayType.SICK_DAY))
            }
            for (day in 6..11) {
                workDays.add(createFullDay(date = LocalDate.of(2026, 9, day), location = WorkLocation.OFFICE, minutes = 426))
            }

            // When
            val result = calculateQuota(workDays, defaultSettings, testMonth)

            // Then: Bereinigtes Soll = 6390 min -> 2556 / 6390 = 40.0% -> Quote erfüllt
            assertThat(result.officePercent).isWithin(0.01).of(40.0)
            assertThat(result.percentQuotaMet).isTrue()
        }

        @Test
        @DisplayName("Gleittag (DayType FLEX_DAY): Gleittage reduzieren das herangezogene Monatssoll")
        fun `flex days reduce monthly target for quota calculation`() {
            // Given: 3 Gleittage gebucht -> Abzug: 3 * 426 min = 1278 min
            // Bereinigtes Soll = 8520 - 1278 = 7242 min
            // Benötigte Bürozeit für 40%: 7242 * 0.40 = 2896.8 min
            // Wir buchen 2897 min Bürozeit über 7 Tage: 6 Tage à 426 min (2556 min) + 1 Tag à 341 min = 2897 min
            val workDays = mutableListOf<WorkDay>()
            for (day in 1..3) {
                workDays.add(createNeutralDay(date = LocalDate.of(2026, 9, day), dayType = DayType.FLEX_DAY))
            }
            for (day in 4..9) {
                workDays.add(createFullDay(date = LocalDate.of(2026, 9, day), location = WorkLocation.OFFICE, minutes = 426))
            }
            workDays.add(createFullDay(date = LocalDate.of(2026, 9, 10), location = WorkLocation.OFFICE, minutes = 341))

            // When
            val result = calculateQuota(workDays, defaultSettings, testMonth)

            // Then: 2897 / 7242 * 100 = 40.002% >= 40% -> percentQuotaMet == true
            assertThat(result.officePercent).isGreaterThan(40.0)
            assertThat(result.percentQuotaMet).isTrue()
        }

        @Test
        @DisplayName("Sonderurlaub (DayType SPECIAL_VACATION): Reduziert ebenfalls das Monatssoll")
        fun `special vacation reduces monthly target`() {
            // Given: 2 Tage Sonderurlaub
            val workDays = listOf(
                createNeutralDay(date = LocalDate.of(2026, 9, 1), dayType = DayType.SPECIAL_VACATION),
                createNeutralDay(date = LocalDate.of(2026, 9, 2), dayType = DayType.SPECIAL_VACATION)
            )

            // When: 2 * 426 = 852 min Abzug von 8520 = 7668 min Soll
            val result = calculateQuota(workDays, defaultSettings, testMonth)

            // Then: Neutrale Tage erzeugen 0 Arbeitsminuten
            assertThat(result.officeMinutes).isEqualTo(0L)
            assertThat(result.homeOfficeMinutes).isEqualTo(0L)
            assertThat(result.officeDays).isEqualTo(0)
            assertThat(result.officePercent).isEqualTo(0.0)
        }

        @Test
        @DisplayName("Kombination neutraler Tage: Urlaub, Gleittag und Krankheit zusammen gerechnet")
        fun `mixed neutral days are summed up for target deduction`() {
            // Given: 2 Urlaubstage, 2 Krankheitstage, 1 Gleittag = 5 neutrale Tage
            // Bereinigtes Soll = 8520 - (5 * 426) = 6390 min
            val workDays = mutableListOf<WorkDay>()
            workDays.add(createNeutralDay(LocalDate.of(2026, 9, 1), DayType.VACATION))
            workDays.add(createNeutralDay(LocalDate.of(2026, 9, 2), DayType.VACATION))
            workDays.add(createNeutralDay(LocalDate.of(2026, 9, 3), DayType.SICK_DAY))
            workDays.add(createNeutralDay(LocalDate.of(2026, 9, 4), DayType.SICK_DAY))
            workDays.add(createNeutralDay(LocalDate.of(2026, 9, 5), DayType.FLEX_DAY))
            // 6 Bürotage = 2556 min
            for (day in 6..11) {
                workDays.add(createFullDay(date = LocalDate.of(2026, 9, day), location = WorkLocation.OFFICE, minutes = 426))
            }

            // When
            val result = calculateQuota(workDays, defaultSettings, testMonth)

            // Then: 2556 / 6390 * 100 = 40.0% -> percentQuotaMet == true
            assertThat(result.officePercent).isWithin(0.01).of(40.0)
            assertThat(result.percentQuotaMet).isTrue()
            assertThat(result.officeDays).isEqualTo(6)
        }

        @Test
        @DisplayName("Monat komplett mit neutralen Tagen (20 Tage Urlaub) führt zu 0% Quote ohne Fehler")
        fun `entire month with neutral days yields zero target and zero percent without division by zero`() {
            // Given: 20 Urlaubstage (20 * 426 = 8520 min Abzug = 0 min verbleibendes Soll)
            val workDays = (1..20).map { day ->
                createNeutralDay(date = LocalDate.of(2026, 9, day), dayType = DayType.VACATION)
            }

            // When
            val result = calculateQuota(workDays, defaultSettings, testMonth)

            // Then: fixedTarget ist 0 -> officePercent ist 0.0 (kein NaN / Exception)
            assertThat(result.officePercent).isEqualTo(0.0)
            assertThat(result.percentQuotaMet).isFalse()
            assertThat(result.daysQuotaMet).isFalse()
        }

        @Test
        @DisplayName("Neutrale Tage beachten abweichende WorkTimeRule Tagessollzeiten")
        fun `neutral day deduction respects daily target from active WorkTimeRule`() {
            // Given: WorkTimeRule mit 300 min Tagessoll und 6000 min Monatssoll (z.B. Teilzeit)
            val rule = WorkTimeRule(
                id = 1L,
                validFrom = YearMonth.of(2026, 1),
                dailyWorkMinutes = 300,
                monthlyWorkMinutes = 6000
            )
            // 2 Urlaubstage à 300 min = 600 min Abzug -> fixedTarget = 5400 min
            // 40% von 5400 min = 2160 min Bürozeit (4 Tage à 540 min = 2160 min)
            val workDays = listOf(
                createNeutralDay(LocalDate.of(2026, 9, 1), DayType.VACATION),
                createNeutralDay(LocalDate.of(2026, 9, 2), DayType.VACATION)
            ) + (3..6).map {
                createFullDay(LocalDate.of(2026, 9, it), WorkLocation.OFFICE, minutes = 540)
            }

            // When
            val result = calculateQuota(
                workDays = workDays,
                settings = defaultSettings,
                yearMonth = testMonth,
                workTimeRules = listOf(rule)
            )

            // Then: Büroanteil 2160 / 5400 * 100 = 40.0% -> percentQuotaMet == true
            assertThat(result.officePercent).isWithin(0.01).of(40.0)
            assertThat(result.percentQuotaMet).isTrue()
        }
    }

    // =========================================================================
    // Szenario 5: Gesamtstatus (quotaMet ODER-Logik) und September 2026 Realdaten
    // =========================================================================
    @Nested
    @DisplayName("Szenario 5: Gesamtstatus (quotaMet ODER-Logik) und Realdaten-Fixture")
    inner class OverallQuotaStatusAndRealDataScenarios {

        @Test
        @DisplayName("quotaMet ist true wenn Prozentquote erfüllt ist (auch wenn Tagequote verfehlt wird)")
        fun `quotaMet is true when percent quota is met even if days quota fails`() {
            // Given: 7 Bürotage à 580 min = 4060 min bei 10000 min Soll (40.6% >= 40%)
            // Min-Tage = 8 (nur 7 Tage geleistet -> daysQuotaMet == false)
            val settings = defaultSettings.copy(monthlyWorkMinutes = 10000, officeQuotaMinDays = 8)
            val workDays = (1..7).map {
                createFullDay(LocalDate.of(2026, 9, it), WorkLocation.OFFICE, minutes = 580)
            }

            // When
            val result = calculateQuota(workDays, settings, testMonth)

            // Then: percentQuotaMet == true, daysQuotaMet == false, aber quotaMet == true
            assertThat(result.percentQuotaMet).isTrue()
            assertThat(result.daysQuotaMet).isFalse()
            assertThat(result.quotaMet).isTrue()
        }

        @Test
        @DisplayName("quotaMet ist true wenn Tagequote erfüllt ist (auch wenn Prozentquote verfehlt wird)")
        fun `quotaMet is true when days quota is met even if percent quota fails`() {
            // Given: 8 sehr kurze Bürotage (je 120 min = 960 min) bei 8520 min Soll (11.27% < 40%)
            // Tagequote = 8 erreicht (8 >= 8)
            val workDays = (1..8).map { day ->
                createFullDay(LocalDate.of(2026, 9, day), WorkLocation.OFFICE, minutes = 120)
            }

            // When
            val result = calculateQuota(workDays, defaultSettings, testMonth)

            // Then: percentQuotaMet == false, daysQuotaMet == true, aber quotaMet == true
            assertThat(result.percentQuotaMet).isFalse()
            assertThat(result.daysQuotaMet).isTrue()
            assertThat(result.quotaMet).isTrue()
        }

        @Test
        @DisplayName("quotaMet ist false wenn weder Prozent- noch Tagequote erfüllt ist")
        fun `quotaMet is false when both percent and days quota fail`() {
            // Given: 3 Bürotage à 426 min = 1278 min (15% < 40%), Min-Tage 3 < 8
            val workDays = (1..3).map { day ->
                createFullDay(LocalDate.of(2026, 9, day), WorkLocation.OFFICE, minutes = 426)
            }

            // When
            val result = calculateQuota(workDays, defaultSettings, testMonth)

            // Then: Beide Kriterien verfehlt -> quotaMet == false
            assertThat(result.percentQuotaMet).isFalse()
            assertThat(result.daysQuotaMet).isFalse()
            assertThat(result.quotaMet).isFalse()
            assertThat(result.requiredOfficeDaysForQuota).isEqualTo(5) // 8 - 3
        }

        @Test
        @DisplayName("Verifikation mit Realdaten: September 2026 Fixture (7 Bürotage, 15 HO-Tage)")
        fun `verify September 2026 real export dataset quota calculation`() {
            // Given: Die 22 Arbeitstage des realen September 2026 FleX-Exports
            val workDays = September2026ExportFixture.createSeptember2026WorkDays()
            val settings = September2026ExportFixture.createDefaultSettings()

            // When: Quotenberechnung ausgeführt wird
            val result = calculateQuota(
                workDays = workDays,
                settings = settings,
                yearMonth = September2026ExportFixture.YEAR_MONTH
            )

            // Then:
            // 1. Exakt 7 Bürotage und 15 HO-Tage gemäß Fixture-Erwartung
            assertThat(result.officeDays).isEqualTo(September2026ExportFixture.EXPECTED_OFFICE_DAYS)
            assertThat(result.homeOfficeDays).isEqualTo(September2026ExportFixture.EXPECTED_HOME_OFFICE_DAYS)

            // 2. Tagequote verfehlt: 7 von 8 Tagen -> 1 Tag fehlt
            assertThat(result.daysQuotaMet).isFalse()
            assertThat(result.requiredOfficeDaysForQuota).isEqualTo(1)

            // 3. Prozentquote: 3275 min Bürozeit bei 9372 min Soll = 34.94% (< 40%)
            assertThat(result.officePercent).isWithin(0.1).of(34.94)
            assertThat(result.percentQuotaMet).isFalse()

            // 4. Gesamtquote verfehlt
            assertThat(result.quotaMet).isFalse()
        }
    }

    // =========================================================================
    // Szenario 6: Dienstreise / Dienstgang in der Quotenberechnung (Minutengenau ohne 5-Min-Rundung)
    // =========================================================================
    @Nested
    @DisplayName("Szenario 6: Dienstreise / Dienstgang in der Quotenberechnung")
    inner class BusinessTripQuotaScenarios {

        @Test
        @DisplayName("Dienstreise mit ungeraden Zeiten wird für die Büro-Quote minutengenau ohne 5-Minuten-Rundung erfasst")
        fun `business trip with unrounded times counts exact minutes towards office quota without 5 minute rounding`() {
            // Given: Ein Dienstgang am 21.07.2026 von 09:00 bis 17:32 (8:32 brutto = 512 min, 30 min Pause -> 482 min netto = 8:02h)
            // Wenn 5-Min-Rundung fälschlicherweise greifen würde, wäre 17:32 auf 17:35 gerundet worden (515 min brutto, +3 min Differenz).
            val tripDay = WorkDay(
                date = LocalDate.of(2026, 7, 21),
                location = WorkLocation.OFFICE,
                dayType = DayType.BUSINESS_TRIP,
                timeBlocks = listOf(
                    TimeBlock(
                        startTime = LocalTime.of(9, 0),
                        endTime = LocalTime.of(17, 32),
                        location = WorkLocation.OFFICE,
                        isDuration = false
                    )
                )
            )

            // When: Quotenberechnung ausgeführt wird
            val result = calculateQuota(
                workDays = listOf(tripDay),
                settings = defaultSettings,
                yearMonth = YearMonth.of(2026, 7)
            )

            // Then:
            // 1. Zählt als 1 voller Bürotag
            assertThat(result.officeDays).isEqualTo(1)
            assertThat(result.homeOfficeDays).isEqualTo(0)

            // 2. Bürozeit beträgt exakt 482 Minuten (8:02h) und NICHT 485 Minuten (keine Rundung auf 17:35)
            assertThat(result.officeMinutes).isEqualTo(482L)
            assertThat(result.homeOfficeMinutes).isEqualTo(0L)
        }
    }

    // =========================================================================
    // Hilfsfunktionen
    // =========================================================================

    private fun createFullDay(
        date: LocalDate,
        location: WorkLocation,
        minutes: Long
    ): WorkDay {
        val start = LocalTime.of(8, 0)
        return WorkDay(
            id = date.toEpochDay(),
            date = date,
            location = location,
            dayType = DayType.WORK,
            timeBlocks = listOf(
                TimeBlock(
                    id = date.toEpochDay(),
                    workDayId = date.toEpochDay(),
                    startTime = start,
                    endTime = start.plusMinutes(minutes),
                    location = location,
                    isDuration = true
                )
            )
        )
    }

    private fun createNeutralDay(
        date: LocalDate,
        dayType: DayType
    ): WorkDay {
        return WorkDay(
            id = date.toEpochDay(),
            date = date,
            location = WorkLocation.OFFICE,
            dayType = dayType,
            timeBlocks = emptyList()
        )
    }
}
