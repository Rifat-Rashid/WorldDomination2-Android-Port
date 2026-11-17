"""Extract or verify the original opaque shape 5813 embedded artwork."""

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
SOURCE = ROOT / "app/src/main/assets/original/runtime/shapes/5813.svgz"
TARGET = ROOT / "app/src/main/assets/original/ui/setup-panel-source-5813.png"
SOURCE_SHA256 = "110617835e39b2cc5955caeec2a94ba610a93410c49b15ecd75dc919c3905549"
WEBP_SHA256 = "0bf24bf324f56b1c9b51ef982fa3e973761a6d028d1f79270f459adb2fe15658"
EXPECTED_SIZE = (798, 601)


def extract_image(source=SOURCE):
    """Validate the source and return a detached RGBA image of its WebP."""
    raw = Path(source).read_bytes()
    if hashlib.sha256(raw).hexdigest() != SOURCE_SHA256:
        raise ValueError("Unexpected shape 5813 SVGZ hash")
    try:
        svg = gzip.decompress(raw)
    except (OSError, EOFError) as error:
        raise ValueError("Invalid shape 5813 SVGZ") from error
    matches = re.findall(rb"data:image/webp;base64,([^\"']+)", svg)
    if len(matches) != 1:
        raise ValueError("Expected one shape 5813 embedded WebP")
    try:
        payload = base64.b64decode(matches[0], validate=True)
    except binascii.Error as error:
        raise ValueError("Invalid embedded WebP encoding") from error
    if hashlib.sha256(payload).hexdigest() != WEBP_SHA256:
        raise ValueError("Unexpected shape 5813 embedded WebP hash")
    with Image.open(io.BytesIO(payload)) as image:
        if image.format != "WEBP" or image.size != EXPECTED_SIZE:
            raise ValueError("Unexpected shape 5813 image format or dimensions")
        return image.convert("RGBA")


def prepare(source=SOURCE, target=TARGET, *, check=False):
    """Generate the PNG, or compare decoded pixels without changing either file."""
    target = Path(target)
    with extract_image(source) as original:
        if check:
            with Image.open(target) as packaged:
                if packaged.format != "PNG" or packaged.size != original.size:
                    raise ValueError("Unexpected packaged setup panel format or dimensions")
                with packaged.convert("RGBA") as actual:
                    if actual.tobytes() != original.tobytes():
                        raise ValueError("Packaged setup panel pixels differ from source")
        else:
            target.parent.mkdir(parents=True, exist_ok=True)
            original.save(target, format="PNG")


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="verify packaged pixels without writing")
    args = parser.parse_args(argv)
    prepare(check=args.check)


if __name__ == "__main__":
    main()
