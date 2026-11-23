import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest


SCRIPT = Path(__file__).resolve().parents[1] / "gradle_rate_limit_retry.sh"
BASH = (Path(os.environ.get("ProgramFiles", r"C:\Program Files")) / "Git/bin/bash.exe"
        if os.name == "nt" else Path(shutil.which("bash") or "bash"))


class GradleRateLimitRetryTest(unittest.TestCase):
    def run_scenario(self, body):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            (root / "sleep").write_text("#!/usr/bin/env bash\nexit 0\n", encoding="utf-8")
            command = root / "gradle"
            command.write_text("#!/usr/bin/env bash\n" + body, encoding="utf-8")
            env = dict(os.environ, PATH=str(root) + os.pathsep + str(BASH.parent)
                       + os.pathsep + os.environ["PATH"],
                       RETRY_COUNT_FILE=(root / "count").as_posix())
            for file in (root / "sleep", command):
                file.chmod(0o755)
            result = subprocess.run([str(BASH), SCRIPT.as_posix(), str(BASH), command.as_posix()],
                                    env=env, capture_output=True, text=True, timeout=60)
            count = int((root / "count").read_text()) if (root / "count").exists() else 0
            return result, count

    def test_transient_maven_rate_limit_retries_the_same_command(self):
        result, count = self.run_scenario(
            'n=0; [[ -f "$RETRY_COUNT_FILE" ]] && n=$(cat "$RETRY_COUNT_FILE")\n'
            'n=$((n+1)); echo "$n" > "$RETRY_COUNT_FILE"\n'
            'if (( n == 1 )); then\n'
            ' echo "Could not GET https://repo.maven.apache.org/maven2/a. Received status code 429 from server: Too Many Requests"\n'
            ' exit 1\nfi\n'
            'echo "BUILD SUCCESSFUL"\n')
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(2, count)
        self.assertIn("BUILD SUCCESSFUL", result.stdout)

    def test_test_failure_is_not_retried(self):
        result, count = self.run_scenario(
            'echo 1 > "$RETRY_COUNT_FILE"\necho "Test failed"\nexit 3\n')
        self.assertEqual(3, result.returncode)
        self.assertEqual(1, count)

    def test_unrelated_rate_limit_is_not_retried(self):
        result, count = self.run_scenario(
            'echo 1 > "$RETRY_COUNT_FILE"\n'
            'echo "other.example Received status code 429 from server: Too Many Requests"\nexit 4\n')
        self.assertEqual(4, result.returncode)
        self.assertEqual(1, count)

    def test_a_test_failure_is_not_hidden_by_a_simultaneous_rate_limit(self):
        result, count = self.run_scenario(
            'echo 1 > "$RETRY_COUNT_FILE"\n'
            'echo "https://repo.maven.apache.org/maven2/a Received status code 429 from server: Too Many Requests"\n'
            'echo "There were failing tests"\nexit 5\n')
        self.assertEqual(5, result.returncode)
        self.assertEqual(1, count)

    def test_repeated_rate_limit_fails_after_three_attempts(self):
        result, count = self.run_scenario(
            'n=0; [[ -f "$RETRY_COUNT_FILE" ]] && n=$(cat "$RETRY_COUNT_FILE")\n'
            'n=$((n+1)); echo "$n" > "$RETRY_COUNT_FILE"\n'
            'echo "https://repo.maven.apache.org/maven2/a Received status code 429 from server: Too Many Requests"\nexit 7\n')
        self.assertEqual(7, result.returncode)
        self.assertEqual(3, count)


if __name__ == "__main__":
    unittest.main()
