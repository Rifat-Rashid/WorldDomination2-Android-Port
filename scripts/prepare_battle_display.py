#!/usr/bin/env python3
"""Cache the original battle parent's placement metadata without unpacking its art."""

import gzip
import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
RUNTIME = ROOT / "app/src/main/assets/original/runtime"


def main():
    scene = json.loads(gzip.decompress((RUNTIME / "scene.json.gz").read_bytes()))
    symbol = next(item for item in scene["symbols"] if item["id"] == 5766)
    document = {
        "format": "wd2.battle-display.v1",
        "source_sha256": scene["source_sha256"],
        "fps": scene["fps"],
        "symbol": symbol,
    }
    encoded = json.dumps(document, separators=(",", ":"), ensure_ascii=False).encode("utf-8")
    target = RUNTIME / "battle-root.json.gz"
    target.write_bytes(gzip.compress(encoded, mtime=0))
    print(f"Cached original battle display metadata: {target.name} ({target.stat().st_size} bytes)")


if __name__ == "__main__":
    main()
