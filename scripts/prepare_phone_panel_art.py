"""Capture actual Python phone panels outside the read-only reference tree.

Requires pygame-ce and Pillow. Run with Python -B and pass explicit paths.
"""
import argparse
import os, sys, hashlib, json
from pathlib import Path
parser = argparse.ArgumentParser()
parser.add_argument('--reference-root', type=Path, required=True)
parser.add_argument('--output-dir', type=Path, required=True)
args = parser.parse_args()
REF = args.reference_root.resolve()
OUT = args.output_dir.resolve()
if OUT == REF or REF in OUT.parents:
    parser.error('output directory must be outside the reference tree')
OUT.mkdir(parents=True, exist_ok=True)
os.environ['PYTHONDONTWRITEBYTECODE'] = '1'
os.environ['SDL_VIDEODRIVER'] = 'dummy'
os.environ['SDL_AUDIODRIVER'] = 'dummy'
sys.dont_write_bytecode = True

def inside(value):
    if not isinstance(value, (str, bytes, os.PathLike)):
        return False
    p = Path(os.fsdecode(value)).resolve()
    return p == REF or REF in p.parents

def guard(event, args):
    if event == 'open' and args and inside(args[0]):
        mode = args[1] if len(args) > 1 else None
        flags = args[2] if len(args) > 2 and isinstance(args[2], int) else 0
        if (isinstance(mode, str) and any(c in mode for c in 'wax+')) or flags & (os.O_WRONLY | os.O_RDWR | os.O_CREAT | os.O_TRUNC | os.O_APPEND):
            raise PermissionError('reference write: ' + str(args[0]))
    if event in {'os.remove', 'os.rmdir', 'os.mkdir', 'os.rename', 'os.replace', 'os.chmod', 'os.utime', 'os.link', 'os.symlink'} and any(inside(v) for v in args[:2]):
        raise PermissionError('reference mutation: ' + event)
sys.addaudithook(guard)
sys.path.insert(0, str(REF))
import pygame as pg
from wd2.setup_menu import SetupMenu
from wd2.iphone_skin import MobileSkin
pg.init()
pg.display.set_mode((1, 1))
m = SetupMenu(0)
for button in ('splashScreenMC', 'btn2', 'BTN_allLeaders_screen1', 'BTN_allLeaders_screen2'):
    m.press(button)
m.start_game()
skin = MobileSkin(m, pg)
pg.image.save(skin.texture, str(OUT / 'source-texture-330x36.png'))
# Current 1194x552 zero-inset native logical layout; integer values are rounded.
cases = {'portrait': (81, 51), 'logo': (255, 62), 'header': (607, 62), 'toolbar': (314, 34),
         'stock': (300, 435), 'message': (407, 64), 'action': (314, 64),
         'threshold-42': (160, 42), 'threshold-43': (160, 43)}
for name, (width, height) in cases.items():
    image = pg.Surface((width, height), pg.SRCALPHA)
    skin.panel(image, pg.Rect(0, 0, width, height))
    pg.image.save(image, str(OUT / (name + '.png')))
# Isolate the source-drawn chrome using a constant opaque texture.  Pixels
# equal to the source-rendered interior are left transparent; all other pixels
# are opaque source primitives (including round corners and fasteners).
from PIL import Image
original_texture = skin.texture
synthetic = pg.Surface(original_texture.get_size(), pg.SRCALPHA)
synthetic.fill((237, 13, 179, 255))
skin.texture = synthetic
for height in (42, 43):
    surface = pg.Surface((160, height), pg.SRCALPHA)
    skin.panel(surface, pg.Rect(0, 0, 160, height))
    image = Image.frombytes('RGBA', surface.get_size(), pg.image.tobytes(surface, 'RGBA'))
    baseline = image.getpixel((40, 20))
    image.putdata([(0, 0, 0, 0) if pixel == baseline else pixel for pixel in image.getdata()])
    image.putpixel((3, 3), Image.open(OUT / 'logo.png').convert('RGBA').getpixel((3, 3)))
    image.save(OUT / ('chrome-' + str(height) + '.png'))
skin.texture = original_texture
stock = Image.open(OUT / 'stock.png').convert('RGBA')
stock.crop((3, 17, 163, 31)).save(OUT / 'interior-tile.png')
def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()
sources = ['wd2/iphone_skin.py', 'wd2/iphone_ui.py', 'wd2/setup_menu.py', 'wd2/surface_renderer.py', 'assets/runtime/shapes/4450.svgz']
report = {'method': 'actual MobileSkin.panel after SetupMenu.start_game; untitled; transparent target',
          'sizeNote': '1194x552 zero-inset native layout, dimensions rounded to nearest integer; not Python phone layout',
          'sourceCropStageXYWH': [45, 4, 110, 12], 'texturePixels3x': [330, 36],
          'sourceSha256': {p: digest(REF / p) for p in sources},
          'cases': {n: {'size': list(size), 'sha256': digest(OUT / (n + '.png'))} for n, size in cases.items()},
          'textureSha256': digest(OUT / 'source-texture-330x36.png')}
(OUT / 'provenance.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
m.audio.close()
pg.quit()
print(json.dumps(report, indent=2))
