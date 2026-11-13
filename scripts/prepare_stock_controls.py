"""Rasterize the four packaged Flash stock-control shapes at 3x."""
import gzip
import hashlib
from pathlib import Path

import resvg_py

ROOT = Path(__file__).resolve().parents[1] / "app/src/main/assets/original"
OUTPUT = ROOT / "ui/stock-controls"
OUTPUT.mkdir(parents=True, exist_ok=True)

SOURCE_SHA256 = {
    4483: "1aea61ce8d8760c188deb0a42df38ce516930c1fbd642390f0c913255d71f8f9",
    4490: "84dcbfaa8577fc80dd2d164982665f62d455ec5f2a6d89217ba12c25b4dc8cc2",
    4491: "76d1a85233b39a07402e8d9504ffce8081fb797f13720bf148c25723c736be39",
    4493: "662c96fa954588d8cd89837b5b1f2dbc3cc2a33f832273e2712a8fe0e1e7538d",
}

for shape_id, name in ((4483, "counter"), (4490, "unavailable"),
                       (4491, "eligible"), (4493, "selected")):
    compressed = (ROOT / f"runtime/shapes/{shape_id}.svgz").read_bytes()
    if hashlib.sha256(compressed).hexdigest() != SOURCE_SHA256[shape_id]:
        raise ValueError(f"Source shape {shape_id} differs from inspected artwork")
    source = gzip.decompress(compressed)
    width = 96 if shape_id == 4483 else 90
    image = resvg_py.svg_to_bytes(svg_string=source.decode("utf-8"),
                                  width=width, height=78, skip_system_fonts=True)
    (OUTPUT / f"{name}.png").write_bytes(image)
