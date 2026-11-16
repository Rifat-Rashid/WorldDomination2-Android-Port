"""Tests for narrowly scoped Gradle HTTP throttling retries."""
from __future__ import annotations

import contextlib
import io
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import retry_gradle_dependency_download as retry

URL = 'https://repo.maven.apache.org/maven2/org/example/lib/1/lib-1.aar'
THROTTLED = ("* What went wrong:\n> Could not download lib-1.aar (org.example:lib:1)\n"
             f"   > Could not get resource '{URL}'.\n"
             f"      > Could not GET '{URL}'. Received status code 429 from server: Too Many Requests\n")


class RetryGradleDependencyTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)

    def test_actual_gradle_dependency_chain_is_retryable(self):
        self.assertTrue(retry.retryable(THROTTLED, self.root))

    def test_429_must_belong_to_matching_dependency_url(self):
        self.assertFalse(retry.retryable('test HTTP 429 Too Many Requests', self.root))
        self.assertFalse(retry.retryable(THROTTLED.replace("Could not GET '" + URL,
                                                           "Could not GET 'https://other.invalid/x"), self.root))
        self.assertFalse(retry.retryable(THROTTLED.replace('status code 429', 'status code 503'), self.root))
        self.assertFalse(retry.retryable(f"Could not GET '{URL}'. Received status code 429", self.root))

    def test_connected_task_and_instrumentation_markers_block_retry(self):
        for marker in ('> Task :app:connectedDebugAndroidTest',
                       '> Task :app:connectedReleaseAndroidTest FAILED',
                       '> Task :app:testDebugUnitTest',
                       '> Task :app:test', '> Task :test',
                       '\x1b[32m> Task :app:connectedDebugAndroidTest\x1b[0m',
                       'Running tests on devices', 'Starting 3 tests on emulator',
                       'INSTRUMENTATION_STATUS: class=Example',
                       'INSTRUMENTATION_RESULT: shortMsg=Process crashed',
                       'FAILURES!!!', 'There were failing tests'):
            with self.subTest(marker=marker):
                self.assertFalse(retry.retryable(THROTTLED + '\n' + marker, self.root))

    def test_android_result_xml_anywhere_under_results_blocks_retry(self):
        nested = self.root / retry.RESULTS_PATH / 'connected' / 'debug' / 'nested'
        nested.mkdir(parents=True)
        (nested / 'TEST-example.xml').write_text('<testsuite/>', encoding='utf8')
        self.assertFalse(retry.retryable(THROTTLED, self.root))

    def test_unrelated_failure_categories_override_dependency_429(self):
        for marker in ('Execution failed for task :app:lintDebug',
                       'java.lang.AssertionError: broken',
                       'Received status code 401 from server',
                       'Received status code 403 from server',
                       'SDK license not accepted',
                       'Android SDK not found', 'Authentication failed',
                       'Failed to install the following Android SDK packages'):
            with self.subTest(marker=marker):
                self.assertFalse(retry.retryable(THROTTLED + '\n' + marker, self.root))

    def test_retries_twice_at_15_and_30_seconds_then_returns_success(self):
        runner = mock.Mock(side_effect=[(1, THROTTLED), (2, THROTTLED), (0, 'ok')])
        sleep = mock.Mock()
        with contextlib.redirect_stderr(io.StringIO()):
            code = retry.run_with_retry(['./gradlew', ':app:connectedDebugAndroidTest'],
                                        self.root, run=runner, sleep=sleep)
        self.assertEqual(0, code)
        self.assertEqual(3, runner.call_count)
        self.assertEqual([mock.call(15), mock.call(30)], sleep.call_args_list)

    def test_exhaustion_preserves_last_exit_code(self):
        runner = mock.Mock(side_effect=[(1, THROTTLED), (2, THROTTLED), (37, THROTTLED)])
        sleep = mock.Mock()
        with contextlib.redirect_stderr(io.StringIO()):
            code = retry.run_with_retry(['gradlew'], self.root, run=runner, sleep=sleep)
        self.assertEqual(37, code)
        self.assertEqual(3, runner.call_count)
        self.assertEqual([mock.call(15), mock.call(30)], sleep.call_args_list)

    def test_success_and_nonretryable_failure_never_sleep(self):
        for result in ((0, THROTTLED), (23, 'ordinary build failure')):
            with self.subTest(result=result):
                runner, sleep = mock.Mock(return_value=result), mock.Mock()
                self.assertEqual(result[0], retry.run_with_retry(['gradlew'], self.root,
                                                                 run=runner, sleep=sleep))
                runner.assert_called_once()
                sleep.assert_not_called()

    def test_result_created_between_attempts_blocks_next_retry(self):
        results = self.root / retry.RESULTS_PATH
        def runner(_):
            results.mkdir(parents=True, exist_ok=True)
            (results / 'TEST-run.xml').write_text('<testsuite/>', encoding='utf8')
            return 9, THROTTLED
        sleep = mock.Mock()
        self.assertEqual(9, retry.run_with_retry(['gradlew'], self.root, run=runner, sleep=sleep))
        sleep.assert_not_called()

    def test_subprocess_forwards_command_output_and_exit_code(self):
        script = Path(retry.__file__).resolve()
        command = [sys.executable, str(script), '--', sys.executable, '-c',
                   "import sys; print('forwarded stdout'); print('forwarded stderr', file=sys.stderr); sys.exit(17)"]
        result = subprocess.run(command, cwd=self.root, capture_output=True, text=True, check=False)
        self.assertEqual(17, result.returncode)
        self.assertIn('forwarded stdout', result.stdout)
        self.assertIn('forwarded stderr', result.stdout)
        self.assertNotIn('retrying', result.stderr)

    def test_real_driver_retries_throttle_then_completes_android_tests(self):
        driver = self.root / 'gradle_driver.py'
        driver.write_text(
            'import pathlib, sys\n'
            'root = pathlib.Path(sys.argv[1])\n'
            'counter = root / "attempts"\n'
            'n = int(counter.read_text()) + 1 if counter.exists() else 1\n'
            'counter.write_text(str(n))\n'
            'if n == 1:\n'
            '    print(sys.argv[2], flush=True)\n'
            '    sys.exit(1)\n'
            'print("> Task :app:connectedDebugAndroidTest", flush=True)\n'
            'report = root / "app/build/outputs/androidTest-results/connected/debug/TEST-device.xml"\n'
            'report.parent.mkdir(parents=True, exist_ok=True)\n'
            'report.write_text("<testsuite tests=\\"1\\"/>")\n'
            'sys.exit(0)\n', encoding='utf8')
        output, errors, sleep = io.StringIO(), io.StringIO(), mock.Mock()
        with contextlib.redirect_stdout(output), contextlib.redirect_stderr(errors):
            code = retry.run_with_retry([sys.executable, str(driver), str(self.root), THROTTLED],
                                        self.root, sleep=sleep)
        self.assertEqual(0, code)
        self.assertEqual('2', (self.root / 'attempts').read_text())
        self.assertEqual(1, output.getvalue().count('> Task :app:connectedDebugAndroidTest'))
        self.assertTrue((self.root / retry.RESULTS_PATH / 'connected/debug/TEST-device.xml').is_file())
        sleep.assert_called_once_with(15)

    def test_missing_command_returns_usage(self):
        with contextlib.redirect_stderr(io.StringIO()):
            self.assertEqual(2, retry.main([]))


if __name__ == '__main__':
    unittest.main()
