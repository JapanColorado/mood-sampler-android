# Mood Sampler

Android app for mood/time tracking via **random experience sampling** (ESM).
The phone prompts you at random moments during waking hours; each prompt is a
~15-second capture (mood valence + energy sliders, what/where/with-whom
quick-picks, optional note). Over weeks this builds a statistically
representative picture of your mood and time use — unlike diaries, which
oversample memorable moments.

- Kotlin + Jetpack Compose (Material 3), single module, manual DI
- minSdk 31 (Android 12+), target/compile SDK 35
- Stratified random schedule: waking hours split into N equal windows, one
  uniform-random prompt per window (N and hours configurable)
- Night-owl friendly: an end time earlier than the start time runs past midnight
  (09:00–01:00 is a 16-hour day), and those small-hours prompts stay grouped with
  the day that started them everywhere — stats, charts, and export
- Prompts expire (default 20 min) and are recorded as **missed** so response
  rates stay honest; expired prompts can't be answered late
- Follows the phone's time zone: day start/end are local times, and the
  upcoming schedule is redrawn automatically when the zone changes (travel)
- The UI never reveals upcoming prompt times — anticipation biases sampling
- Insights dashboard (mood over time, by time-of-day, by activity/place/company),
  CSV export via the share sheet

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
7. Home → **Debug: seed 30 days of fake data** → Insights shows all charts;
   Settings → Export CSV → share to yourself and eyeball the file.
8. Settings → change prompts/day → `pixi run alarms` shows a re-armed schedule.

## Data

Room DB `moodsampler.db`: `scheduled_prompt` (one row per scheduled prompt,
status PENDING → NOTIFIED → ANSWERED/MISSED, or CANCELLED on settings change),
`sample` (the answers), `category` (editable activity/place/company lists;
archived, never deleted, so history keeps its labels). CSV export includes
missed prompts as empty rows.
