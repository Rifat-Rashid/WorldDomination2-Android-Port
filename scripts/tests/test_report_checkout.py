"""Keep the report verifier's checkout small without weakening its evidence gate."""

import ast
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
WORKFLOW = ROOT / '.github/workflows/android-tests.yml'
VERIFIER = ROOT / 'scripts/verify_test_partition.py'

sys.path.insert(0, str(ROOT / 'scripts'))
from verify_test_partition import selectors


def job(text, name):
    match = re.search(rf'(?ms)^  {re.escape(name)}:\n(.*?)(?=^  [\w-]+:|\Z)', text)
    if match is None:
        raise AssertionError(f'Missing {name} job')
    return match.group(1)


def step(text, name):
    match = re.search(rf'(?ms)^      - name: {re.escape(name)}\n(.*?)(?=^      - (?:name:|uses:)|\Z)', text)
    if match is None:
        raise AssertionError(f'Missing {name} step')
    return match.group(1)


class ReportCheckoutTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        workflow = WORKFLOW.read_text(encoding='utf-8')
        cls.producer = job(workflow, 'full-suite')
        cls.matrix = job(workflow, 'tests')

    def test_producer_keeps_full_checkout_and_publishes_complete_reports(self):
        checkout = step(self.producer, 'Check out source')
        self.assertRegex(checkout, r'(?m)^        uses: actions/checkout@v5$')
        self.assertNotIn('sparse-checkout:', checkout)
        self.assertEqual(1, self.producer.count('uses: actions/checkout@v5'))
        self.assertIn('run: bash scripts/gradle_rate_limit_retry.sh ./gradlew --no-daemon --stacktrace :app:testDebugUnitTest', self.producer)
        provenance = step(self.producer, 'Record report provenance')
        for field in ("'sha': os.environ['GITHUB_SHA']",
                      "'run_id': os.environ['GITHUB_RUN_ID']",
                      "'outcome': os.environ['TEST_OUTCOME']",
                      "'reports': {p.relative_to(output).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest()"):
            self.assertIn(field, provenance)
        published = step(self.producer, 'Publish executed JUnit reports')
        self.assertIn('name: executed-debug-junit', published)
        self.assertIn('path: app/build/test-results/testDebugUnitTest/', published)

    def test_matrix_checkout_selects_only_stdlib_verifier_file(self):
        checkout = step(self.matrix, 'Check out source')
        self.assertRegex(checkout, r'(?m)^        uses: actions/checkout@v5$')
        self.assertEqual(1, self.matrix.count('uses: actions/checkout@v5'))
        self.assertRegex(checkout, r'(?m)^          sparse-checkout: scripts/verify_test_partition\.py$')
        self.assertRegex(checkout, r'(?m)^          sparse-checkout-cone-mode: false$')
        self.assertEqual(1, checkout.count('sparse-checkout:'))
        tree = ast.parse(VERIFIER.read_text(encoding='utf-8'))
        modules = [alias.name.split('.')[0] for node in ast.walk(tree)
                   if isinstance(node, ast.Import) for alias in node.names]
        modules += [node.module.split('.')[0] for node in ast.walk(tree)
                    if isinstance(node, ast.ImportFrom) and node.module]
        self.assertTrue(modules)
        self.assertTrue(set(modules) <= sys.stdlib_module_names)

    def test_aircraft_checks_share_the_producer_and_keep_stable_names(self):
        expected = {
            'Carrier strike route parity',
            'Carrier aircraft playback parity',
            'Carrier Flash artwork and audio integrity',
            'Airfield aircraft route and event parity',
            'Airfield aircraft source-tick playback',
            'Airfield aircraft Flash artwork and audio',
            'Airfield aircraft source smoke trails',
            'Airfield aircraft post-impact fly-off',
            'Original aircraft trail artwork integrity',
        }
        names = re.findall(r'(?m)^            check: (.+)$', self.matrix)
        self.assertTrue(expected <= set(names))
        for name in expected:
            self.assertEqual(1, names.count(name), name)
        self.assertFalse((WORKFLOW.parent / 'android-carrier-playback.yml').exists())

    def test_matrix_still_requires_same_run_successful_producer_reports(self):
        self.assertRegex(self.matrix, r'(?m)^    needs: full-suite$')
        self.assertRegex(self.matrix, r'(?m)^    if: always\(\)$')
        tasks = re.findall(r'(?m)^            task: (.+)$', self.matrix)
        self.assertEqual(229, len(tasks))
        filters = [pattern for task in tasks for pattern in selectors(task.strip().strip("'"))]
        self.assertEqual(483, len(filters))
        information_panel = 'com.example.worlddomination.game.ui.InformationPanelTextTest'
        self.assertEqual(1, filters.count(information_panel))
        task = next(task for task in tasks if information_panel in task)
        self.assertEqual([information_panel], selectors(task.strip().strip("'")))
        self.assertRegex(self.matrix,
                         r'(?m)^            check: Phone funds and fractional population statistics$')
        hidden_classes = {
            'com.example.worlddomination.game.ui.SourceHiddenHelpStateTest',
            'com.example.worlddomination.game.ui.SourceHiddenHelpTypographyTest',
            'com.example.worlddomination.game.simulation.SourceHiddenCommandsTest',
            'com.example.worlddomination.game.simulation.SourceHiddenCommandGameIsolationTest',
            'com.example.worlddomination.game.assets.SourceHiddenHelpAssetsTest',
            'com.example.worlddomination.game.assets.SourceHiddenCreatureTimelineTest',
            'com.example.worlddomination.game.rendering.SourceHiddenBattleEffectsTest',
            'com.example.worlddomination.game.rendering.SourceHiddenHelpRendererTest',
            'com.example.worlddomination.game.rendering.SourceHiddenCityExplosionTest',
            'com.example.worlddomination.game.rendering.SourceDustCleanupTest',
            'com.example.worlddomination.game.rendering.SourceDiscoBattleActionFramesTest',
            'com.example.worlddomination.game.rendering.SourceDiscoAutoplayTest',
            'com.example.worlddomination.game.rendering.SourceDiscoActionBuildingTest',
            'com.example.worlddomination.game.ui.MatchOutcomeSequenceTest',
        }
        hidden_task = next(task for task in tasks if 'SourceHiddenHelpStateTest' in task)
        self.assertEqual(hidden_classes, set(selectors(hidden_task.strip().strip("'"))))
        for name in (hidden_classes - {'com.example.worlddomination.game.ui.MatchOutcomeSequenceTest'}) | {
                'com.example.worlddomination.game.rendering.SourceCityImpactStateTest'}:
            self.assertEqual(1, filters.count(name), name)
        self.assertEqual(1, filters.count(
            'com.example.worlddomination.game.simulation.SourcePreparedShipTubeCancelTest'))
        self.assertEqual(1, filters.count(
            'com.example.worlddomination.game.ui.MassMediaTextTest'))
        self.assertEqual(1, filters.count(
            'com.example.worlddomination.game.ui.SourceBudgetTextTest'))
        self.assertEqual(1, filters.count(
            'com.example.worlddomination.game.simulation.SourcePlannedDiplomacyTest'))
        self.assertEqual(1, filters.count(
            'com.example.worlddomination.game.simulation.NegativeBudgetActionAcceptanceTest'))
        self.assertEqual(1, filters.count(
            'com.example.worlddomination.game.simulation.SourceMultiSeedCampaignTest'))
        self.assertEqual(1, filters.count(
            'com.example.worlddomination.game.simulation.SourceLeaderCampaignTest'))
        self.assertEqual(1, filters.count(
            'com.example.worlddomination.game.simulation.SourceAcceptedIncomingCampaignTest'))
        self.assertEqual(1, filters.count(
            'com.example.worlddomination.game.ai.SourceCompanionTargetRetryTest'))
        downloaded = step(self.matrix, "Download this run's executed reports")
        self.assertIn('uses: actions/download-artifact@v7', downloaded)
        self.assertIn('name: executed-debug-junit', downloaded)
        self.assertIn('path: junit-reports', downloaded)
        verify = step(self.matrix, 'Verify ${{ matrix.check }} executed and passed')
        for field in ('TEST_TASK: ${{ matrix.task }}', 'REPORT_SHA: ${{ github.sha }}',
                      'REPORT_RUN: ${{ github.run_id }}',
                      'PRODUCER_RESULT: ${{ needs.full-suite.result }}',
                      'test "$PRODUCER_RESULT" = success',
                      'python3 scripts/verify_test_partition.py junit-reports --task "$TEST_TASK" --sha "$REPORT_SHA" --run-id "$REPORT_RUN"'):
            self.assertIn(field, verify)

    def test_single_checked_out_verifier_accepts_only_matching_report_proof(self):
        with tempfile.TemporaryDirectory() as temporary:
            isolated = Path(temporary)
            script = isolated / 'verify_test_partition.py'
            shutil.copy2(VERIFIER, script)
            reports = isolated / 'junit-reports'
            reports.mkdir()
            xml = reports / 'TEST-demo.xml'
            xml.write_text('<testsuite tests="1"><testcase classname="p.Demo" name="works"/>'
                           '</testsuite>', encoding='utf-8')
            provenance = {
                'sha': 'commit', 'run_id': '42', 'outcome': 'success',
                'reports': {'TEST-demo.xml': hashlib.sha256(xml.read_bytes()).hexdigest()},
            }
            (reports / 'provenance.json').write_text(json.dumps(provenance), encoding='utf-8')
            self.assertEqual({'verify_test_partition.py', 'junit-reports'},
                             {p.name for p in isolated.iterdir()})
            command = [sys.executable, '-B', str(script), str(reports), '--task',
                       ':app:testDebugUnitTest --tests "p.Demo"', '--sha', 'commit',
                       '--run-id', '42']
            environment = os.environ.copy()
            environment.pop('PYTHONPATH', None)

            def run(args):
                return subprocess.run(args, cwd=isolated, env=environment,
                                      text=True, capture_output=True, check=False)

            passed = run(command)
            self.assertEqual(0, passed.returncode, passed.stderr)
            self.assertIn('1 executed test cases', passed.stdout)
            wrong_sha_command = command.copy()
            wrong_sha_command[wrong_sha_command.index('--sha') + 1] = 'other'
            wrong_sha = run(wrong_sha_command)
            self.assertNotEqual(0, wrong_sha.returncode)
            self.assertIn('successful producer for this commit and run', wrong_sha.stderr)
            xml.write_text(xml.read_text(encoding='utf-8').replace('works', 'other'),
                           encoding='utf-8')
            tampered = run(command)
            self.assertNotEqual(0, tampered.returncode)
            self.assertIn('inventory or content differs', tampered.stderr)


if __name__ == '__main__':
    unittest.main()
