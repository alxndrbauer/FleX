# Implementierungsplan: Dashboard-Erweiterung (Features 1–5)

## 1. Übersicht & Ziel
Das neue Dashboard wird um 5 wertvolle Informationsmodule erweitert:

1. **🎯 Hochrechnung & Quoten-Prognose (Forecast):**
   - Quoten-Pacing: Konkrete Handlungsempfehlung (z. B. *„Noch 4 Büro-Tage an 12 offenen Tagen nötig (alle 3 Tage)“* oder *„Quote bereits erfüllt! 🎉“*).
   - Gleitzeit-Monatsprognose: Hochrechnung des Monats-Gleitzeitstands bei aktuellem Schnitt am Monatsende.
2. **📅 Nächste Termine & Abwesenheiten („Coming Up“):**
   - Zeigt die nächsten bevorstehenden Termine ab heute an: Nächster geplanter Urlaub (als Zeitraum), nächster Gleittag, nächste Dienstreise, nächster Feiertag.
3. **⏱️ Arbeitszeit-Gewohnheiten & Durchschnitte (Work Habits):**
   - Ø Arbeitsbeginn (z. B. 07:45 Uhr), Ø Feierabend (z. B. 16:30 Uhr), Ø tägliche Nettoarbeitszeit (z. B. 8h 12m), Ø Pause (z. B. 42 min).
4. **🏖️ Vollständiges Jahres-Abwesenheits- & Budget-Widget:**
   - Übersicht aller Abwesenheitstypen: Urlaub, Sonderurlaub, Gleittage (`FLEX_DAY`), Kranktage, Dienstreisen (`BUSINESS_TRIP`).
5. **⚡ Aktuelle Woche (Kompakt-Status):**
   - Wochenarbeitszeit bisher vs. Wochensoll (z. B. 24h / 38.5h in KW 42) mit Fortschrittsbalken und Standorten (*2 Büro | 1 Home-Office*).

---

## 2. Architektur & Datenmodelle

### Domain-Modelle (`com.flex.domain.model.DashboardMetrics.kt`)
```kotlin
data class DashboardForecast(
    val quotaPacingText: String,
    val isQuotaMet: Boolean,
    val projectedFlextimeText: String?
)

data class UpcomingEvent(
    val date: LocalDate,
    val endDate: LocalDate? = null,
    val title: String,
    val daysCount: Int = 1,
    val eventType: UpcomingEventType
)

enum class UpcomingEventType {
    VACATION,
    FLEX_DAY,
    BUSINESS_TRIP,
    PUBLIC_HOLIDAY
}

data class WorkHabits(
    val avgStartTime: LocalTime?,
    val avgEndTime: LocalTime?,
    val avgNetMinutes: Long,
    val avgBreakMinutes: Long
)

data class CurrentWeekStatus(
    val weekNumber: Int,
    val workedMinutes: Long,
    val targetMinutes: Long,
    val officeDays: Int,
    val homeOfficeDays: Int
)

data class AbsenceSummary(
    val vacationDays: Int,
    val specialVacationDays: Int,
    val flexDays: Int,
    val sickDays: Int,
    val businessTripDays: Int
)
```

### Domain Use Case: `CalculateDashboardMetricsUseCase`
Berechnet die 5 Metriken unabhängig von der UI, 100% testbar mit Pure-Kotlin Unit Tests.

---

## 3. Arbeitspakete & TDD-Vorgehen

### 🤖 Schritt 1: Domain-Modelle & Use Case (TDD)
- Datei `app/src/main/java/com/flex/domain/model/DashboardMetrics.kt` anlegen.
- Testdatei `app/src/test/java/com/flex/domain/usecase/CalculateDashboardMetricsUseCaseTest.kt` anlegen.
- Use Case `app/src/main/java/com/flex/domain/usecase/CalculateDashboardMetricsUseCase.kt` implementieren.
- Tests ausführen und grünstellen.

### 🤖 Schritt 2: ViewModel-Integration
- `CalculateDashboardMetricsUseCase` in `DashboardViewModel` injecten.
- `DashboardUiState` um die neuen Metriken erweitern.
- Unit Tests in `DashboardViewModelTest.kt` anpassen und erweitern.

### 🤖 Schritt 3: UI-Komponenten & DashboardScreen
- Neue wiederverwendbare / modulare Karten in `DashboardScreen.kt`:
  - `ForecastQuotaCard`: Quoten-Pacing & Gleitzeit-Monatsprognose
  - `CurrentWeekCard`: Wochenstunden, Soll, Standorte
  - `UpcomingEventsCard`: Bevorstehende Abwesenheiten & Feiertage
  - `WorkHabitsCard`: Durchschnittszeiten (Start, Ende, Netto, Pause)
  - `AbsenceOverviewRow`: Farblich akzentuierte Abwesenheits-Kacheln
- Responsives Layout mit Material 3 Design.

### 🤖 Schritt 4: Verifikation & Emulator-Prüfung
- `./gradlew testDebugUnitTest`
- `./gradlew compileDebugAndroidTestKotlin`
- `./gradlew assembleDebug`
- Auf Emulator installieren und alle 5 Features visuell validieren.
