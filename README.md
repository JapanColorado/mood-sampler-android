# Mood Sampler

Android app for mood/time tracking via **random experience sampling** (ESM).
The phone prompts you at random moments during waking hours; each prompt is a
~15-second capture (mood valence + energy sliders, a 0–10 physical-pain rating,
what/where/with-whom quick-picks — several activities at once — and an optional
note). Over weeks this builds a statistically representative picture of your
mood and time use — unlike diaries, which oversample memorable moments.

- Kotlin + Jetpack Compose (Material 3), single module, manual DI
- minSdk 31 (Android 12+), target/compile SDK 35
- Stratified random schedule: waking hours split into N equal windows, one
  uniform-random prompt per window (N and hours configurable)
- Night-owl friendly: an end time earlier than the start time runs past midnight
  (09:00–01:00 is a 16-hour day), and those small-hours prompts stay grouped with
  the day that started them everywhere — stats, charts, and export
- Prompts expire (default 20 min) and are recorded as **missed** so response
  rates stay honest; expired prompts can't be answered late
- Notification actions: **Remind me in 5 min** (only while enough time is left;
  never extends the window) and **Skip**. The capture screen's "Can't answer
  now" records a skip *reason* (busy, driving, sleeping, not safe, other) —
  skips still count as missed
- First-run onboarding explains the method, asks for notification and
  exact-alarm permission, and sets the sampling day; nothing is scheduled until
  it finishes
- Follows the phone's time zone: day start/end are local times, and the
  upcoming schedule is redrawn automatically when the zone changes (travel)
- The UI never reveals upcoming prompt times — anticipation biases sampling
- Insights dashboard: mood and pain over time, pleasantness by pain level, and a
  pleasantness / energy / pain toggle for the time-of-day and
  activity/place/company breakdowns
- History tab: every prompt by day, note/tag search, edit or delete an answer
  (deleting marks the prompt CANCELLED — excluded from response rate, not missed)
- Editable activity/place/company lists: rename, reorder, archive and restore,
  with per-item usage counts; 28 default activities and 16 default places
- CSV export via the share sheet; full JSON backup and restore; "Delete all
  data" (answers and prompts only — categories and settings stay) behind a
  confirmation that states the counts

## Building

Requires Java 17+ and an Android SDK at the path in `local.properties`
(`sdk.dir=…`; not committed). First-time setup on a fresh machine:

```sh
sdkmanager --sdk_root=$HOME/Android/sdk "platform-tools" "platforms;android-35" "build-tools;35.0.0"
yes | sdkmanager --sdk_root=$HOME/Android/sdk --licenses
echo "sdk.dir=$HOME/Android/sdk" > local.properties
pixi install          # provides gradle for the one-time wrapper bootstrap
pixi run bootstrap    # only if gradle/wrapper is missing
```

Day-to-day (pixi tasks wrap `./gradlew`):

```sh
pixi run build            # assembleDebug
pixi run test             # JVM unit tests (schedule generator, CSV format)
pixi run install          # install debug build on the connected phone
pixi run connected-test   # instrumented Room DAO tests (device required)
pixi run run              # install + launch
pixi run alarms           # inspect armed alarms via dumpsys
pixi run logs             # filtered logcat
```

The debug build id is `com.russellwhite.moodsampler.debug`, so it never
collides with a release install.

## Manual verification checklist (phone over USB)

1. `pixi run run` — app opens; grant the notification permission.
2. Home → **Debug: fire test prompt in 10 s**, lock the phone → notification
   appears on the lock screen; tapping it opens the capture screen; saving
   bumps today's answered count.
3. Fire another test prompt and ignore it for the expiry duration → Home shows
   it as missed, and opening it from a stale notification shows the
   "expired" screen.
4. `pixi run alarms` — one `RTC_WAKEUP` alarm armed for the next prompt.
5. `adb reboot`, unlock, wait ~1 min → `pixi run alarms` shows the alarm
   re-armed (BootReceiver).
6. `adb shell dumpsys deviceidle force-idle` then fire a test prompt →
   still arrives (exact-and-allow-while-idle); `adb shell dumpsys deviceidle unforce`.
7. Home → **Debug: seed 30 days of fake data** → Insights shows all charts,
   including "Pain over time" and "Pleasantness by pain level"; toggle the
   breakdown between Pleasantness / Energy / Pain (the axis rescales to 0–10).
   Settings → Export CSV → share to yourself and eyeball the 12 columns.
8. Settings → change prompts/day → `pixi run alarms` shows a re-armed schedule.
9. Fire a test prompt → the notification offers **Skip** and **Remind me in
   5 min**; snooze → `pixi run alarms` shows the re-show alarm next to the
   original expiry; after 5 min it re-posts with the remaining time. Set expiry
   to 5 min → no snooze action. Skip → Home missed +1.
10. Fire a test prompt → open it → **Can't answer now** → Busy → History shows
    "Skipped · Busy"; CSV `skip_reason` = `busy`.
11. History → search a note → tap a row → change the pain rating → Update →
    row updates. Delete → confirm → row gone, Home response rate unchanged.
12. Settings → tap a category chip → usage count, ▲▼ reorder, Archive →
    "Archived (1)" → Restore.
13. Settings → **Back up to file…** → `adb shell pm clear
    com.russellwhite.moodsampler.debug` → `pixi run run` → onboarding appears
    (no Home flash), grant notifications, **Start sampling** → Settings →
    **Restore from file…** → confirm → History/Insights match, editing an old
    entry works, `pixi run alarms` shows one re-armed prompt alarm.
14. Install over a v1 database (real data): Settings lists the new
    activities/places with "Other" last, old samples keep their activity, and
    onboarding is skipped.

## Data

Room DB `moodsampler.db`, schema v2 (`app/schemas/` holds the exported JSON
for every version; migrations are explicit — there is no destructive fallback):

- `scheduled_prompt` — one row per scheduled prompt, status PENDING → NOTIFIED →
  ANSWERED/MISSED, or CANCELLED (settings change, or the user deleted the
  answer). `skipReason` is set when a MISSED prompt was declined on purpose.
- `sample` — the answers: `valence` and `arousal` in −1..1, `pain` 0–10
  (null for samples recorded before v2), place and company ids, note.
- `sample_activity` — many-to-many sample ↔ activity (a sample can tag several).
- `category` — editable activity/place/company lists; archived, never deleted,
  so history keeps its labels. The v1→v2 migration back-fills the new default
  labels for existing installs, skipping any the user already has.

CSV columns: `scheduled_at_iso, answered_at_iso, status, skip_reason, latency_s,
valence, arousal, pain, activities, location, social, note` — missed prompts
appear as rows with empty measures; several activities are joined with `; `.

JSON backup (`Settings → Back up to file…`) contains every table plus the
sampling settings with ids preserved; restore replaces all local data and
redraws the schedule.
