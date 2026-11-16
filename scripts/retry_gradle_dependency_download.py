#!/usr/bin/env python3
"""Retry only pre-instrumentation Gradle dependency downloads throttled with HTTP 429."""
from __future__ import annotations

import re
import subprocess
import sys
import time
from pathlib import Path
from typing import Callable, Sequence

MAX_ATTEMPTS = 3
RETRY_DELAYS_SECONDS = (15, 30)
RESULTS_PATH = Path('app/build/outputs/androidTest-results')
_ANSI = re.compile(r'\x1b\[[0-?]*[ -/]*[@-~]')

# Keep the URL coupled between Gradle's resource and GET diagnostics. A 429
# printed by a test or an unrelated service is not evidence of this failure.
_RESOURCE = re.compile(
    r"Could not get resource ['\"](?P<url>https?://[^'\"]+)['\"]\.\s*"
    r"(?:>\s*)?Could not GET ['\"](?P=url)['\"]\.\s*"
    r"Received status code 429\b", re.IGNORECASE)
_DOWNLOAD = re.compile(
    r"Could not download [^\r\n]+\r?\n\s*(?:>\s*)?"
    r"Could not GET ['\"]https?://[^'\"]+['\"]\.\s*"
    r"Received status code 429\b", re.IGNORECASE)
_STARTED = re.compile(
    r"(?im)^\s*>\s*Task\s+:app:connected\S*AndroidTest\b|"
    r"^\s*>\s*Task\s+:\S*:test\S*(?:UnitTest|AndroidTest)\b|"
    r"^\s*>\s*Task\s+:(?:[A-Za-z0-9_-]+:)*test(?:\s|$)|"
    r"\bRunning tests on\b|\bStarting\s+\d+\s+tests?\s+on\b|"
    r"\bINSTRUMENTATION_(?:STATUS|RESULT|CODE|FAILED)\b|"
    r"FAILURES!!!|\b(?:There were failing tests|TESTS FAILED)\b")
_OTHER_FAILURE = re.compile(
    r"(?im)execution failed for task ['\"]?[^\r\n]*(?:lint|test)|"
    r"\b(?:sdk license|licen[cs]es?[^\r\n]*not accepted|"
    r"sdk location not found|android sdk not found|"
    r"failed to install the following android sdk|authentication failed|"
    r"invalid credentials|unauthori[sz]ed|forbidden|"
    r"received status code (?:401|403)\b|assertionerror|assertionfailed)\b")


def has_android_test_xml(root: Path) -> bool:
    results = root / RESULTS_PATH
    return results.is_dir() and any(p.is_file() for p in results.rglob('*.xml'))


def retryable(output: str, root: Path) -> bool:
    """Require a genuine matching Gradle download failure and no test evidence."""
    output = _ANSI.sub('', output)
    return (not has_android_test_xml(root)
            and not _STARTED.search(output)
            and not _OTHER_FAILURE.search(output)
            and bool(_RESOURCE.search(output) or _DOWNLOAD.search(output)))


def run_once(command: Sequence[str]) -> tuple[int, str]:
    process = subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                               bufsize=1, text=True, encoding='utf-8', errors='replace')
    assert process.stdout is not None
    output = []
    for line in process.stdout:
        sys.stdout.write(line)
        sys.stdout.flush()
        output.append(line)
    return process.wait(), ''.join(output)


def run_with_retry(command: Sequence[str], root: Path, *,
                   run: Callable[[Sequence[str]], tuple[int, str]] = run_once,
                   sleep: Callable[[float], None] = time.sleep) -> int:
    for attempt in range(MAX_ATTEMPTS):
        code, output = run(command)
        if code == 0 or attempt == MAX_ATTEMPTS - 1 or not retryable(output, root):
            return code
        delay = RETRY_DELAYS_SECONDS[attempt]
        print(f'Gradle dependency download was throttled before Android tests; '
              f'retrying in {delay} seconds (attempt {attempt + 2}/{MAX_ATTEMPTS}).',
              file=sys.stderr, flush=True)
        sleep(delay)
    raise AssertionError('unreachable')


def main(argv: Sequence[str] | None = None) -> int:
    args = list(sys.argv[1:] if argv is None else argv)
    if args and args[0] == '--':
        args.pop(0)
    if not args:
        print('Usage: retry_gradle_dependency_download.py [--] ./gradlew [arguments...]',
              file=sys.stderr)
        return 2
    try:
        return run_with_retry(args, Path.cwd())
    except OSError as exc:
        print(f'Unable to start Gradle command: {exc}', file=sys.stderr)
        return 127


if __name__ == '__main__':
    sys.exit(main())
