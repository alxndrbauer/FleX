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

### ⌚ Wear OS Companion App
- **App**: 2 wischbare Screens:
  - Heute: Tageszeit, Stempel-Status, Ein-/Ausstempeln-Button
  - Flexzeit & Überstunden Saldo (farbcodiert)
- **Tiles** (Kacheln in Wear OS):
  - *FleX*: Tageszeit + Ein-/Ausstempeln-Knopf
  - *Flexzeit*: Gleitzeit- und Überstunden-Saldo
  - *Büroquote*: Bürotage-Fortschritt und Quote-Status
- Echtzeit-Synchronisation mit der Phone-App via Wearable Data Layer
- Einstempeln von der Uhr funktioniert auch am Wochenende

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
- **Wear OS**: Wear Compose 1.4.1, Wear Tiles 1.4.1 + Protolayout 1.2.1

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

## Deployment (lokal)

Das `deploy.sh` Skript baut und installiert APKs direkt auf verbundene Geräte via ADB.

### Voraussetzungen

1. `.env` anlegen (aus `.env.example` kopieren):
   ```bash
   cp .env.example .env
   ```
2. `.env` befüllen:
   ```env
   KEYSTORE_FILE=/absoluter/pfad/android.jks
   KEYSTORE_PASSWORD=...
   KEY_ALIAS=...
   KEY_PASSWORD=...
   PHONE_DEVICE=<ADB Geräte-ID des Phones>   # z.B. R3CN...  oder IP:Port
   WATCH_DEVICE=<IP:Port der Pixel Watch>     # z.B. 192.168.1.42:5555
   ANDROID_HOME=/Users/dein-name/Library/Android/sdk
   JAVA_TOOL_OPTIONS=-Djava.awt.headless=true
   ```

### Verwendung

```bash
./deploy.sh        # Baut und installiert App + Wear (Standard)
./deploy.sh app    # Nur Phone App
./deploy.sh wear   # Nur Wear App
```

### Wear OS Gerät verbinden

ADB-Debugging auf der Uhr aktivieren, dann entweder via Android Studio verbinden (empfohlen) oder manuell:
```bash
adb connect 192.168.x.x:5555
```

## CI/CD

GitHub Actions baut automatisch bei jedem Push auf `main`:
- ✅ Unit Tests
- 📦 Release-Build (Phone + Wear) mit Keystore-Signing
- 🏷️ GitHub Release mit `flex-vX.Y.Z.apk` und `flex-wear-vX.Y.Z.apk`
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
├── wearable/        # Wear OS Sync (WearSyncHelper, WearDataService)
└── di/              # Hilt dependency injection

wear/src/main/java/com/flex/wear/
├── WearMainActivity.kt    # Entry point, 2 wischbare Screens
├── QuotaScreen.kt         # Screen 1: Heute + Stempeln
├── FlextimeScreen.kt      # Screen 2: Flexzeit & Überstunden
├── FlexTileService.kt     # Tile: Heute + Stempeln
├── FlextimeTileService.kt # Tile: Flexzeit & Überstunden
├── QuotaTileService.kt    # Tile: Büroquote
├── WearListenerService.kt # Data Layer Listener → Tiles refresh
├── TileActionActivity.kt  # Bridge für Tile-Tap-Aktionen
├── WearContract.kt        # Message-Pfade & Data-Keys
└── WearStatus.kt          # Data-Model + DataItem Extensions
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
