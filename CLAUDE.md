# CLAUDE.md — SIREN

**SIREN (Seismic Integrated Response and Emergency Notification)** is an IoT
earthquake detection system built around an ESP32 and an **MPU6050** accelerometer,
with a Kotlin Multiplatform companion app (Practical Research 2, City of Bogo Senior
High School). The device detects local shaking, raises an on-site alarm, and writes
an alert to Firestore that the app turns into a full-screen warning. Students confirm
"I'm Safe" or "I Need Help"; teachers and guardians watch a live roll call.

The project **won its competition**, and the work now underway is a second phase of
features on top of a system that already works end to end. See **Next phase** below
before starting anything new.

This repo holds **both** halves: the app (`shared/`, `app/`, `iosApp/`) and the
firmware (`firmware/siren_esp32/`).

### Repo layout

The repo was seeded from a zip export, so everything used to sit one level down under
`Research-main/`. It was flattened on 25 Sep 2026: `gradlew`, the module roots and this
file now live at the repo root, and every path in this file is relative to it. A clone
made before that date still has the nested layout until it pulls; an old working copy
with uncommitted changes under `Research-main/` should commit or stash them first, and
git's rename detection carries them across on the pull.

---

## Current state

| Module | Status |
|---|---|
| `:shared` | ✅ Compose Multiplatform library. All UI, models and the data layer. `compileCommonMainKotlinMetadata` and `compileAndroidMain` both pass. |
| `:app` | ⚠️ Thin Android host (3 files). Builds only where `app/google-services.json` has been restored — see **Secrets**. |
| `iosApp/` | ⚠️ Swift sources + Podfile written, **never compiled**. Needs a Mac — see below. |
| `firmware/` | ✅ **v3.0-mpu6050**, committed 23 Sep 2026, matching the board. The v2.0 ADXL335 sketch — and the optional SIM800L GSM-SMS fallback that only it carried — stay in history at `fcff24a`. See **Firmware**. |
| Shipped APK | `dist/` holds **v2.9.2** debug and release. The next build is **v3.0.0** — see **Shipping an APK**. |

```powershell
$env:JAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot"   # per-machine
$env:ANDROID_HOME="$env:LOCALAPPDATA\Android\Sdk"
$env:Path="$env:JAVA_HOME\bin;$env:Path"
cd <repo root>          # wherever this clone lives

.\gradlew.bat :app:assembleDebug                    # Android debug APK
.\gradlew.bat :app:assembleRelease                  # signed release APK
.\gradlew.bat :shared:compileCommonMainKotlinMetadata   # type-checks common code for BOTH platforms
.\gradlew.bat :shared:compileAndroidMain               # type-checks shared/src/androidMain
```

**The `JAVA_HOME` path above is that machine's, not a requirement.** Any JDK 17 works —
the 23 Sep 2026 debug build was made with Azul Zulu 17.0.18 at
`C:\Program Files\Zulu\zulu-17`. Check what a machine actually has with
`(Get-Command java).Source` before assuming the toolchain is missing. `compileSdk 37`
resolved without a manual `sdkmanager` step on a machine whose newest installed
platform was `android-36.1` and which had no `cmdline-tools` at all, so try the build
before working through the SDK bootstrap below.

The two `:shared` tasks are the fastest way to verify shared code without a Mac —
and, more usefully, **without `google-services.json`**. Only `:app` applies the
Google Services plugin, so the whole shared module (which is all the UI, the data
layer and both platform implementations) compiles on a clone that has no Firebase
credentials at all. `:app` cannot: `processDebugGoogleServices` runs ahead of every
compile task, so even `:app:compileDebugKotlin` fails with "File google-services.json
is missing" before a single line is compiled.

### Setting up the SDK from scratch — two traps

Both cost a cycle:

- **The command-line tools at the well-known `commandlinetools-win-*_latest.zip` URL
  are revision 12.0 and cannot resolve API 37 packages at all.** `sdkmanager --list`
  happily shows them; `sdkmanager "platforms;android-37.0"` fails with a bare
  "Failed to find package". Bootstrap first with
  `sdkmanager "cmdline-tools;latest"`, which lands revision 22.0 in
  `cmdline-tools/latest-2`, then use *that* binary for everything else.
- **API 37 has minor versions.** The package is `platforms;android-37.0` — plain
  `platforms;android-37` does not exist. `37.1` and `37.2-beta` are also published.
  `build-tools;37.0.0` keeps the old three-part form.

Gradle will also pull `build-tools;36.0.0` in on its own; that is expected, not a
misconfiguration.

---

## Architecture

```
shared/src/commonMain/     everything cross-platform
  ui/App.kt                auth gate + back stack + role-based bottom nav
  ui/screens/              16 screens
  ui/theme/                colours, Inter typography, spacing
  ui/components/           shared widgets, Haptics facade
  data/SirenRepository     auth, Firestore, settings  (GitLive Firebase KMP)
  model/Models.kt          enums + data classes
  platform/Platform.kt     PlatformServices interface + installer
  util/                    DateFmt (expect), Format helpers
  composeResources/        28 ic_sg_* pictograms, 5 Inter weights, vectors

shared/src/androidMain/    AndroidPlatformServices, DateFmt actual, BackHandler actual
shared/src/iosMain/        IosPlatformServices, DateFmt actual, MainViewController
app/                       SirenApp, MainActivity, SirenMessagingService + launcher res
iosApp/                    Swift host (unbuilt)
```

Platform differences go through **`PlatformServices`** (an interface, installed at
start-up), not expect/actual — vibration, notifications, dial/SMS, settings storage,
FCM topic, wall-clock. Only `DateFmt` and `PlatformBackHandler` use expect/actual,
because they need per-platform *compile-time* bindings.

`App()` is the single entry point: `MainActivity.setContent { App() }` on Android,
`MainViewController()` on iOS.

## Tech stack

- **Kotlin 2.4.10 · Compose Multiplatform 1.11.1 · AGP 9.3.1 · Gradle 9.6.1 · JDK 17**
- **compileSdk 37 · minSdk 24 · targetSdk 35 · iOS 15+**
- **applicationId** `com.research.siren` (must match Firebase) · **namespace** `com.siren.mobile`
- **Firebase** via **GitLive KMP SDK 2.5.0** (auth + firestore); Cloud Messaging stays
  platform-specific because GitLive does not wrap it
- **State** — `StateFlow` on a single `SirenRepository` object. No DI, no ViewModels;
  screens are pure composables fed from `App()`
- **Offline** — Firestore's on-device cache queues writes and replays on reconnect

---

## Hard-won constraints — do not rediscover these

Each of these cost a debugging cycle:

- **AGP 9 rejects `org.jetbrains.kotlin.android`.** Kotlin is built in. The Compose
  *compiler* plugin is still applied separately.
- **AGP 9 rejects `com.android.application` + `org.jetbrains.kotlin.multiplatform`.**
  That is why shared code is a library (`com.android.kotlin.multiplatform.library`)
  with `:app` as a separate host, instead of one `composeApp` module.
- **Declare KMP/Compose plugin versions only in the root `build.gradle.kts`** with
  `apply false`; submodules apply them by bare id. Repeating a version in a submodule
  fails with *"already on the classpath with an unknown version."*
- **AGP 9's KMP-library plugin does NOT package Compose resources.** `shared.aar`
  ships with **zero** asset entries and only iOS gets assembled resources. `:app`
  works around this with `CopyComposeResourcesTask` + `variant.sources.assets
  .addGeneratedSourceDirectory`, which puts them at
  `assets/composeResources/com.siren.mobile.resources/`. **Without that the app
  compiles but every icon and font fails at runtime.** Verify after changing
  resources:
  ```powershell
  Add-Type -AssemblyName System.IO.Compression.FileSystem
  $apk = Resolve-Path .\app\build\outputs\apk\debug\app-debug.apk
  $z = [IO.Compression.ZipFile]::OpenRead($apk)
  @($z.Entries | ? { $_.FullName -like '*composeResources*' -and $_.Name -like 'ic_sg_*' }).Count  # 28
  @($z.Entries | ? { $_.FullName -like '*composeResources*' -and $_.Name -like '*.ttf' }).Count    # 5
  $z.Dispose()
  ```
  Verified against the 23 Sep 2026 debug build: 28 and 5.
