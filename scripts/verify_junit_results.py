#!/usr/bin/env python3
"""Reject missing, empty or unsuccessful JVM/Android JUnit test reports."""

from __future__ import annotations

import sys
import xml.etree.ElementTree as ET
from pathlib import Path


def verify(results_dir: Path) -> tuple[int, int]:
    reports = sorted(results_dir.rglob("TEST-*.xml"))
    if not reports:
        raise ValueError(f"No JUnit XML reports found in {results_dir}")

    executed = 0
    for report in reports:
        root = ET.parse(report).getroot()
        if int(root.get("failures", "0")) or int(root.get("errors", "0")) \
                or root.find(".//failure") is not None or root.find(".//error") is not None:
            raise ValueError(f"Unsuccessful tests in {report}")
        # Some Android test engines emit only <testsuites tests="0" .../>
        # on APK installation failure while Gradle itself exits successfully.
        suites = [suite for suite in root.iter("testsuite") if not suite.findall("testsuite")]
        if not suites:
            suites = [root]
        for suite in suites:
            tests = int(suite.get("tests", "0"))
            skipped = int(suite.get("skipped", "0"))
            failures = int(suite.get("failures", "0"))
            errors = int(suite.get("errors", "0"))
            if min(tests, skipped, failures, errors) < 0 or skipped > tests:
                raise ValueError(f"Invalid JUnit test counts in {report}")
            if failures or errors or suite.find(".//failure") is not None \
                    or suite.find(".//error") is not None:
                raise ValueError(f"Unsuccessful tests in {report}")
            cases = suite.findall("testcase")
            if len(cases) != tests:
                raise ValueError(f"JUnit test count does not match actual test cases in {report}")
            executed += sum(case.find("skipped") is None for case in cases)

    if executed == 0:
        raise ValueError(f"No tests executed for this CI check in {results_dir}")
    return executed, len(reports)


def main() -> int:
    results_dir = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(
        "app/build/test-results/testDebugUnitTest"
    )
    try:
        executed, reports = verify(results_dir)
    except (ValueError, OSError, ET.ParseError) as error:
        print(str(error), file=sys.stderr)
        return 1

    print(f"Verified {executed} executed test case(s) in {reports} JUnit report(s)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
