"""Extract the authored navigation frame and remove its baked labels."""
import argparse
import base64
import binascii
import gzip
import hashlib
import io
from pathlib import Path
import re

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'app/src/main/assets/original/runtime/shapes/4450.svgz'
TARGET = ROOT / 'app/src/main/assets/original/ui/navigation-panel.png'
SOURCE_SHA256 = 'cc116b79d10537ca6c3d7f67fb436bc4776e92e21bce0d81034914f1ebc2953f'
WEBP_SHA256 = '1e5dc61bf20e18dc99555b8cbd367746808269e54d2d2a074ff3fb3f9a2d159d'
CROP = (113, 153, 293, 454)
PATCHES = ((60, 0, 130, 16), (60, 285, 130, 301))


def extract_image(source=SOURCE):
    raw = Path(source).read_bytes()
    if hashlib.sha256(raw).hexdigest() != SOURCE_SHA256:
        raise ValueError('Unexpected shape 4450 SVGZ hash')
    try:
        svg = gzip.decompress(raw)
    except (OSError, EOFError) as error:
        raise ValueError('Invalid shape 4450 SVGZ') from error
    matches = re.findall(rb'data:image/webp;base64,([^"\']+)', svg)
    if len(matches) != 1:
        raise ValueError('Expected one shape 4450 embedded WebP')
    try:
        payload = base64.b64decode(matches[0], validate=True)
    except binascii.Error as error:
        raise ValueError('Invalid embedded WebP encoding') from error
    if hashlib.sha256(payload).hexdigest() != WEBP_SHA256:
        raise ValueError('Unexpected shape 4450 embedded WebP hash')
    with Image.open(io.BytesIO(payload)) as image:
        if image.format != 'WEBP' or image.size != (297, 768):
            raise ValueError('Unexpected shape 4450 image format or dimensions')
        return image.convert('RGBA')


def render_panel(source=SOURCE):
    with extract_image(source) as original:
        panel = original.crop(CROP)
    for left, top, right, bottom in PATCHES:
        with panel.crop((20, top, 60, bottom)) as rail:
            with rail.resize((right-left, bottom-top), Image.Resampling.BILINEAR) as replacement:
                panel.paste(replacement, (left, top))
    return panel


def prepare(source=SOURCE, target=TARGET, *, check=False):
    target = Path(target)
    with render_panel(source) as expected:
        if check:
            with Image.open(target) as actual:
                if actual.format != 'PNG' or actual.size != expected.size:
                    raise ValueError('Unexpected packaged navigation panel format or dimensions')
                with actual.convert('RGBA') as pixels:
                    if pixels.tobytes() != expected.tobytes():
                        raise ValueError('Packaged navigation panel pixels differ')
        else:
            target.parent.mkdir(parents=True, exist_ok=True)
            expected.save(target, format='PNG')


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true', help='verify pixels without writing')
    args = parser.parse_args(argv)
    prepare(check=args.check)


if __name__ == '__main__':
    main()
