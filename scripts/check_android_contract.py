#!/usr/bin/env python3
"""Guard the native, Android-only app configuration in CI."""

from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
ANDROID_NS = "http://schemas.android.com/apk/res/android"


def fail(message: str) -> int:
    print(f"Android platform contract failed: {message}", file=sys.stderr)
    return 1


def main() -> int:
    settings_path = ROOT / "settings.gradle.kts"
    app_gradle_path = ROOT / "app" / "build.gradle.kts"
    manifest_path = ROOT / "app" / "src" / "main" / "AndroidManifest.xml"

    settings = settings_path.read_text(encoding="utf-8")
    includes = re.findall(r'include\s*\(([^)]*)\)', settings)
    modules = [module for group in includes for module in re.findall(r'"(:[^\"]+)"', group)]
    if modules != [":app"]:
        return fail(f"expected only the :app module, found {modules or 'no modules'}")

    gradle = app_gradle_path.read_text(encoding="utf-8")
    for setting in ("sourceCompatibility", "targetCompatibility"):
        if not re.search(rf"{setting}\s*=\s*JavaVersion\.VERSION_11\b", gradle):
            return fail(f"{setting} must remain Java 11 compatible")

    manifest = ET.parse(manifest_path).getroot()
    android_attr = lambda name: f"{{{ANDROID_NS}}}{name}"
    for permission in manifest.findall("uses-permission"):
        if permission.get(android_attr("name")) == "android.permission.INTERNET":
            application = manifest.find("application")
            if application is None or application.get(android_attr("usesCleartextTraffic")) != "false":
                return fail("optional model provisioning with INTERNET requires TLS-only traffic")

    launcher = None
    for activity in manifest.findall("./application/activity"):
        actions = {node.get(android_attr("name")) for node in activity.findall("./intent-filter/action")}
        categories = {node.get(android_attr("name")) for node in activity.findall("./intent-filter/category")}
        if "android.intent.action.MAIN" in actions and "android.intent.category.LAUNCHER" in categories:
            launcher = activity
            break
    if launcher is None:
        return fail("no Android MAIN/LAUNCHER activity is declared")
    if launcher.get(android_attr("screenOrientation")) != "landscape":
        return fail("the launcher activity must be locked to landscape")

    forbidden_imports = re.compile(
        r"^\s*import\s+(?:android\.webkit\.WebView|com\.chaquo\.python|org\.python)(?:\.|;)"
    )
    network_imports = re.compile(r"^\s*import\s+(?:java\.net|javax\.net|okhttp3|android\.net\.http)\.")
    java_source = ROOT / "app" / "src" / "main" / "java"
    model_provisioner = java_source / "com/example/worlddomination/platform/qwen/QwenModel.java"
    for source in java_source.rglob("*.java"):
        for line_number, line in enumerate(source.read_text(encoding="utf-8").splitlines(), start=1):
            if forbidden_imports.search(line):
                return fail(f"non-native runtime import in {source.relative_to(ROOT)}:{line_number}")
            if source != model_provisioner and network_imports.search(line):
                return fail(f"network import outside model provisioning in {source.relative_to(ROOT)}:{line_number}")

    print("Verified Android-only :app target, Java 11, isolated TLS model provisioning, and landscape launcher")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
