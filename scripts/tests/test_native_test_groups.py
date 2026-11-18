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
                if evidence == "beta-ui-evidence":
                    for selected, required in (
                            (groups.ACTION_MENUS, groups.ACTION_MENUS_EVIDENCE),
                            (groups.ACTION_MENUS, groups.PREPARED_SHIP_TUBE_EVIDENCE),
                            (groups.STOCK_CONTROLS, groups.PREPARED_STOCK_EVIDENCE),
                            (groups.LEGAL_TERMINAL, groups.LEGAL_TERMINAL_EVIDENCE),
                            (groups.HELP, groups.HELP_EVIDENCE),
                            (groups.HIDDEN_HELP, groups.HIDDEN_HELP_EVIDENCE),
                            (groups.HIDDEN_TYPOGRAPHY, groups.HIDDEN_TYPOGRAPHY_EVIDENCE),
                            (groups.HIDDEN_CONTEXT, groups.HIDDEN_CONTEXT_EVIDENCE),
                            (groups.SETUP_PANELS, groups.SETUP_PANELS_EVIDENCE),
                            (groups.SETUP_LAYOUT, groups.SETUP_LAYOUT_EVIDENCE),
                            (groups.SETUP_COLORS, groups.SETUP_COLORS_EVIDENCE),
                            (groups.SETUP_BUTTONS, groups.SETUP_BUTTONS_EVIDENCE),
                            (groups.SETUP_CAROUSEL, groups.SETUP_CAROUSEL_EVIDENCE),
                            (groups.SETUP_BUILDINGS, groups.SETUP_BUILDINGS_EVIDENCE),
                            (groups.SETUP_ENEMIES, groups.SETUP_ENEMIES_EVIDENCE),
                            (groups.NAVIGATION_PANEL, groups.NAVIGATION_PANEL_EVIDENCE),
                            (groups.INFORMATION_PANEL, groups.INFORMATION_PANEL_EVIDENCE),
                            (groups.STATISTIC_BARS, groups.STATISTIC_BARS_EVIDENCE),
                            (groups.EQUIPMENT_CARDS, groups.EQUIPMENT_CARDS_EVIDENCE),
                            (groups.SCORE_PANEL, groups.SCORE_PANEL_EVIDENCE),
                            (groups.MASS_MEDIA_FIELDS, groups.MASS_MEDIA_FIELDS_EVIDENCE),
                            (groups.PHONE_TOOLBAR_AUDIO, groups.PHONE_TOOLBAR_AUDIO_EVIDENCE),
                            (groups.MESSAGE_WHOLE_LINES, groups.MESSAGE_WHOLE_LINES_EVIDENCE),
                            (groups.PHONE_PANELS, groups.PHONE_PANELS_EVIDENCE),
                            (groups.SAFE_AREA, groups.SAFE_AREA_EVIDENCE),
                            (groups.RESOURCE_MODAL_CONTEXT,
                             groups.RESOURCE_MODAL_CONTEXT_EVIDENCE),
                            (groups.TECHNOLOGY_FIELDS, groups.TECHNOLOGY_FIELDS_EVIDENCE),
                            (groups.ENDGAME_FAILURE, groups.ENDGAME_FAILURE_EVIDENCE)):
                        if selected in group["classes"]:
                            for name in required:
                                (folder / name).write_bytes(PNG)

    def test_manifest_is_exact_closed_inventory_and_both_groups_make_beta(self):
        self.assertEqual(117, len(self.manifest["requiredClasses"]))
        self.assertEqual(189, len(self.manifest["expectedCases"]))
        self.assertEqual({"a", "b"}, {g["id"] for g in self.manifest["groups"]})
        self.assertEqual({"beta-ui-evidence", "propaganda-evidence"},
                         {e for g in self.manifest["groups"] for e in g["evidence"]})
        self.assertEqual(set(self.manifest["requiredClasses"]),
                         {c for g in self.manifest["groups"] for c in g["classes"]})
        self.assertEqual(set(self.manifest["requiredClasses"]),
                         {name for name, _ in self.manifest["expectedCases"]})

    def test_message_whole_line_cases_and_screenshots_are_required(self):
        name = groups.MESSAGE_WHOLE_LINES
        self.assertIn(name, groups.group_by_id(self.manifest, "a")["classes"])
        self.assertEqual({"completeRowsMatchIndependentVisibleTextAtBothSizes",
                          "liveWorldRetainsFullHistoryAndReachableLastRow"},
                         {method for selected, method in self.manifest["expectedCases"]
                          if selected == name})
        self.fixture()
        folder = self.root / "android-native-a-results" / "reports/androidTests/beta-ui-evidence"
        for filename in groups.MESSAGE_WHOLE_LINES_EVIDENCE:
            path = folder / filename
            original = path.read_bytes()
            path.unlink()
            with self.assertRaisesRegex(ValueError, "message whole lines screenshots"):
                groups.verify_artifacts(self.manifest, self.root)
            path.write_bytes(original)

    def test_phone_panel_cases_and_all_screenshots_are_required(self):
        name = groups.PHONE_PANELS
        self.assertIn(name, groups.group_by_id(self.manifest, "a")["classes"])
        methods = {"sourcePanelsMatchIndependentCapturesAtIntegerSize",
                   "liveWorldPanelArtStaysInsideItsBoundsAtBothAspectRatios"}
        self.assertEqual(methods, {method for selected, method in self.manifest["expectedCases"]
                                   if selected == name})
        self.assertEqual(8, len(groups.PHONE_PANELS_EVIDENCE))
        self.fixture()
        report = (self.root / "android-native-a-results"
                  / "outputs/androidTest-results/connected/debug/TEST-device.xml")
        original = report.read_bytes()
        for method in sorted(methods):
            tree = ET.fromstring(original)
            suite = next(s for s in tree.iter("testsuite") if s.get("name") == name)
            case = next(c for c in suite.findall("testcase") if c.get("name") == method)
            suite.remove(case)
            suite.set("tests", str(int(suite.get("tests")) - 1))
            tree.set("tests", str(int(tree.get("tests")) - 1))
            report.write_bytes(ET.tostring(tree))
            with self.subTest(method=method), self.assertRaisesRegex(ValueError, "JUnit method mismatch"):
                groups.verify_artifacts(self.manifest, self.root)
            report.write_bytes(original)
        folder = self.root / "android-native-a-results/reports/androidTests/beta-ui-evidence"
        for filename in sorted(groups.PHONE_PANELS_EVIDENCE):
            image = folder / filename
            image.unlink()
            with self.subTest(image=filename), self.assertRaisesRegex(ValueError, "Missing required phone panels screenshots"):
                groups.verify_artifacts(self.manifest, self.root)
            image.write_bytes(PNG)

    def test_bitmap_pixmap_transfer_is_selected_in_group_a_with_both_cases(self):
        name = "com.example.worlddomination.game.assets.BitmapPixmapTransferInstrumentedTest"
        self.assertIn(name, groups.group_by_id(self.manifest, "a")["classes"])
        self.assertEqual({"exactTransferForPremultipliedAndUnpremultipliedPixels",
                          "exactTransferForPackagedOriginalBitmap"},
                         {method for selected, method in self.manifest["expectedCases"]
                          if selected == name})

    def test_production_aircraft_gain_is_required_in_its_selected_group(self):
        name = "com.example.worlddomination.game.rendering.SourceAircraftAudioInstrumentedTest"
        method = "productionOriginalAudioPreservesSourceGainFadeMuteAndCleanup"
        self.assertIn(name, groups.group_by_id(self.manifest, "b")["classes"])
        self.assertIn([name, method], self.manifest["expectedCases"])
        self.fixture()
        report = (self.root / "android-native-b-results"
                  / "outputs/androidTest-results/connected/debug/TEST-device.xml")
        tree = ET.parse(report)
        suite = next(s for s in tree.iter("testsuite") if s.get("name") == name)
        case = next(c for c in suite.findall("testcase") if c.get("name") == method)
        suite.remove(case)
        suite.set("tests", str(int(suite.get("tests")) - 1))
        tree.getroot().set("tests", str(int(tree.getroot().get("tests")) - 1))
        tree.write(report)
        with self.assertRaisesRegex(ValueError, "JUnit method mismatch"):
            groups.verify_artifacts(self.manifest, self.root)

    def test_resource_modal_context_recovery_case_is_required(self):
        name = groups.RESOURCE_MODAL_CONTEXT
        method = "resourceModalRestoresAfterEglContextLoss"
        self.assertEqual(1, self.manifest["requiredClasses"].count(name))
        self.assertEqual(1, groups.group_by_id(self.manifest, "a")["classes"].count(name))
        self.assertEqual([[name, method]],
                         [pair for pair in self.manifest["expectedCases"] if pair[0] == name])
        self.fixture()
        report = (self.root / "android-native-a-results"
                  / "outputs/androidTest-results/connected/debug/TEST-device.xml")
        tree = ET.parse(report)
        suite = next(s for s in tree.iter("testsuite") if s.get("name") == name)
        suite.remove(next(c for c in suite.findall("testcase") if c.get("name") == method))
        suite.set("tests", "0")
        tree.getroot().set("tests", str(int(tree.getroot().get("tests")) - 1))
        tree.write(report)
        with self.assertRaisesRegex(ValueError, "JUnit class mismatch|JUnit method mismatch"):
            groups.verify_artifacts(self.manifest, self.root)

    def test_resource_modal_context_recovery_requires_before_and_after_screenshots(self):
        self.fixture()
        self.assertEqual({"resource-modal-context-before.png", "resource-modal-context-after.png"},
                         groups.RESOURCE_MODAL_CONTEXT_EVIDENCE)
        folder = self.root / "android-native-a-results/reports/androidTests/beta-ui-evidence"
        for filename in sorted(groups.RESOURCE_MODAL_CONTEXT_EVIDENCE):
            image = folder / filename
            image.unlink()
            with self.subTest(image=filename), self.assertRaisesRegex(
                    ValueError, "Missing required resource modal context recovery screenshots"):
                groups.verify_artifacts(self.manifest, self.root)
            image.write_bytes(PNG)

    def test_technology_fields_require_selected_wrapped_and_clipped_screenshots(self):
        self.fixture()
        self.assertEqual({"source-technology-original-empty.png",
                          "source-technology-original-selected.png",
                          "source-technology-wrapped-name.png",
                          "source-technology-six-selected.png"}, groups.TECHNOLOGY_FIELDS_EVIDENCE)
        folder = self.root / "android-native-b-results/reports/androidTests/beta-ui-evidence"
        for filename in sorted(groups.TECHNOLOGY_FIELDS_EVIDENCE):
            image = folder / filename
            image.unlink()
            with self.subTest(image=filename), self.assertRaisesRegex(
                    ValueError, "Missing required technology fields screenshots"):
                groups.verify_artifacts(self.manifest, self.root)
            image.write_bytes(PNG)

    def test_mass_media_fields_require_source_oracle_and_wrong_religion_controls(self):
        self.fixture()
        self.assertEqual({f"source-mass-media-{variant}-{width}.png"
                          for variant in ("value", "value-old-control", "mus", "mus-oracle", "wrong-religion")
                          for width in (800, 1194)}, groups.MASS_MEDIA_FIELDS_EVIDENCE)
        folder = self.root / "android-native-b-results/reports/androidTests/beta-ui-evidence"
        for filename in sorted(groups.MASS_MEDIA_FIELDS_EVIDENCE):
            image = folder / filename
            image.unlink()
            with self.subTest(image=filename), self.assertRaisesRegex(
                    ValueError, "Missing required Mass Media source fields screenshots"):
                groups.verify_artifacts(self.manifest, self.root)
            image.write_bytes(PNG)

    def test_biological_idle_handoff_case_is_required(self):
        name = "com.example.worlddomination.game.rendering.SourceBiologicalPlaybackInstrumentedTest"
        method = "legalSubmarineIdleAndNextConfirmKeepCanonicalCloudsAbsent"
        self.assertIn(name, groups.group_by_id(self.manifest, "b")["classes"])
        self.assertIn([name, method], self.manifest["expectedCases"])
        self.fixture()
        report = (self.root / "android-native-b-results"
                  / "outputs/androidTest-results/connected/debug/TEST-device.xml")
        tree = ET.parse(report)
        suite = next(s for s in tree.iter("testsuite") if s.get("name") == name)
        suite.remove(next(c for c in suite.findall("testcase") if c.get("name") == method))
        suite.set("tests", str(int(suite.get("tests")) - 1))
        tree.getroot().set("tests", str(int(tree.getroot().get("tests")) - 1))
        tree.write(report)
        with self.assertRaisesRegex(ValueError, "JUnit method mismatch"):
            groups.verify_artifacts(self.manifest, self.root)

    def test_safe_area_requires_both_rendered_scenes(self):
        self.fixture()
        self.assertIn(groups.SAFE_AREA, groups.group_by_id(self.manifest, "a")["classes"])
        folder = self.root / "android-native-a-results/reports/androidTests/beta-ui-evidence"
        for filename in sorted(groups.SAFE_AREA_EVIDENCE):
            image = folder / filename
            image.unlink()
            with self.subTest(image=filename), self.assertRaisesRegex(
                    ValueError, "Missing required safe area viewport screenshots"):
                groups.verify_artifacts(self.manifest, self.root)
            image.write_bytes(PNG)

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
        self.assertEqual(117, sum(row["classes"] for row in result.values()))
        self.assertEqual(189, sum(row["executed"] for row in result.values()))
        self.assertEqual(223, sum(row["requiredPngs"] for row in result.values()))

    def test_unsaved_score_requires_actual_endgame_capture(self):
        self.fixture()
        selected = next(g for g in self.manifest["groups"] if groups.ENDGAME_FAILURE in g["classes"])
        image = (self.root / ("android-native-" + selected["id"] + "-results")
                 / "reports/androidTests/beta-ui-evidence/source-endgame-unsaved-score.png")
        image.unlink()
        with self.assertRaisesRegex(ValueError, "Missing required unsaved score screenshots"):
            groups.verify_artifacts(self.manifest, self.root)

    def test_legal_terminal_campaigns_require_battle_score_and_new_game_images(self):
        self.fixture()
        group = next(g for g in self.manifest["groups"] if groups.LEGAL_TERMINAL in g["classes"])
        folder = (self.root / ("android-native-" + group["id"] + "-results")
                  / "reports/androidTests/beta-ui-evidence")
        for name in groups.LEGAL_TERMINAL_EVIDENCE:
            image = folder / name
            image.unlink()
            with self.subTest(image=name), self.assertRaisesRegex(
                    ValueError, "Missing required legal terminal playback screenshots"):
                groups.verify_artifacts(self.manifest, self.root)
            image.write_bytes(PNG)

    def test_prepared_stock_counts_require_accept_and_removal_screenshots(self):
        self.fixture()
        group = next(g for g in self.manifest["groups"]
                     if groups.STOCK_CONTROLS in g["classes"])
        folder = (self.root / ("android-native-" + group["id"] + "-results")
                  / "reports/androidTests/beta-ui-evidence")
        for name in groups.PREPARED_STOCK_EVIDENCE:
            image = folder / name
            image.unlink()
            with self.subTest(image=name), self.assertRaisesRegex(
                    ValueError, "Missing required prepared stock counts screenshots"):
                groups.verify_artifacts(self.manifest, self.root)
            image.write_bytes(PNG)

    def test_missing_zero_budget_action_menu_capture_is_rejected(self):
        self.fixture()
        group = next(g for g in self.manifest["groups"]
                     if groups.ACTION_MENUS in g["classes"])
        folder = (self.root / ("android-native-" + group["id"] + "-results")
                  / "reports/androidTests/beta-ui-evidence")
        missing = folder / "source-diplomacy-zero-budget.png"
        missing.unlink()
        with self.assertRaisesRegex(ValueError, "Missing required action menu zero budget screenshots"):
            groups.verify_artifacts(self.manifest, self.root)

    def test_missing_prepared_ship_tube_reselection_capture_is_rejected(self):
        self.fixture()
        group = next(g for g in self.manifest["groups"]
                     if groups.ACTION_MENUS in g["classes"])
        folder = (self.root / ("android-native-" + group["id"] + "-results")
                  / "reports/androidTests/beta-ui-evidence")
        missing = folder / "source-ship-tube-reselected.png"
        missing.unlink()
        with self.assertRaisesRegex(ValueError, "Missing required prepared ship tube reselection screenshots"):
            groups.verify_artifacts(self.manifest, self.root)

    def test_missing_phone_toolbar_audio_screenshots_are_rejected(self):
        self.fixture()
        self.assertEqual(5, len(groups.PHONE_TOOLBAR_AUDIO_EVIDENCE))
        group = next(g for g in self.manifest["groups"]
                     if groups.PHONE_TOOLBAR_AUDIO in g["classes"])
        folder = (self.root / ("android-native-" + group["id"] + "-results")
                  / "reports/androidTests/beta-ui-evidence")
        for name in sorted(groups.PHONE_TOOLBAR_AUDIO_EVIDENCE):
            image = folder / name
            image.unlink()
            with self.subTest(image=name), self.assertRaisesRegex(
                    ValueError, "Missing required phone toolbar audio screenshots"):
                groups.verify_artifacts(self.manifest, self.root)
            image.write_bytes(PNG)

    def test_missing_score_panel_screenshots_are_rejected(self):
        self.fixture()
        self.assertEqual(11, len(groups.SCORE_PANEL_EVIDENCE))
        group = next(g for g in self.manifest["groups"]
                     if groups.SCORE_PANEL in g["classes"])
        folder = (self.root / ("android-native-" + group["id"] + "-results")
                  / "reports/androidTests/beta-ui-evidence")
        for name in sorted(groups.SCORE_PANEL_EVIDENCE):
            image = folder / name
            image.unlink()
            with self.subTest(image=name), self.assertRaisesRegex(
                    ValueError, "Missing required score panel screenshots"):
                groups.verify_artifacts(self.manifest, self.root)
            image.write_bytes(PNG)

    def test_missing_equipment_card_screenshots_are_rejected(self):
        self.fixture()
        self.assertEqual(10, len(groups.EQUIPMENT_CARDS_EVIDENCE))
        group = next(g for g in self.manifest["groups"]
                     if groups.EQUIPMENT_CARDS in g["classes"])
        folder = (self.root / ("android-native-" + group["id"] + "-results")
                  / "reports/androidTests/beta-ui-evidence")
        for name in sorted(groups.EQUIPMENT_CARDS_EVIDENCE):
            image = folder / name
            image.unlink()
            with self.subTest(image=name), self.assertRaisesRegex(
                    ValueError, "Missing required equipment cards screenshots"):
                groups.verify_artifacts(self.manifest, self.root)
            image.write_bytes(PNG)

    def test_missing_information_panel_screenshots_are_rejected(self):
        self.fixture()
        self.assertEqual(11, len(groups.INFORMATION_PANEL_EVIDENCE))
        group = next(g for g in self.manifest["groups"]
                     if groups.INFORMATION_PANEL in g["classes"])
        folder = (self.root / ("android-native-" + group["id"] + "-results")
                  / "reports/androidTests/beta-ui-evidence")
        for name in sorted(groups.INFORMATION_PANEL_EVIDENCE):
            image = folder / name
            image.unlink()
            with self.subTest(image=name), self.assertRaisesRegex(
                    ValueError, "Missing required information panel screenshots"):
                groups.verify_artifacts(self.manifest, self.root)
            image.write_bytes(PNG)

    def test_missing_statistic_bar_screenshots_are_rejected(self):
        self.fixture()
        self.assertEqual(3, len(groups.STATISTIC_BARS_EVIDENCE))
        group = next(g for g in self.manifest["groups"]
                     if groups.STATISTIC_BARS in g["classes"])
        folder = (self.root / ("android-native-" + group["id"] + "-results")
                  / "reports/androidTests/beta-ui-evidence")
        for name in sorted(groups.STATISTIC_BARS_EVIDENCE):
            image = folder / name
            image.unlink()
            with self.subTest(image=name), self.assertRaisesRegex(
                    ValueError, "Missing required statistic bars screenshots"):
                groups.verify_artifacts(self.manifest, self.root)
            image.write_bytes(PNG)

    def test_missing_navigation_panel_screenshots_are_rejected(self):
        self.fixture()
        self.assertEqual(7, len(groups.NAVIGATION_PANEL_EVIDENCE))
        group = next(g for g in self.manifest["groups"]
                     if groups.NAVIGATION_PANEL in g["classes"])
        folder = (self.root / ("android-native-" + group["id"] + "-results")
                  / "reports/androidTests/beta-ui-evidence")
        for name in sorted(groups.NAVIGATION_PANEL_EVIDENCE):
            image = folder / name
            image.unlink()
            with self.subTest(image=name), self.assertRaisesRegex(
                    ValueError, "Missing required navigation panel screenshots"):
                groups.verify_artifacts(self.manifest, self.root)
            image.write_bytes(PNG)

    def test_missing_hidden_help_evidence_is_rejected(self):
        self.fixture()
        group = next(g for g in self.manifest["groups"] if groups.HIDDEN_HELP in g["classes"])
        folder = (self.root / ("android-native-" + group["id"] + "-results")
                  / "reports/androidTests/beta-ui-evidence")
        (folder / "hidden-help-command.png").unlink()
        with self.assertRaisesRegex(ValueError, "Missing required hidden Help screenshots"):
            groups.verify_artifacts(self.manifest, self.root)

    def test_hidden_typography_case_and_every_oracle_control_image_are_required(self):
        name = groups.HIDDEN_TYPOGRAPHY
        self.assertEqual(1, self.manifest["requiredClasses"].count(name))
        self.assertEqual(1, groups.group_by_id(self.manifest, "a")["classes"].count(name))
        self.assertEqual([[name, "sourceDynamicFieldsMatchAuthoredFontsAndRejectResourceArial"]],
                         [pair for pair in self.manifest["expectedCases"] if pair[0] == name])
        self.assertEqual(12, len(groups.HIDDEN_TYPOGRAPHY_EVIDENCE))
        self.fixture()
        folder = (self.root / "android-native-a-results/reports/androidTests/beta-ui-evidence")
        for name in sorted(groups.HIDDEN_TYPOGRAPHY_EVIDENCE):
            image = folder / name
            image.unlink()
            with self.subTest(image=name), self.assertRaisesRegex(
                    ValueError, "Missing required hidden Help typography screenshots"):
                groups.verify_artifacts(self.manifest, self.root)
            image.write_bytes(PNG)

    def test_cheated_outcome_case_is_required_without_inventing_screenshots(self):
        name = groups.HIDDEN_OUTCOME
        self.assertEqual(1, self.manifest["requiredClasses"].count(name))
        self.assertEqual(1, groups.group_by_id(self.manifest, "a")["classes"].count(name))
        self.assertEqual([[name, "cheatedVictoryShowsAuthoredScoreWithoutSavingPersonalBest"]],
                         [pair for pair in self.manifest["expectedCases"] if pair[0] == name])
        self.fixture()
        result = groups.verify_artifacts(self.manifest, self.root)
        self.assertEqual(223, sum(row["requiredPngs"] for row in result.values()))

    def test_hidden_context_case_and_both_first_frame_images_are_required(self):
        name = groups.HIDDEN_CONTEXT
        self.assertEqual(1, self.manifest["requiredClasses"].count(name))
        self.assertEqual(1, groups.group_by_id(self.manifest, "a")["classes"].count(name))
        self.assertEqual([[name, "authoredCommandGlyphsRestoreOnFirstFrameAfterForcedEglLoss"]],
                         [pair for pair in self.manifest["expectedCases"] if pair[0] == name])
        self.assertEqual({"hidden-help-context-before.png", "hidden-help-context-first-resumed.png"},
                         groups.HIDDEN_CONTEXT_EVIDENCE)
        self.fixture()
        folder = self.root / "android-native-a-results/reports/androidTests/beta-ui-evidence"
        for filename in sorted(groups.HIDDEN_CONTEXT_EVIDENCE):
            image = folder / filename
            image.unlink()
            with self.subTest(image=filename), self.assertRaisesRegex(
                    ValueError, "Missing required hidden Help context recovery screenshots"):
                groups.verify_artifacts(self.manifest, self.root)
            image.write_bytes(PNG)

    def test_missing_help_screenshots_cannot_pass_using_other_test_images(self):
        self.fixture()
        self.assertEqual(25, len(groups.HELP_EVIDENCE))
        group = next(g for g in self.manifest["groups"] if groups.HELP in g["classes"])
        folder = self.root / ("android-native-" + group["id"] + "-results") / "reports/androidTests/beta-ui-evidence"
        (folder / "source-help-help_index-800x600-actual.png").unlink()
        with self.assertRaisesRegex(ValueError, "Missing required Help screenshots"):
            groups.verify_artifacts(self.manifest, self.root)

    def test_missing_setup_screenshots_cannot_pass_using_other_test_images(self):
        self.fixture()
        self.assertEqual(6, len(groups.SETUP_PANELS_EVIDENCE))
        self.assertEqual(6, len(groups.SETUP_LAYOUT_EVIDENCE))
        for selected, filename, label in (
                (groups.SETUP_PANELS, "source-setup-panels-wide-stretched.png", "setup panels"),
                (groups.SETUP_LAYOUT, "source-setup-full-1194x552-enabled.png", "setup layout"),
                (groups.SETUP_LAYOUT, "source-setup-full-1194x552-leader-name.png", "setup layout"),
                (groups.SETUP_LAYOUT, "source-setup-full-800x600-leader-name.png", "setup layout")):
            group = next(g for g in self.manifest["groups"] if selected in g["classes"])
            folder = self.root / ("android-native-" + group["id"] + "-results") / "reports/androidTests/beta-ui-evidence"
            missing = folder / filename
            missing.unlink()
            with self.subTest(selected=selected), self.assertRaisesRegex(
                    ValueError, "Missing required " + label + " screenshots"):
                groups.verify_artifacts(self.manifest, self.root)
            missing.write_bytes(PNG)

    def test_missing_setup_color_screenshot_is_rejected(self):
        self.fixture()
        self.assertEqual(4, len(groups.SETUP_COLORS_EVIDENCE))
        group = next(g for g in self.manifest["groups"] if groups.SETUP_COLORS in g["classes"])
        folder = self.root / ("android-native-" + group["id"] + "-results") / "reports/androidTests/beta-ui-evidence"
        (folder / "source-setup-colors-1194x552-expected.png").unlink()
        with self.assertRaisesRegex(ValueError, "Missing required setup colors screenshots"):
            groups.verify_artifacts(self.manifest, self.root)

    def test_missing_setup_button_screenshot_is_rejected(self):
        self.fixture()
        self.assertEqual(6, len(groups.SETUP_BUTTONS_EVIDENCE))
        group = next(g for g in self.manifest["groups"] if groups.SETUP_BUTTONS in g["classes"])
        folder = self.root / ("android-native-" + group["id"] + "-results") / "reports/androidTests/beta-ui-evidence"
        (folder / "source-setup-buttons-1194x552-wrong.png").unlink()
        with self.assertRaisesRegex(ValueError, "Missing required setup buttons screenshots"):
            groups.verify_artifacts(self.manifest, self.root)

    def test_missing_setup_carousel_screenshot_is_rejected(self):
        self.fixture()
        self.assertEqual(10, len(groups.SETUP_CAROUSEL_EVIDENCE))
        group = next(g for g in self.manifest["groups"] if groups.SETUP_CAROUSEL in g["classes"])
        folder = self.root / ("android-native-" + group["id"] + "-results") / "reports/androidTests/beta-ui-evidence"
        for name in ("source-setup-carousel-1194x552-wrong.png",
                     "source-setup-carousel-responsive-800x600.png",
                     "source-setup-carousel-responsive-1194x552.png",
                     "source-setup-carousel-native-mesh-actual.png",
                     "source-setup-carousel-native-mesh-expected.png"):
            image = folder / name
            image.unlink()
            with self.subTest(image=name), self.assertRaisesRegex(ValueError, "Missing required setup carousel screenshots"):
                groups.verify_artifacts(self.manifest, self.root)
            image.write_bytes(PNG)

    def test_missing_setup_building_screenshot_is_rejected(self):
        self.fixture()
        self.assertEqual(6, len(groups.SETUP_BUILDINGS_EVIDENCE))
        group = next(g for g in self.manifest["groups"] if groups.SETUP_BUILDINGS in g["classes"])
        folder = self.root / ("android-native-" + group["id"] + "-results") / "reports/androidTests/beta-ui-evidence"
        (folder / "source-setup-buildings-1194x552-wrong.png").unlink()
        with self.assertRaisesRegex(ValueError, "Missing required setup buildings screenshots"):
            groups.verify_artifacts(self.manifest, self.root)

    def test_missing_setup_enemy_screenshots_are_rejected(self):
        self.fixture()
        self.assertEqual(42, len(groups.SETUP_ENEMIES_EVIDENCE))
        group = next(g for g in self.manifest["groups"] if groups.SETUP_ENEMIES in g["classes"])
        folder = self.root / ("android-native-" + group["id"] + "-results") / "reports/androidTests/beta-ui-evidence"
        for name in ("source-enemy-wells-800-wrong.png", "source-selected-1194-0-expected.png",
                     "source-selected-800-4-shifted.png", "source-selected-1194-4-opaque.png"):
            image = folder / name
            image.unlink()
            with self.subTest(image=name), self.assertRaisesRegex(ValueError, "Missing required setup enemies screenshots"):
                groups.verify_artifacts(self.manifest, self.root)
            image.write_bytes(PNG)

    def test_missing_new_setup_enemy_case_is_rejected(self):
        self.fixture()
        group = next(g for g in self.manifest["groups"] if groups.SETUP_ENEMIES in g["classes"])
        xml = self.root / ("android-native-" + group["id"] + "-results") / "outputs/androidTest-results/connected/debug/TEST-device.xml"
        original = xml.read_bytes()
        for method in ("originalFourWellsAndArrowAtBothSizes",
                       "portraitAndCancelUseIndependentSourceRegistrationAndOpacity"):
            tree = ET.parse(xml)
            suite = next(s for s in tree.getroot().findall("testsuite")
                         if s.get("name") == groups.SETUP_ENEMIES)
            case = next(c for c in suite.findall("testcase") if c.get("name") == method)
            suite.remove(case)
            suite.set("tests", str(int(suite.get("tests")) - 1))
            tree.getroot().set("tests", str(int(tree.getroot().get("tests")) - 1))
            tree.write(xml)
            with self.subTest(method=method), self.assertRaisesRegex(ValueError, "JUnit method mismatch"):
                groups.verify_artifacts(self.manifest, self.root)
            xml.write_bytes(original)

    def test_seed29_easy_ship_retry_campaign_case_is_required(self):
        self.fixture()
        name = "com.example.worlddomination.game.simulation.SourceLegalEasyCampaignInstrumentedTest"
        method = "seed29EasyShipRetryCampaignMatchesEveryCapturedState"
        self.assertEqual(1, sum(selected == name and case == method
                                for selected, case in self.manifest["expectedCases"]))
        group = next(g for g in self.manifest["groups"] if name in g["classes"])
        xml = (self.root / ("android-native-" + group["id"] + "-results")
               / "outputs/androidTest-results/connected/debug/TEST-device.xml")
        original = xml.read_bytes()
        tree = ET.parse(xml)
        suite = next(s for s in tree.getroot().findall("testsuite") if s.get("name") == name)
        case = next(c for c in suite.findall("testcase") if c.get("name") == method)
        suite.remove(case)
        suite.set("tests", str(int(suite.get("tests")) - 1))
        tree.getroot().set("tests", str(int(tree.getroot().get("tests")) - 1))
        tree.write(xml)
        with self.assertRaisesRegex(ValueError, "JUnit method mismatch"):
            groups.verify_artifacts(self.manifest, self.root)
        xml.write_bytes(original)

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
        # Add a duplicate without removing any mandatory screenshot. Choosing
        # and renaming a glob result depended on filesystem iteration order and
        # could trigger the missing-evidence check before duplicate detection.
        source = a / "a-beta-ui-evidence-proof.png"
        self.assertTrue(source.is_file())
        self.assertFalse((b / source.name).exists())
        shutil.copy2(source, b / source.name)
        with self.assertRaisesRegex(ValueError, "Duplicate native PNG basename"):
            groups.verify_artifacts(self.manifest, self.root)


if __name__ == "__main__":
    unittest.main()
