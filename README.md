<div align="center">

# World Domination 2 - Android Port

**A native, offline Android adaptation built with Java and libGDX.**

[![Android build](https://github.com/Rifat-Rashid/WorldDomination2-Android-Port/actions/workflows/android-build.yml/badge.svg?branch=main)](https://github.com/Rifat-Rashid/WorldDomination2-Android-Port/actions/workflows/android-build.yml)
[![Android quality](https://github.com/Rifat-Rashid/WorldDomination2-Android-Port/actions/workflows/android-quality.yml/badge.svg?branch=main)](https://github.com/Rifat-Rashid/WorldDomination2-Android-Port/actions/workflows/android-quality.yml)
[![Java 11](https://img.shields.io/badge/Java-11-ED8B00?logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Android](https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white)](https://developer.android.com/)

</div>

This project ports the original *World Domination 2* game to Android. It uses the original game as the reference for its battlefield, interface, artwork, and rules while rebuilding the app in Java with libGDX and OpenGL ES.

> **Status: In active development.** The Android app builds and runs, and core screens and gameplay systems are being brought into parity. This is not yet a complete feature-for-feature port.

## Screenshots

Captured from the Android emulator. Select an image to view it at full size.

<p align="center">
  <a href="docs/screenshots/character-selection.png">
    <img src="docs/screenshots/character-selection.png" width="49%" alt="World Domination 2 Android character and game setup screen">
  </a>
  <a href="docs/screenshots/gameplay.png">
    <img src="docs/screenshots/gameplay.png" width="49%" alt="World Domination 2 Android gameplay screen showing the five-island battlefield and equipment panel">
  </a>
</p>

<p align="center"><sub>Character and game setup | Five-island battlefield</sub></p>

## Project details

- Native Android application using Java and libGDX; no Python runtime, WebView, or network service is used for gameplay.
- Landscape, immersive game presentation with the original five-island battlefield and metallic interface style.
- Original Flash-derived game artwork is loaded into cached atlases and rendered through libGDX.
- The simulation, AI, rendering, UI, assets, audio, and persistence code are organized into separate packages.
- Android source compatibility is Java 11; builds use JDK 17 and the Android Gradle Plugin.

The port is being developed against the original game's implemented behavior. Screens and systems are still under active restoration and validation.

## Build and run

Open this repository in Android Studio, allow Gradle sync to finish, then run the `app` configuration on an Android emulator or device.

Requirements: JDK 17 and Android SDK Platform 37 with Build Tools 36.0.0.

Build a debug APK on Windows:

```powershell
.\gradlew.bat :app:assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

Run the local unit tests and Android lint:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug
```

GitHub Actions runs Android build, unit-test, lint, dependency, and project-integrity checks for changes to `main`.

## Repository map

| Path | Purpose |
| --- | --- |
| `app/src/main/java/com/example/worlddomination/game/simulation/` | Game state and deterministic rules |
| `app/src/main/java/com/example/worlddomination/game/ai/` | Opponent decision logic |
| `app/src/main/java/com/example/worlddomination/game/rendering/` | libGDX rendering and animation |
| `app/src/main/java/com/example/worlddomination/game/ui/` | Screen layout and UI behavior |
| `app/src/main/assets/original/` | Packaged original artwork and runtime assets |
| `app/src/test/` | Unit tests for gameplay and rendering support |
| `docs/screenshots/` | README screenshots captured from Android |

## License

The repository source code is licensed under Apache-2.0; see [LICENSE](LICENSE).
