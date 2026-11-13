#!/usr/bin/env python3
"""Verify named Gradle filters against this run's complete executed JUnit suite."""
import argparse
import json
import re
import shlex
import xml.etree.ElementTree as ET
from pathlib import Path


def selectors(task):
    args = shlex.split(task)
    if not args or args.pop(0) != ':app:testDebugUnitTest':
        raise ValueError('Only the debug JUnit task is supported')
    result = []
    while args:
        if args.pop(0) != '--tests' or not args:
            raise ValueError('Expected --tests followed by a filter')
        result.append(args.pop(0))
    return result


def verify(directory, task, sha, run_id):
    directory = Path(directory)
    provenance = json.loads((directory / 'provenance.json').read_text())
    if (provenance.get('sha') != sha or str(provenance.get('run_id')) != str(run_id)
            or provenance.get('outcome') != 'success'):
        raise ValueError('Reports are missing a successful producer for this commit and run')
    cases = []
    reports = list(directory.rglob('TEST-*.xml'))
    if not reports:
        raise ValueError('No JUnit XML reports')
    for report in reports:
        root = ET.parse(report).getroot()
        if root.tag not in ('testsuite', 'testsuites'):
            raise ValueError('Unknown JUnit root')
        for suite in root.iter():
            if suite.tag in ('testsuite', 'testsuites'):
                if any(int(suite.get(key, '0')) for key in ('failures', 'errors')):
                    raise ValueError('JUnit summary reports failed tests')
        report_cases = list(root.iter('testcase'))
        if not report_cases:
            raise ValueError(f'No actual test cases in {report}')
        for case in report_cases:
            if not case.get('classname') or not case.get('name'):
                raise ValueError('Unnamed test case')
            if case.find('failure') is not None or case.find('error') is not None:
                raise ValueError('The complete suite contains failed tests')
        cases.extend(report_cases)
    filters = selectors(task)
    for pattern in filters or ['*']:
        regex = re.compile('^' + re.escape(pattern).replace(r'\*', '.*') + '$')
        matches = []
        for case in cases:
            cls, name = case.get('classname'), case.get('name')
            # Gradle also selects parameterized invocations by their base method.
            base = name.split('[', 1)[0]
            if any(regex.fullmatch(value) for value in (cls, cls + '.' + name, cls + '.' + base)):
                matches.append(case)
        if not matches or not any(case.find('skipped') is None for case in matches):
            raise ValueError(f'Filter executed no tests: {pattern}')
    return len(cases), len(filters)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('directory')
    parser.add_argument('--task', required=True)
    parser.add_argument('--sha', required=True)
    parser.add_argument('--run-id', required=True)
    args = parser.parse_args()
    count, filters = verify(args.directory, args.task, args.sha, args.run_id)
    print(f'Verified {filters or 1} partition filter(s) against {count} executed test cases')


if __name__ == '__main__':
    main()