- **AGP 9 forbids `Provider`s in the SourceSet API** (`assets.srcDir(provider)`), and
  `addGeneratedSourceDirectory` requires a task exposing a `DirectoryProperty` —
  a plain `Copy` task will not do.
- **`material-icons-extended` was discontinued for Multiplatform after 1.7.3.** The
  app uses ~40 extended icons, so 1.7.3 is pinned deliberately against CMP 1.11.1.
  It resolves and type-checks on both platforms.
- **`iosX64` is omitted** (Intel-Mac simulator) — some dependencies no longer publish
  that variant. Apple Silicon uses `iosSimulatorArm64`.
- **CMP has no `androidx.compose.ui.backhandler`** in 1.11.1 — hence
  `PlatformBackHandler` expect/actual.
- **CMP's `Font()` is `@Composable`**, so typography cannot be a top-level `val` —
  hence `interFamily()` / `sirenTypography()`.
- **`androidx.fragment` must be pinned to 1.8.9.** Firebase drags in 1.1.0
  transitively and `lintVitalRelease` then fails `registerForActivityResult`. This
  only breaks **release** builds.
- **Auto-mirrored icons need their own import** —
  `androidx.compose.material.icons.automirrored.filled.ArrowBack`.
- **No `String.format`/`SimpleDateFormat` in common code.** Use `Double.toFixed/asG/
  asGSpaced` and `DateFmt`.
- **Resource shrinking silently deleted the alarm audio from the release build.**
  `isShrinkResources = true` could not see `R.raw.siren_alarm` as reachable, because the
  only reference is passed into `AndroidPlatformServices` and stashed in a static on the
  alarm service. Debug played fine; release shipped with **zero** `res/raw` entries and
  a completely silent alarm. Related: `raiseAlarmVolume` records `priorAlarmVolume` only when
  it is null, or a Yellow alert followed by a Red one captures the 60% floor as the user's
  own setting and "restores" them to it, ratcheting their alarm volume up for good. `app/src/main/res/raw/keep.xml` pins it, and
  `isShrinkResources` is now `false` besides. A passing build proves nothing here — check
  the artifact.
- **Do not check for the alarm audio by looking for `res/raw/siren_alarm.mp3` in a release
  APK.** AGP's resource *path shortening* renames it — in 2.8.0 it lands as `res/dQ.mp3`,
  and the release APK legitimately has **no `res/raw` entries at all**. Listing paths
  therefore reports a silent alarm on a perfectly good build. Ask the resource table
  instead, which resolves the logical name to whatever the file was renamed to:
  ```powershell
  aapt2 dump resources <apk> | Select-String -Context 0,1 "raw/siren_alarm"
  # raw/siren_alarm -> res/dQ.mp3   (139,695 bytes, same as debug)
  ```
  Path shortening does not apply to debug builds, which is why the by-name check works
  there and quietly misleads in release — the one build where it matters.
- **Android 14+ restricts `USE_FULL_SCREEN_INTENT`** to calling/alarm apps, and denies it
  by default. If it is denied, the full-screen alert silently never appears — leaving a
  user with a looping alarm and no visible way to stop it. Three layers answer that, and
  they are ordered: the app **asks** for the grant on launch; failing that
  `SirenAlarmService.raiseAlertScreen` starts the alert Activity itself under the
  `SYSTEM_ALERT_WINDOW` exemption; failing *that*, the notification's **I'm safe / I need
  help / Stop alarm** actions remain the fallback. Test with it denied — that is the
  default state on a real phone, not the edge case.
- **`raiseAlertScreen` must no-op when the full-screen intent is allowed.** Both paths
  target the same Activity, so letting them both fire races the notification's own launch.
- **Notification channel settings are immutable after creation.** Changing sound or
  vibration on `siren_alerts` does nothing on existing installs — bump the channel id.
  The alarm service uses its own **silent** channel (`siren_alarm_playback`) precisely
  so the notification does not play a second sound over MediaPlayer.
- **Android Studio's bundled JBR 25 is too new.** Always build with JDK 17.
- **GitLive's `DocumentReference.update(vararg Pair)` is deprecated** in favour of
  `updateFields`. Every call site still compiles and warns; migrate them together
  rather than piecemeal, so the diff is one reviewable change rather than noise
  spread across the repository.

---

## Firmware — `firmware/siren_esp32/`

An Arduino sketch, not a Gradle module. Open the folder in Arduino IDE (the folder
name and the `.ino` name must match, or the IDE refuses to open it), board **ESP32
Dev Module**, and copy `secrets.h.example` to `secrets.h` first.

Libraries: **ArduinoJson 7.x** (by Benoît Blanchon) and **LiquidCrystal I2C** (by
Frank de Brabander). Everything else ships with the ESP32 board package. The MPU6050
is driven with plain `Wire`, so it needs no library at all.

### Wiring

| Part | ESP32 |
|---|---|
| MPU6050 VCC / GND / SDA / SCL | 3V3 / GND / GPIO21 / GPIO22 |
| LCD 16x2 I2C SDA / SCL / VCC / GND | GPIO21 / GPIO22 / 5V / GND |
| Green / Yellow / Red LED anode | GPIO25 / 26 / 27, each through 220 Ω |
| All LED cathodes | GND rail |
| Buzzer 1 signal / Buzzer 2 signal | GPIO14 / GPIO13 |

The MPU6050 and the LCD share one I2C bus and do not clash: the LCD answers on 0x27,
the MPU on 0x68 (0x69 if AD0 is pulled high). GPIO 34, 35 and 32 are now free, and
**GPIO12 must stay unconnected** because it is a strapping pin that picks the flash
voltage at boot.

Each LED needs its **own** breadboard rows. Commoning the three anodes lights all
three at once, which reads as a hardware fault in the firmware and is not one.

### How detection works

1. **Calibrate** (10 s, 1000 samples). Records the per-axis bias, which includes
   gravity, and the noise floor `sigma_g`.
2. **Trigger** = `3 × sigma_g`, clamped to `[MIN_TRIGGER_G, MAX_TRIGGER_G]` =
   `[0.030, 0.090]`. The clamp exists in both directions: the floor keeps noise from
   triggering, the cap keeps a noisy calibration from pushing the trigger past the
   Red boundary and silently making Yellow impossible.
3. **Confirm**: motion must stay above the trigger for `MIN_SAMPLES_ABOVE` = 8
   samples inside a 300 ms window, otherwise it is logged as `REJECT` and ignored.
4. **Classify on the mean over the confirmation window, not the peak.** A knock or a
   dropped object is one enormous spike and nothing after it; an earthquake shakes
   continuously. The mean separates them. Peak classification sent every desk tap
   straight to Red.
5. **Alert**: LEDs, both buzzers (Yellow and Red only), LCD, then a Firestore write.
6. **Hold 3 s, cooldown 0 s**, then straight back to monitoring. These were 15 s and
   30 s. Anything longer is a 45-second blind window in which the system looks broken
   because it ignores every shake.

### Serial console (115200 baud)

| Key | Does |
|---|---|
| `I` | Scan the I2C bus. Expect `0x27 LCD` and `0x68 MPU6050`. |
| `L` | Live per-axis reading in g. At rest one axis reads about ±1.000, the others about 0.000, total about 1.000. |
| `C` | Recalibrate. Board must be flat and untouched. |
| `Z` | Reprint the last calibration. |
| `W` | WiFi, IP, auth and clock status. |
| `G` / `Y` / `R` | Fire a fake Green / Yellow / Red alert. |
| `S` | Stop the current alert. |

### Firmware constraints — do not rediscover these

