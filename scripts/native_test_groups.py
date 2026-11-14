#!/usr/bin/env python3
"""Closed Android instrumentation class inventory and shard artifact audit."""

from __future__ import annotations

import argparse
import json
import re
import struct
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

from verify_junit_results import verify as verify_junit_results


MANIFEST = Path(__file__).with_name("native_test_groups.json")
EVIDENCE = {"beta-ui-evidence", "propaganda-evidence"}
CLASS = re.compile(r"com\.example\.worlddomination\.[A-Za-z][A-Za-z0-9_.]*InstrumentedTest")
BETA_PRODUCERS = {
    "com.example.worlddomination.game.rendering.StockControlsInstrumentedTest",
    "com.example.worlddomination.game.assets.EarlySetupPortraitsInstrumentedTest",
    "com.example.worlddomination.game.rendering.SourceAirfieldDefenseRenderingInstrumentedTest",
}
PROPAGANDA = "com.example.worlddomination.game.ui.PropagandaUiInstrumentedTest"


def load_manifest(path: Path = MANIFEST) -> dict:
    data = json.loads(path.read_text(encoding="utf-8"))
    required = data.get("requiredClasses")
    groups = data.get("groups")
    expected_cases = data.get("expectedCases")
    if data.get("version") != 1 or not isinstance(required, list) \
            or not required or not isinstance(groups, list) or len(groups) != 2:
        raise ValueError("Native manifest needs a nonempty inventory in two groups")
    if any(not isinstance(name, str) or CLASS.fullmatch(name) is None for name in required):
        raise ValueError("Invalid required Android test class")
    if len(set(required)) != len(required):
        raise ValueError("Duplicate required Android test class")
    if not isinstance(expected_cases, list) or not expected_cases or any(
            not isinstance(pair, list) or len(pair) != 2
            or not isinstance(pair[0], str) or not isinstance(pair[1], str)
            or not pair[1] for pair in expected_cases):
        raise ValueError("Native manifest needs nonempty (class, method) pairs")
    if len({tuple(pair) for pair in expected_cases}) != len(expected_cases):
        raise ValueError("Duplicate expected Android test case")
    if {pair[0] for pair in expected_cases} != set(required):
        raise ValueError("Expected Android test case classes differ from required inventory")
    selected = []
    ids = set()
    for group in groups:
        if not isinstance(group, dict) or group.get("id") not in {"a", "b"}:
            raise ValueError("Invalid native group id")
        group_id = group["id"]
        if group_id in ids:
            raise ValueError("Duplicate native group id")
        ids.add(group_id)
        classes, evidence = group.get("classes"), group.get("evidence")
        if not isinstance(classes, list) or not classes \
                or any(not isinstance(name, str) for name in classes):
            raise ValueError(f"Empty or invalid classes for group {group_id}")
        if not isinstance(evidence, list) or not evidence \
                or len(set(evidence)) != len(evidence) or not set(evidence) <= EVIDENCE:
            raise ValueError(f"Invalid evidence for group {group_id}")
        if "beta-ui-evidence" not in evidence or not set(classes) & BETA_PRODUCERS:
            raise ValueError(f"Group {group_id} cannot produce required beta evidence")
        if (PROPAGANDA in classes) != ("propaganda-evidence" in evidence):
            raise ValueError("Propaganda evidence must be produced by its selected class")
        selected.extend(classes)
    if ids != {"a", "b"}:
        raise ValueError("Both native groups a and b are required")
    if len(set(selected)) != len(selected):
        raise ValueError("Duplicate selected Android test class")
    if set(selected) != set(required):
        missing = sorted(set(required) - set(selected))
        extra = sorted(set(selected) - set(required))
        raise ValueError(f"Native class inventory mismatch: missing={missing}, extra={extra}")
    if not any("propaganda-evidence" in group["evidence"] for group in groups):
        raise ValueError("Missing propaganda evidence group")
    return data


def group_by_id(manifest: dict, group_id: str) -> dict:
    for group in manifest["groups"]:
        if group["id"] == group_id:
            return group
    raise ValueError(f"Unknown native group {group_id}")


def _artifact_child(root: Path, relative: str) -> Path:
    direct = root / relative
    nested = root / "app/build" / relative
    if direct.exists() and nested.exists():
        raise ValueError(f"Ambiguous artifact path for {relative}")
    return direct if direct.exists() else nested


def _png_header(path: Path) -> None:
    with path.open("rb") as stream:
        header = stream.read(33)
    if len(header) < 33 or header[:8] != b"\x89PNG\r\n\x1a\n" \
            or header[8:16] != b"\x00\x00\x00\rIHDR":
        raise ValueError(f"Invalid PNG header: {path}")
    width, height = struct.unpack(">II", header[16:24])
    if width == 0 or height == 0:
        raise ValueError(f"Invalid PNG dimensions: {path}")


