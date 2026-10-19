# Mood Sampler

Android app for mood/time tracking via **random experience sampling** (ESM).
The phone prompts you at random moments during waking hours; each prompt is a
~15-second capture (0–10 valence + energy taps, a 0–10 physical-pain rating,
what/where/with-whom quick-picks — several activities at once, grouped under
headers for fast scanning — and an optional note). Optionally, each answer
records one location fix and pre-selects your place from where you answered
before, and a short Bluetooth scan pre-selects who you're with from the devices
that were around last time. Over weeks this builds a statistically representative picture of your
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
  exact-alarm permission (plus optional location and nearby devices), and sets the sampling day; nothing is scheduled until
  it finishes
- Follows the phone's time zone: day start/end are local times, and the
  upcoming schedule is redrawn automatically when the zone changes (travel)
- The UI never reveals upcoming prompt times — anticipation biases sampling
- Insights dashboard: mood and pain over time, valence by pain level, and a
  valence / energy / pain toggle for the time-of-day and
  activity/place/company breakdowns
- History tab: every prompt by day, note/tag search, edit or delete an answer
  (deleting marks the prompt CANCELLED — excluded from response rate, not missed)
- Optional automatic location: with the location permission granted and the
  Settings switch on, the capture screen takes **one foreground fix** while it
  is open (platform `LocationManager`, no Play Services, no background
  tracking) and stores lat/lon/accuracy on the sample. Past answers within
  ~150 m (wider when the fix is imprecise) vote for a place, and the winner is
  pre-selected — so at familiar spots the place needs zero taps. Skip/snooze
  from the notification record nothing; editing an old answer keeps its fix
- Optional company suggestion: with the nearby-devices permission
  (`BLUETOOTH_SCAN`, flagged `neverForLocation`) and the Settings switch on,
  the capture screen runs a **4-second BLE scan** while it is open. Devices
  within roughly a room (RSSI ≥ −85 dBm, strongest 40) are stored on the sample
  as salted 64-bit hashes of their address and advertised name, never the raw
  values; addresses that rotate (most phones) are skipped on Android 15+.
  A device seen with ≥ 2 past answers, ≥ 60 % of them with one company option
  and clearly above that option's usual share, votes for it; the winner is
  pre-selected if the user hasn't picked one. Own earbuds and other ever-present
  devices therefore say nothing. With nothing informative nearby, nothing is
  pre-selected. The company row always shows every option
- Simpler place picking: the row shows only the suggested place, the most-used
  places and the current selection; **More…** reveals the full list. With no
  fix nothing is pre-selected (a stale guess would bias the data)
- Activities are grouped (Work & study, Chores & errands, Social, Leisure &
  media, Health & body, Getting around, Rest, Other) both on the capture screen
  and in Settings; each activity's group is editable, user-added ones start in
  Other
- Editable activity/place/company lists: rename, regroup, reorder, archive and
  restore, with per-item usage counts; 28 default activities and 16 default
  places
- CSV export via the share sheet; full JSON backup and restore; "Delete all
  data" (answers and prompts only — categories and settings stay) behind a
  confirmation that states the counts

## Building

