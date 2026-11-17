"""Extract the source score backing, active window and five color-dot frames."""
import argparse
import base64
import binascii
import gzip
import hashlib
import io
from pathlib import Path
import re
import xml.etree.ElementTree as ET

from PIL import Image
import resvg_py

ROOT = Path(__file__).resolve().parents[1]
SOURCE_DIR = ROOT / 'app/src/main/assets/original/runtime/shapes'
TARGET_DIR = ROOT / 'app/src/main/assets/original/ui/score-panel'
# filename, shape ID, SVG hash, bitmap hash, bitmap size, output size
ASSETS = (
    (
        'backing.png', 4450,
        'cc116b79d10537ca6c3d7f67fb436bc4776e92e21bce0d81034914f1ebc2953f',
        '1e5dc61bf20e18dc99555b8cbd367746808269e54d2d2a074ff3fb3f9a2d159d',
        (297, 768), (180, 49),
    ),
    (
        'window.png', 4580,
        '2098c65b107758766e1e339d784483164f2c5f59cf9e700bdccbe467ca239fed',
        '5a800354fdaae3cd77ce9517673e09744cc0d4cabed119d8768bf225c5aeaa83',
        (135, 28), (135, 28),
    ),
    (
        'dot-1.png', 4613,
        'edff77acde909ebc1ef55a046e7487cb5c40a31cc7c6b40945e2e73b45577282',
        '0b3ca01bf52eea85ea916f41b8a5932e4f8fdecefaf92ee9cc5e89ddd91c0aec',
        (18, 18), (18, 18),
    ),
    (
        'dot-2.png', 4615,
        '05a9ecdc09fb5bd92b504053e7d90eba4dfa87c191b90c4bb19acc4503c91089',
        '343a0dce40ae99a29890118276d0f3e03c1e969722aca63d38689597f3b134a4',
        (18, 18), (18, 18),
    ),
    (
        'dot-3.png', 4617,
        'eaf0578578172523d98725caab5e21579dddf434dec3b1ddb45b9aa22485a39c',
        'ef0e3d114014637102f7300be02cb52db1d0f56667196908c6ee3e8b93212593',
        (18, 18), (18, 18),
    ),
    (
        'dot-4.png', 4619,
        '91c3725a582c6096e5f25fd65de9a99edc6e26feb88f399ce1f99655b2e7570c',
        '4aaa15c2751d95a10e5b563529ced5debf7c5f8a3fa3399d6515f0b49777c7ec',
        (18, 18), (18, 18),
    ),
    (
        'dot-5.png', 4621,
        '458c05a45111d3dc8a78458725a2d78e85fd2e97b83e474c2faf4777cfe13435',
        'a3b5deff6e644fa81e4d2cfd5f7e0515391bc05e5d61be557cbb97c699c037c6',
        (18, 18), (18, 18),
    ),
)
BACKING_CROP = (113, 555, 293, 604)
TITLE_SHA256 = 'fd0f850e16162d5d54d3a162160284da7f80c6183fd1ae64aa3187bdff063779'


def validate_bitmap_geometry(svg, shape, bitmap_size):
    """Require the exact full-rectangle paths and cancelling registrations."""
    root = ET.fromstring(svg)
    groups = root.findall('./{*}g')
    patterns = root.findall('.//{*}pattern')
    images = root.findall('.//{*}image')
    paths = root.findall('.//{*}path')
    if len(groups) != 1 or len(patterns) != 1 or len(images) != 1 or len(paths) != 1:
        raise ValueError('Expected one bitmap rectangle and registration')
    if shape == 4580:
        group = 'matrix(1.0, 0.0, 0.0, 1.0, 67.5, 14.0)'
        pattern = 'matrix(1.0, 0.0, 0.0, 1.0, -67.5, -14.0)'
        path = 'M67.5 14.0 L-67.5 14.0 -67.5 -14.0 67.5 -14.0 67.5 14.0'
    else:
        group = 'matrix(1.0, 0.0, 0.0, 1.0, 12.75, -1.0)'
        pattern = 'matrix(1.0, 0.0, 0.0, 1.0, -12.75, 1.0)'
        path = 'M5.25 19.0 L-12.75 19.0 -12.75 1.0 5.25 1.0 5.25 19.0'
    if (groups[0].get('transform') != group
            or patterns[0].get('patternTransform') != pattern
            or paths[0].get('d') != path
            or tuple(int(images[0].get(k)) for k in ('width', 'height')) != bitmap_size
            or root.get('width') != f'{bitmap_size[0]}.0px'
            or root.get('height') != f'{bitmap_size[1]}.0px'):
        raise ValueError('Unexpected bitmap rectangle or registration')