def verify_group_artifact(manifest: dict, group: dict, artifact: Path) -> tuple[int, int, int]:
    results = _artifact_child(artifact, "outputs/androidTest-results/connected/debug")
    if not results.is_dir():
        raise ValueError(f"Missing Android JUnit results: {results}")
    executed, reports = verify_junit_results(results)
    classes = set()
    executed_cases = 0
    actual_cases = set()
    for report in sorted(results.rglob("TEST-*.xml")):
        root = ET.parse(report).getroot()
        for summary in root.iter():
            if summary.tag in {"testsuites", "testsuite"} \
                    and int(summary.get("skipped", "0")) != 0:
                raise ValueError(f"Skipped Android tests in {report}")
        for suite in root.iter("testsuite"):
            if suite.findall("testsuite"):
                continue
            name = suite.get("name")
            cases = suite.findall("testcase")
            if not cases:
                continue
            if name in classes:
                raise ValueError(f"Repeated JUnit class suite: {name}")
            classes.add(name)
            if int(suite.get("skipped", "0")) != 0:
                raise ValueError(f"Skipped Android tests in {report}")
            for case in cases:
                if case.get("classname") != name or case.find("skipped") is not None:
                    raise ValueError(f"Skipped or mismatched Android test case in {report}")
                pair = (name, case.get("name"))
                if not pair[1] or pair in actual_cases:
                    raise ValueError(f"Missing name or duplicate Android test case in {report}")
                actual_cases.add(pair)
                executed_cases += 1
    if classes != set(group["classes"]):
        raise ValueError(f"Group {group['id']} JUnit class mismatch: "
                         f"missing={sorted(set(group['classes']) - classes)}, "
                         f"extra={sorted(classes - set(group['classes']))}")
    if executed_cases != executed:
        raise ValueError("Android executed case count differs from JUnit report")
    expected = {tuple(pair) for pair in manifest["expectedCases"]
                if pair[0] in set(group["classes"])}
    if actual_cases != expected:
        raise ValueError(f"Group {group['id']} JUnit method mismatch: "
                         f"missing={sorted(expected - actual_cases)}, "
                         f"extra={sorted(actual_cases - expected)}")
    png_count = 0
    for evidence in group["evidence"]:
        folder = _artifact_child(artifact, f"reports/androidTests/{evidence}")
        if not folder.is_dir():
            raise ValueError(f"Missing required evidence folder: {folder}")
        images = sorted(folder.rglob("*.png"))
        if not images:
            raise ValueError(f"Empty required evidence folder: {folder}")
        for image in images:
            _png_header(image)
        png_count += len(images)
    return executed, reports, png_count


def verify_artifacts(manifest: dict, directory: Path) -> dict:
    expected_directories = {"android-native-a-results", "android-native-b-results"}
    found_directories = {path.name for path in directory.glob("android-native-*-results")}
    if found_directories != expected_directories:
        raise ValueError(f"Native artifact directory set mismatch: "
                         f"missing={sorted(expected_directories - found_directories)}, "
                         f"extra={sorted(found_directories - expected_directories)}")
    results = {}
    png_names = set()
    for group_id in ("a", "b"):
        group = group_by_id(manifest, group_id)
        artifact = directory / f"android-native-{group_id}-results"
        if not artifact.is_dir():
            raise ValueError(f"Missing group artifact: {artifact}")
        executed, reports, images = verify_group_artifact(manifest, group, artifact)
        for evidence in group["evidence"]:
            folder = _artifact_child(artifact, f"reports/androidTests/{evidence}")
            for path in folder.rglob("*.png"):
                if path.name in png_names:
                    raise ValueError(f"Duplicate native PNG basename across shards: {path.name}")
                png_names.add(path.name)
        results[group_id] = {"classes": len(group["classes"]),
                             "executed": executed, "reports": reports,
                             "requiredPngs": images}
    if sum(row["classes"] for row in results.values()) != len(manifest["requiredClasses"]):
        raise ValueError("Combined native group class count is incomplete")
    return results


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", type=Path, default=MANIFEST)
    parser.add_argument("--group", choices=("a", "b"))
    parser.add_argument("--github-output", type=Path)
    parser.add_argument("--artifacts", type=Path)
    args = parser.parse_args(argv)
    try:
        manifest = load_manifest(args.manifest)
        if args.group is not None:
            group = group_by_id(manifest, args.group)
            classes = ",".join(group["classes"])
            evidence = " ".join(group["evidence"])
            if args.github_output is not None:
                with args.github_output.open("a", encoding="utf-8") as output:
                    output.write(f"classes={classes}\nevidence={evidence}\n")
            else:
                print(classes)
                print(evidence)
        if args.artifacts is not None:
            print(json.dumps(verify_artifacts(manifest, args.artifacts), sort_keys=True))
    except (ValueError, OSError, ET.ParseError, json.JSONDecodeError) as error:
        print(str(error), file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
