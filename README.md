# Teller for iOS (Compose Multiplatform)

**Teller** is a comprehensive accounting, GST compliance, and inventory management application for iOS, powered by **Compose Multiplatform (CMP)** and **SwiftUI**.

---

## Architecture Overview

```
                      ┌─────────────────────────┐
                      │    Teller iOS App       │
                      │  (SwiftUI + Xcode 15+)  │
                      └────────────┬────────────┘
                                   │
                                   ▼
                      ┌─────────────────────────┐
                      │   ComposeUIViewController│
                      │   (MainViewController)   │
                      └────────────┬────────────┘
                                   │
                      ┌─────────────────────────┐
                      │       composeApp        │
                      │ (Shared KMP Logic & UI) │
                      └────────────┬────────────┘
         ┌─────────────────────────┼─────────────────────────┐
         ▼                         ▼                         ▼
┌──────────────────┐      ┌──────────────────┐      ┌──────────────────┐
│  Accounting Core │      │ Room Database v33│      │  Compose UI &    │
│  (GST, Vouchers) │      │ (Bundled SQLite) │      │  40+ M3 Screens  │
└──────────────────┘      └──────────────────┘      └──────────────────┘
```

### Key Modules:
- **`composeApp/`**: Kotlin Multiplatform shared module containing all accounting logic, 40+ Jetpack Compose screens, ViewModels, Room database entities, and GST engines.
  - `src/commonMain`: Shared cross-platform code.
  - `src/iosMain`: iOS platform bindings (`MainViewController.kt`, `Platform.ios.kt`, Room bundled SQLite driver).
- **`iosApp/`**: Native Xcode project (`iosApp.xcodeproj`) embedding the shared Kotlin framework.
  - `iOSApp.swift`: SwiftUI `@main` lifecycle entry.
  - `ContentView.swift`: Embeds `ComposeUIViewController`.
  - `Info.plist`: Camera, Photo Library, Biometrics (Face ID) permissions.

---

## Prerequisites

- **macOS** (14.0 Sonoma or newer recommended)
- **Xcode** 15.0 or newer
- **JDK 17 or 21** (`JAVA_HOME` pointing to OpenJDK 17/21)
- **CocoaPods** (optional) / Gradle 8.5+

---

## How to Build & Run

### 1. Build Shared Framework
From the terminal in the root directory:
```bash
./gradlew :composeApp:assembleComposeAppDebugXCFramework
```

### 2. Open in Xcode
Open the Xcode project:
```bash
open iosApp/iosApp.xcodeproj
```

Select an iOS Simulator (e.g., iPhone 15 Pro or iPhone 16) and press **Cmd + R** to run.

---

## Continuous Integration (CI)

A GitHub Actions workflow is provided at `.github/workflows/ios-build.yml` which automatically:
1. Provisions a macOS runner (`macos-14`).
2. Configures JDK 21 and Xcode.
3. Assembles the Compose Multiplatform framework.
4. Validates the Xcode build against the iOS Simulator destination.
