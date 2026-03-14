# FleX – Time Tracking App

<div align="center">
  <img src="flex-logo.png" alt="FleX Logo">
</div>

Eine Android-App zur Erfassung von Arbeitszeiten mit Fokus auf Büro/Home-Office-Quoten, Gleitzeit und Überstundenverwaltung.

## Features

### 📊 Arbeitszeit-Tracking
- Erfassung von Arbeitszeiten pro Tag (Start/Ende oder Gesamtdauer)
- Unterscheidung zwischen Büro und Home-Office
- Automatische Berechnung der Netto-Arbeitszeit
- Notizen zu jedem Arbeitstag

### 🏢 Büro-Quote Management
- Konfigurierbares Soll-Quoten-Verhältnis (Standard: 40%)
- Mindestanzahl Büro-Tage pro Monat
- Zeitraum-basierte Quote-Regeln (mit Gültigkeitsdatum)
- Prognose für laufende Monate

### ⏱️ Gleitzeit & Überstunden
- Automatische Berechnung des Gleitzeit-Saldos
- **Samstags-Bonus**: 50% Aufschlag auf Arbeitszeit
  - Tatsächliche Arbeitszeit → Gleitzeit
  - 50% Bonus → Separates Überstundenkonto
- Konfigurierbarer Anfangssaldo für beide Konten
- Tagesweise Gleitzeit-Ansicht

### 🗓️ Urlaub & Spezialregelungen
- Jahresurlaub mit Resturlaub aus Vorjahr
- Sonderurlaub (verfällt 31. Oktober)
- Gleittage (voller Tag Abzug)
- Feiertage (Hamburg mit Gauss-Osteralgorithmus)
- Geplante vs. tatsächliche Tage

### 🔒 Datenverwaltung
- Lokale SQLite-Datenbank
- JSON-Export/Import für Backups
- Automatische Sicherungen via WorkManager (max. 5 lokale Backups)

### 📅 UI
- **Home**: Heute auf einen Blick (Arbeitszeit, Tagestyp, Quote)
- **Monats-Ansicht**: Kalender + Eintragsübersicht mit Edit/Delete
- **Planungs-Modus**: Zukünftige Tage vorkonfigurieren
- **Quoten-Dashboard**: Monatliche & jährliche Übersichten
- **Einstellungen**: Arbeitszeiten, Quoten, Urlaub, Backups

## Tech Stack

- **Language**: Kotlin 2.2.10
- **UI**: Jetpack Compose + Material 3
- **Architecture**: MVVM + Clean Architecture
- **Database**: Room 2.7.1
- **DI**: Hilt 2.56.2
- **Build**: Gradle 9.2.1, AGP 9.0.1

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

## Entwicklung

### Struktur
```
app/src/main/java/com/vrema/
├── domain/          # Business Logic & Models
│   ├── model/       # Data classes
│   ├── repository/  # Interfaces
│   └── usecase/     # Use cases
├── data/            # Repository implementations
│   ├── local/       # Room entities & DAOs
│   ├── repository/  # Repository impl
│   └── backup/      # Backup logic
├── ui/              # Compose screens & ViewModels
└── di/              # Hilt dependency injection
```

### Key Models
- `WorkDay`: Ein Arbeitstag mit Typ, Ort, Zeitblöcke, Notizen
- `TimeBlock`: Start/End oder Dauer für einen Arbeitstag
- `Settings`: Arbeitszeitsoll, Quoten, Urlaub, Anfangssaldi
- `FlextimeBalance`: Gleitzeit + Überstunden mit Saldo-Berechnung
- `QuotaStatus`: Monatliche Quote (Stunden & Tage)

## Lizenz

Siehe [LICENSE](LICENSE)