- **A sensor that reads nothing looks exactly like a violent earthquake.** The dead
  ADXL335 produced a steady 12 g and a false alert every few seconds. Calibration now
  checks that the sensor feels about 1.000 g of gravity at rest, which catches a bad
  connection immediately, and a failed I2C read returns "no motion" instead of a
  garbage spike. Keep both.
- **The board must not be tilted after calibration.** The bias includes gravity, so a
  few degrees of tilt reads as permanent shaking. Mount the MPU firmly and press `C`
  if it ever moves.
- **`G` / `Y` / `R` must fire magnitudes inside their own bands** (0.005 / 0.050 /
  0.300). They were 0.22 / 0.48 / 0.85, all above the Red boundary, so all three fired
  Red for months. The app's `simulateAlert` has the correct values; keep the two in
  step.
- **`ADC_ATTEN_DB_12` is an ESP-IDF name and does not compile under Arduino**, which
  exports `ADC_0db, ADC_2_5db, ADC_6db, ADC_11db`. Accepting the compiler's suggestion
  of `ADC_ATTENDB_MAX` compiles and then floods the log with `invalid ADC attenuation`
  while every analog read returns garbage. Moot now that the sensor is digital, but
  that is what a wall of `adc_cali` errors means.
- **The Firestore write blocks for 2 to 3 seconds** because `postJson` opens a fresh
  TLS connection per call. The local alarm fires in about 350 ms, well before it. If
  the phone alert needs to be faster, reuse the `WiFiClientSecure` and call
  `setReuse(true)` rather than touching detection.
- **Upload problems are almost never the sketch.** A `Write timeout` with COM3 to
  COM10 listed means those are Bluetooth virtual ports and the real board is missing
  its **CP210x driver** — the device sits under "Other devices" in Device Manager with
  a yellow triangle until the Silicon Labs VCP driver is installed. A corrupted build
  cache (`file format not recognized` on a `.o`) is fixed by deleting
  `%LOCALAPPDATA%\arduino\sketches`.
- **WiFi must be 2.4 GHz.** An iPhone hotspot needs "Maximize Compatibility" on.

### Firmware state

`firmware/siren_esp32/siren_esp32.ino` is **v3.0-mpu6050** and matches the board, as of
23 Sep 2026. It compiles clean for `esp32:esp32:esp32`: about 82% of program storage and
15% of dynamic memory.

The sketch it replaced, **v2.0-singleboard**, read the ADXL335 on GPIO 34/35/32 and also
carried an optional **SIM800L GSM-SMS fallback** for when WiFi is down, off by default
and switched on from `secrets.h`. **v3.0 has no SIM800L code at all.** If that fallback
is ever wanted back, it is in history at `fcff24a` and has to be ported forward, not
just restored — the sensor layer underneath it changed completely. The four `SIM_*`
defines still sitting in `secrets.h.example` are leftovers from it and are read by
nothing; harmless, but they document a feature the sketch no longer has.

---

## REMAINING WORK

### 1. Build and test on iOS ❌

Everything under `iosApp/` and `shared/src/iosMain/` was written on Windows and has
**never been compiled**. Kotlin/Native cannot build Apple targets off macOS.
`iosApp/README.md` has the full step-by-step. In short:

1. A Mac with Xcode 15+ and CocoaPods
2. **Paid Apple Developer account ($99/yr)** — free provisioning does *not* grant the
   Push Notifications capability, and without APNs an iOS build installs and runs but
   **never alerts anyone**, which defeats the app's purpose
3. Register an iOS app in Firebase (`com.research.siren`), add
   `GoogleService-Info.plist`, upload an APNs key
4. Create `iosApp.xcodeproj` in Xcode (a `.pbxproj` cannot be hand-authored reliably)
   and add the two Swift files
5. `pod install`, then run

Expect `IosPlatformServices` to need fixes on first compile — it is unverified.

### 2. Wire the FCM topic subscription on iOS ⚠️

`IosPlatformServices.subscribeToAlertsTopic()` is a no-op; the Swift `AppDelegate`
subscribes instead. Consolidate once the FirebaseMessaging pod is linked.

### 3. Request the critical-alerts entitlement ⚠️

Red alerts should bypass silent mode. Apple grants that entitlement only on request;
until then `defaultCriticalSound()` degrades to a normal sound.

### 4. Enable phone sign-up in the Firebase console ⚠️

The Android code path is complete and unexercised. Until the Blaze plan, the release
SHA-256 and the Phone provider are all in place, tapping "Sign up with Phone" reaches a
specific, actionable error and nothing more. See **Authentication → Phone sign-up**.

### 5. Optional cleanups

- `app/src/main/res/drawable/ic_phone_outline.xml` and `ic_brand_tile.xml` may now be
  unused — check before deleting
- `Platform.services.clearNotifications()` and `vibrateTap()` are implemented but not
  called anywhere yet
- `Yellow` still steps down after 30 s by design, while Red loops until dismissed. If
  the intent is for *every* level to ring until acknowledged, that is a one-line change
  in `AndroidPlatformServices.startAlarm` (`EXTRA_TIMEOUT_MS`)

---

## Next phase (post-competition)

Five features, in the order that makes sense to build them. Nothing here is started.

**Before adding any field to an `alerts` document**, read the warning at the end of
**Data model** — a field the app cannot decode silently removes the alert from the
list, which looks exactly like the hardware failing.

### 0. Background push is still missing (do this first)

Nothing publishes to the FCM `alerts` topic when a document is created. The app only
sees an alert through its Firestore listener, which needs the app to be running. **On
a phone where the app has been swiped away, no alert arrives at all**, and that is by
far the biggest hole in the system.

The fix is a ~30-line Cloud Function on `onDocumentCreated("alerts/{alertId}")` that
sends a data-only, high-priority message to the topic with `alertId`, `intensity`,
`magnitudeG` and `nodeId` — the exact keys `SirenMessagingService` already reads, so
no app change is needed. It requires the **Blaze plan**, which needs a billing card
even though this usage stays inside the free tier. If billing is impossible, say so in
the paper as a limitation rather than pretending the app alerts when closed.

### 1. GPS location tracking

Where a student is during an earthquake, on a map, for their linked guardians and
teachers.

- Use the **phone's** GPS. No hardware change.
- This tracks minors, so design the limits in from the start: explicit opt-in,
  location shared **only while an alert is active**, visible only to already-linked
  guardians and the student's own adviser, and a visible indicator while sharing.
  Continuous background tracking is out of scope and should stay out.
- Google Maps needs an API key with billing attached. **OpenStreetMap via a tile
  library avoids that** and is the better default for a school project.
- Goes through `PlatformServices` like every other platform capability, not
  expect/actual.
- Firestore: add to the response document rather than the alert, e.g.
  `alerts/{alertId}/responses/{userId}` gaining `lat`, `lng`, `accuracyM`,
  `locatedAt`. That keeps it scoped to one event and expires naturally.

### 2. Official earthquake data in the app

**PHIVOLCS has no public API.** Their bulletin page can be scraped, but it breaks
easily and has had certificate problems, and scraping needs a server. Use the standard
seismological feeds instead:

- **EMSC** primary: `https://www.seismicportal.eu/fdsnws/event/1/query` (FDSN, JSON,
  no key). Better regional pickup.
- **USGS** fallback: `https://earthquake.usgs.gov/fdsnws/event/1/query` and the
  GeoJSON summary feeds. No key, CDN-cached, updated about every minute.
- Filter to the Philippines with a bounding box, roughly lat 4 to 21, lon 116 to 127.

Honest limits to carry into the UI and the paper: these are global networks, so small
local events PHIVOLCS reports may be missing; magnitudes can differ from PHIVOLCS by a
tenth or two because different agencies use different stations and scales; values are
revised after first publication; and the feed is minutes behind, so it is a
**confirmation** source, not the warning. **Label the source in the UI.** Do not call
it PHIVOLCS data.