def source_image(asset, source_dir=SOURCE_DIR):
    name, shape, svg_hash, bitmap_hash, bitmap_size, output_size = asset
    raw = (Path(source_dir) / f'{shape}.svgz').read_bytes()
    if hashlib.sha256(raw).hexdigest() != svg_hash:
        raise ValueError(f'Unexpected {name} SVGZ hash')
    try:
        svg = gzip.decompress(raw).decode('utf8')
    except (OSError, EOFError, UnicodeDecodeError) as error:
        raise ValueError(f'Invalid {name} SVGZ') from error
    matches = list(re.finditer('data:image/webp;base64,([^"\\\']+)', svg))
    if len(matches) != 1:
        raise ValueError(f'Expected one {name} WebP')
    m = matches[0]
    try:
        payload = base64.b64decode(m.group(1), validate=True)
    except binascii.Error as error:
        raise ValueError(f'Invalid {name} WebP encoding') from error
    if hashlib.sha256(payload).hexdigest() != bitmap_hash:
        raise ValueError(f'Unexpected {name} bitmap hash')
    with Image.open(io.BytesIO(payload)) as image:
        if image.format != 'WEBP' or image.size != bitmap_size:
            raise ValueError(f'Unexpected {name} bitmap dimensions')
        if shape == 4450:
            # Shape 4450 uses a single full-rectangle bitmap fill.
            return image.convert('RGBA').crop(BACKING_CROP)
        validate_bitmap_geometry(svg, shape, bitmap_size)
        pixels = image.convert('RGBA')
        # RGB under zero alpha is normalized; visible source RGBA is untouched.
        data = bytearray(pixels.tobytes())
        for offset in range(0, len(data), 4):
            if data[offset + 3] == 0:
                data[offset:offset + 3] = b'\0\0\0'
        pixels.frombytes(bytes(data))
        if pixels.size != output_size:
            raise ValueError(f'Unexpected {name} raster dimensions')
        return pixels


def source_title(source_dir=SOURCE_DIR):
    raw = (Path(source_dir).parent / 'texts/4629.svgz').read_bytes()
    if hashlib.sha256(raw).hexdigest() != TITLE_SHA256:
        raise ValueError('Unexpected title SVGZ hash')
    try:
        svg = gzip.decompress(raw).decode('utf8')
    except (OSError, EOFError, UnicodeDecodeError) as error:
        raise ValueError('Invalid title SVGZ') from error
    png = resvg_py.svg_to_bytes(svg_string=svg, width=148, height=40, skip_system_fonts=True)
    with Image.open(io.BytesIO(png)) as image:
        if image.size != (148, 40):
            raise ValueError('Unexpected title dimensions')
        return image.convert('RGBA')


def prepare(source_dir=SOURCE_DIR, target_dir=TARGET_DIR, *, check=False):
    target_dir = Path(target_dir)
    for asset in ASSETS:
        name = asset[0]
        target = target_dir / name
        with source_image(asset, source_dir) as expected:
            if check:
                with Image.open(target) as actual:
                    if actual.format != 'PNG' or actual.size != asset[-1]:
                        raise ValueError(f'Unexpected {name} packaged dimensions')
                    with actual.convert('RGBA') as pixels:
                        if pixels.tobytes() != expected.tobytes():
                            raise ValueError(f'{name} pixels differ')
            else:
                target_dir.mkdir(parents=True, exist_ok=True)
                expected.save(target, format='PNG')
    target = target_dir / 'title.png'
    with source_title(source_dir) as expected:
        if check:
            with Image.open(target) as actual:
                if actual.format != 'PNG' or actual.size != (148, 40):
                    raise ValueError('Unexpected title packaged dimensions')
                with actual.convert('RGBA') as pixels:
                    if pixels.tobytes() != expected.tobytes():
                        raise ValueError('title.png pixels differ')
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
