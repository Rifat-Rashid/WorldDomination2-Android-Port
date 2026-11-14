#!/usr/bin/env bash
set -euo pipefail

classes="${1:?Pass the comma-separated Android instrumentation classes}"

reports="app/build/reports/androidTests"
staging="app/build/native-evidence-staging"
device_files="/sdcard/Android/data/com.example.worlddomination/files"

collect_directory() {
  local name="$1" attempt stage candidate png_count
  if [[ -e "$reports/$name" ]]; then
    echo "Evidence $name: stale destination could not be cleared" >&2
    return 1
  fi
  for attempt in 1 2 3; do
    echo "Evidence $name: attempt $attempt/3"
    stage="$(mktemp -d "$staging/$name.attempt.XXXXXX")" || return 1
    candidate="$stage/$name"
    if (( attempt > 1 )); then
      timeout --kill-after=5s 5s adb reconnect offline || true
    fi
    if ! timeout --kill-after=5s 10s adb wait-for-device; then
      echo "Evidence $name: device unavailable on attempt $attempt" >&2
    elif ! timeout --kill-after=5s 30s adb pull "$device_files/$name" "$candidate"; then
      echo "Evidence $name: pull failed on attempt $attempt" >&2
    else
      if ! png_count="$(find "$candidate" -type f -iname '*.png' 2>/dev/null | wc -l)"; then
        echo "Evidence $name: could not inspect pulled files on attempt $attempt" >&2
      elif (( png_count > 0 )); then
        if ! mv -- "$candidate" "$reports/$name"; then
          echo "Evidence $name: publishing recovered files failed" >&2
          return 1
        fi
        if ! remove_stage "$name" "$stage"; then return 1; fi
        echo "Evidence $name: recovered $png_count PNG(s)"
        return 0
      else
        echo "Evidence $name: pull contained no PNGs on attempt $attempt" >&2
      fi
    fi
    if ! remove_stage "$name" "$stage"; then return 1; fi
    if (( attempt < 3 )); then sleep 1; fi
  done
  return 1
}

remove_stage() {
  local name="$1" stage="$2"
  if [[ "$stage" != "$staging/$name.attempt."* ]] \
      || [[ "$(cd "$stage" && pwd -P)" != "$stage" ]]; then
    echo "Evidence $name: unsafe temporary destination" >&2
    return 1
  fi
  rm -rf -- "$stage"
}

collect_evidence() {
  status=$?
  trap - EXIT
  set +e
  if ! mkdir -p "$reports" "$staging"; then
    echo "::error title=Android native screenshots::Could not create evidence directories" >&2
    if (( status != 0 )); then exit "$status"; fi
    exit 86
  fi
  # Resolve the parent before removing old evidence, so this invocation never
  # publishes stale files from a previous test run.
  workspace="$(pwd -P)"
  resolved_reports="$(cd "$reports" && pwd -P)"
  resolved_staging="$(cd "$staging" && pwd -P)"
  if [[ "$resolved_reports" != "$workspace/app/build/reports/androidTests" ]] \
      || [[ "$resolved_staging" != "$workspace/app/build/native-evidence-staging" ]]; then
    echo "::error title=Android native screenshots::Unsafe evidence destination" >&2
    if (( status != 0 )); then exit "$status"; fi
    exit 86
  fi
  reports="$resolved_reports"
  staging="$resolved_staging"
  evidence_status=0
  rm -rf -- "$reports/beta-ui-evidence" || evidence_status=1
  rm -rf -- "$reports/propaganda-evidence" || evidence_status=1
  collect_directory beta-ui-evidence || evidence_status=1
  collect_directory propaganda-evidence || evidence_status=1
  if (( evidence_status != 0 )); then
    echo "::error title=Android native screenshots::Required screenshot evidence could not be recovered" >&2
  fi
  if (( status == 0 && evidence_status != 0 )); then exit 86; fi
  exit "$status"
}
trap collect_evidence EXIT

./gradlew --no-daemon --stacktrace :app:connectedDebugAndroidTest \
  "-Pandroid.testInstrumentationRunnerArguments.class=$classes" \
  -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true
python3 scripts/verify_junit_results.py app/build/outputs/androidTest-results/connected/debug
