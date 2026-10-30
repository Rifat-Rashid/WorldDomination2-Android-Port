#!/usr/bin/env python3
"""Guard the platform-independent Java gameplay and AI packages."""

from __future__ import annotations

import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
PACKAGE_ROOT = ROOT / "app" / "src" / "main" / "java" / "com" / "example" / "worlddomination" / "game"
CORE_PACKAGES = ("simulation", "ai")
FORBIDDEN_IMPORT = re.compile(
    r"^\s*import\s+(?:static\s+)?(?:android(?:x)?\.|com\.badlogic\.gdx\.)"
)


def scan_package(package_root: Path, package_name: str) -> list[str]:
    """Return source locations that violate the platform boundary."""
    if not package_root.is_dir():
        return [f"missing gameplay package: {package_root}"]

    violations = []
    for source in sorted(package_root.rglob("*.java")):
        relative = source.relative_to(package_root).as_posix()
        expected_package = f"com.example.worlddomination.game.{package_name}"
        if relative != source.name:
            expected_package += "." + relative.rsplit("/", 1)[0].replace("/", ".")

        package_declaration = None
        for line_number, line in enumerate(source.read_text(encoding="utf-8").splitlines(), 1):
            stripped = line.strip()
            if stripped.startswith("package "):
                package_declaration = stripped.removeprefix("package ").removesuffix(";")
            if FORBIDDEN_IMPORT.match(line):
                violations.append(
                    f"{source}:{line_number}: gameplay code imports Android or libGDX APIs"
                )

        if package_declaration != expected_package:
            violations.append(
                f"{source}: expected package {expected_package}, found {package_declaration}"
            )
    return violations


def find_violations(game_root: Path = PACKAGE_ROOT) -> list[str]:
    violations = []
    for package_name in CORE_PACKAGES:
        violations.extend(scan_package(game_root / package_name, package_name))
    return violations


def main() -> int:
    violations = find_violations()
    if violations:
        print("Gameplay architecture boundary failed:", file=sys.stderr)
        for violation in violations:
            print(f" - {violation}", file=sys.stderr)
        return 1

    print("Verified simulation and AI remain in correctly declared, platform-independent Java packages")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