The strong research angle here is cross-referencing: when the node fires, query the
feed for a nearby event in the same window and mark the alert confirmed or
unconfirmed. That gives the evaluation an independent ground truth, which is the first
thing a panel asks about.

### 3. Magnitude and intensity together

Display "Magnitude 4.4 | Intensity III".

**The device cannot measure magnitude.** Magnitude is energy at the source and takes
several stations to compute. The node measures shaking where it sits, which is
intensity. So: **magnitude from the feed, intensity from the device**, each labelled
with where it came from. Anything else is a claim the hardware cannot support, and it
is the easiest thing for a panel to pull apart.

Mapping PGA to a PEIS roman numeral belongs in `Intensity` in `Models.kt` so the app
and the firmware keep one source of truth. Note that USGS's `mmi` field is Modified
Mercalli, not PEIS, and is often empty for smaller events.

### 4. In-app voice alert

The app speaks the alert aloud, for example "Intensity seven. Take cover."

- Android `TextToSpeech`, iOS `AVSpeechSynthesizer`, both behind `PlatformServices`.
- Filipino voices exist on most Android phones. **Cebuano generally does not**, so if
  the alert should be in Bisaya, use short pre-recorded clips instead of TTS.
- The alarm already owns audio. `SirenAlarmService` holds a `MediaPlayer` with
  `USAGE_ALARM` and an exclusive audio-focus request, so speech has to be sequenced
  against it or it will either be inaudible or steal focus from the siren. Speak once
  after the first alarm loop rather than over it.

### 5. Web version (future)

For iOS and desktop users, on the same Firestore backend. Compose Multiplatform has a
`wasmJs` target, so some of `shared/` may carry over, but GitLive's Firebase bindings
and every `PlatformServices` implementation would need a web actual. Treat it as a
separate front end against the same data, not a fourth target of this build.

---

## Emergency alarm

**The Red alarm stops only when the user taps.** Not on a timer, not when the
notification is swiped, not when the app is backgrounded, not on silent/DND, and not
when audio focus is lost to a call. The three exits are **I'm Safe**, **I Need Help**
and **Stop alarm** — and "Stop alarm" silences the sound while deliberately leaving the
safety confirmation outstanding.

**All three exits are on `AlertScreen` itself**, not behind it. They used to be: the
alert screen offered a single "Confirm Your Status" that navigated to
`SafetyConfirmationScreen`, so on the phones where the full-screen intent is denied the
notification actions were the *only* place "I'm safe" and "I need help" existed.
`onConfirmStatus` still opens the detailed safety screen — response timestamp, locked
answer — but nothing that ends an alarm is behind that step any more. Responding silences
the alarm because `submitMyResponse` calls `stopAlarm()` first, which is also what the
notification actions do.

| Level | Sound | Vibration | Service |
|---|---|---|---|
| Green — Intensity I–IV | single chime, respects ringer | one pulse | none |
| Yellow — Intensity V–VI | repeats, stops after 30 s | repeating | foreground |
| **Red — Intensity VII+** | **loops until dismissed, bypasses silent** | **continuous** | foreground |

Android runs it from `SirenAlarmService` (a foreground service) — audio driven from a
composable dies the moment the app is backgrounded, which is exactly when the alarm
matters. It uses `USAGE_ALARM` audio attributes (that is what bypasses the ringer and
DND), a `PARTIAL_WAKE_LOCK`, and `AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE` whose loss
listener intentionally does nothing above Green.

The tone is `app/src/main/res/raw/siren_alarm.mp3` — the **NDRRMC alert audio supplied
by the project owner**, used at their explicit direction.

Worth knowing if this is ever distributed beyond the school: an identical official tone
can lead people to take this supplementary app for an official PHIVOLCS warning, and
reproducing official emergency signals outside genuine alerts is restricted in a number
of jurisdictions. A distinct synthesised alternative (alternating 960/720 Hz,
square-dominant, whole-cycle segments so it loops seamlessly) can be regenerated at any
time with `tools/make-alarm-tone.ps1` and dropped in as `siren_alarm.wav`.

Because MP3 carries encoder padding, its loop point is not perfectly gapless. A
2-second watchdog in the service restarts playback if it ever stalls, so the alarm
cannot fall silent while an event is unanswered.

**`USAGE_ALARM` bypasses the ringer and DND, but not the alarm stream's own volume.** A
phone with alarm volume at zero played nothing at all, silently and with no error — the one
failure mode none of the routing flags above address. `raiseAlarmVolume` lifts
`STREAM_ALARM` to a floor (90% Red, 60% Yellow) only when it is below one, remembers the
previous value, and `stopEverything` puts it back. `setStreamVolume` can throw under some
DND policies without `ACCESS_NOTIFICATION_POLICY`; that is caught rather than requested,
because playback already bypasses DND and a refused volume change still leaves the alarm
audible at whatever it was set to.

### Waking a dark, locked phone

A full-screen intent launches `MainActivity`, but on a locked device that alone puts it
*behind* the keyguard with the screen still off — the alarm sounds and nothing is
visible. Three things address that, and all three are needed:

- `android:showWhenLocked` / `android:turnScreenOn` **in the manifest**, because the
  attributes apply to the launch itself; setting them only in `onCreate` is too late
- the same two set again in code, plus `FLAG_KEEP_SCREEN_ON`, when the activity is opened
  from an alert
- `SirenAlarmService.wakeScreen()` — a `SCREEN_BRIGHT_WAKE_LOCK or ACQUIRE_CAUSES_WAKEUP`
  held for 30 s. Deprecated, and the only mechanism that still lights a display from a
  service. It is the fallback for when the full-screen intent is **denied outright**,
  which Android 14 does by default.

The keyguard is only asked to dismiss when it is **not** secured. `requestDismissKeyguard`
on a PIN-locked phone prompts for the PIN, which is the last thing to put between someone
and an earthquake warning. Showing over the lock screen is enough, and the notification's
I'm safe / I need help actions work from there.

Waking the screen was never the whole problem, though — **what the woken screen showed
was**. Four further things, each of which independently produced a sounding alarm behind a
blank or wrong screen:

- **The alert overlay must live outside the auth gates.** `AppContent` returns early on
  `!authResolved`, `!signedIn` and a null user document, and the overlay used to be drawn
  *after* all three. A full-screen intent wakes a locked phone into a **cold start**, so
  every one of those gates is closed at the moment the alert arrives: the phone lit up
  showing the splash, then a spinner, while the alarm ran. The overlay is now a sibling of
  the shell in a `Box` (`AppContent` → `AppShell` + `AlertOverlay`) and paints regardless of
  sign-in state. **Do not move it back inside `AppShell`.**
- **The system splash must not be held during an alert.**
  `setKeepOnScreenCondition { !authResolved }` keeps Android's own splash window above
  everything the app draws — including the fix above — for as long as Firebase takes, which
  on a phone that woke with no network is forever. `MainActivity` now checks for the alert
  extra on the launch intent and skips the hold.
- **Render the alert from the push, not from Firestore.** `showAlertById` needs a document
  read, and a locked, dozing, possibly offline phone is the worst case for one.
  `showAlertFromPush` builds the `AlertRecord` from the payload the push already carries and
  shows it immediately; the Firestore copy refines it when it lands. Its `detectedAt` is
  arrival time until then.
- **`_incomingAlert` is push state, not session state.** `attachFor` opens with `detachAll`,
  which used to null it — so on a cold start the auth emission wiped an alert the push had
  already painted over the keyguard, the Activity dropped back behind the lock screen, and
  the alarm kept looping against a dark display. Attach and the signed-out branch now pass
  `clearIncomingAlert = false`; only an explicit sign-out clears it. For the same reason the
  alerts listener's first snapshot, which deliberately does not raise anything, now *upgrades*
  an alert already on screen to the stored record instead of ignoring it.
- **An alert the user dismissed must not come back.** `showAlertById`'s Firestore read can
  land seconds after the tap; `dismissedAlertIds` stops it re-raising a screen the student
  deliberately cleared.
