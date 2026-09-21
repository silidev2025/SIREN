# Prompt — SIREN UI overhaul + persistent emergency alarm

Paste everything below the line into a fresh Claude Code session opened at the repo root.

---

You are working on **SIREN**, an earthquake detection and alert app for City of
Bogo Senior High School (Practical Research 2). Read `CLAUDE.md` first — it documents
the architecture and a list of build constraints that must not be rediscovered.

Stack: Kotlin + **Compose Multiplatform**. Nearly all UI lives in
`shared/src/commonMain/kotlin/com/siren/mobile/`. `:app` is a 3-file Android host.
Verify shared code with `.\gradlew.bat :shared:compileCommonMainKotlinMetadata`
(~10 s, type-checks for Android **and** iOS) and build with
`.\gradlew.bat :app:assembleDebug`.

There are two jobs: **A) make the UI look professionally designed** and **B) make the
Red-level alarm behave like a real emergency alarm**. Do both. Keep the app compiling
after every step.

---

## A. Make the UI look designed, not generated

The current UI works but reads as machine-produced: everything is a rounded white card
in a vertical stack, spacing is applied ad hoc, and there is no visual hierarchy. Fix
the underlying system, not just the surface.

### A1. Establish real hierarchy

Right now every card is `elevation 0` + `1dp border` + `RoundedCornerShape(20.dp)`, so
nothing looks more important than anything else. Introduce a deliberate three-tier
system:

- **Primary surface** — the one thing that matters on the screen (system status,
  active event). Tonal fill or elevation, larger radius, more internal padding.
- **Secondary** — list rows, quick tiles. Flat, hairline separation, tighter padding.
- **Tertiary** — metadata, helper text, timestamps. No container at all.

Kill the "everything is a card" pattern. Grouped list rows with dividers read as more
professional than 8 floating cards, and are faster to scan under stress.

### A2. Enforce the type scale

`ui/theme/Type.kt` defines a full scale, then screens override it with raw `fontSize =
26.sp` / `FontWeight.ExtraBold` inline. Remove every inline `fontSize` and
`fontWeight` in `ui/screens/` and use the scale. If a style is missing from the scale,
add it to the scale — do not inline it.

Set optical sizes properly: numeric readouts (`0.74 g`, roster counts) should use
tabular figures so they don't jitter when values change.

### A3. Spacing rhythm

`Space` and `Layout` tokens exist but are used inconsistently (`Space.m` next to a raw
`14.dp` next to `Space.l`). Audit every screen; every gap, padding and size must come
from a token. Establish a 4dp base grid and stick to it.

### A4. Use colour semantically, not decoratively

`SirenGradients.night` is currently applied to any panel that "should look important".
Gradients should mean something. Reserve them for intensity/severity only; use flat
tonal surfaces everywhere else. Verify contrast for **every** text/background pair —
this is an emergency app read in bad conditions; target WCAG AA (4.5:1 body, 3:1 large).

### A5. Real interaction feedback

`PrimaryButton` / `SecondaryButton` / `QuickTile` in `ui/components/Common.kt` are
`Box + clickable` — no ripple, no state layer, no pressed/hover/focus/disabled
treatment, and no accessibility role. Rebuild them on Material 3 `Button` /
`Surface(onClick=)` so they get ripples and state layers for free, and keep the custom
look via `colors`/`shape`. Every interactive element needs pressed and disabled states.

### A6. Ship the missing states

Most screens only render the happy path. Every data-driven screen needs four:

- **Loading** — skeleton placeholders, not a bare spinner
- **Empty** — a real explanation and a next action (only `HistoryScreen` has one)
- **Error** — what failed and a retry affordance
- **Offline** — a persistent, non-alarming banner; `SirenRepository.online` already
  exists but is barely surfaced

### A7. Motion with intent

Screen changes currently swap instantly because navigation is a `mutableStateListOf`
back stack. Add shared transitions: forward navigation slides/fades in, back reverses.
Animate roster status changes so a student flipping to "Needs help" is noticeable.
Keep everything under 300 ms and respect reduce-motion settings.

### A8. Accessibility

Many `Icon`s pass `contentDescription = null` where they carry meaning. Audit all of
them. Minimum 48dp touch targets. Every status must be conveyed by more than colour
alone (colour + icon + text) — colour-blind users must be able to read the roster.

### A9. Content design

Externalise every hardcoded UI string. Nothing should be a literal in a composable.
This also opens the door to **Cebuano/Filipino localisation**, which matters for the
actual users of this app. Tighten the microcopy: shorter, calmer, imperative under
stress ("Confirm you're safe", not "Please confirm your safety status now").

### A10. Density and layout

Test at 360dp width **and** on a tablet. Nothing should stretch full-bleed on a large
screen — constrain content width.

The dark theme this section used to ask you to verify has since been **removed**. It
was never visually checked, and when it finally was, the login screen turned out to be
unreadable under it. The app is light-only now — see the Theme section in `CLAUDE.md`.

---

## B. Persistent emergency alarm

### B1. Sound

Add an **NDRRMC-style** emergency alert tone — the urgency profile and cadence of a
Philippine disaster alert, but a distinct asset of our own.

> **Do not ship the actual official NDRRMC/PHIVOLCS alert tone.** This app is
> documented as supplementary and explicitly does not replace official PHIVOLCS
> warnings; an identical tone would invite users to mistake it for an official
> government alert during a real earthquake. Several jurisdictions also restrict
> reproducing official emergency signals outside genuine alerts. Commission or
> synthesise a tone that is unmistakably urgent and unmistakably ours.

