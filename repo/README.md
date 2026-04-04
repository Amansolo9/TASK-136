# Task-136 Offline Operations Suite

This repository contains a Kotlin Multiplatform app with Android Views UI (`composeApp`) and shared domain/data logic (`shared`).

## Prerequisites

- JDK 17+
- Android SDK (set `ANDROID_HOME` or `sdk.dir` in `local.properties`)
- Android platform tools (`adb`) for install/run
- Docker (optional, for containerized build/test path)

## Project Modules

- `:shared` - KMP shared logic (Room entities/DAO, RBAC/ABAC, ViewModels, workflows, tests)
- `:composeApp` - Android app module (Activity, Fragments, layouts, manifest)

Declared in `settings.gradle.kts`.

## Bootstrap and Startup

1. Build shared tests/artifacts:

```bash
./gradlew :shared:testDebugUnitTest
```

2. Build Android debug app:

```bash
./gradlew :composeApp:assembleDebug
```

3. Install on device/emulator:

```bash
adb install -r composeApp/build/outputs/apk/debug/composeApp-debug.apk
```

4. Launch app entry point:

- Activity: `com.eaglepoint.task136.MainActivity`
- Manifest: `composeApp/src/androidMain/AndroidManifest.xml`

Windows PowerShell equivalents:

```powershell
.\gradlew.bat :shared:testDebugUnitTest
.\gradlew.bat :composeApp:assembleDebug
adb install -r composeApp\build\outputs\apk\debug\composeApp-debug.apk
```

## Test Commands

- Preferred local path (non-Docker):

```bash
./run_tests.sh
```

`run_tests.sh` first tries local Gradle wrappers, then falls back to Docker if wrappers are unavailable.

- Direct Gradle:

```bash
./gradlew :shared:testDebugUnitTest
```

## Demo/Bootstrap Accounts

In debug builds, demo users are seeded at first launch from `LocalAuthService`:

- `admin` / `Admin1234!`
- `supervisor` / `Super1234!`
- `operator` / `Oper12345!`
- `viewer` / `Viewer1234!`
- `companion` / `Companion1!` (delegated to operator)

## Static Verification Playbook

Use this checklist for review without emulator instrumentation:

1. **Build graph and module wiring**
   - Confirm `settings.gradle.kts` includes only `:shared` and `:composeApp`
   - Confirm app entry is `MainActivity` in manifest

2. **Security controls**
   - Verify session expiry hooks in `MainActivity`
   - Verify attendee read/write ABAC checks in `MeetingWorkflowViewModel`
   - Verify object-level DAO filters (`OrderDao`, `CartDao`, `MeetingDao`)

3. **Required Android Views flows**
   - Verify fragment navigation for Calendar, Meeting Detail, Invoice Detail
   - Confirm corresponding XML layouts exist under `composeApp/src/androidMain/res/layout`

4. **Finance and receipts**
   - Verify refund path uses `OrderStateMachine` transition success checks
   - Verify receipt sharing uses `FileProvider` and chooser intent on Android

5. **Delegation attribution**
   - Confirm owner-vs-actor context propagation in order/cart/meeting flows
   - Confirm companion operations can execute on behalf of delegated owner

6. **Unit tests**
   - Run `./run_tests.sh`
   - If SDK path missing, configure `ANDROID_HOME` or `local.properties` and rerun