- **Back must not close the alert.** Nothing registered a back handler over it, so a Back
  press finished the Activity and left the alarm looping behind a blank keyguard. The three
  deliberate exits are the buttons on the alert itself.
- **The alert reaches signed-out devices.** `subscribeToAlertsTopic()` runs unconditionally
  at start and nothing unsubscribes, so every install that has been opened once receives the
  `alerts` topic. Since the overlay now sits outside the auth gates it renders in that state
  too — hence `canRespond`, without which the response buttons appeared, discarded the tap,
  and left "your adviser is notified the moment you respond" on screen.
- **`setShowWhenLocked` and `FLAG_KEEP_SCREEN_ON` are sticky.** Nothing cleared them, so
  after an alert was answered the app stayed visible over the lock screen with the display
  pinned on. `MainActivity` follows `incomingAlert` and clears the override when it goes
  null — while ignoring the initial null, or it would clear the flags `handleIntent` had
  just set, before the alert had been fetched.

### Three OS grants that fail silently

Notifications being off, and full-screen alerts being denied, both produce *no error*:
the first means no alert ever arrives, the second means a Red event sounds the alarm with
nothing on screen to explain it. Settings reads both back
(`notificationsEnabled()` / `canUseFullScreenIntent()`) and links to the OS screen that
changes them.

Settings reporting them was not enough. A grant nobody knows to look for is a grant
nobody has, and on a stock Android 14 phone the default state is denied — so every Red
alert arrived exactly as the notification-only screenshot showed it, alarm looping behind
a heads-up banner. `AppContent` now **asks** on launch, once, whenever the alert has no
way onto the screen at all.

The third grant is `SYSTEM_ALERT_WINDOW` ("Display over other apps"), read through
`canLaunchAlertOverOtherApps()`. **Nothing is ever drawn over another app.** It is
requested only because it is the one documented exemption from the Android 10+ ban on
background activity starts, which is what otherwise stops `SirenAlarmService` from
launching the alert Activity itself once the full-screen intent has been refused. Either
grant is sufficient, so the prompt appears only when both are missing, and it offers both
routes because several OEM builds do not ship the
`ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT` screen at all — on those, the pop-up
permission is the only way through.

The notification actions remain the last fallback and are not going anywhere: with
neither grant, they are still the only way to answer.

### The push payload must be data-only

`onMessageReceived` is **not called for a `notification`-payload push while the app is
killed** — the system tray draws it and the app never runs, so no alarm sounds. The
sender must use a **data-only, `priority: high`** message carrying `alertId`, `intensity`
and `magnitudeG`. A high-priority FCM message is also what exempts the foreground-service
start from Android 12+ background restrictions. `SirenMessagingService` logs loudly when
a push arrives without an `alertId`, because that misconfiguration is otherwise invisible.

`startInForeground` returns a Boolean and the service bails if the OS refused, posting a
plain notification instead. Letting `ForegroundServiceStartNotAllowedException` propagate
would crash the process during an earthquake.

Simulated (Demo Mode) events always render a `DEMO — NOT A REAL EVENT` badge on the
full-screen alert. A simulation that is indistinguishable from a real earthquake would
be a serious failure during a defence.

## Authentication

Firebase **Email/Password** and **Phone**. Anonymous sign-in was used in v1.0 and has
been removed — it lost accounts on reinstall, breaking parent links and history.

- Role is chosen **before** the account exists, then written into the user document
- **A fresh install opens on Create Account, not Login.** Somebody who has just
  downloaded the app has no credentials, so a login form asks for something that does
  not exist yet. `SirenSettings.hasAccount` (persisted in the settings JSON) flips the
  entry point to Login once an account has been created or signed into on the device.
  It defaults to false, so pre-existing installs see Create Account once — the harmless
  direction to be wrong in.
- **Role can be changed in Settings → Switch role.** This reverses the v2.x freeze:
  the sign-up screen told users the role could be changed later and Settings had nowhere
  to do it, so the app was simply lying. Switching *to* Student mints a `shortCode` if
  the account has never had one, or the student is invisible to parents and advisers.
- **Profile is editable** in Settings → Edit profile (name, class, mobile). A name typed
  wrong at sign-up used to be permanent, and that name is what an adviser reads off the
  roll call.
- The **ESP32 has its own account** and writes alert documents directly; the app only listens

### Phone sign-up

Implemented on **Android only**, through `PlatformServices.sendPhoneCode` /
`confirmPhoneCode` — the same seam Cloud Messaging uses, because GitLive KMP 2.5.0 wraps
neither. The native `com.google.firebase:firebase-auth` SDK backs it, and because
GitLive's `Firebase.auth` delegates to that same instance, a phone sign-in still lands in
`SirenRepository`'s `authStateChanged` listener with no extra plumbing.

`PhoneAuthProvider.verifyPhoneNumber` needs a real **Activity** for its reCAPTCHA
fallback, which `AndroidPlatformServices` does not have — `SirenApp` tracks the
foreground activity through `ActivityLifecycleCallbacks` and passes it in as a lambda.

Android can also verify a SIM without any code being typed (`onVerificationCompleted`
fires immediately). `PhoneCodeRequest.AutoVerified` covers that: the user is already
signed in, so the code field is skipped and `completeAutoVerifiedPhone` writes the
profile. Miss that path and the account exists with no user document, and the app sits
on the "profile loading" spinner forever.

**It cannot send a single SMS until three things are done in the Firebase console**, and
all three fail at runtime rather than at build time:

1. ~~**Blaze plan.**~~ **Done — the project is on Blaze.** Every verification SMS is
   billed per message, so keep an eye on the quota during a defence demo.
2. **SHA-256 fingerprint** registered against the Android app. Register **both**, or
   phone sign-up works on one build and not the other:

   The console's fingerprint list was empty until 19 Aug 2026, which is the whole
   reason phone sign-up had never sent an SMS. It is populated now. **Both keys have
   since changed again** — see *The release signing key* below before you touch either.

   | Build | SHA-256 | Since |
   |---|---|---|
   | release (`siren-release.jks`) | `BA:20:E1:93:A4:8A:A7:81:46:76:B9:A6:EB:40:DE:16:F4:47:33:46:1A:A6:96:82:60:09:09:B7:A2:88:50:7D` | 2.9.2 |
   | debug (`~/.android/debug.keystore`, **the machine this file was written on**) | `84:CB:0A:7B:D6:B4:24:22:52:4E:F2:8A:54:79:C6:BF:B0:EE:37:49:0A:81:AF:14:7B:79:3D:97:C5:8C:10:88` | 2.9.2 |
   | debug (`~/.android/debug.keystore`, **the Windows machine holding the zip-seeded clone**) | `0F:57:86:B9:D3:2D:77:FB:D7:05:92:0C:B0:68:CA:03:0C:18:0A:1D:5C:FE:57:FD:20:8B:DD:8D:8B:04:A1:46` | 23 Sep 2026 |
   | release — **superseded**, signed 2.8.0–2.9.1 | `EF:2E:14:D5:A2:C4:4D:19:72:58:CD:A7:8D:50:18:57:63:C7:ED:60:FD:77:6A:5E:EE:CC:CC:8B:1F:C8:8D:FC` | — |
   | debug — superseded, signed 2.8.0–2.9.1 | `84:45:C3:F4:A6:C4:F4:D8:23:72:FC:84:D6:84:30:BF:52:4B:2B:71:2B:9A:F8:1C:DD:AC:C0:60:22:44:60:11` | — |

   **The debug fingerprint is per-machine.** `debug.keystore` is generated locally and
   never committed, so every new build machine produces a different one and phone
   sign-up breaks there until it is registered too. Read the local one with
   `keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey
   -storepass android`.

   Registering only the release fingerprint is the classic mistake: every debug build
   then fails with "This app is not authorized", which reads like a code fault.
   Re-download `google-services.json` after adding them.
3. **Phone enabled** under Authentication → Sign-in method.

