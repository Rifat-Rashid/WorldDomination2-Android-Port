<div align="center">

# World Domination 2 — Android Port

**A native Android reimplementation of the five-island strategy game, built with Java and libGDX.**

[![Android build](https://github.com/Rifat-Rashid/WorldDomination2-Android-Port/actions/workflows/android-build.yml/badge.svg?branch=main)](https://github.com/Rifat-Rashid/WorldDomination2-Android-Port/actions/workflows/android-build.yml)
[![Android tests](https://github.com/Rifat-Rashid/WorldDomination2-Android-Port/actions/workflows/android-tests.yml/badge.svg?branch=main)](https://github.com/Rifat-Rashid/WorldDomination2-Android-Port/actions/workflows/android-tests.yml)
[![Android quality](https://github.com/Rifat-Rashid/WorldDomination2-Android-Port/actions/workflows/android-quality.yml/badge.svg?branch=main)](https://github.com/Rifat-Rashid/WorldDomination2-Android-Port/actions/workflows/android-quality.yml)
[![Platform: Android](https://img.shields.io/badge/platform-Android-3DDC84?logo=android&logoColor=white)](https://developer.android.com/)
[![Java 11](https://img.shields.io/badge/Java-11-ED8B00?logo=openjdk&logoColor=white)](https://openjdk.org/)

</div>

## Screenshots

The current Android build includes the game setup flow and the five-island battlefield. Select either image to view it at full size.

<p align="center">
  <a href="docs/screenshots/character-selection.png">
    <img src="docs/screenshots/character-selection.png" width="49%" alt="Character selection: choose difficulty, player color, building style, and opponents">
  </a>
  <a href="docs/screenshots/gameplay.png">
    <img src="docs/screenshots/gameplay.png" width="49%" alt="Gameplay: five-island battlefield between the navigation, status, and equipment panels">
  </a>
</p>

<p align="center"><strong>Character selection</strong> · <strong>Gameplay</strong></p>

## About

World Domination 2 is a turn-based strategy game about managing resources, developing technology, building an arsenal, and competing for control of a five-island world. This project ports the game to Android as a Java application rendered with libGDX and OpenGL ES.

> **Development status:** Active. The Android app builds and runs, while gameplay behavior and the original presentation continue to be ported and checked against the Python reference. Full feature parity is not complete. Screenshots show the current Android build.

## Project highlights

- Android-only application with offline gameplay; no Python runtime, WebView, or network service is required to play.
- Landscape interface based on the original five-island battlefield and metallic game UI.
- Original artwork and audio packaged with the app and loaded through cached asset pipelines.
- Separate packages for simulation, opponent AI, rendering, UI, audio, assets, and persistence.
- Java 11 source compatibility, built with JDK 17 and the Android Gradle Plugin.

## Build and run

Open this repository in Android Studio, allow Gradle to sync, then run the `app` configuration on an Android emulator or device.

| Requirement | Version |
| --- | --- |
| JDK | 17 |
| Android SDK Platform | 37 |
| Android Build Tools | 36.0.0 |

Build a debug APK on Windows:

```powershell
.\gradlew.bat :app:assembleDebug
```

On macOS or Linux:

```bash
./gradlew :app:assembleDebug
```

Run the unit tests and Android lint on Windows:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`. GitHub Actions also validates Android assembly, unit tests, and lint for changes to `main`.

## Code map

| Directory | Responsibility |
| --- | --- |
| `app/src/main/java/com/example/worlddomination/game/simulation/` | Game state, rules, and turn resolution |
| `app/src/main/java/com/example/worlddomination/game/ai/` | Opponent decision-making |
| `app/src/main/java/com/example/worlddomination/game/rendering/` | libGDX battlefield, interface, and animation rendering |
| `app/src/main/java/com/example/worlddomination/game/ui/` | Screen layout and interaction behavior |
| `app/src/main/assets/original/` | Packaged original artwork and runtime assets |
| `app/src/test/` | Deterministic gameplay and presentation support tests |
| `docs/screenshots/` | Character selection and gameplay captures shown above |

The Python implementation is kept as a read-only behavioral reference. For current progress and known parity gaps, see the pull requests and project issues.

## License

Source code is licensed under Apache-2.0. See [LICENSE](LICENSE).

## Android beta builds

See [the beta release guide](docs/BETA_RELEASE.md) for release validation, external upload signing, and the remaining beta testing scope. CI builds an unsigned validation bundle; store uploads require the permanent app ID and upload key.
