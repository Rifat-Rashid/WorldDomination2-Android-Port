# Android beta release

The beta uses the native Android `:app` module, Java 11 source compatibility,
JDK 17, and the Gradle wrapper. It runs offline. The current beta version is
`0.1.0-beta.8`, version code `9`.

## Build and verify

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
.\gradlew.bat :app:bundleRelease :app:testReleaseUnitTest :app:lintRelease
```

The release bundle is `app/build/outputs/bundle/release/app-release.aab`.
Without upload credentials it is **unsigned**, for validation only. CI never
uses the debug key to sign a store bundle and does not publish to Play.

CI separately validates release compilation/tests/lint, bundletool structure,
offline audio/UI contents, 16 KB native-library compatibility, and Android GLES
mask rendering, confirm/AI playback, source action-menu touch routing, and Resources/Production/Technology budgeting and cancellation on an emulator. The regular debug and deterministic gameplay
checks also remain enabled.

## Prepare a Play upload

Choose the permanent application ID and the upload key for the Play Console
app before the first upload. `com.example.worlddomination` is the existing
development ID; `preparePlayBeta` rejects it for uploads.

Keep the following settings in user-level Gradle properties outside the
repository, or use the corresponding environment variables. Do not put
keystores, passwords, or signing configuration in project files or commits.

| Gradle property | Environment variable |
| --- | --- |
| `wd2UploadStoreFile` | `WD2_UPLOAD_STORE_FILE` |
| `wd2UploadStorePassword` | `WD2_UPLOAD_STORE_PASSWORD` |
| `wd2UploadKeyAlias` | `WD2_UPLOAD_KEY_ALIAS` |
| `wd2UploadKeyPassword` | `WD2_UPLOAD_KEY_PASSWORD` |

With all four configured, use the confirmed ID:

```powershell
.\gradlew.bat --no-configuration-cache :app:preparePlayBeta -Pwd2ApplicationId=your.permanent.id
```

Back up the upload keystore securely. Android Studio's normal **Generate Signed
App Bundle / APK** workflow is also supported.

Before uploading, use the official bundletool to validate the bundle and dump
its manifest, then run:

```text
python scripts/verify_android_bundle.py app/build/outputs/bundle/release/app-release.aab --manifest manifest.xml --require-upload
```

This gate rejects an unsigned bundle, a placeholder application ID, a
debuggable manifest, missing current UI/audio, and incompatible 64-bit native
libraries. Signature authenticity should also be checked with JDK `jarsigner`.
Increase the version code for each subsequent Play upload.

## Beta scope

The beta includes source flag colors, faith poles and 24 Hz cloth animation,
conquest ownership updates, empty flags for unused islands, and a clean original
terrain layer with native cities instead of fixed buildings underneath them.
Existing enhanced map and font assets remain preserved. The terrain currently
retains the prior map's island skins; dynamic terrain-style selection remains
a parity task. It also includes the sharper Resources, Technology and bounded Mass Media screens, source Technology color transforms and clipped HUD compositing, original Production/Technology warning dialogs that retain selected budgets, larger
information rows with the INFO PANEL heading removed, original action-slot
animation, fixed first/second action slots and the original empty-slot Skip dialog, animated confirm closure and original AI action announcements, source diplomacy/propaganda/defense/prepared/direct-attack menus, corrected enemy portraits, custom launcher icon, and recovered
gameplay rules. It is a work-in-progress port, not a claim of complete Flash
parity. AI scoring and resolved-turn tests compare seeded Python reference sessions of up to 40 turns, including a terminal defeat. This verifies those scenarios; it does not establish exhaustive AI parity.

Broader visual/animation/audio comparisons, long play sessions,
additional physical devices, and lifecycle recovery remain part of testing.

For an internal or closed Play test, complete the Play Console listing,
content-rating, audience, data-safety, app-access, and any account-specific
testing requirements. Confirm distribution rights to the original game content
before store publication. This project is an unofficial port; see the main
README's attribution and status.
