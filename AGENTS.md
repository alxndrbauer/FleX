# Agent Instructions for FleX

## Task Delegation Strategy

Für komplexe Aufgaben wird folgende Strategie verwendet:

### 1️⃣ Pro-Modell (Planung & Review)
**Wann:** Komplexe Probleme, Architektur-Entscheidungen, Code-Review
- Codebase analysieren
- Implementierungs-Plan erstellen
- Subagents für Teilaufgaben einsetzen
- Ergebnisse reviewen und korrigieren

### 2️⃣ Standard-Modell / Subagents (Implementierung mittlerer Komplexität)
**Wann:** Klare Anforderungen, mehrere zusammenhängende Änderungen, >10 Zeilen Code
- UI-Komponenten erweitern
- Repository/DAO Änderungen
- Use Cases implementieren
- Tests schreiben

### 3️⃣ Leichtes Modell (Einfache Tasks)
**Wann:** Trivial, klar abgegrenzt, <10 Zeilen Code
- Einzelne Felder hinzufügen
- Simple Bug-Fixes
- Textänderungen
- Dependency-Updates
- Triviales Refactoring

## Workflow

### 1. Plan erstellen
Bei komplexen Aufgaben immer erst einen Plan (`/plan`) erstellen, bevor Code geschrieben wird.

### 2. Implementierung
- TDD: Tests zuerst schreiben, dann implementieren
- Code-Review nach größeren Änderungen
- Subagents (via `invoke_subagent` oder `define_subagent`) nutzen, um Teilaufgaben parallel bearbeiten zu lassen.

### 3. Git & Commit
- `git add` + `git commit` → selbst ausführen
- Conventional Commits verwenden (siehe unten)
- `git push` → **niemals ausführen** – der User pusht immer selbst

### 4. Verifikation
- Unit Tests: `./gradlew testDebugUnitTest`
- Android Tests kompilieren: `./gradlew compileDebugAndroidTestKotlin`
- Debug Build: `./gradlew assembleDebug`

## Projektspezifisch

### Tech Stack
- **Language:** Kotlin 2.3.10
- **UI:** Jetpack Compose + Material 3
- **Architecture:** MVVM + Clean Architecture
- **Database:** Room 2.8.4
- **DI:** Hilt 2.57.1
- **Build:** Gradle 9.4.0, AGP 9.1.0
- **Version Catalog:** `gradle/libs.versions.toml`

### Key Patterns
- **UI Layer:** `*Screen.kt` (Composables) + `*ViewModel.kt` (StateFlow, Hilt)
- **Domain Layer:** `model/` (data classes) + `usecase/` (Injected, call-only)
- **Data Layer:** `entity/` (Room) → `repository/` (impl + mapping)
- **DI:** `di/AppModule.kt` mit Migrations + Singletons

### Build & Release
- Debug: `./gradlew assembleDebug`
- Release: `./gradlew assembleRelease` (signiert mit Keystore)
- Beide Builds müssen erfolgreich sein vor Merge
- GitHub Actions: Auto-Build & Release auf Push zu main

### Important Files
- `gradle/libs.versions.toml` - Version Catalog (alle Dependency-Versionen zentral)
- `app/build.gradle.kts` - App-Dependencies, Signing Config
- `app/src/main/java/com/flex/data/local/FlexDatabase.kt` - DB-Version & Migrations
- `.github/workflows/build.yml` - Release Workflow
- `.github/workflows/pr-check.yml` - PR Validation
- `.github/dependabot.yml` - Auto-Updates (monthly)
- `gradle.properties` - Headless Mode für KSP

## Commit-Konvention

Format:
```
<type>(<scope>): <subject>

<description>
```

**Types:** `feat`, `fix`, `refactor`, `chore`, `ci`, `docs`
**Scopes:** `domain`, `data`, `ui`, `deps`, `build`, `ci`, `test`

Beispiele:
```
feat(domain): Add workDays per week to WorkTimeRule

Allows configuring which weekdays count as working days.
4-day weeks (e.g. Mon–Thu) are now supported.
```

```
fix(ui): Delete button now appears in month view dialog
```

## Versioning

Mit jeder Änderung soll die Version der App gemäß semver angepasst werden.

## Regeln

✅ **DO**
- Komplexe Tasks in Subtasks aufteilen und via Subagents delegieren
- TDD: Tests vor der Implementierung schreiben
- Tests für neue Features schreiben
- Code-Review nach größeren Änderungen
- Descriptive Commit Messages (Conventional Commits)
- Gradle Tasks dürfen ohne Nachfragen ausgeführt werden
- **Niemals `git push` ausführen** – der User pusht immer selbst
- Bei neuen Room-Feldern: immer Migration hinzufügen + DB-Version erhöhen
- Bei Konstruktor-Änderungen: alle Aufrufe in androidTest prüfen

❌ **DON'T**
- Force-Push zu main
- Unsigned Releases pushen
- `.env`, `*.jks`, `local.properties` committen
- Deprecation-Warnungen ignorieren (fix oder dokumentieren)
- Multiple simultane Builds (concurrency aktiv)

## Debugging

### Build-Fehler
- Headless-Mode: `JAVA_TOOL_OPTIONS=-Djava.awt.headless=true`
- Gradle Cache: `rm -rf .gradle`
- Android SDK: `ANDROID_HOME=/Users/abauer/Library/Android/sdk`

### Runtime-Fehler
- Room Migrations: `FlexDatabase.kt` – Version prüfen, alle Migrations registriert?
- Hilt: Alle Custom Classes müssen `@Inject` oder im `AppModule` sein
- Compose: `@Composable` und State-Management prüfen
- Android Tests: Konstruktor-Änderungen in `androidTest/` nachziehen

## Skills
- Bei der Arbeit mit dem System auf mitgelieferte Skills und bereitgestellte Plugin-Fähigkeiten (wie `android-cli`) zurückgreifen, falls zutreffend.

## Kontakt & Fragen

Bei Fragen zur Architektur oder unklaren Anforderungen → **Plan schreiben** vor Implementierung (`/plan`)!