Requires [pixi](https://pixi.sh) (it supplies JDK 21) and an Android SDK at
`~/Android/Sdk` (exported as `ANDROID_HOME` on pixi activation). Never run
`./gradlew` outside pixi. First-time setup on a fresh machine:

```sh
sdkmanager --sdk_root=$HOME/Android/Sdk "platform-tools" "platforms;android-35" "build-tools;35.0.0"
yes | sdkmanager --sdk_root=$HOME/Android/Sdk --licenses
pixi install          # one-time: fetches the JDK (and gradle for the wrapper bootstrap)
pixi run bootstrap    # only if gradle/wrapper is missing
```

Day-to-day (pixi tasks wrap `./gradlew`):

```sh
pixi run build            # assembleRelease (signed if keystore.properties exists, else debug-signed)
pixi run install          # build + install release on the connected phone
pixi run run              # install release + launch
pixi run build-debug / install-debug / run-debug   # debug variant (.debug appId)
pixi run test             # JVM unit tests (schedule generator, CSV format)
pixi run lint             # Android lint
pixi run connected-test   # instrumented Room DAO tests (device required)
pixi run alarms           # inspect armed alarms via dumpsys
pixi run logs             # filtered logcat
```

The debug build id is `dev.russell.moodsampler.debug`, so it never
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
   including "Pain over time" and "Valence by pain level"; toggle the
   breakdown between Valence / Energy / Pain.
   Settings → Export CSV → share to yourself and eyeball the 16 columns.
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
    dev.russell.moodsampler.debug` → `pixi run run-debug` → onboarding appears
    (no Home flash), grant notifications, **Start sampling** → Settings →
    **Restore from file…** → confirm → History/Insights match, editing an old
    entry works, `pixi run alarms` shows one re-armed prompt alarm.
14. Install over a v1 database (real data): Settings lists the new
    activities/places with "Other" last, old samples keep their activity, and
    onboarding is skipped.
15. Install over a v2 database: Settings → Activities shows group headers with
    the defaults sorted into them and any custom activity under "Other"; old
    samples are intact. Tap an activity → change its group → the capture
    screen regroups it.
16. Settings → "Record location with each answer" on → **Allow location** →
    fire a test prompt → the "Where are you?" caption reads "Finding
    location…" then "📍 Location captured (±N m)"; pick "Home", Save. Fire
    another prompt at the same spot → "Home" is pre-selected and the caption
    says "suggested: Home"; **More…** shows the full place list. CSV export
    has lat/lon/accuracy in the last three columns.
17. Switch the setting off (or deny the permission) → the next prompt shows no
    caption, nothing is pre-selected, saving works, CSV lat/lon are empty.
18. Settings → "Suggest company from nearby devices" on → **Allow nearby
    devices** → fire a test prompt → the "With" row reads "checking nearby
    devices…" for a few seconds and shows every option without a tap. Answer a
    few prompts with a Bluetooth device (watch, earbuds, speaker) close by and
    the same company, plus a couple with a different company away from it →
    the next prompt near that device pre-selects that company. With Bluetooth off the caption
    says "Bluetooth off" and nothing is pre-selected.

## Data

Room DB `moodsampler.db`, schema v8 (`app/schemas/` holds the exported JSON
for every version; migrations are explicit — there is no destructive fallback):

- `scheduled_prompt` — one row per scheduled prompt, status PENDING → NOTIFIED →
  ANSWERED/MISSED, or CANCELLED (settings change, or the user deleted the
  answer). `skipReason` is set when a MISSED prompt was declined on purpose.
- `sample` — the answers: `valence` and `arousal` (energy) as integers 0–10
  (v6; v5 used 1–10 and v4 and earlier −1..1 slider values, both converted on migration), `pain` 0–10
  (null for samples recorded before v2), place and company ids, note, and
  (v3) `latitude`/`longitude`/`accuracyM`/`fixAtEpochMs` — all null when
  location was off, denied or unavailable. `photoPath` (v4) is the file name
  of an optional photo in private app storage.
- `sample_activity` — many-to-many sample ↔ activity (a sample can tag several).
- `sample_device` (v8) — salted hashes of the Bluetooth devices nearby while a
  sample was answered; trains company suggestions. Not in the CSV.
- `category` — editable activity/place/company lists; archived, never deleted,
  so history keeps its labels. `groupKey` (v3) is the `ActivityGroup` of an
  activity. The v1→v2 migration back-fills the new default labels for existing
  installs, skipping any the user already has; v2→v3 assigns groups to the
  default activity labels and puts everything else in `OTHER`. v6→v7 archives
  the default "Other" activity (the capture form's "Other…" chip adds a named
  option instead); samples already tagged with it keep it. v7→v8 adds the
  empty `sample_device` table.

CSV columns: `scheduled_at_iso, answered_at_iso, status, skip_reason, latency_s,
valence, arousal, pain, activities, location, social, note, latitude,
longitude, accuracy_m, photo` (`photo` is the stored file name) — missed prompts appear as rows with empty measures;
several activities are joined with `; `.

JSON backup (`Settings → Back up to file…`) contains every table plus the
sampling settings (including the auto-location and company-scan switches and
the device-hash salt, so suggestions keep working after a restore) with ids preserved;
restore replaces all local data and redraws the schedule. Pre-v3 backups
restore fine: default activities get their groups, coordinates stay empty.
Photos are referenced by file name only, so a restore on the same phone keeps
them; restored elsewhere, those answers have no photo.
