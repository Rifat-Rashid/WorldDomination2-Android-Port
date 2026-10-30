#!/usr/bin/env python3
"""Fail a focused CI job when its Gradle test filter selected no tests."""

from __future__ import annotations

import sys
import xml.etree.ElementTree as ET
from pathlib import Path


def main() -> int:
    results_dir = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(
        "app/build/test-results/testDebugUnitTest"
    )
    reports = sorted(results_dir.glob("TEST-*.xml"))
    if not reports:
        print(f"No JUnit XML reports found in {results_dir}", file=sys.stderr)
        return 1

    executed = 0
    for report in reports:
        root = ET.parse(report).getroot()
        suites = [root] if root.tag == "testsuite" else root.findall("testsuite")
        executed += sum(int(suite.get("tests", "0")) for suite in suites)

    if executed == 0:
        print(f"No tests matched this CI check in {results_dir}", file=sys.stderr)
        return 1

    print(f"Verified {executed} test case(s) in {len(reports)} JUnit report(s)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
