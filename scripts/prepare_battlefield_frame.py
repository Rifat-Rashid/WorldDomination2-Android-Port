"""Extract the authored transparent battlefield border without resampling it."""
import gzip
import hashlib
from pathlib import Path

import resvg_py

ROOT = Path(__file__).resolve().parents[1] / "app/src/main/assets/original"
source = (ROOT / "runtime/shapes/4447.svgz").read_bytes()
if hashlib.sha256(source).hexdigest() != "faef8abcfdd4a344495c5037a3c08aa6993d7de3da8987ddfd89d9f28c9bcc74":
    raise ValueError("Source battlefield frame differs from inspected artwork")
png = resvg_py.svg_to_bytes(svg_string=gzip.decompress(source).decode("utf-8"),
                            skip_system_fonts=True)
if hashlib.sha256(png).hexdigest() != "f12203872b0981cf7cd3a90132ec673d4c7888dd893f6439b57b62457b534ae6":
    raise ValueError("Frame rasterizer output differs from the reference renderer")
(ROOT / "ui/battlefield-frame.png").write_bytes(png)
