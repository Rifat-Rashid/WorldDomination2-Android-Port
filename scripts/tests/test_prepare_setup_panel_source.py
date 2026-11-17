"""Verify extraction and read-only validation of source setup artwork."""

import base64
import gzip
import hashlib
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from PIL import Image
from scripts import prepare_setup_panel_source as panel


class SetupPanelSourceTests(unittest.TestCase):
    def test_source_dimensions_and_rgba_hash(self):
        with panel.extract_image() as image:
            self.assertEqual((798, 601), image.size)
            self.assertEqual("RGBA", image.mode)
            self.assertEqual("8bec470f77dedd85f43dcc893de2c92800b9d6e6768f7c2b1857b2587b6d6be9",
                             hashlib.sha256(image.tobytes()).hexdigest())

    def test_generate_and_check_are_pixel_identical_and_check_is_read_only(self):
        with tempfile.TemporaryDirectory() as tmp:
            source = Path(tmp) / "source.svgz"
            target = Path(tmp) / "output.png"
            source.write_bytes(panel.SOURCE.read_bytes())
            panel.prepare(source, target)
            source_before = source.read_bytes()
            target_before = target.read_bytes()
            with Image.open(target) as actual, panel.extract_image(source) as expected:
                self.assertEqual(expected.tobytes(), actual.convert("RGBA").tobytes())
            panel.prepare(source, target, check=True)
            self.assertEqual(source_before, source.read_bytes())
            self.assertEqual(target_before, target.read_bytes())
            panel.prepare(check=True)

    def test_check_rejects_altered_pixel_and_missing_png_without_writes(self):
        with tempfile.TemporaryDirectory() as tmp:
            target = Path(tmp) / "output.png"
            with self.assertRaises(FileNotFoundError):
                panel.prepare(panel.SOURCE, target, check=True)
            self.assertFalse(target.exists())
            panel.prepare(panel.SOURCE, target)
            with Image.open(target) as image:
                image = image.convert("RGBA")
                pixel = image.getpixel((0, 0))
                image.putpixel((0, 0), (pixel[0] ^ 1, *pixel[1:]))
                image.save(target)
            altered = target.read_bytes()
            with self.assertRaisesRegex(ValueError, "pixels differ"):
                panel.prepare(panel.SOURCE, target, check=True)
            self.assertEqual(altered, target.read_bytes())

    def test_rejects_wrong_source_and_invalid_embedded_data(self):
        with tempfile.TemporaryDirectory() as tmp:
            source = Path(tmp) / "source.svgz"
            source.write_bytes(b"unexpected source")
            with self.assertRaisesRegex(ValueError, "SVGZ hash"):
                panel.extract_image(source)
            for svg, message in ((b"<svg/>", "Expected one"),
                                 (b'<svg href="data:image/webp;base64,@@@"/>', "encoding"),
                                 (b'<svg href="data:image/webp;base64,' + base64.b64encode(b"bad") + b'"/>', "WebP hash")):
                raw = gzip.compress(svg, mtime=0)
                source.write_bytes(raw)
                with self.subTest(message=message), patch.object(panel, "SOURCE_SHA256", hashlib.sha256(raw).hexdigest()):
                    with self.assertRaisesRegex(ValueError, message):
                        panel.extract_image(source)


if __name__ == "__main__":
    unittest.main()
