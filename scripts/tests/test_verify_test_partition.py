"""Fail closed when shared CI reports cannot prove a named check ran."""
import hashlib
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
        self.write_report('<testsuite tests="2"><testcase classname="p.Demo" name="works[0]"/>'
                               '<testcase classname="p.Other" name="passes"/></testsuite>')

    def write_provenance(self):
        (self.root / 'provenance.json').write_text(json.dumps(self.provenance))

    def write_report(self, text):
        self.report.write_text(text)
        self.provenance['reports'] = {
            p.relative_to(self.root).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest()
            for p in self.root.rglob('TEST-*.xml')}
        self.write_provenance()

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
        self.write_report('<testsuite tests="200"/>')
        with self.assertRaisesRegex(ValueError, 'No actual'):
            self.run_check()

    def test_failed_or_error_tests_fail_other_partitions_too(self):
        for tag in ('failure', 'error'):
            self.write_report(f'<testsuite><testcase classname="p.Demo" name="works"><{tag}/></testcase></testsuite>')
            with self.assertRaises(ValueError):
                self.run_check()

    def test_all_skipped_filter_fails(self):
        self.write_report('<testsuite><testcase classname="p.Demo" name="works"><skipped/></testcase></testsuite>')
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
        self.write_report('<testsuite errors="1"><testcase classname="p.Demo" name="works"/></testsuite>')
        with self.assertRaises(ValueError):
            self.run_check()

    def test_missing_or_tampered_report_rejected(self):
        extra = self.root / 'TEST-extra.xml'
        extra.write_bytes(self.report.read_bytes())
        self.provenance['reports']['TEST-extra.xml'] = hashlib.sha256(extra.read_bytes()).hexdigest()
        self.write_provenance()
        extra.unlink()
        with self.assertRaisesRegex(ValueError, 'inventory'):
            self.run_check()
        self.provenance['reports'].pop('TEST-extra.xml')
        self.write_provenance()
        self.report.write_text('<testsuite><testcase classname="p.Demo" name="tampered"/></testsuite>')
        with self.assertRaisesRegex(ValueError, 'inventory'):
            self.run_check()

    def test_declared_count_must_match_cases(self):
        self.write_report('<testsuite tests="20"><testcase classname="p.Demo" name="works"/></testsuite>')
        with self.assertRaisesRegex(ValueError, 'count'):
            self.run_check()

    def test_unmanifested_report_is_rejected(self):
        (self.root / 'TEST-extra.xml').write_bytes(self.report.read_bytes())
        with self.assertRaisesRegex(ValueError, 'inventory'):
            self.run_check()

    def test_malformed_xml(self):
        self.write_report('<bad')
        with self.assertRaises(Exception):
            self.run_check()

    def test_task_is_parsed_never_executed(self):
        for task in (':app:assembleDebug', ':app:testDebugUnitTest --tests',
                     ':app:testDebugUnitTest ; echo hidden', ':app:testDebugUnitTest --info'):
            with self.assertRaises(ValueError):
                selectors(task)


if __name__ == '__main__':
    unittest.main()
