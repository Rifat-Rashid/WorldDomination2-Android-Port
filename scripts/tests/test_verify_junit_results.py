import importlib.util
from pathlib import Path
import tempfile
import unittest

SPEC = importlib.util.spec_from_file_location(
    "verify_junit_results", Path(__file__).resolve().parents[1] / "verify_junit_results.py"
)
check = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(check)


class JUnitResultTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)

    def report(self, text, name="TEST-example.xml", add_cases=True):
        path = self.root / name
        path.parent.mkdir(parents=True, exist_ok=True)
        if add_cases:
            try:
                root = check.ET.fromstring(text)
                for suite in root.iter("testsuite"):
                    if suite.findall("testsuite") or suite.findall("testcase"):
                        continue
                    tests, skipped = int(suite.get("tests", "0")), int(suite.get("skipped", "0"))
                    for index in range(max(0, tests)):
                        case = check.ET.SubElement(suite, "testcase", name=f"case{index}")
                        if index < skipped:
                            check.ET.SubElement(case, "skipped")
                text = check.ET.tostring(root, encoding="unicode")
            except (ValueError, check.ET.ParseError):
                pass
        path.write_text(text, encoding="utf-8")

    def test_missing_reports_fail(self):
        with self.assertRaisesRegex(ValueError, "No JUnit"):
            check.verify(self.root)

    def test_android_success_exit_with_zero_tests_is_rejected(self):
        self.report('<testsuites tests="0" failures="0" errors="0" skipped="0"/>')
        with self.assertRaisesRegex(ValueError, "No tests executed"):
            check.verify(self.root)

    def test_empty_filtered_suite_is_rejected(self):
        self.report('<testsuite tests="0"/>')
        with self.assertRaises(ValueError):
            check.verify(self.root)

    def test_skipping_every_test_is_not_execution(self):
        self.report('<testsuite tests="3" skipped="3"/>')
        with self.assertRaises(ValueError):
            check.verify(self.root)

    def test_nested_device_results_and_jvm_reports_are_counted(self):
        self.report('<testsuites><testsuite tests="2"/><testsuite tests="3" skipped="1"/></testsuites>',
                    "Pixel_10/TEST-device.xml")
        self.report('<testsuite tests="4"/>')
        self.assertEqual((8, 2), check.verify(self.root))

    def test_failed_and_error_reports_are_rejected(self):
        for attribute in ("failures", "errors"):
            with self.subTest(attribute=attribute):
                self.report(f'<testsuite tests="1" {attribute}="1"/>')
                with self.assertRaises(ValueError):
                    check.verify(self.root)

    def test_failure_element_cannot_hide_behind_zero_failure_count(self):
        self.report('<testsuite tests="1" failures="0"><testcase><failure/></testcase></testsuite>')
        with self.assertRaises(ValueError):
            check.verify(self.root)

    def test_engine_error_is_rejected_even_with_successful_child_suite(self):
        self.report('<testsuites tests="1" errors="1"><testsuite tests="1"/></testsuites>')
        with self.assertRaises(ValueError):
            check.verify(self.root)

    def test_nested_suite_summaries_do_not_double_count(self):
        self.report('<testsuite tests="2"><testsuite tests="2"/></testsuite>')
        self.assertEqual((2, 1), check.verify(self.root))

    def test_malformed_report_is_rejected(self):
        self.report('<testsuite')
        with self.assertRaises(check.ET.ParseError):
            check.verify(self.root)

    def test_invalid_counts_are_rejected(self):
        for counts in ('tests="-1"', 'tests="1" skipped="2"', 'tests="not-an-int"'):
            with self.subTest(counts=counts):
                self.report(f'<testsuite {counts}/>')
                with self.assertRaises(ValueError):
                    check.verify(self.root)

    def test_positive_summary_without_actual_cases_is_rejected(self):
        self.report('<testsuite tests="3"/>', add_cases=False)
        with self.assertRaisesRegex(ValueError, "actual test cases"):
            check.verify(self.root)
