"""Materialize the pinned trainable snapshot and verify every file against the Hub manifest."""
import argparse
import hashlib
import json
import urllib.request
from pathlib import Path

MODEL = "Qwen/Qwen3-0.6B"
REVISION = "c1899de289a04d12100db370d81485cdf75e47ca"
WEIGHTS_SHA256 = "f47f71177f32bcd101b7573ec9171e6a57f4f4d31148d38e382306f42996874b"
TEMPLATE_SHA256 = "d5d09f07b48c3086c508b30d1c9114bd1189145b74e982a265350c923acd8101"
FILES = ("LICENSE", "config.json", "generation_config.json", "merges.txt", "vocab.json",
         "tokenizer.json", "tokenizer_config.json", "model.safetensors")


def verify(path, info):
    if not path.is_file() or path.stat().st_size != info["size"]:
        return False
    lfs = info.get("lfs")
    digest = hashlib.sha256() if lfs else hashlib.sha1(f"blob {info['size']}\0".encode())
    with path.open("rb") as stream:
        while chunk := stream.read(1024 * 1024):
            digest.update(chunk)
    return digest.hexdigest() == (lfs["sha256"] if lfs else info["blobId"])


def provision(directory):
    directory.mkdir(parents=True, exist_ok=True)
    with urllib.request.urlopen(f"https://huggingface.co/api/models/{MODEL}/revision/{REVISION}?blobs=true", timeout=45) as response:
        manifest = json.load(response)
    if manifest["sha"] != REVISION:
        raise ValueError("Unexpected base revision")
    entries = {entry["rfilename"]: entry for entry in manifest["siblings"]}
    if entries["model.safetensors"]["lfs"]["sha256"] != WEIGHTS_SHA256:
        raise ValueError("Unexpected base weights")
    for name in FILES:
        target = directory / name
        if not verify(target, entries[name]):
            part = target.with_suffix(target.suffix + ".partial")
            with urllib.request.urlopen(f"https://huggingface.co/{MODEL}/resolve/{REVISION}/{name}", timeout=120) as source, part.open("wb") as dest:
                while block := source.read(1024 * 1024):
                    dest.write(block)
            if not verify(part, entries[name]):
                raise ValueError(f"Invalid download of {name}")
            part.replace(target)
    if hashlib.sha256((directory / "tokenizer_config.json").read_bytes()).hexdigest() != TEMPLATE_SHA256:
        raise ValueError("Unexpected chat template")
    print(f"Verified {MODEL}@{REVISION} in {directory}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("directory", type=Path)
    provision(parser.parse_args().directory)
