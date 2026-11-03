# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Mood Sampler (`dev.russell.moodsampler`): an Android app (Kotlin, Jetpack Compose / Material 3, single `app` module) for random **experience sampling**. It prompts the user at random moments during waking hours and records a quick mood/activity capture. `README.md` has the full feature list, the data model, and an 18-step manual verification checklist to run on a phone.

## Commands

pixi tasks wrap `./gradlew` (the wrapper pins Gradle 8.11.1; pixi supplies JDK 21). Run `pixi task list` to see them all.

```sh
pixi run test             # JVM unit tests (./gradlew testDebugUnitTest)
pixi run lint             # ./gradlew lintDebug
pixi run build-debug      # assembleDebug; `build` = assembleRelease (minified, signed only if keystore.properties exists, else debug-signed)
pixi run run-debug        # install debug on the connected adb device and launch it
pixi run connected-test   # instrumented tests (Room migrations, backup import); needs a device
pixi run alarms           # dumpsys the armed AlarmManager alarms
pixi run logs             # logcat filtered to tag "MoodSampler"
```

Single test: `./gradlew testDebugUnitTest --tests 'dev.russell.moodsampler.scheduling.ScheduleGeneratorTest'` (append `.methodName` to run one method). For an instrumented test: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.russell.moodsampler.data.MigrationTest`.

The debug build uses the appId `dev.russell.moodsampler.debug`, so it installs alongside the release build. The debug build also puts "fire test prompt in 10 s" and "seed 30 days of fake data" buttons on Home (`BuildConfig.DEBUG`).

## Releases

Every push to `main` that passes `.github/workflows/ci.yml` (the same checks as `pixi run check`) publishes a signed APK as a GitHub release tagged `v<versionName>`. Obtainium on the phone installs it. Pushes that touch only docs don't release. `versionCode` is the commit count and `versionName` is `<base>.<count>`: bump the base in `app/build.gradle.kts` by hand, never hard-code `versionCode`, and never rewrite `main`'s history. CI signs with the keystore in the repo secrets, which must stay the key the installed app was signed with (the workflow's `EXPECTED_CERT_SHA256` enforces this). The shared change → emulator → push loop is in `~/Projects/Android-Apps/CLAUDE.md`.

## Architecture

- **Manual DI**: `AppGraph` builds every repository and service once, in `MoodSamplerApp`. Code reaches it through `context.appGraph`. There is no Hilt or Koin; to add a service, wire it in `AppGraph`.
- **UI**: `ui/<feature>/` pairs each `*Screen.kt` with a `*ViewModel.kt`. Navigation lives in `ui/AppNav.kt`. The capture form (`ui/capture/CaptureForm.kt`) is shared by new captures and History edits (`EditSampleScreen`).
- **Design**: a fixed "warm journal" look, set in `ui/theme/Theme.kt` (paper/ink palettes, Fraunces + Figtree) with shared pieces in `ui/components/Journal.kt`. Build screens from those rather than stock `Card`, `FilterChip` or segmented buttons. Measure colors and the per-value ramps live in `MoodChartColors`; re-run a CVD check if you change the chart hues. The app is edge-to-edge: `AppNav` pads only the bottom, so each top-level screen applies `statusBarsPadding()` itself. The UI uses `moodWash` to tint a page by valence, and on the capture form only after the valence is picked, so it can't lead the answer.
- **Scheduling** (`scheduling/`) is the core of the app:
  - `ScheduleGenerator` is pure. It splits the waking span into N equal windows and draws one uniform-random time in each. Unit tests cover it.
  - `PromptScheduler` handles AlarmManager orchestration. Only the **next** PENDING prompt has an alarm, plus one expiry alarm per live notification. Every entry point (app launch, alarm, boot, time/zone change, settings change) funnels into the idempotent, mutex-guarded `ensureScheduled()`, which keeps about 2 days of rows in `scheduled_prompt`. There is no WorkManager and no background job.
  - `AlarmReceiver` dispatches PROMPT / EXPIRE / SNOOZE / RESHOW / SKIP intents to the scheduler. `BootReceiver` re-arms after a reboot.
  - Prompt lifecycle: PENDING → NOTIFIED → ANSWERED / MISSED, or CANCELLED. Expired and skipped prompts become MISSED, and skips also store a `skipReason`. A deleted answer becomes CANCELLED.
- **Sampling days are not calendar days.** If the end time is earlier than the start time, the day runs past midnight. Use `SamplingConfig.samplingDayFor` whenever you group by day (stats, charts, CSV, History).
- **Time zones**: always use `DeviceClock`, which re-reads the system zone on every call, never `Clock.systemDefaultZone()`. Prompts are stored as instants. When the zone changes, `ensureScheduled` redraws the future schedule.
- **Never show upcoming prompt times in the UI.** Anticipation biases the sampling.
- **Data** (`data/`): Room (`data/db/`), DataStore settings (`data/settings/`), `CsvExporter`, a JSON backup/restore (`data/backup/`), `PhotoStore` (photos live in `filesDir`; the sample stores only the file name), and a one-shot foreground location fix plus `PlaceMatcher`, which suggests a place from nearby past answers, and (`data/bluetooth/`) a few-second BLE scan whose salted device hashes feed `CompanyMatcher`, which suggests company the same way. `OnboardingGate` stops any scheduling until onboarding finishes.
- **Claude capture assist** (`data/claude/`): `CaptureAssistant` sends the narration (the sample's `note`), photo and option labels to the Anthropic API through the official Java SDK (`ClaudeClient`, structured output) and maps the reply onto category ids, adding new options via `CategoryRepository.findOrAdd`. Prompt text, schema and reply cleanup are pure in `AssistPrompt` (unit-tested). Claude never sets valence, energy or the pain number. The API key lives in its own DataStore (`ClaudeKeyStore`), excluded from the JSON backup and from Android backup (`res/xml/data_extraction_rules.xml`).

## Database changes

- The schema version is `AppDatabase.VERSION`, currently 9. Keep the README's "Data" section in step with it.
- Migrations live in `data/db/Migrations.kt`. They are explicit, and there is **no destructive fallback**. A new migration must be registered in `AppDatabase.build`'s `addMigrations(...)`.
- KSP exports schemas to `app/schemas/`. Commit the new `N.json`, because `MigrationTest` also uses these files as fixtures. Add a `MigrationTest` case for each migration.
- A schema or default-category change usually also needs updates to:
  - `BackupModels`: DTOs and the `schemaVersion` handling for older backups
  - `CsvExporter`, plus the README's CSV column list and the column count in checklist step 7
  - `DebugSeeder`
