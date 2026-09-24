# Changelog
## [Unreleased]

### Bugfixes

- Disable application backup in app manifest (Alert #16)
- Disable application backup in wear manifest (Alert #17)
- Refactor work time sum in YearOverviewViewModel (Alert #33)
- Replace deprecated PreferenceManager in GeofenceMapPreview (Alert #26)
- Use fillPaint and outlinePaint in GeofenceMapPreview (Alerts #27, #28, #29)
- Use modern Geocoder API on Android 13+ in SettingsViewModel (Alert #30)
- Use ConnectivityManager on Android 10+ in SettingsViewModel (Alert #31)
- Gate getConnectedSsidLegacy to Android versions below Q (Alert #32)
- Download APK directly into internal private storage (Alert #18)
- Resolve compiler deprecations and packaging warnings
- Prevent timer reset to 0 on app reopen and update immediately on resume

### Features

- Add Quick Settings tile, app shortcuts and adaptive layout
- Toggle time block location via long click on home screen
- Add pause button, live pause mode and collapse shade on actions
- Automatically book planned days when their date is reached
- Bump minSdk to 33 (Android 13) and clean up legacy fallbacks
- Live flextime balances and adjusted time booking in home view
- Configurable default start time for planned and manual days
- Add warning for overlapping time blocks in home, month and export views

### Verbesserungen

- Remove Wear OS companion app and module
## [1.7.5] - 2026-08-09

### Bugfixes

- Use WorkTimeRule/QuotaRule correctly in MonthViewModel quota calculation
- Apply WorkTimeRule-aware targets in QuotaViewModel
- Use selected month's QuotaRule in HomeViewModel, not today's
## [1.7.3] - 2026-08-01

### Features

- Add flextime UI indicators and fix monthly target calculation
## [1.7.2] - 2026-08-01

### Bugfixes

- WorkDays auch in Planungsfüllung und Büro-Quote berücksichtigen
- Android-Integrationstests an aktuelle Codebase anpassen
## [1.7.1] - 2026-07-31

### Bugfixes

- Dynamic resolution of monthly work time targets per period

### Features

- Arbeitstage pro woche konfigurierbar (4-Tage-Woche)

### Verbesserungen

- Monatsgenaue arbeitszeit-zeiträume (YearMonth)
## [1.7.0] - 2026-07-30

### Bugfixes

- Stabilize geofence and WiFi auto-stamp against spurious clock events
- Replace DWELL with ENTER trigger for reliable clock-in
- Add ENTER to transition types so DWELL fires correctly
- Handle both ENTER and DWELL for clock-in
- Revert to ENTER-only trigger for clock-in
- Restore ENTER+DWELL clock-in with ENTER registered

### Features

- Add public holidays API with persistent Room cache
- Add week comparison
- Live work timer notification with Android 16 Live Updates
- Fix bugs and add interactive map preview
- Warn when enabled features lose permissions
- Flexible arbeitszeit-zeiträume mit tagesgenauer gültigkeit
## [1.5.8] - 2026-04-06

### Bugfixes

- Prevent phantom clock-outs from stale state
- Resolve all compiler warnings
- Remove unnecessary non-null assertions in deleteTimeBlock
- Rename FLEX_DAY calendar label to "Gleittag"
- Actually suppress reminders via CalendarContract.Reminders
- Change noAlarm default to off, improve UI hint

### Features

- Add calendar sync and ICS export
- Merge consecutive vacation days into multi-day calendar events
## [1.5.6] - 2026-04-02

### Bugfixes

- Make remainingWorkDays test time-independent
- Add WhatsNewPreferences mock to HomeViewModelTest

### Features

- Add Geleistet column and rename month summary card
## [1.5.5] - 2026-03-25

### Features

- FleX time tracking app v1.5.0
- Add in-app update checker with automatic APK download

