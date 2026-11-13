#!/usr/bin/env bash
set -euo pipefail

classes="${1:?Pass the comma-separated Android instrumentation classes}"

collect_evidence() {
  status=$?
  trap - EXIT
  set +e
  mkdir -p app/build/reports/androidTests
  adb pull /sdcard/Android/data/com.example.worlddomination/files/beta-ui-evidence app/build/reports/androidTests/beta-ui-evidence
  adb pull /sdcard/Android/data/com.example.worlddomination/files/propaganda-evidence app/build/reports/androidTests/propaganda-evidence
  exit "$status"
}
trap collect_evidence EXIT

./gradlew --no-daemon --stacktrace :app:connectedDebugAndroidTest \
  "-Pandroid.testInstrumentationRunnerArguments.class=$classes" \
  -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true
python3 scripts/verify_junit_results.py app/build/outputs/androidTest-results/connected/debug
