"""Export standalone F16, Q8_0 and Q4_K_M files with the Android llama.cpp revision."""
import argparse
import json
from pathlib import Path
import subprocess
import sys

from sft import file_hash

CONVERTER_SHA256 = "f23c686fe6ac57249260af2db95108a6e4f41c3032d57ff295f8d753da8e7bda"
LLAMA_COMMIT = "707cbafcaa3a88d893147bcc107e76b925a867da"


def export(args):
    converter = args.source / "convert_hf_to_gguf.py"
    if file_hash(converter) != CONVERTER_SHA256:
        raise ValueError("Use the pinned llama.cpp b7700 source archive")
    suffix = ".exe" if sys.platform == "win32" else ""
    server = args.bin / ("llama-server" + suffix)
    found = subprocess.run([str(server), "--version"], capture_output=True, text=True, check=True)
    if LLAMA_COMMIT[:9] not in found.stderr + found.stdout:
        raise ValueError("Use the b7700 host binaries")
    quantizer = args.bin / ("llama-quantize" + suffix)
    merged = args.run / "merged"
    out = args.run / "gguf"
    if out.exists():
        raise ValueError("Export already started; preserve the original directory for audit")
    out.mkdir()
    f16 = out / "opponent-f16.gguf"
    subprocess.run([sys.executable, str(converter), str(merged), "--outtype", "f16", "--outfile", str(f16)],
                   check=True)
    artifacts = [f16]
    for mode in ("Q8_0", "Q4_K_M"):
        target = out / ("opponent-" + mode.lower() + ".gguf")
        subprocess.run([str(quantizer), str(f16), str(target), mode], check=True)
        artifacts.append(target)
    manifest = {"llama_commit": LLAMA_COMMIT, "converter_sha256": CONVERTER_SHA256,
                "merged_sha256": file_hash(merged / "model.safetensors"),
                "artifacts": {p.name: {"sha256": file_hash(p), "bytes": p.stat().st_size} for p in artifacts}}
    (out / "manifest.json").write_text(json.dumps(manifest, indent=2), encoding="utf8")
    print(json.dumps(manifest))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    for flag in ("run", "source", "bin"):
        parser.add_argument("--" + flag, type=Path, required=True)
    export(parser.parse_args())
