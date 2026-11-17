"""Check source-clipped cards, narrow edits, and read-only verification."""
import hashlib
import tempfile
import unittest
from pathlib import Path

from PIL import Image
from scripts import prepare_equipment_cards as cards


class EquipmentCardTests(unittest.TestCase):
    def test_each_original_clip_is_preserved_outside_title_patch(self):
        for card in cards.CARDS:
            name, shape, size, (left, top, right, bottom), _ = card
            with self.subTest(name=name), cards.source_image(card) as original, Image.open(cards.TARGET_DIR / f'{name}.png') as stored:
                self.assertEqual('PNG', stored.format)
                self.assertEqual(size, stored.size)
                actual = stored.convert('RGBA')
                for y in range(size[1]):
                    for x in range(size[0]):
                        if left <= x < right and top <= y < bottom:
                            continue
                        self.assertEqual(original.getpixel((x,y)), actual.getpixel((x,y)), (name,x,y))
                first = original.crop((25,top,50,bottom)).transpose(Image.Transpose.FLIP_LEFT_RIGHT).resize((right-left,bottom-top), Image.Resampling.BILINEAR)
                second = original.crop((147,top,172,bottom)).transpose(Image.Transpose.FLIP_LEFT_RIGHT).resize((right-left,bottom-top), Image.Resampling.BILINEAR)
                for y in range(bottom-top):
                    for x in range(right-left):
                        weight=round(255*x/(right-left-1))
                        expected=tuple((a*(255-weight)+b*weight+127)//255 for a,b in zip(first.getpixel((x,y)),second.getpixel((x,y))))
                        self.assertEqual(expected,actual.getpixel((left+x,top+y)))
                self.assertNotEqual(original.crop((left,top,right,bottom)).tobytes(),actual.crop((left,top,right,bottom)).tobytes())
                self.assertEqual(original.getchannel('A').tobytes(), actual.getchannel('A').tobytes())

    def test_affected_shape_has_original_irregular_clip_and_no_fill_leak(self):
        card = next(c for c in cards.CARDS if c[1] == 4536)
        with cards.source_image(card) as source:
            self.assertEqual((197,79),source.size)
            self.assertIsNone(source.getchannel('A').crop((0,69,197,79)).getbbox())
        # The path includes a right strip down to y378.1, yet its bitmap is
        # transparent there. A generator may not fill it with adjacent artwork.

    def test_check_is_read_only_and_rejects_mutated_pixels(self):
        with tempfile.TemporaryDirectory() as tmp:
            target = Path(tmp) / 'cards'
            cards.prepare(target_dir=target)
            before={p.name:p.read_bytes() for p in target.iterdir()}
            cards.prepare(target_dir=target, check=True)
            self.assertEqual(before,{p.name:p.read_bytes() for p in target.iterdir()})
            path=target/'rockets.png'
            with Image.open(path) as stored:
                image=stored.convert('RGBA')
            rgba=image.getpixel((80,4));image.putpixel((80,4),(rgba[0]^1,*rgba[1:]));image.save(path)
            changed=path.read_bytes()
            with self.assertRaisesRegex(ValueError,'pixels differ'):
                cards.prepare(target_dir=target, check=True)
            self.assertEqual(changed,path.read_bytes())
            cards.prepare(check=True)

    def test_rejects_invalid_svg_before_writing_first_target(self):
        with tempfile.TemporaryDirectory() as tmp:
            source=Path(tmp)/'source';target=Path(tmp)/'target';source.mkdir()
            (source/'4503.svgz').write_bytes(b'bad')
            with self.assertRaisesRegex(ValueError,'SVGZ hash'):
                cards.prepare(source_dir=source,target_dir=target)
            self.assertFalse(target.exists())


if __name__ == '__main__':
    unittest.main()