Read a fingerprint back off any APK with
`apksigner verify --print-certs <apk>` — the release keystore does not have to be
present, and `keytool -printcert -jarfile` returns nothing here because these APKs
carry only a v2 signature, not a legacy JAR one.

`AndroidPlatformServices.phoneAuthMessage` maps each of those failures to a specific
sentence ending in "use email instead", because the raw SDK text is unreadable and the
fix is never in the app.

iOS reports `phoneAuthSupported = false`, which hides the option entirely: the
FirebaseAuth pod is not linked, and silent-push device verification needs an APNs key
that requires the paid Apple Developer account the project does not have.

## The emergency SMS

When a **student** taps "I need help", the app texts their approved guardians automatically,
before writing anything to Firestore. That ordering is the point: the Firestore path assumes
a guardian is holding an unlocked phone, with the app installed, on a working connection.
After a real earthquake none of that is safe to assume, and SMS survives conditions data does
not. `sendHelpSms` therefore sits outside the write's `runCatching` — it must not be reached
only if the network succeeded.

Only students send. A teacher or parent tapping the same button is answering for themselves
and has no guardians to notify.

- **`parentContact` is not a phone number.** `UserProfile.contact` is
  `email.ifBlank { phone }`, so it resolves to email for every email/password signup. Link
  requests carry a separate **`parentPhone`**, written when the parent raises the request,
  and it lives on the request document specifically so the *student's* device holds it
  offline — which is exactly when it is needed. Old requests fall back to `parentContact`,
  but only through `looksLikePhone`, which rejects anything containing `@`.
- **A mobile number is required at sign-up**, on both the email and phone routes, and stored
  through `normalisePhone` as E.164. An account without one is silently unreachable at the
  only moment that matters. Accounts predating this show a red banner in Settings until they
  add one; `parentPhone` is **not** back-filled onto existing approved link requests, so
  those guardians stay unreachable until they edit their profile.
- **`SEND_SMS` is requested at the moment of first use**, not at launch — asking to send
  texts on first run, before anyone has seen why, invites a reflexive refusal. If it is
  denied the response is still recorded and the student is told the texts did not go.
- **`sendTextMessage` does not throw when a message fails to send.** Every real failure —
  no service, radio off, no credit, carrier reject, rate limit — arrives *only* through the
  `sentIntent` PendingIntent. Passing null there and trusting the absence of an exception
  counts binder calls, not sends, and told a student mid-earthquake that guardians had been
  texted when nothing had left the phone. `dispatchOne` registers a receiver and awaits the
  real verdict per recipient, bounded at 30 s, with a SIM-state pre-check ahead of it.
  **Never go back to a null `sentIntent`.**
- **The permission cannot be requested from the path that needs it.** `SEND_SMS` can only be
  prompted from a resumed Activity, and the case it exists for — answering from the alarm
  notification on a locked phone — has no Activity at all. `ensureSmsPermission` is therefore
  called from the app while it is open, once the student actually has a guardian to text.
  When a dispatch finds the permission missing and no Activity, that is reported as
  `couldNotAsk`, **not** `permissionDenied`: telling someone they declined something they
  were never asked is both wrong and unactionable.
- **Outcomes are reported by notification as well as snackbar.** `_events` has no replay and
  its only collector lives inside the composition, so on the notification-answer path every
  SMS outcome was being discarded — silently telling nobody that nobody had been texted.
- **The cold-start tap must not return empty-handed.** On a push-woken process `_user` and
  `_linkRequests` have not arrived yet, and the original early returns meant the lock-screen
  tap sent nothing and said nothing. `sendHelpSmsNow` waits for the profile (10 s) and
  briefly for links (6 s), and reports failure if they never come.
- **One batch per alert** (`helpSmsSentFor`), because both the alert screen and the
  notification action reach the same code and a frightened student taps more than once. The
  entry is released again if nothing was sent, so a failure is still retryable.
- **A simulated event must say so in the text.** The alert screen badges demos, but the
  guardian receiving the SMS is the one person who cannot see that screen.
- **"Sent" means handed to the radio, not delivered.** There are no delivery receipts.
  `SmsDispatchResult` carries sent / failed / noNumber / permissionDenied / unsupported
  separately because the student has to be told something true; an earthquake is the worst
  moment to imply a message went out when it did not.
- **Messages are split with `divideMessage`.** The body is deliberately longer than 160
  characters, and a single `sendTextMessage` would silently truncate it.
- **iOS cannot do this at all.** `MFMessageComposeViewController` always requires a tap and
  no entitlement changes that, so `directSmsSupported` is false there, the Settings toggle is
  hidden, and the dispatch reports `unsupported`.
- **Nothing is ever texted to the official responders.** Police and fire are one-tap manual
  calls only. An unattended false alarm to an emergency hotline during a school drill is a
  real hazard, and false emergency reports carry legal weight.
- `SEND_SMS` is a **restricted permission on Google Play** and would need a declared
  exception if this app were ever published there. It is fine for a sideloaded build.

## Profile pictures

Stored as a **base64 JPEG on the user document**, not in Firebase Storage.

That is a deliberate choice, not a workaround for the old billing limit. At 256px and
quality 80 a photo encodes to roughly 20 KB against Firestore's 1 MiB document ceiling,
and it arrives on the profile snapshot the app already listens to — so a roster of
thirty faces costs zero extra reads and has no per-avatar loading state. Storage would
add a bucket, a second set of security rules, download URLs to manage and a failure mode
per image, to solve a problem this app does not have. Revisit only if pictures ever need
to be bigger than a tile.

Downscaling happens on the **platform** side of `PlatformServices`, in
`ProfilePhotoEncoder`, because shared code cannot decode or re-encode an image and an
unresized camera photo would be rejected by Firestore outright. Two stages, both
load-bearing: `inSampleSize` decodes at reduced resolution so a 12-megapixel photo never
becomes a full-size `Bitmap` (decoding one at full size just to shrink it is a routine
OOM on a cheap phone), then an exact scale hits `PROFILE_PHOTO_MAX_PX`.

`registerForActivityResult` must be called before the Activity finishes being created,
so the launcher cannot live in `AndroidPlatformServices` — `MainActivity` implements
`ProfilePhotoPicker` and the services class reaches it through the current-activity
lambda, the same indirection phone verification uses. `PickVisualMedia` needs **no**
storage permission; asking for `READ_MEDIA_IMAGES` to set an avatar would be asking to
read the whole gallery.

`Avatar` decodes inside `remember(photo)`. That key is load-bearing too: a roster
redraws on every incoming safety response during an event, and decoding thirty JPEGs per
frame would stutter the one screen that must not stutter. A corrupt string falls back to
initials rather than throwing.

iOS reports `photoPickerSupported = false` and hides the control — `PHPickerViewController`
needs a `UIViewController` to present from, which `IosPlatformServices` does not hold.

## Emergency contacts

Three official City of Bogo responders are seeded for every user on first run
(`DefaultEmergencyContacts` in `model/Models.kt`): Bogo Police Station (primary),
Emergency Response Unit, and Bogo Fire Department.

Seeding is guarded by a `seededDefaults` flag in the stored settings JSON, which does
two jobs: installs that predate the defaults pick them up once on upgrade, and a
contact the user deliberately deletes does **not** reappear on the next launch. If one
is removed, a "Restore official numbers" button appears on the Emergency Contacts
screen — and only when something is actually missing.

## User roles

- **Student** — receives alerts, submits own status, gets a 6-char `shortCode`,
  confirms or declines guardian link requests
- **Teacher / School Admin** — roster for their `classId`, live roll-call, close events,
  **adds students to their class by linking code**, **Demo Mode**
- **Parent / Guardian** — status of students in `linkedStudentIds`, once each student
  has confirmed the link, **Demo Mode**

