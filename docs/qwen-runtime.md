# Local Qwen opponents

Qwen is optional and starts off. Open Settings and enable **qwen3 0.6b ai** on Android 13 or newer with an arm64-v8a process or an x86_64 process with AVX2, FMA, and F16C support. The game downloads the official 639 MB model over HTTPS on first use. It reports the transferred bytes, verifies the model, reports llama.cpp's weight-loader fraction, and then prepares the inference context. Ordinary opponents remain available until Qwen is ready. Once the model is cached, opponent inference works in airplane mode.

Turning Qwen off cancels work and releases the native engine when the worker stops. The verified model remains in app-private, backup-excluded storage. If the app reports a damaged file or interrupted transfer, use Retry in Settings. Uninstalling the app or clearing its app data also clears the model.

## Reproducible Android build

Install NDK `28.2.13676358` and CMake `3.22.1` through Android Studio's SDK Manager. Use the existing Gradle wrapper and the repository's Android SDK and JDK setup.

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

CMake fetches the pinned llama.cpp source and checks its archive SHA-256. To use a previously downloaded archive during an offline build, add `-Pwd2LlamaSourceArchive=<absolute path>`. This archive must match the same checksum. Qwen ships only on the two supported 64-bit ABIs. The APK continues to ship libGDX on all four existing ABIs, and the application's minimum Android version remains 24.

| Dependency | Pin |
| --- | --- |
| llama.cpp | b7700, commit `707cbafcaa3a88d893147bcc107e76b925a867da` |
| llama.cpp source archive | SHA-256 `777b763cc439c6b88183f73e935b0bef85aea9cb637ef5fcfb918c82eddd099b` |
| Qwen base checkpoint and tokenizer | `Qwen/Qwen3-0.6B@c1899de289a04d12100db370d81485cdf75e47ca` |
| Base `tokenizer_config.json` | SHA-256 `d5d09f07b48c3086c508b30d1c9114bd1189145b74e982a265350c923acd8101` |
| Official deployment GGUF | `Qwen/Qwen3-0.6B-GGUF@23749fefcc72300e3a2ad315e1317431b06b590a`, `Qwen3-0.6B-Q8_0.gguf` |
| Model file | 639,446,688 bytes, SHA-256 `9465e63a22add5354d9bb4b99e90117043c7124007664907259bd16d043bb031` |

The JNI adapter applies the GGUF's Jinja template with `enable_thinking=false`, verifies its empty think block during initialization, and parses the rendered special tokens once. Each independent request starts with an empty KV cache and a new grammar and greedy sampler. The context holds 2,048 tokens, including a 20-token output allowance. The runtime accepts only an eligible ID in the exact JSON selection, and the engine revalidates the selected plan.

## Provision a debug installation without an in-app download

Use the provisioning script to fetch or verify the official file and copy it to a debuggable app with adb. The `wd2DeviceQa` Gradle property adds the model and replay instrumentation to the test APK for dedicated runs. Use a separate application ID for this validation.

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest `
    '-Pwd2ApplicationId=com.example.worlddomination.qwenqa' '-Pwd2DeviceQa=true'
adb -s emulator-5556 install -r -t app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5556 install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
python scripts/provision-qwen-model.py D:/models/Qwen3-0.6B-Q8_0.gguf --download --provision `
    --serial emulator-5556 --package com.example.worlddomination.qwenqa

$runtimeTest = 'com.example.worlddomination.platform.qwen.QwenRuntimeDeviceTest'
$runner = 'com.example.worlddomination.qwenqa.test/androidx.test.runner.AndroidJUnitRunner'
adb -s emulator-5556 shell am instrument -w -e class `
    "$runtimeTest#stockModelCleansUpAndReturnsOnlyCurrentConstrainedSelections,$runtimeTest#damagedCacheReportsRetryAndNeverReachesNativeReady" $runner
adb -s emulator-5556 shell am instrument -w -e wd2QwenOffline true -e wd2QwenRivals 4 -e class `
    com.example.worlddomination.game.rendering.QwenOpponentIntegrationInstrumentedTest $runner
```

Put the emulator or device in airplane mode before the in-game test. The runtime cases require a cached model. They exercise independent constrained requests, deadline and oversized context rejection, cancellation on disable, rapid re-enablement, pause/resume, and recovery from a corrupt file. The in-game case runs on a fresh QA install and checks normal opponent settlement and fallback against a seeded baseline. To evaluate shared decision fixtures, provision a nonempty JSON array with `system`, `user`, and `planIds` fields at `no_backup/qwen3/decision-fixtures.json`, then select `$runtimeTest#sharedDecisionFixturesReturnLegalPlanIds` explicitly.

The initial x86_64 SSE4.2 build loaded the verified cache in 1.7–4.4 seconds offline on an Android 17 emulator. Three shared decisions at 963–1,008 prompt tokens took 31.5–33.9 seconds each. A sample taken during inference showed approximately 968 MiB PSS and 1,079 MiB RSS for the app process. These measurements characterize the emulator and do not establish phone performance or model strategy quality.

The AVX2 stock run downloaded, verified, and initialized the official file in 128.8 seconds on the test emulator. An offline four-opponent round committed three model plans in 37.1–50.1 seconds each and used original-AI fallback after a 60-second timeout for one opponent. The whole round took 185.5 seconds under concurrent emulator load. Its sampled app memory peaked at approximately 1,115 MiB PSS and 1,242 MiB RSS. Phone latency, memory, and sustained use still need measurement.

## Compare a candidate GGUF in a debug build

Set both `-Pwd2QwenDebugModelSha256=<lowercase SHA-256>` and `-Pwd2QwenDebugModelSize=<bytes>` when assembling a debug APK. Provision the file with `scripts/provision-qwen-model.py` using matching `--debug-sha256` and `--debug-size` options in addition to `--provision`. A separate debug application ID keeps the game and model caches isolated. The instrumented runtime uses the same architecture, embedded non-thinking template, context, constraint, and checksum validation as stock. Release builds use the official stock model.

The smoke-trained Q8_0 export completed an offline, one-opponent Android round in 11.1 seconds at 898 prompt tokens. Disabling it during the following decision reproduced the seeded original AI settlement. The smoke run verifies that Android can execute a plan from the exported file. Model quality requires a held-out comparison.
