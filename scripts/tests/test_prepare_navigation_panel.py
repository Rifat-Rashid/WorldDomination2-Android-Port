"""Verify navigation extraction, restricted label edits and read-only checks."""
import base64
import gzip
import hashlib
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from PIL import Image
from scripts import prepare_navigation_panel as panel


class NavigationPanelTests(unittest.TestCase):
    def test_packaged_pixels_match_source_outside_exact_patches(self):
        with panel.extract_image() as source, Image.open(panel.TARGET) as stored:
            original = source.crop(panel.CROP)
            actual = stored.convert('RGBA')
            self.assertEqual('PNG', stored.format)
            self.assertEqual((180, 301), actual.size)
            for y in range(301):
                for x in range(180):
                    inside = any(l <= x < r and t <= y < b for l, t, r, b in panel.PATCHES)
                    if not inside:
                        self.assertEqual(original.getpixel((x, y)), actual.getpixel((x, y)), (x, y))
            for l, t, r, b in panel.PATCHES:
                replacement = original.crop((20, t, 60, b)).resize((r-l, b-t), Image.Resampling.BILINEAR)
                self.assertEqual(replacement.tobytes(), actual.crop((l, t, r, b)).tobytes())
                self.assertNotEqual(original.crop((l, t, r, b)).tobytes(), replacement.tobytes())
        self.assertEqual('352b929deed53b28062a1c8ec18e16cb42098f694cb55b2c59fe7911a263f4d8',
                         hashlib.sha256(panel.TARGET.read_bytes()).hexdigest())

    def test_check_is_read_only_and_rejects_any_changed_pixel(self):
        with tempfile.TemporaryDirectory() as tmp:
            target = Path(tmp) / 'panel.png'
            panel.prepare(target=target)
            original = target.read_bytes()
            panel.prepare(target=target, check=True)
            self.assertEqual(original, target.read_bytes())
            for position in ((0, 0), (80, 3), (80, 295)):
                with Image.open(target) as image:
                    altered = image.convert('RGBA')
                pixel = altered.getpixel(position)
                altered.putpixel(position, (pixel[0] ^ 1, *pixel[1:]))
                altered.save(target)
                before = target.read_bytes()
                with self.subTest(position=position), self.assertRaisesRegex(ValueError, 'pixels differ'):
                    panel.prepare(target=target, check=True)
                self.assertEqual(before, target.read_bytes())
                target.write_bytes(original)
            panel.prepare(check=True)

    def test_invalid_source_is_rejected_without_output(self):
        with tempfile.TemporaryDirectory() as tmp:
            source = Path(tmp) / 'bad.svgz'
            target = Path(tmp) / 'target.png'
            source.write_bytes(b'incorrect')
            with self.assertRaisesRegex(ValueError, 'SVGZ hash'):
                panel.prepare(source, target)
            self.assertFalse(target.exists())
            for svg, message in ((b'<svg/>', 'Expected one'),
                                 (b'<svg href="data:image/webp;base64,@@@"/>', 'encoding'),
                                 (b'<svg href="data:image/webp;base64,' + base64.b64encode(b'bad') + b'"/>', 'WebP hash')):
                raw = gzip.compress(svg, mtime=0)
                source.write_bytes(raw)
                with self.subTest(message=message), patch.object(panel, 'SOURCE_SHA256', hashlib.sha256(raw).hexdigest()):
                    with self.assertRaisesRegex(ValueError, message):
                        panel.prepare(source, target)
                    self.assertFalse(target.exists())


if __name__ == '__main__':
    unittest.main()
