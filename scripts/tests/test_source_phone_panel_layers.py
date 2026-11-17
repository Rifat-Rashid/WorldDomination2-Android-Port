"""Compare source-derived layer composition to independent captured phone panels."""
import unittest
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / 'app/src/main/assets/original/ui/phone-panel'
FIXTURES = ROOT / 'scripts/tests/fixtures/phone-panel'

class SourcePhonePanelLayersTest(unittest.TestCase):
    def test_integer_panel_composites_equal_source_captures(self):
        tile = Image.open(ASSETS / 'interior-tile.png').convert('RGBA')
        self.assertEqual((160, 14), tile.size)
        for name in ('logo', 'header', 'toolbar', 'stock', 'message', 'action',
                     'threshold-42', 'threshold-43', 'portrait'):
            with self.subTest(name=name):
                expected = Image.open(FIXTURES / (name + '.png')).convert('RGBA')
                width, height = expected.size
                chrome = Image.open(ASSETS / ('chrome-43.png' if height > 42 else 'chrome-42.png')).convert('RGBA')
                result = Image.new('RGBA', (width, height))
                for y in range(3, height - 4, 14):
                    for x in range(3, width - 4, 160):
                        part = tile.crop((0, 0, min(160, width - 4 - x), min(14, height - 4 - y)))
                        result.paste(part, (x, y))
                bottom = chrome.height - 10
                parts = [(0,0,10,10,0,0,10,10), (150,0,10,10,width-10,0,10,10),
                         (0,bottom,10,10,0,height-10,10,10), (150,bottom,10,10,width-10,height-10,10,10),
                         (10,0,1,10,10,0,width-20,10), (10,bottom,1,10,10,height-10,width-20,10),
                         (0,10,10,1,0,10,10,height-20), (150,10,10,1,width-10,10,10,height-20)]
                for sx,sy,sw,sh,dx,dy,dw,dh in parts:
                    part = chrome.crop((sx,sy,sx+sw,sy+sh)).resize((dw,dh), Image.Resampling.NEAREST)
                    result.alpha_composite(part, (dx,dy))
                self.assertEqual(expected.tobytes(), result.tobytes())

if __name__ == '__main__':
    unittest.main()
