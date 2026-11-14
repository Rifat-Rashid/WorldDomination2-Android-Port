"""Closed native class coverage and downloaded-shard evidence checks."""

from __future__ import annotations

import base64
import copy
from pathlib import Path
import shutil
import sys
import tempfile
import unittest
import xml.etree.ElementTree as ET

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import native_test_groups as groups


PNG = base64.b64decode(
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+"
    "X2ioAAAAASUVORK5CYII="
)


class NativeGroupTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.manifest = groups.load_manifest()

    def manifest_file(self, data):
        import json
        path = self.root / "manifest.json"
        path.write_text(json.dumps(data), encoding="utf-8")
        return path

    def fixture(self, tamper=None):
        methods = {}
        for name, method in self.manifest["expectedCases"]:
            methods.setdefault(name, []).append(method)
        for group in self.manifest["groups"]:
            group_id = group["id"]
            artifact = self.root / f"android-native-{group_id}-results"
            results = artifact / "outputs/androidTest-results/connected/debug"
            results.mkdir(parents=True)
            root = ET.Element("testsuites", tests="0",
                              failures="0", errors="0", skipped="0")
            if tamper == (group_id, "summary-skip"):
                root.set("skipped", "1")
            omitted_method = None
            if tamper == (group_id, "omit-method"):
                omitted_method = next((name, methods[name][0]) for name in group["classes"]
                                      if len(methods[name]) > 1)
            for name in group["classes"]:
                if tamper == (group_id, "zero-executed"):
                    break
                if tamper == (group_id, "omit-class") and name == group["classes"][0]:
                    continue
                case_names = [method for method in methods[name]
                              if (name, method) != omitted_method]
                suite = ET.SubElement(root, "testsuite", name=name,
                                      tests=str(len(case_names)),
                                      failures="0", errors="0", skipped="0")
                for method in case_names:
                    case = ET.SubElement(suite, "testcase", classname=name,
                                         name=method, time="1")
                if tamper == (group_id, "skip") and name == group["classes"][0]:
                    suite.set("skipped", "1")
                    ET.SubElement(suite.findall("testcase")[0], "skipped")
                if tamper == (group_id, "fail") and name == group["classes"][0]:
                    suite.set("failures", "1")
                    ET.SubElement(suite.findall("testcase")[0], "failure", message="regression")
                if tamper == (group_id, "duplicate-suite") and name == group["classes"][0]:
                    duplicate = ET.SubElement(root, "testsuite", name=name, tests="1",
                                              failures="0", errors="0", skipped="0")
                    ET.SubElement(duplicate, "testcase", classname=name,
                                  name=methods[name][0])
            root.set("tests", str(sum(int(suite.get("tests"))
                                       for suite in root.findall("testsuite"))))
            (results / "TEST-device.xml").write_bytes(ET.tostring(root))
            for evidence in group["evidence"]:
                if tamper == (group_id, "missing-evidence") and evidence == group["evidence"][0]:
                    continue
                folder = artifact / "reports/androidTests" / evidence
                folder.mkdir(parents=True)
                image = folder / f"{group_id}-{evidence}-proof.png"
                image.write_bytes(b"not png" if tamper == (group_id, "bad-png")
                                  and evidence == group["evidence"][0] else PNG)

    def test_manifest_is_exact_closed_inventory_and_both_groups_make_beta(self):
        self.assertEqual(64, len(self.manifest["requiredClasses"]))
        self.assertEqual(99, len(self.manifest["expectedCases"]))
        self.assertEqual({"a", "b"}, {g["id"] for g in self.manifest["groups"]})
        self.assertEqual({"beta-ui-evidence", "propaganda-evidence"},
                         {e for g in self.manifest["groups"] for e in g["evidence"]})
        self.assertEqual(set(self.manifest["requiredClasses"]),
                         {c for g in self.manifest["groups"] for c in g["classes"]})
        self.assertEqual(set(self.manifest["requiredClasses"]),
                         {name for name, _ in self.manifest["expectedCases"]})

    def test_missing_duplicate_and_unselected_classes_rejected(self):
        data = copy.deepcopy(self.manifest)
        removable = next(name for name in data["groups"][0]["classes"]
                         if name != groups.PROPAGANDA and name not in groups.BETA_PRODUCERS)
        data["groups"][0]["classes"].remove(removable)
        with self.assertRaisesRegex(ValueError, "inventory mismatch"):
            groups.load_manifest(self.manifest_file(data))
        data = copy.deepcopy(self.manifest)
        data["groups"][0]["classes"].append(data["groups"][1]["classes"][0])
        with self.assertRaisesRegex(ValueError, "Duplicate selected"):
            groups.load_manifest(self.manifest_file(data))
        data = copy.deepcopy(self.manifest)
        data["groups"][0]["classes"].append(
            "com.example.worlddomination.game.rendering.NotSelectedInstrumentedTest")
        with self.assertRaisesRegex(ValueError, "inventory mismatch"):
            groups.load_manifest(self.manifest_file(data))
        data = copy.deepcopy(self.manifest)
        data["requiredClasses"].pop()
        with self.assertRaisesRegex(ValueError, "differ from required inventory"):
            groups.load_manifest(self.manifest_file(data))

    def test_expected_case_inventory_rejects_duplicates_and_unknown_classes(self):
        data = copy.deepcopy(self.manifest)
        data["expectedCases"].append(list(data["expectedCases"][0]))
        with self.assertRaisesRegex(ValueError, "Duplicate expected"):
            groups.load_manifest(self.manifest_file(data))
        data = copy.deepcopy(self.manifest)
        data["expectedCases"][0][0] = (
            "com.example.worlddomination.game.rendering.NotSelectedInstrumentedTest")
        with self.assertRaisesRegex(ValueError, "differ from required inventory"):
            groups.load_manifest(self.manifest_file(data))

    def test_group_output_contains_validated_csv_and_evidence(self):
        path = self.root / "github-output"
        self.assertEqual(0, groups.main(["--group", "a", "--github-output", str(path)]))
        lines = path.read_text(encoding="utf-8").splitlines()
        selected = groups.group_by_id(self.manifest, "a")
        self.assertEqual("classes=" + ",".join(selected["classes"]), lines[0])
        self.assertEqual("evidence=beta-ui-evidence propaganda-evidence", lines[1])

    def test_downloaded_groups_require_exact_junit_sets_and_valid_png_headers(self):
        self.fixture()
        result = groups.verify_artifacts(self.manifest, self.root)
        self.assertEqual(64, sum(row["classes"] for row in result.values()))
        self.assertEqual(99, sum(row["executed"] for row in result.values()))
        self.assertEqual(3, sum(row["requiredPngs"] for row in result.values()))

    def test_tampered_junit_skips_and_failed_cases_rejected(self):
        for defect in ("omit-class", "omit-method", "skip", "summary-skip", "fail",
                       "duplicate-suite", "zero-executed"):
            with self.subTest(defect=defect):
                with tempfile.TemporaryDirectory() as temporary:
                    old = self.root
                    self.root = Path(temporary)
                    try:
                        self.fixture(("a", defect))
                        with self.assertRaises(ValueError):
                            groups.verify_artifacts(self.manifest, self.root)
                    finally:
                        self.root = old

    def test_multicase_method_disappearance_fails_even_with_class_and_counts(self):
        self.fixture(("a", "omit-method"))
        with self.assertRaisesRegex(ValueError, "JUnit method mismatch"):
            groups.verify_artifacts(self.manifest, self.root)

    def test_missing_or_invalid_evidence_rejected(self):
        for defect in ("missing-evidence", "bad-png"):
            with self.subTest(defect=defect):
                with tempfile.TemporaryDirectory() as temporary:
                    old = self.root
                    self.root = Path(temporary)
                    try:
                        self.fixture(("b", defect))
                        with self.assertRaises(ValueError):
                            groups.verify_artifacts(self.manifest, self.root)
                    finally:
                        self.root = old

    def test_missing_and_unexpected_artifact_directories_rejected(self):
        self.fixture()
        missing = self.root / "android-native-b-results"
        shutil.rmtree(missing)
        with self.assertRaisesRegex(ValueError, "directory set mismatch"):
            groups.verify_artifacts(self.manifest, self.root)
        shutil.rmtree(self.root / "android-native-a-results")
        self.fixture()
        (self.root / "android-native-c-results").mkdir()
        with self.assertRaisesRegex(ValueError, "directory set mismatch"):
            groups.verify_artifacts(self.manifest, self.root)

    def test_duplicate_png_basename_across_shards_rejected(self):
        self.fixture()
        a = self.root / "android-native-a-results/reports/androidTests/beta-ui-evidence"
        b = self.root / "android-native-b-results/reports/androidTests/beta-ui-evidence"
        source = next(a.glob("*.png"))
        target = next(b.glob("*.png"))
        target.rename(b / source.name)
        with self.assertRaisesRegex(ValueError, "Duplicate native PNG basename"):
            groups.verify_artifacts(self.manifest, self.root)


if __name__ == "__main__":
    unittest.main()
