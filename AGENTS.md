# Claude Code Instructions for FleX

## Task Delegation Strategy

Für komplexe Aufgaben wird folgende Strategie verwendet:

### 1️⃣ Opus (Planung & Review)
**Wann:** Komplexe Probleme, Architektur-Entscheidungen, Code-Review
- Codebase analysieren
- Implementierungs-Plan erstellen
- TODOs mit Model-Assignments erstellen
- Ergebnisse reviewen und korrigieren

### 2️⃣ Sonnet (Implementierung mittlerer Komplexität)
**Wann:** Klare Anforderungen, mehrere zusammenhängende Änderungen, >10 Zeilen Code
- Model-UI Komponenten erweitern
- Repository/DAO Änderungen
- Use Cases implementieren
- Tests schreiben

### 3️⃣ Haiku (Einfache Tasks)
**Wann:** Trivial, klar abgegrenzt, <10 Zeilen Code
- Einzelne Felder hinzufügen
- Simple Bug-Fixes
- Textänderungen
- Dependency-Updates
- Triviale Refactoring

## Workflow

### 1. Plan erstellen (Opus)
```
📋 [Plan]
- Task 1: Beschreibung [Model: Sonnet]
- Task 2: Beschreibung [Model: Haiku]
- Task 3: Beschreibung [Model: Opus]
```

### 2. TODOs erstellen
```bash
TaskCreate mit status: "pending"
TaskUpdate mit model assignments in metadata
```

### 3. An Modelle delegieren
Delegation erfolgt über den `model` Parameter des Task-Tools:
```bash
Task(subagent_type: "general-purpose", model: "sonnet", prompt: "...")
Task(subagent_type: "general-purpose", model: "haiku", prompt: "...")
```
**Wichtig:** `subagent_type` ist immer `"general-purpose"`, das Modell wird über `model` gewählt.

### 4. Git & Commit
- `git add` + `git commit` → selbst ausführen, aber vorher **Skill `conventional-commit` aufrufen** für die Commit Message
- `git push` → **niemals ausführen** – der User pusht immer selbst

### 5. Review & Merge
Opus prüft alle Ergebnisse

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
- `.github/workflows/build.yml` - Release Workflow
- `.github/workflows/pr-check.yml` - PR Validation
- `.github/dependabot.yml` - Auto-Updates (monthly)
- `gradle.properties` - Headless Mode für KSP

## Commit-Konvention

Format:
```
<type>(<scope>): <subject>

<description>

Co-Authored-By: Claude <Model> <noreply@anthropic.com>
```

**Types:** `feat`, `fix`, `refactor`, `chore`, `ci`, `docs`
**Scopes:** `domain`, `data`, `ui`, `deps`, `build`, `ci`

Beispiele:
```
feat(domain): Add overtime calculation to FlextimeBalance

Add separate overtime tracking independent from flextime.

Co-Authored-By: Claude Sonnet <noreply@anthreply.com>
```

```
fix(ui): Delete button now appears in month view dialog

Co-Authored-By: Claude Haiku <noreply@anthreply.com>
```

## Versioning

Mit jeder Änderung soll die Version der App gemäß semver angepasst werden.
## Regeln

✅ **DO**
- Komplexe Tasks in Subtasks aufteilen
- Model-Delegation bei >3 Subtasks nutzen
- Tests für neue Features schreiben
- Code-Review vor Merge
- Descriptive Commit Messages
- Du darfst gradle Tasks ohne nachfragen ausführen
- **Niemals `git push` ausführen** – der User pusht immer selbst
- **Vor jedem Commit den Skill `conventional-commit` aufrufen**

❌ **DON'T**
- Force-Push zu main
- Unsigned Releases pushen
- `.env`, `*.jks`, `local.properties` committen
- Deprecation-Warnungen ignorieren (fix oder dokumentieren)
- Multiple simultanee Builds (concurrency aktiv)

## Debugging

### Build-Fehler
- Headless-Mode: `JAVA_TOOL_OPTIONS=-Djava.awt.headless=true`
- Gradle Cache: `rm -rf .gradle`
- Android SDK: `ANDROID_HOME=/Users/abauer/Library/Android/sdk`

### Runtime-Fehler

- Room Migrations: Check `FlexDatabase.kt` version
- Hilt: Alle Custom Classes müssen `@Inject` oder im `AppModule`
- Compose: `@Composable` und State-Management prüfen

## Skills

Vor jeder passenden Aufgabe den entsprechenden Skill aufrufen – **nicht warten bis der User fragt**:

| Skill | Wann aufrufen |
|---|---|
| `mobile-android-design` | Vor jeder Compose UI Aufgabe – Layout, Komponenten, Material 3 Design |
| `android-kotlin` | Coroutines, Hilt, MockK, Compose State, Room – alles Kotlin/Android-spezifisch |
| `context7-mcp` | Immer wenn eine Library/Framework-API genutzt wird (Room, Hilt, Compose, etc.) |
| `conventional-commit` | Beim Erstellen von Commit Messages |
| `simplify` | Nach größeren Implementierungen – Code auf Qualität und Redundanz prüfen |
| `claude-api` | Wenn Anthropic SDK / Claude API verwendet wird |
| `update-config` | Für Änderungen an Claude Code Settings, Hooks, Permissions |
| `keybindings-help` | Für Keyboard-Shortcut-Anpassungen |
| `loop` | Wenn eine Aufgabe wiederkehrend auf Intervall laufen soll |

## Kontakt & Fragen

Bei Fragen zur Architektur oder unklaren Anforderungen → **Plan schreiben** vor Implementierung!
