"""Mocked subprocess coverage for CI's Android instrumentation wrapper."""

import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest


SCRIPT = Path(__file__).resolve().parents[1] / "run_android_native_tests.sh"


def bash_executable():
    git = shutil.which("git")
    if git and os.name == "nt":
        installation = Path(git).resolve().parent.parent
        for candidate in (installation / "bin" / "bash.exe",
                          installation / "usr" / "bin" / "bash.exe"):
            if candidate.is_file():
                return str(candidate)
    candidate = shutil.which("bash")
    if candidate:
        return candidate
    raise AssertionError("Bash is required to test the Android CI runner")


class AndroidNativeRunnerTest(unittest.TestCase):
    def run_case(self, scenario, gradle_status=0, verifier_status=0,
                 required_folders=None, valid=True):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            (root / "scripts").mkdir()
            (root / "bin").mkdir()
            (root / "state").mkdir()
            shutil.copyfile(SCRIPT, root / "scripts" / SCRIPT.name)
            mocks = {
                "gradlew": 'printf "gradle %s\\n" "$*" >> "$TRACE"\n'
                           'exit "$GRADLE_STATUS"\n',
                "python3": 'printf "verify %s\\n" "$*" >> "$TRACE"\n'
                           'exit "$VERIFIER_STATUS"\n',
                "timeout": 'printf "timeout %s\\n" "$*" >> "$TRACE"\n'
                           'shift 2\n'
                           'if [[ "$SCENARIO" == "timeout-pull" && "$*" == '
                           '*"adb pull"*"beta-ui-evidence"* ]]; then exit 124; fi\n'
                           '"$@"\n',
                "sleep": 'printf "sleep %s\\n" "$*" >> "$TRACE"\n',
                "adb": r'''printf 'adb %s\n' "$*" >> "$TRACE"
if [[ "$1" == "wait-for-device" ]]; then
  if [[ "$SCENARIO" == "permanent-offline" ]]; then exit 1; fi
  if [[ "$SCENARIO" == "transient-offline" && ! -e "$MOCK_STATE/offline-seen" ]]; then
    touch "$MOCK_STATE/offline-seen"
    exit 1
  fi
  exit 0
fi
if [[ "$1" == "reconnect" ]]; then exit 0; fi
if [[ "$1" != "pull" ]]; then exit 2; fi
name="${2##*/}"
counter="$MOCK_STATE/$name-count"
count=0
if [[ -e "$counter" ]]; then count="$(cat "$counter")"; fi
count=$((count + 1))
printf '%s' "$count" > "$counter"
mkdir -p "$3"
if [[ "$name" == "beta-ui-evidence" ]]; then
  case "$SCENARIO" in
    permanent-error) printf 'partial' > "$3/partial.png"; exit 13 ;;
    empty-success) exit 0 ;;
    partial-recovery)
      if (( count == 1 )); then
        printf 'partial' > "$3/stale.png"
        exit 13
      fi ;;
  esac
  printf 'fresh' > "$3/fresh.png"
else
  if [[ "$SCENARIO" == "propaganda-error" ]]; then exit 13; fi
  printf 'propaganda' > "$3/propaganda.png"
fi
''',
            }
            for name, body in mocks.items():
                destination = (root / "gradlew" if name == "gradlew"
                               else root / "bin" / name)
                destination.write_text("#!/usr/bin/env bash\n" + body,
                                       encoding="utf-8")
                destination.chmod(0o755)
            reports = root / "app/build/reports/androidTests"
            old_beta = reports / "beta-ui-evidence"
            old_beta.mkdir(parents=True)
            (old_beta / "old.png").write_text("stale", encoding="utf-8")
            old_propaganda = reports / "propaganda-evidence"
            old_propaganda.mkdir()
            (old_propaganda / "old.png").write_text("stale", encoding="utf-8")

            trace = root / "calls.txt"
            env = os.environ.copy()
            env.update({
                "SCENARIO": scenario,
                "GRADLE_STATUS": str(gradle_status),
                "VERIFIER_STATUS": str(verifier_status),
                "MOCK_STATE": str(root / "state"),
                "TRACE": str(trace),
                "PATH": str(root / "bin") + os.pathsep + env.get("PATH", ""),
            })
            requested = (["beta-ui-evidence", "propaganda-evidence"]
                         if required_folders is None else required_folders)
            result = subprocess.run(
                [bash_executable(), "-c",
                 'export PATH="$PWD/bin:$PATH"; '
                 'exec bash scripts/run_android_native_tests.sh "$@"',
                 "native-runner", "example.First,example.Second",
                 *(required_folders or [])], cwd=root, env=env,
                text=True, capture_output=True, check=False, timeout=20)
            calls = (trace.read_text(encoding="utf-8").splitlines()
                     if trace.exists() else [])
            if not valid:
                self.assertEqual([], calls)
                self.assertEqual(["old.png"],
                                 sorted(path.name for path in old_beta.glob("*.png")))
                self.assertEqual(["old.png"],
                                 sorted(path.name for path in old_propaganda.glob("*.png")))
                return result, calls, None
            self.assertTrue(calls[0].startswith("gradle --no-daemon --stacktrace "
                                                    ":app:connectedDebugAndroidTest "), calls)
            self.assertIn("-Pandroid.testInstrumentationRunnerArguments.class="
                          "example.First,example.Second", calls[0])
            self.assertIn("-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true",
                          calls[0])
            self.assertEqual(gradle_status == 0,
                             any(call.startswith("verify ") for call in calls), calls)
            for name in requested:
                self.assertIn("Evidence " + name + ": attempt 1/3", result.stdout)
            for name in {"beta-ui-evidence", "propaganda-evidence"} - set(requested):
                self.assertNotIn("Evidence " + name + ": attempt", result.stdout)
            self.assertNotIn("old.png", [path.name for path in reports.rglob("*.png")])
            for call in calls:
                if call.startswith("timeout ") and "adb pull" in call:
                    self.assertTrue(call.startswith(
                        "timeout --kill-after=5s 30s adb pull "), call)
                if call.startswith("timeout ") and "wait-for-device" in call:
                    self.assertEqual(
                        "timeout --kill-after=5s 10s adb wait-for-device", call)
            return result, calls, {
                name: sorted(path.name for path in (reports / name).glob("*.png"))
                for name in ("beta-ui-evidence", "propaganda-evidence")
            }

    def test_success_publishes_both_directories(self):
        result, calls, files = self.run_case("success")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(["fresh.png"], files["beta-ui-evidence"])
        self.assertEqual(["propaganda.png"], files["propaganda-evidence"])
        self.assertEqual(1, sum(call.startswith("adb pull ") and "beta-ui-evidence" in call
                                for call in calls))

    def test_single_required_folder_runs_all_tests_and_only_pulls_beta(self):
        result, calls, files = self.run_case(
            "success", required_folders=["beta-ui-evidence"])
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(["fresh.png"], files["beta-ui-evidence"])
        self.assertEqual([], files["propaganda-evidence"])
        self.assertEqual(1, sum(call.startswith("adb pull ") for call in calls))
        self.assertTrue(any(call.startswith("verify ") for call in calls))

    def test_explicit_both_folders_follow_requested_order(self):
        result, calls, files = self.run_case(
            "success", required_folders=["propaganda-evidence", "beta-ui-evidence"])
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(["fresh.png"], files["beta-ui-evidence"])
        self.assertEqual(["propaganda.png"], files["propaganda-evidence"])
        pulls = [call for call in calls if call.startswith("adb pull ")]
        self.assertIn("propaganda-evidence", pulls[0])
        self.assertIn("beta-ui-evidence", pulls[1])

    def test_single_folder_failure_retains_gradle_status(self):
        result, calls, files = self.run_case(
            "permanent-error", gradle_status=7,
            required_folders=["beta-ui-evidence"])
        self.assertEqual(7, result.returncode)
        self.assertEqual([], files["beta-ui-evidence"])
        self.assertEqual([], files["propaganda-evidence"])
        self.assertEqual(3, sum(call.startswith("adb pull ") for call in calls))

    def test_invalid_folder_arguments_fail_before_gradle_or_cleanup(self):
        for folders in ([""], ["beta-ui-evidence", "beta-ui-evidence"],
                        ["../beta-ui-evidence"], ["propaganda-evidence/child"],
                        ["beta-ui-evidence/"], ["unknown-evidence"]):
            with self.subTest(folders=folders):
                result, calls, _ = self.run_case(
                    "success", required_folders=folders, valid=False)
                self.assertEqual(64, result.returncode)
                self.assertIn("evidence folder", result.stderr)

    def test_transient_offline_wait_recovers_without_real_sleep(self):
        result, calls, files = self.run_case("transient-offline")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("adb reconnect offline", calls)
        self.assertIn("sleep 1", calls)
        self.assertEqual(["fresh.png"], files["beta-ui-evidence"])

    def test_permanent_pull_failure_reports_artifact_error_and_tries_both(self):
        result, calls, files = self.run_case("permanent-error")
        self.assertEqual(86, result.returncode)
        self.assertIn("::error title=Android native screenshots::", result.stderr)
        self.assertEqual([], files["beta-ui-evidence"])
        self.assertEqual(["propaganda.png"], files["propaganda-evidence"])
        self.assertEqual(3, sum(call.startswith("adb pull ") and "beta-ui-evidence" in call
                                for call in calls))

    def test_empty_success_is_not_published(self):
        result, calls, files = self.run_case("empty-success")
        self.assertEqual(86, result.returncode)
        self.assertIn("contained no PNGs", result.stderr)
        self.assertEqual([], files["beta-ui-evidence"])
        self.assertEqual(3, sum(call.startswith("adb pull ") and "beta-ui-evidence" in call
                                for call in calls))

    def test_partial_failed_pull_cannot_leak_stale_png(self):
        result, calls, files = self.run_case("partial-recovery")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(["fresh.png"], files["beta-ui-evidence"])
        self.assertEqual(2, sum(call.startswith("adb pull ") and "beta-ui-evidence" in call
                                for call in calls))

    def test_gradle_failure_preserves_original_status_and_collects_both(self):
        result, calls, files = self.run_case("success", gradle_status=7)
        self.assertEqual(7, result.returncode)
        self.assertFalse(any(call.startswith("verify ") for call in calls))
        self.assertEqual(["fresh.png"], files["beta-ui-evidence"])
        self.assertEqual(["propaganda.png"], files["propaganda-evidence"])

    def test_verifier_failure_preserves_original_status_and_collects_both(self):
        result, calls, files = self.run_case("success", verifier_status=9)
        self.assertEqual(9, result.returncode)
        self.assertTrue(any(call.startswith("verify ") for call in calls))
        self.assertEqual(["fresh.png"], files["beta-ui-evidence"])
        self.assertEqual(["propaganda.png"], files["propaganda-evidence"])

    def test_gradle_failure_wins_over_artifact_failure(self):
        result, calls, files = self.run_case("permanent-error", gradle_status=7)
        self.assertEqual(7, result.returncode)
        self.assertEqual([], files["beta-ui-evidence"])
        self.assertEqual(["propaganda.png"], files["propaganda-evidence"])

    def test_permanent_offline_waits_are_bounded_and_both_dirs_attempted(self):
        result, calls, files = self.run_case("permanent-offline")
        self.assertEqual(86, result.returncode)
        self.assertEqual([], files["beta-ui-evidence"])
        self.assertEqual([], files["propaganda-evidence"])
        self.assertEqual(6, sum(call == "adb wait-for-device" for call in calls))
        self.assertEqual(0, sum(call.startswith("adb pull ") for call in calls))
        self.assertEqual(4, sum(call == "adb reconnect offline" for call in calls))

    def test_second_directory_failure_does_not_hide_first(self):
        result, calls, files = self.run_case("propaganda-error")
        self.assertEqual(86, result.returncode)
        self.assertEqual(["fresh.png"], files["beta-ui-evidence"])
        self.assertEqual([], files["propaganda-evidence"])
        self.assertEqual(3, sum(call.startswith("adb pull ") and
                                "propaganda-evidence" in call for call in calls))

    def test_verifier_failure_wins_over_evidence_failure(self):
        result, calls, files = self.run_case("propaganda-error", verifier_status=9)
        self.assertEqual(9, result.returncode)
        self.assertEqual(["fresh.png"], files["beta-ui-evidence"])
        self.assertEqual([], files["propaganda-evidence"])
        self.assertTrue(any(call.startswith("verify ") for call in calls))

    def test_timeout_exit_is_retried_and_cannot_publish_evidence(self):
        result, calls, files = self.run_case("timeout-pull")
        self.assertEqual(86, result.returncode)
        self.assertEqual([], files["beta-ui-evidence"])
        self.assertEqual(["propaganda.png"], files["propaganda-evidence"])
        self.assertEqual(3, sum(call.startswith("timeout ") and
                                "adb pull" in call and "beta-ui-evidence" in call
                                for call in calls))


if __name__ == "__main__":
    unittest.main()