Characteristics: two alternating tones roughly 700–1000 Hz, ~0.5 s each, harsh square
or sawtooth timbre, no fade-in, loops seamlessly. It must cut through ambient noise and
be instantly distinguishable from a ringtone or notification chime.

Store as `shared/src/commonMain/composeResources/files/` (or platform raw resources if
CMP file resources prove awkward — check how the existing `composeResources` assets are
packaged first; see the AGP 9 caveat in `CLAUDE.md`).

### B2. Behaviour — this is the core requirement

**The alarm must not stop on its own. It stops only when the user explicitly dismisses
it by tapping.**

Specifically it must keep sounding through all of:

- the screen locking
- the app being backgrounded or swiped from recents
- the notification being swiped away
- the device being on silent / Do Not Disturb
- an incoming call ending
- the alert being marked `closed` remotely

It stops **only** when the user taps one of: **I'm Safe**, **I Need Help**, or an
explicit **Stop alarm** control. Tapping "Stop alarm" must still leave the safety
confirmation pending and visible — silencing is not responding.

Escalate by intensity, matching the thresholds in `Intensity`:

| Level | Sound | Vibration |
|---|---|---|
| Green ≤ 0.30 g | single short chime, respects silent mode | one pulse |
| Yellow 0.31–0.60 g | repeating tone, ~30 s then stops | repeating pattern |
| **Red ≥ 0.61 g** | **loops until dismissed, bypasses silent** | **continuous until dismissed** |

### B3. Android implementation notes

- Play through a **foreground service** so it survives backgrounding. Anything driven
  from a composable dies with the Activity.
- `MediaPlayer`/`ExoPlayer` with `isLooping = true`, `AudioAttributes` of
  `USAGE_ALARM` + `CONTENT_TYPE_SONIFICATION` — the alarm stream is what ignores the
  ringer and DND.
- Request `AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE`, and **do not stop on focus loss** for
  Red; duck for Green/Yellow only.
- `PARTIAL_WAKE_LOCK` while the alarm is active; release it on dismissal without fail.
- Notification: `setOngoing(true)`, `setAutoCancel(false)`, `CATEGORY_ALARM`,
  `setFullScreenIntent(..., true)`, plus **notification actions** for I'm Safe /
  I Need Help so the user can respond from the lock screen.
- **Android 14+ restricts `USE_FULL_SCREEN_INTENT`** to calling and alarm apps. Declare
  the permission, and handle the case where it is denied — otherwise the full-screen
  alert silently never appears and the user has no visible way to stop the alarm. The
  notification actions are the required fallback; verify this path.
- Existing channel `siren_alerts` already sets `setBypassDnd(true)` and an alarm sound;
  extend rather than duplicate it. Note channel settings are immutable after creation —
  bump the channel id if you change its behaviour.
- Vibrate on a repeating waveform in parallel; cancel it on the same dismissal path.

### B4. iOS implementation notes

iOS deliberately makes this hard, and the honest constraints must be documented rather
than faked:

- Looping audio while backgrounded needs the `audio` background mode and an
  `AVAudioSession` category of `.playback` with `numberOfLoops = -1`.
- Bypassing silent mode requires the **Critical Alerts entitlement**, which Apple
  grants only on request. Until it is granted, `defaultCriticalSound()` degrades to a
  normal sound — see `iosApp/README.md`.
- A push cannot itself loop a sound; the app must be foregrounded (or launched via the
  notification) for the looping alarm. Document this limitation in `CLAUDE.md` rather
  than pretending parity with Android.
- **iOS has never been compiled on this project.** If you touch `iosMain`, verify with
  the metadata task and say clearly in your summary that it remains unbuilt.

### B5. Safety requirements

- There must **always** be a reachable way to stop the alarm. If the full-screen alert
  cannot show, the notification actions must be present. Test with full-screen intent
  denied.
- The alarm must never trigger from a **simulated** event without an obvious "DEMO"
  label on screen — Demo Mode must not be mistakable for a real earthquake.
- Guarantee release of the wake lock and audio focus on every exit path, including
  process death.

---

## Definition of done

- `.\gradlew.bat :shared:compileCommonMainKotlinMetadata` passes (Android + iOS)
- `.\gradlew.bat :app:assembleDebug` and `:app:assembleRelease` both pass
- The APK still contains **28** `ic_sg_*` icons and **5** Inter fonts under
  `assets/composeResources/` — this is the canary for the resource-packaging
  workaround described in `CLAUDE.md`; check it, do not assume it
- Red alert: alarm loops through lock, background, notification swipe, and silent mode;
  stops **only** on I'm Safe / I Need Help / Stop alarm
- Every screen checked in light **and** dark theme
- No inline `fontSize`/`fontWeight`/raw `dp` left in `ui/screens/`
- No user-facing string literals left in composables

## Do not

- Do not add a DI framework, ViewModels, or a navigation library — the existing
  `StateFlow` + back-stack approach is deliberate and adequate
- Do not restyle by scattering more gradients; fix hierarchy instead
- Do not change the Firestore schema or the intensity thresholds — the ESP32 firmware
  depends on both
- Do not claim iOS behaviour is verified; it cannot be built on this machine
- Do not ship the genuine official NDRRMC/PHIVOLCS alert tone
