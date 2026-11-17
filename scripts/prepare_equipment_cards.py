"""Prepare eight source-clipped equipment cards with their baked titles removed.

Requires Pillow and resvg-py. Fractional SVG clip edges are rasterized at the
SVG's natural size by resvg; the decoded WebP is losslessly embedded as PNG.
"""
import argparse
import base64
import binascii
import gzip
import hashlib
import io
from pathlib import Path
import re

from PIL import Image
import resvg_py

ROOT = Path(__file__).resolve().parents[1]
SOURCE_DIR = ROOT / 'app/src/main/assets/original/runtime/shapes'
TARGET_DIR = ROOT / 'app/src/main/assets/original/ui/equipment-cards'
BITMAP_SHA256 = '12b3a7d4df3f6c1e14d2dfd18e78d2e34bb3e605ebc57e6b1c612e073f02b8c8'
# name, shape ID, expected output size, title patch, compressed SVG hash
CARDS = (
    ('rockets', 4503, (197, 73), (78, 0, 120, 14), '17b65810e18f69cc373c9d7afbf1785747d1eebdf043a09cc92c77b0569394f3'),
    ('aircraft', 4480, (197, 42), (78, 0, 120, 14), '35c77aa9f937c0ea57162b60edfea387352e7a32c2387636447bdf70c07aa818'),
    ('special', 4514, (197, 117), (60, 0, 138, 14), '48c5706e7f8c5ec56cec87702c4c41322c8d54ab1f2d84e0fc921e4e7f934b62'),
    ('warheads', 4525, (197, 69), (73, 0, 123, 14), '832b93abf0bf203f9e5ce89f5d3243e3ff8cea6e6119069edb3d16bbd11c5957'),
    ('bioloads', 4536, (197, 79), (74, 0, 124, 14), 'ab753a707e24be3ea12d678ccc942f2312aca06a7fc9f6ef6331a9bb39f0e323'),
    ('infantry', 4547, (197, 69), (72, 0, 125, 14), '80ae819241830c96448a0f91c7840125b9d7c47d7e0ab134fc21bf4243e14cb3'),
    ('intelligence', 4558, (197, 69), (66, 0, 131, 14), '3cd345d6b721dcf0f4699730d235e85e4a4191dcad494d4f8e1228db756e9b51'),
    ('defense', 4569, (197, 48), (65, 0, 132, 14), '63d59a0f4b144b67c0e510ea085a960bc9b240174b0a63597f7d843266e99466'),
)


def source_image(card, source_dir=SOURCE_DIR):
    name, shape, size, _patch, expected_hash = card
    raw = (Path(source_dir) / f'{shape}.svgz').read_bytes()
    if hashlib.sha256(raw).hexdigest() != expected_hash:
        raise ValueError(f'Unexpected {name} SVGZ hash')
    try:
        svg = gzip.decompress(raw).decode('utf-8')
    except (OSError, EOFError, UnicodeDecodeError) as error:
        raise ValueError(f'Invalid {name} SVGZ') from error
    matches = list(re.finditer(r'data:image/webp;base64,([^"\']+)', svg))
    if len(matches) != 1:
        raise ValueError(f'Expected one embedded WebP in {name}')
    match = matches[0]
    try:
        payload = base64.b64decode(match.group(1), validate=True)
    except binascii.Error as error:
        raise ValueError(f'Invalid embedded WebP in {name}') from error
    if hashlib.sha256(payload).hexdigest() != BITMAP_SHA256:
        raise ValueError(f'Unexpected {name} bitmap hash')
    with Image.open(io.BytesIO(payload)) as image:
        if image.format != 'WEBP' or image.size != (197, 554):
            raise ValueError(f'Unexpected {name} bitmap dimensions')
        stream = io.BytesIO()
        image.save(stream, format='PNG')
    data_url = 'data:image/png;base64,' + base64.b64encode(stream.getvalue()).decode('ascii')
    svg = svg[:match.start()] + data_url + svg[match.end():]
    png = resvg_py.svg_to_bytes(svg_string=svg, skip_system_fonts=True)
    with Image.open(io.BytesIO(png)) as image:
        if image.size != size:
            raise ValueError(f'Unexpected {name} raster dimensions')
        return image.convert('RGBA')


def render_card(card, source_dir=SOURCE_DIR):
    image = source_image(card, source_dir)
    left, top, right, bottom = card[3]
    size = (right - left, bottom - top)
    with image.crop((25, top, 50, bottom)) as a, image.crop((147, top, 172, bottom)) as b:
        with a.transpose(Image.Transpose.FLIP_LEFT_RIGHT).resize(size, Image.Resampling.BILINEAR) as first, \
                b.transpose(Image.Transpose.FLIP_LEFT_RIGHT).resize(size, Image.Resampling.BILINEAR) as second:
            mask = Image.new('L', size)
            mask.putdata([round(255 * x / (size[0] - 1)) for _ in range(size[1]) for x in range(size[0])])
            with mask, Image.composite(second, first, mask) as patch:
                image.paste(patch, (left, top))
    return image


def prepare(source_dir=SOURCE_DIR, target_dir=TARGET_DIR, *, check=False):
    target_dir = Path(target_dir)
    for card in CARDS:
        name = card[0]
        target = target_dir / f'{name}.png'
        with render_card(card, source_dir) as expected:
            if check:
                with Image.open(target) as actual:
                    if actual.format != 'PNG' or actual.size != expected.size:
                        raise ValueError(f'Unexpected {name} packaged dimensions')
                    with actual.convert('RGBA') as pixels:
                        if pixels.tobytes() != expected.tobytes():
                            raise ValueError(f'{name} packaged pixels differ')
            else:
                target_dir.mkdir(parents=True, exist_ok=True)
                expected.save(target, format='PNG')


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true', help='verify decoded pixels without writing')
    args = parser.parse_args(argv)
    prepare(check=args.check)


if __name__ == '__main__':
    main()
