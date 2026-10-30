"""Tests for the gameplay-layer platform boundary check."""

from pathlib import Path
import sys
import tempfile
import unittest


SCRIPTS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(SCRIPTS))

from check_simulation_boundary import find_violations  # noqa: E402


class SimulationBoundaryCheckTest(unittest.TestCase):
    def write_source(self, root: Path, package: str, relative_path: str, content: str) -> None:
        source = root / package / relative_path
        source.parent.mkdir(parents=True, exist_ok=True)
        source.write_text(content, encoding="utf-8")

    def test_accepts_platform_independent_simulation_and_ai_packages(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            game_root = Path(temporary)
            self.write_source(
                game_root,
                "simulation",
                "GameState.java",
                "package com.example.worlddomination.game.simulation;\n"
                "public final class GameState {}\n",
            )
            self.write_source(
                game_root,
                "ai",
                "planning/AiPlanner.java",
                "package com.example.worlddomination.game.ai.planning;\n"
                "public final class AiPlanner {}\n",
            )

            self.assertEqual([], find_violations(game_root))

    def test_reports_android_and_libgdx_imports_with_source_lines(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            game_root = Path(temporary)
            self.write_source(
                game_root,
                "simulation",
                "GameState.java",
                "package com.example.worlddomination.game.simulation;\n"
                "import android.os.SystemClock;\n"
                "import static com.badlogic.gdx.math.MathUtils.random;\n",
            )
            self.write_source(
                game_root,
                "ai",
                "AiPlanner.java",
                "package com.example.worlddomination.game.ai;\n",
            )

            violations = find_violations(game_root)

            self.assertEqual(2, len(violations))
            self.assertIn("GameState.java:2", violations[0])
            self.assertIn("GameState.java:3", violations[1])

    def test_reports_package_declaration_drift(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            game_root = Path(temporary)
            self.write_source(
                game_root,
                "simulation",
                "GameState.java",
                "package com.example.worlddomination.game.rendering;\n",
            )
            self.write_source(
                game_root,
                "ai",
                "AiPlanner.java",
                "package com.example.worlddomination.game.ai;\n",
            )

            violations = find_violations(game_root)

            self.assertEqual(1, len(violations))
            self.assertIn("expected package com.example.worlddomination.game.simulation", violations[0])


if __name__ == "__main__":
    unittest.main()
