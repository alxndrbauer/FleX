# Changelog
## [Unreleased]

### Bugfixes

- Stabilize geofence and WiFi auto-stamp against spurious clock events

### Features

- Add public holidays API with persistent Room cache
- Add week comparison
- Live work timer notification with Android 16 Live Updates
- Fix bugs and add interactive map preview
- Warn when enabled features lose permissions
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

