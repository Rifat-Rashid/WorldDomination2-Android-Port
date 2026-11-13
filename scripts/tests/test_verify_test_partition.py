"""Fail closed when shared CI reports cannot prove a named check ran."""
import json
from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from verify_test_partition import selectors, verify


class PartitionTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.provenance = {'sha': 'commit', 'run_id': '42', 'outcome': 'success'}
        self.write_provenance()
        self.report = self.root / 'nested' / 'TEST-demo.xml'
        self.report.parent.mkdir()
        self.report.write_text('<testsuite tests="2"><testcase classname="p.Demo" name="works[0]"/>'
                               '<testcase classname="p.Other" name="passes"/></testsuite>')

    def write_provenance(self):
        (self.root / 'provenance.json').write_text(json.dumps(self.provenance))

    def run_check(self, filters=''):
        return verify(self.root, ':app:testDebugUnitTest ' + filters, 'commit', '42')

    def test_full_suite(self):
        self.assertEqual((2, 0), self.run_check())

    def test_class_and_parameterized_method(self):
        self.assertEqual((2, 2), self.run_check('--tests p.Demo --tests p.Demo.works'))

    def test_wildcard(self):
        self.assertEqual((2, 1), self.run_check('--tests "p.*"'))

    def test_every_filter_must_match(self):
        with self.assertRaisesRegex(ValueError, 'no tests'):
            self.run_check('--tests p.Demo --tests p.Missing')

    def test_case_sensitive(self):
        with self.assertRaises(ValueError):
            self.run_check('--tests p.demo')

    def test_missing_reports(self):
        self.report.unlink()
        with self.assertRaisesRegex(ValueError, 'No JUnit'):
            self.run_check()

    def test_empty_summary_is_not_execution(self):
        self.report.write_text('<testsuite tests="200"/>')
        with self.assertRaisesRegex(ValueError, 'No actual'):
            self.run_check()

    def test_failed_or_error_tests_fail_other_partitions_too(self):
        for tag in ('failure', 'error'):
            self.report.write_text(f'<testsuite><testcase classname="p.Demo" name="works"><{tag}/></testcase></testsuite>')
            with self.assertRaises(ValueError):
                self.run_check()

    def test_all_skipped_filter_fails(self):
        self.report.write_text('<testsuite><testcase classname="p.Demo" name="works"><skipped/></testcase></testsuite>')
        with self.assertRaises(ValueError):
            self.run_check('--tests p.Demo')

    def test_wrong_commit_run_or_failed_producer(self):
        for key, value in (('sha', 'other'), ('run_id', '43'), ('outcome', 'failure')):
            original = self.provenance.copy()
            self.provenance[key] = value
            self.write_provenance()
            with self.assertRaises(ValueError):
                self.run_check()
            self.provenance = original

    def test_missing_provenance(self):
        (self.root / 'provenance.json').unlink()
        with self.assertRaises(FileNotFoundError):
            self.run_check()

    def test_suite_failure_cannot_hide_behind_passing_cases(self):
        self.report.write_text('<testsuite errors="1"><testcase classname="p.Demo" name="works"/></testsuite>')
        with self.assertRaises(ValueError):
            self.run_check()

    def test_malformed_xml(self):
        self.report.write_text('<bad')
        with self.assertRaises(Exception):
            self.run_check()

    def test_task_is_parsed_never_executed(self):
        for task in (':app:assembleDebug', ':app:testDebugUnitTest --tests',
                     ':app:testDebugUnitTest ; echo hidden', ':app:testDebugUnitTest --info'):
            with self.assertRaises(ValueError):
                selectors(task)


if __name__ == '__main__':
    unittest.main()
