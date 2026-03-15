# FleX – Time Tracking App

<div align="center">
  <img src="flex-logo.png" alt="FleX Logo">
</div>

Eine Android-App zur Erfassung von Arbeitszeiten mit Fokus auf Büro/Home-Office-Quoten, Gleitzeit und Überstundenverwaltung.

## Features

### 📊 Arbeitszeit-Tracking
- Erfassung von Arbeitszeiten pro Tag (Start/Ende oder Gesamtdauer)
- Ein-/Ausstempeln per Knopfdruck oder manuelle Eingabe
- Unterscheidung zwischen Büro und Home-Office
- Automatische Berechnung der Netto-Arbeitszeit (inkl. Pausenabzug)
- Notizen zu jedem Arbeitstag

### 📍 Automatisches Stempeln (Geofencing)
- Automatisch einstempeln beim Betreten des Büros
- Automatisch ausstempeln beim Verlassen
- Bürostandort per Adresse (Geocoding) oder aktuellem GPS-Standort konfigurieren
- Benachrichtigung mit „Rückgängig"-Aktion bei automatischem Einstempeln
- Opt-in, erfordert Standortberechtigung „Immer erlauben"

### 🏢 Büro-Quote Management
- Konfigurierbares Soll-Quoten-Verhältnis (Standard: 40%)
- Mindestanzahl Büro-Tage pro Monat
- Zeitraum-basierte Quoten-Regeln (mit Gültigkeitsdatum)
- Live-Fortschrittsanzeige (Stunden, Prozent, Tage)

### ⏱️ Gleitzeit & Überstunden
- Automatische Berechnung des Gleitzeit-Saldos
- Separates Überstundenkonto
- **Samstags-Bonus**: Arbeitszeit → Gleitzeit + 50% Bonus auf Überstundenkonto
- **Überstundentag**: Freier Tag auf Kosten des Überstundenkontos (Gleitzeit neutral)
- **Gleittag**: Freier Tag auf Kosten des Gleitzeit-Kontos
- Konfigurierbarer Anfangssaldo für beide Konten

### 🗓️ Urlaub & Spezialregelungen
- Jahresurlaub mit Resturlaub aus Vorjahr
- Sonderurlaub (verfällt 31. Oktober)
- Kranktage
- Feiertage (Hamburg mit Gauß-Osteralgorithmus)
- Geplante vs. tatsächliche Tage

### 📈 Analytics
- Gleitzeit-Verlauf als Zeitreihen-Chart (Monat / Jahr / Gesamt)
- Überstunden-Verlauf als Chart
- Wöchentliche Arbeitsstunden
- Monatliche Arbeitsstunden
- Büro/Home-Office-Verteilung

### 🗂️ Ansichten
- **Home**: Heute auf einen Blick (Einstempeln, Tagestyp, Gleitzeit, Quote)
- **Monatsansicht**: Kalenderübersicht mit Edit/Delete pro Tag
- **Planung**: Zukünftige Tage vorkonfigurieren
- **Jahresübersicht**: Alle Tage des Jahres auf einen Blick
- **Analytics**: Charts und Auswertungen

### 🔒 Datenverwaltung
- Lokale SQLite-Datenbank (kein Cloud-Zwang)
- JSON-Export/Import für Backups
- Automatische Sicherungen via WorkManager (max. 5 lokale Backups)
- PDF-Export

### 🎨 Design
- Material 3
- Dark Mode / Light Mode / System-Modus umschaltbar

## Tech Stack

- **Language**: Kotlin 2.3.10
- **UI**: Jetpack Compose + Material 3
- **Architecture**: MVVM + Clean Architecture
- **Database**: Room 2.8.4
- **DI**: Hilt 2.59.2
- **Location**: Google Play Services Location 21.3.0
- **Charts**: Vico 3.0.3
- **Build**: Gradle 9.4.0, AGP 9.1.0

## Installation

### Build lokal
```bash
ANDROID_HOME=~/Library/Android/sdk ./gradlew assembleDebug
```

### Release-Build (signiert)
```bash
ANDROID_HOME=~/Library/Android/sdk ./gradlew assembleRelease
```

Erfordert Keystore + Environment-Variablen:
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`

## CI/CD

GitHub Actions baut automatisch bei jedem Push auf `main`:
- ✅ Debug-Build für schnelle Tests
- 📦 Release-Build mit Keystore-Signing
- 🏷️ GitHub Release mit signierter APK
- ⚠️ Release wird nur erstellt wenn Build erfolgreich

Releases: https://github.com/alxndrbauer/flexs/releases

## Database Migrations

| Version | Änderung |
|---------|----------|
| 1 | Initial schema |
| 2 | Quota rules table |
| 3 | Time blocks `isDuration` flag |
| 4 | Settings `monthlyWorkMinutes` |
| 5 | Settings `initialOvertimeMinutes` |
| 6 | Time blocks `location` field |
| 7 | Settings: Geofence-Felder (enabled, lat, lon, radius) |
| 8 | Settings: `geofenceAddress` |

## Entwicklung

### Struktur
```
app/src/main/java/com/flex/
├── domain/          # Business Logic & Models
│   ├── model/       # Data classes
│   ├── repository/  # Interfaces
│   └── usecase/     # Use cases
├── data/            # Repository implementations
│   ├── local/       # Room entities & DAOs
│   ├── repository/  # Repository impl
│   └── backup/      # Backup logic
├── geofence/        # Geofencing (Receiver, Manager, Notifications)
├── ui/              # Compose screens & ViewModels
└── di/              # Hilt dependency injection
```

### Key Models
- `WorkDay`: Ein Arbeitstag mit Typ, Ort, Zeitblöcken, Notizen
- `TimeBlock`: Start/End oder Dauer für einen Arbeitstag
- `Settings`: Arbeitszeitsoll, Quoten, Urlaub, Anfangssaldi, Geofence-Konfiguration
- `FlextimeBalance`: Gleitzeit + Überstunden mit Saldo-Berechnung
- `QuotaStatus`: Monatliche Quote (Stunden & Tage)
- `DayType`: WORK, VACATION, SPECIAL_VACATION, FLEX_DAY, SATURDAY_BONUS, SICK_DAY, OVERTIME_DAY

## Lizenz

Siehe [LICENSE](LICENSE)
