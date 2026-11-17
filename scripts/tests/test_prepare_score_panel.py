"""Check source score layers, SVG alpha, and read-only verification."""
import base64
import gzip
import hashlib
import io
import re
import tempfile
import unittest
from pathlib import Path

from PIL import Image
import resvg_py

from scripts import prepare_score_panel as score


class ScorePanelTests(unittest.TestCase):

    def test_all_layers_match_independently_decoded_source(self):
        for asset in score.ASSETS:
            name, shape, _svg_hash, _bitmap_hash, _size, output_size = asset
            with self.subTest(name=name):
                svg = gzip.decompress((score.SOURCE_DIR / f'{shape}.svgz').read_bytes()).decode('utf8')
                matches = re.findall('data:image/webp;base64,([^"\\\']+)', svg)
                self.assertEqual(1, len(matches))
                with Image.open(io.BytesIO(base64.b64decode(matches[0], validate=True))) as source:
                    oracle = source.convert('RGBA')
                if shape == 4450:
                    oracle = oracle.crop((113, 555, 293, 604))
                with Image.open(score.TARGET_DIR / name) as output:
                    self.assertEqual('PNG', output.format)
                    self.assertEqual(output_size, output.size)
                    actual = output.convert('RGBA')
                for y in range(output_size[1]):
                    for x in range(output_size[0]):
                        a = actual.getpixel((x, y))
                        b = oracle.getpixel((x, y))
                        expected = b if shape == 4450 or b[3] else (0, 0, 0, 0)
                        self.assertEqual(expected, a, (name, x, y))
                if shape == 4450:
                    self.assertEqual(oracle.tobytes(), actual.tobytes())

    def test_check_rejects_changes_and_never_writes(self):
        with tempfile.TemporaryDirectory() as temp:
            target = Path(temp) / 'score'
            score.prepare(target_dir=target)
            before = {p.name: p.read_bytes() for p in target.iterdir()}
            score.prepare(target_dir=target, check=True)
            self.assertEqual(before, {p.name: p.read_bytes() for p in target.iterdir()})
            for name in ('backing.png', 'window.png', 'dot-5.png', 'title.png'):
                path = target / name
                with Image.open(path) as image:
                    pixel = image.convert('RGBA')
                point = (pixel.width // 2, pixel.height // 2)
                old = pixel.getpixel(point)
                pixel.putpixel(point, (old[0] ^ 1, *old[1:]))
                pixel.save(path)
                changed = path.read_bytes()
                with self.subTest(name=name), self.assertRaisesRegex(ValueError, 'pixels differ'):
                    score.prepare(target_dir=target, check=True)
                self.assertEqual(changed, path.read_bytes())
                path.write_bytes(before[name])
            score.prepare(check=True)

    def test_wrong_source_rejected_before_any_output(self):
        with tempfile.TemporaryDirectory() as temp:
            source = Path(temp) / 'source'
            target = Path(temp) / 'out'
            source.mkdir()
            (source / '4450.svgz').write_bytes(b'wrong')
            with self.assertRaisesRegex(ValueError, 'SVGZ hash'):
                score.prepare(source_dir=source, target_dir=target)
            self.assertFalse(target.exists())
            asset = score.ASSETS[1]
            raw = (score.SOURCE_DIR / '4580.svgz').read_bytes()
            svg = gzip.decompress(raw).replace(b'M67.5 14.0', b'M67.4 14.0', 1)
            altered = gzip.compress(svg, mtime=0)
            (source / '4580.svgz').write_bytes(altered)
            modified = (asset[0], asset[1], hashlib.sha256(altered).hexdigest(), *asset[3:])
            with self.assertRaisesRegex(ValueError, 'rectangle or registration'):
                score.source_image(modified, source)

    def test_title_is_reproducible_source_glyph_raster(self):
        raw = (score.SOURCE_DIR.parent / 'texts/4629.svgz').read_bytes()
        self.assertEqual('fd0f850e16162d5d54d3a162160284da7f80c6183fd1ae64aa3187bdff063779', hashlib.sha256(raw).hexdigest())
        svg = gzip.decompress(raw).decode('utf8')
        png = resvg_py.svg_to_bytes(svg_string=svg, width=148, height=40, skip_system_fonts=True)
        with Image.open(io.BytesIO(png)) as source, Image.open(score.TARGET_DIR / 'title.png') as stored:
            self.assertEqual((148, 40), stored.size)
            expected = source.convert('RGBA')
            actual = stored.convert('RGBA')
            self.assertEqual(expected.tobytes(), actual.tobytes())
            self.assertIsNotNone(actual.getchannel('A').getbbox())
            self.assertLess(actual.getchannel('A').getextrema()[0], 255)
        self.assertEqual('8bf23663f10eb106ec31b3b6760b39670000f4016e738d70684fdf69dc37bcd6', hashlib.sha256((score.TARGET_DIR / 'title.png').read_bytes()).hexdigest())

    def test_all_dot_frames_are_distinct(self):
        hashes = [hashlib.sha256((score.TARGET_DIR / f'dot-{i}.png').read_bytes()).hexdigest() for i in range(1, 6)]
        self.assertEqual(5, len(set(hashes)))

if __name__ == '__main__':
    unittest.main()