**Demo Mode is reached from the teacher and parent dashboards, not the student one.**
`simulateAlert` writes a real `alerts` document that fans out to *every* device on the
`alerts` topic, so the person triggering a drill should be the one running it, not one
of the students receiving it. The screen itself is role-agnostic — only the entry points
moved — so `Dest.Demo` still renders the same way whoever opens it.

### Guardian linking needs the student's confirmation

A parent typing a code raises a **request**; it is not a link until the student approves
it. The code is six characters and gets read aloud across a classroom, and before this
anyone who overheard one could attach themselves to that student's live safety feed with
the student never being told.

The commit is split across the two clients because Firestore only lets each user write
their own document: the **student** sets `status`, and the **parent's** client sees the
approval and adds the id to its own `linkedStudentIds` (`adoptApprovedLinks`). A decline
removes it again, which is also how revocation works from either side.

Advisers are deliberately **not** gated this way — a teacher adding a student to their
own class is an authoritative school act, and it writes `classId` on the student
directly.

### The adviser roster used to be unreachable

`classId` drives the entire roster, and until now **nothing in the app ever set one**.
Every teacher account shipped with a blank class, so the roll call was permanently empty
and the empty state told advisers to "ask the school registrar", who has no tool either.
Both halves now exist: the class name is set in Edit profile, and students are added with
their linking code.

## Screens (18)

Splash · Login · Role Selection · Sign-up · Parent Linking · Student Dashboard ·
Teacher Dashboard · Parent Dashboard · Earthquake Alert · Safety Confirmation ·
Live Safety Dashboard · Alert History · Demo Mode · Emergency Contacts · Settings ·
Safety Guide · **Edit Profile** · **Parents & Guardians**

Safety Guide is not in the prototype; it is carried over from the shipped v1.0 APK and
uses the 28 recovered `ic_sg_*` pictograms. It is reachable from **all three roles** —
the student dashboard, the parent dashboard, the teacher roster and Settings. It was
previously only linked from the student dashboard, so two of the three roles could not
open it at all.

## Intensity thresholds

Must stay in lockstep with the firmware — `Intensity.fromMagnitude` here,
`BAND_YELLOW_G` / `BAND_RED_G` in `firmware/siren_esp32/siren_esp32.ino`. Change one and
the hardware and the phone disagree about what colour an earthquake is.

**The research paper is the authority for these numbers**, not either codebase. It states
them in both the methodology and the Definition of Terms. The firmware shipped with
`0.31` / `0.61` — off by 5–30× — and was corrected to match; check the paper before
assuming code is right.

Settled since: `MIN_TRIGGER_G` was `0.08f`, which sat above the whole Green band and
most of Yellow, so nearly every real shake landed in Red. With the MPU6050 the trigger
is `0.030` with a cap at `0.090`, which leaves Yellow a real window. Green cannot fire
from the sensor by design: 0.010 g is below the noise floor of any hobby accelerometer,
so Green only ever arrives from Demo Mode or the `G` console command.

Worth knowing for the paper: **hand-shaking the board exceeds 0.120 g easily**, so
manual tests nearly always read Red. That is correct behaviour, not a bug. Human motion
is stronger than typical seismic ground acceleration. Verify Yellow and Green through
the serial commands and Demo Mode, and say so in the methodology.

| Band | Shown to users | g range | Level | Behaviour |
|---|---|---|---|---|
| Green | Intensity I–IV · Light shaking | 0.000 – 0.010 g | 1 | Notification only, single vibration |
| Yellow | Intensity V–VI · Moderate shaking | 0.010 – 0.120 g | 2 | Full-screen alert, repeating vibration |
| Red | Intensity VII+ · Destructive shaking | ≥ 0.120 g | 3 | Alarm sound, continuous vibration, full-screen intent |

**Intensity leads, the g figure follows.** Every readout shows `Intensity.levelText`
("Intensity V–VI") large and first, with the measured peak ground acceleration
underneath it in smaller type as `0.xxx g` — `asGSpaced(3)`, three decimals because the
Green band is only 0.010 g wide and two decimals would render a 0.005 g reading as the
Yellow boundary.

v2.6 hid the g value completely. That went too far: it is the study's actual
measurement, and it was invisible inside the app that collected it. The ordering is what
matters — a student mid-earthquake acts on "Intensity V–VI", and whoever is reading the
numbers gets the precise figure without it competing for attention.

It appears on the full-screen alert, the student status panel, history rows, the live
roll-call header, the safety-confirmation screen and both platforms' notification
bodies. The Demo screen shows it via `asG(3)` as before.

## Theme

**Light-only, deliberately.** `SirenTheme` ignores the system dark setting; there is no
dark scheme and no toggle in Settings. A dark scheme used to ship, and several screens
— the login screen worst of all — dropped to unreadable contrast under it. Do not
reintroduce one without contrast-checking every screen against WCAG AA.

`SettingsDoc` no longer has a `darkMode` field, but installs that predate this still
have the key in their stored JSON. `ignoreUnknownKeys = true` on the repository's `Json`
is what stops those settings failing to parse and wiping the user's saved emergency
contacts. Do not tighten it.

## Data model (Firestore)

```
users/{userId}
  name, email, phone, role ("student"|"teacher"|"parent")
  classId, schoolId
  photo              # base64 JPEG profile picture, ~20 KB, or ""
  shortCode          # students only — the parent linking code
  linkedStudentIds[] # parents only

users/{userId}/responses/{alertId}
  alertId, status, respondedAt      # mirror, avoids a collection-group index

alerts/{alertId}
  intensity ("green"|"yellow"|"red"), magnitudeG, detectedAt
  source ("esp32"|"simulated"), nodeId, closed

alerts/{alertId}/responses/{userId}
  userId, name, status ("safe"|"needs_help"|"no_response"), respondedAt

linkRequests/{studentId}_{parentId}
  studentId, studentName, parentId, parentName, parentContact
  status ("pending"|"approved"|"declined"), requestedAt, respondedAt
```

`linkRequests` is **top-level with both ids denormalised onto it**, so each side watches
its own view with a *single* equality filter — `studentId ==` for the student,
`parentId ==` for the parent. Adding a second filter on `status` is the obvious next step
and would drag a composite index in behind it, so status is filtered client-side; these
lists are a handful of documents. The document id is always `{studentId}_{parentId}`,
which makes the relationship unique by construction and lets a re-request after a decline
overwrite rather than pile up a second row the student has to dismiss twice.

**Firestore rules note.** Two writes here cross user boundaries: a parent creates a
`linkRequests` document, and an adviser sets `classId` on a *student's* user document.
Both work under permissive/test rules. If rules are ever tightened to "each user writes
only their own document", the adviser flow needs a rule allowing a teacher to write
`classId` on a student, or it will fail silently at the write.

Enums serialise lower-case via their `wire` property. Always read through
`Role.fromName` / `Intensity.fromName` — they fall back safely. Documents map through
`@Serializable` DTOs at the bottom of `SirenRepository.kt`.

Parents watch children with one document flow each, `combine`d — deliberately avoiding
a `whereIn` on document ids and the index that implies.

**Adding a field to an alert is an app change first.** The listener maps each snapshot
inside `runCatching { }`, so a document the DTO cannot decode is dropped from the list
with no error, no row and no alert on the phone. That failure looks exactly like the
hardware not writing. The order is: add the field to `AlertDoc` and `AlertRecord`
**with a default**, ship that build to every phone, and only then start writing the
field from the firmware or a Cloud Function. Defaults are what keep the hundreds of
existing documents decoding.

The `alerts` collection also carries a lot of old `SIMULATOR` test documents. Clearing
them before a demo makes real behaviour much easier to see.

## Conventions

- Colours and spacing live in `ui/theme`; never hard-code hex values in screens
- Inter is the only font family
- Simulated events are always tagged so they stay separable from real sensor readings

## Shipping an APK

`dist/` currently holds **v2.9.2**. The next build carrying the second-phase work is
**v3.0.0**, and the version bump is part of the change, not an afterthought: Android
refuses to install an APK whose `versionCode` is not higher than the installed one,
and it says only "App not installed".

