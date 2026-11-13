"""Subprocess checks for CI's one-command Android test runner."""

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
    def run_case(self, gradle_status, verifier_status, expected_status):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            (root / "scripts").mkdir()
            (root / "bin").mkdir()
            shutil.copyfile(SCRIPT, root / "scripts" / SCRIPT.name)
            mocks = {
                "gradlew": 'printf "gradle %s\\n" "$*" >> "$TRACE"\n'
                           'exit "$GRADLE_STATUS"\n',
                "python3": 'printf "verify %s\\n" "$*" >> "$TRACE"\n'
                           'exit "$VERIFIER_STATUS"\n',
                "adb": 'printf "adb %s\\n" "$*" >> "$TRACE"\n'
                       'case "$2" in *beta-ui-evidence) exit 13;; esac\n'
                       'exit 0\n',
            }
            for name, body in mocks.items():
                destination = (root / "gradlew" if name == "gradlew"
                               else root / "bin" / name)
                destination.write_text("#!/usr/bin/env bash\n" + body, encoding="utf-8")
                destination.chmod(0o755)
            trace = root / "calls.txt"
            env = os.environ.copy()
            env.update({
                "GRADLE_STATUS": str(gradle_status),
                "VERIFIER_STATUS": str(verifier_status),
                "TRACE": str(trace),
                "PATH": str(root / "bin") + os.pathsep + env.get("PATH", ""),
            })
            result = subprocess.run(
                [bash_executable(), "scripts/run_android_native_tests.sh",
                 "example.First,example.Second"], cwd=root, env=env,
                text=True, capture_output=True, check=False)
            self.assertEqual(expected_status, result.returncode,
                             result.stdout + result.stderr)
            calls = trace.read_text(encoding="utf-8").splitlines()
            self.assertTrue(calls[0].startswith("gradle --no-daemon --stacktrace "
                                                    ":app:connectedDebugAndroidTest "), calls)
            self.assertIn("-Pandroid.testInstrumentationRunnerArguments.class="
                          "example.First,example.Second", calls[0])
            self.assertIn("-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true",
                          calls[0])
            self.assertEqual(gradle_status == 0,
                             any(call.startswith("verify ") for call in calls), calls)
            self.assertEqual(1, sum("beta-ui-evidence" in call for call in calls), calls)
            self.assertEqual(1, sum("propaganda-evidence" in call for call in calls), calls)
            self.assertTrue(calls[-2].startswith("adb pull "), calls)
            self.assertTrue(calls[-1].startswith("adb pull "), calls)

    def test_success_survives_failed_evidence_pull(self):
        self.run_case(0, 0, 0)

    def test_gradle_failure_skips_verifier_and_preserves_status(self):
        self.run_case(7, 0, 7)

    def test_verifier_failure_preserves_status(self):
        self.run_case(0, 9, 9)


if __name__ == "__main__":
    unittest.main()
