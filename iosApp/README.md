# iOS app — setup (must be done on a Mac)

Everything in this folder was written on **Windows** and has **never been compiled or
run**. Kotlin/Native cannot build Apple targets off macOS, so treat all of it as
unverified until it builds in Xcode.

The Xcode project file (`iosApp.xcodeproj`) is **not** in the repo — a `.pbxproj` is
not something that can be authored reliably by hand. Create it in Xcode on the Mac and
add the two Swift files below to it.

---

## Prerequisites

| Requirement | Why |
|---|---|
| A Mac with Xcode 15+ | Only way to compile Kotlin/Native Apple targets and sign an app |
| CocoaPods (`sudo gem install cocoapods`) | Pulls the Firebase iOS SDK |
| **Paid Apple Developer account ($99/yr)** | Push Notifications capability + APNs. Free provisioning does **not** include it, and without APNs the app can never deliver an earthquake alert |
| An iOS app registered in Firebase | The project currently has only an Android app |

## 1. Create the Xcode project

```
Xcode → File → New → Project → iOS → App
  Product Name:  iosApp
  Interface:     SwiftUI
  Language:      Swift
  Location:      <repo>/iosApp
```

Then:
- Delete the generated `ContentView.swift` and `iosAppApp.swift`
- Add the existing `iosApp/iosApp/iOSApp.swift` and `iosApp/iosApp/ContentView.swift`
- Replace the generated `Info.plist` with `iosApp/iosApp/Info.plist`
- Set the bundle identifier to **`com.research.siren`** (must match Firebase)
- Set the deployment target to **iOS 15.0**

## 2. Link the shared Compose framework

Add a **Run Script** build phase, placed **before** "Compile Sources":

```bash
cd "$SRCROOT/.."
./gradlew :shared:embedAndSignAppleFrameworkForXcode
```

Then in **Build Settings → Framework Search Paths** add:

```
$(SRCROOT)/../shared/build/xcode-frameworks/$(CONFIGURATION)/$(SDK_NAME)
```

Kotlin exports the framework as `ComposeApp` (see `shared/build.gradle.kts`), which is
what `import ComposeApp` in `ContentView.swift` resolves to.

## 3. Firebase

1. Firebase console → project **quicktrip-fe547** → Add app → iOS
2. Bundle ID: `com.research.siren`
3. Download **`GoogleService-Info.plist`** and drag it into the Xcode project
   (tick "Copy items if needed")
4. Project Settings → Cloud Messaging → **upload an APNs auth key** (`.p8`) from
   your Apple Developer account. Without this, pushes silently never arrive.
5. In Xcode → Signing & Capabilities, add **Push Notifications** and
   **Background Modes → Remote notifications**

## 4. Pods

```bash
cd iosApp
pod install
open iosApp.xcworkspace     # NOT iosApp.xcodeproj
```

## 5. Build

```bash
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64   # sanity-check the Kotlin side
```

then Run from Xcode.

---

## Known gaps to finish on the Mac

- **`IosPlatformServices.subscribeToAlertsTopic()` is a no-op.** The Swift
  `AppDelegate` currently does the topic subscription instead. If you prefer it in
  Kotlin, wire it once the FirebaseMessaging pod is linked.
- **Critical alerts** (Red bypassing silent mode) need a separate entitlement that
  Apple grants only on request. Until then, `UNNotificationSound.defaultCriticalSound()`
  falls back to a normal sound.
- **Vibration is approximated.** iOS has no arbitrary vibration patterns like
  Android's `Vibrator`; `IosPlatformServices` repeats Taptic feedback plus the system
  vibrate sound instead.
- **`iosX64` is not a target** (Intel-Mac simulator). Apple Silicon uses
  `iosSimulatorArm64`; add `iosX64()` back in `shared/build.gradle.kts` only if you
  need an Intel Mac, and check every dependency still publishes that variant.