1. Bump both fields in `app/build.gradle.kts`. They move together:
   ```kotlin
   versionCode = 10        // was 9
   versionName = "3.0.0"   // was "2.9.2"
   ```
2. Build from the project root, with JDK 17:
   ```powershell
   .\gradlew.bat :app:assembleDebug
   .\gradlew.bat :app:assembleRelease
   ```
3. Copy the artifacts in under the existing naming convention and delete the previous
   pair, so `dist/` never holds two versions of the same variant:
   ```
   app/build/outputs/apk/debug/app-debug.apk      -> dist/debug/SIREN-v3.0.0-debug.apk
   app/build/outputs/apk/release/app-release.apk  -> dist/release/SIREN-v3.0.0-release.apk
   ```
4. Update `dist/debug/README.md` and `dist/release/README.md`: version, date, size,
   and what changed. Those files are the record of what a given APK actually contains.
5. Verify the release APK before trusting it. A passing build proves nothing about the
   two things that have silently broken before:
   ```powershell
   apksigner verify --print-certs dist\release\SIREN-v3.0.0-release.apk
   aapt2 dump resources dist\release\SIREN-v3.0.0-release.apk | Select-String "raw/siren_alarm"
   ```
   The fingerprint must match the key recorded below, and the alarm audio must resolve
   through the resource table. Do not look for `res/raw/siren_alarm.mp3` by path — see
   the constraint about path shortening.
6. Install it on a real phone and walk Demo Mode through all three tiers. Nothing in
   `dist/README.md` has ever been run; it only records that the code compiled.

A release build needs `keystore.properties` and `siren-release.jks` restored first, or
it comes out unsigned without complaining.

## The release signing key — do not generate another one

**This key has now been lost and regenerated four times.** Each loss forces every
person holding an installed copy to uninstall it by hand before they can update,
because Android refuses to install over an APK signed with a different key and
reports only "App not installed" without saying why. Before running `keytool` for
any reason, assume the key already exists and go looking for it.

**There are currently two keys, and which one to use is an open decision.**

| | Original — signed every shipped APK | Replacement — has signed nothing |
|---|---|---|
| Status | **missing**, not found on any machine searched | on disk, unused |
| File | `siren-release.jks`, repo root, gitignored | `siren-release.jks`, repo root, gitignored |
| Backup | none known | `C:\Users\franc\SIREN-release-key\` (one machine, not synced) |
| Alias | `siren` | `siren` |
| Key | 4096-bit RSA, SHA384withRSA | 4096-bit RSA, SHA384withRSA |
| Generated | 19 Aug 2026, valid to 11 Aug 2056 | 23 Sep 2026, valid to 15 Sep 2056 |
| SHA-256 | `BA:20:E1:93:A4:8A:A7:81:46:76:B9:A6:EB:40:DE:16:F4:47:33:46:1A:A6:96:82:60:09:09:B7:A2:88:50:7D` | `7C:7F:59:A7:DD:0D:C3:41:62:74:9A:BB:A8:1B:C9:1C:92:C6:02:1F:B3:39:82:0E:BF:95:28:4D:0E:13:48:73` |
| SHA-1 | `B5:01:DE:AF:ED:32:C7:28:DD:2C:11:DC:0A:91:51:43:E1:C0:94:64` | `E8:19:33:AE:42:E7:92:31:93:99:CD:1B:3B:40:75:A7:6A:92:74:4E` |
| In Firebase | registered | **not registered yet** |

Both carry the same DN — `CN=SIREN, OU=Practical Research 2, O=City of Bogo Senior High
School, L=Bogo City, ST=Cebu, C=PH` — so the DN does **not** tell them apart. Only the
fingerprint does.

**Prefer the original if it is ever found.** v2.9.2 and everything before it are signed
with it, and the school, the panel and classmates are holding those installs. The first
release signed with `7C:7F:59…` locks every one of them out of updating until they
uninstall by hand, which also wipes their locally stored settings — emergency contacts,
`seededDefaults`, `hasAccount`. Firebase accounts survive, being server-side. Register
the replacement's SHA-256 in the console before shipping anything signed with it, or
phone sign-up fails on release builds with "This app is not authorized".

**Why this keeps happening**, so the fifth time can be avoided: the key only ever exists
on the one disk that built the last release, it is gitignored so no clone or zip export
carries it, and the project moves between machines constantly. A clone on a new machine
always starts with no key. Storing it somewhere that follows the *person* — a password
manager attachment, an encrypted synced archive — is the fix; `keystore.properties` must
travel with the `.jks`, because either file alone is useless.

**The password is deliberately not written here.** This file is committed; the
keystore's value depends on the password not living beside a public description of
where the keystore is. Read it out of `keystore.properties`.

Check a suspected keystore against the fingerprint above before assuming it is lost:

```powershell
keytool -list -v -keystore siren-release.jks -alias siren
apksigner verify --print-certs dist\release\SIREN-v2.9.2-release.apk   # no keystore needed
```

If it genuinely cannot be found, generating a replacement is a **release decision,
not a build step** — it invalidates the fingerprint registered in Firebase and
strands every existing install. Note that `step.txt` section 5 documents
`-keysize 2048 -validity 10000`; that is weaker than the key in use and following it
silently downgrades the project. Match 4096/30 years. `tools/make-keystore.ps1`,
referenced in `dist/README.md`, **does not exist** — use `keytool` directly.

**Back both files up off this machine.** That has been the failure every time.

## Secrets — not in this repo

`.gitignore` excludes `keystore.properties` and `*.jks`. A fresh clone needs both
restored before it can build a release.

The firmware has its own `firmware/siren_esp32/secrets.h`, also gitignored, holding
the WiFi credentials, the Firebase project id and web API key, and the ESP32's own
Firebase account. Copy `secrets.h.example` and fill it in. Every value stays inside
double quotes; removing them produces a misleading "was not declared in this scope".

**The Firebase project is called "Research" but its project id is
`quicktrip-fe547`**, left over from how it was created. That is correct and not a
mistake — `FIREBASE_PROJECT_ID` in `secrets.h` and `project_id` in
`app/google-services.json` must both read `quicktrip-fe547`. A mismatch here means the
device writes somewhere the app never looks, and both sides appear to be working.

The device signs in as its own Firebase user, `esp32@siren.local`, which must exist
under Authentication → Users. `INVALID_LOGIN_CREDENTIALS` on the serial log means it
does not; `API_KEY_INVALID` means the web API key is wrong.

`app/google-services.json` **is now committed**, deliberately, at the project
owner's direction — `:app` cannot build a single task without it, the repo is
private, and the file ships inside every APK anyway. The `.gitignore` entry for it
is retained but has no effect on a tracked file. The signing key is a different
matter and stays out.

## Out of scope

- Enclosure design, PCB layout, mains wiring
- QR-code scanning for parent linking (needs a camera dependency; code entry only)
- Structural damage assessment, evacuation routing, search-and-rescue
- Scraping PHIVOLCS directly — see **Next phase → 2**
- Earthquake prediction. The system detects shaking that has already reached the
  sensor; it gives no warning before ground motion arrives
- Replacing official PHIVOLCS warnings — supplementary local tool only

## Testing priorities

1. Demo Mode: all three levels — colour, vibration, full-screen behaviour
2. Full loop: respond on one account, verify on a teacher/parent account
3. Offline: airplane mode → respond → reconnect → confirm sync
4. Push to topic `alerts` from the Firebase console
5. **Safety Guide icons and Inter fonts actually render** — the canary for the
   Compose-resources packaging workaround above
6. **End to end from the hardware**: `I` and `L` on the serial console to prove the
   sensor reads, `C` for a clean `health,ok`, then shake the board and confirm
   `TRIAL` and `CLOUD,OK` on serial, a document in the `alerts` collection, and the
   alert on a phone. Test it once with the app open and once with it swiped away —
   the second case does not work yet, and that is the point of **Next phase → 0**.
