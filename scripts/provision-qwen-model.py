#!/usr/bin/env python3
"""Download the pinned stock Qwen GGUF or provision it to a debuggable WD2 install."""

import argparse
import hashlib
from pathlib import Path
import re
import subprocess
from urllib.request import urlopen
from uuid import uuid4


# Keep this identity in sync with the Android QwenModel descriptor.
MODEL_NAME = "Qwen3-0.6B-Q8_0.gguf"
MODEL_REVISION = "23749fefcc72300e3a2ad315e1317431b06b590a"
MODEL_SIZE = 639_446_688
MODEL_SHA256 = "9465e63a22add5354d9bb4b99e90117043c7124007664907259bd16d043bb031"
MODEL_URL = f"https://huggingface.co/Qwen/Qwen3-0.6B-GGUF/resolve/{MODEL_REVISION}/{MODEL_NAME}"
CHUNK = 1024 * 1024


def verify(model, size, sha256):
    if model.stat().st_size != size:
        raise ValueError(f"Invalid model size in {model}")
    checksum = hashlib.sha256()
    with model.open("rb") as source:
        while block := source.read(CHUNK):
            checksum.update(block)
    if checksum.hexdigest() != sha256:
        raise ValueError(f"Invalid SHA-256 in {model}")


def download(model):
    model.parent.mkdir(parents=True, exist_ok=True)
    partial = model.with_suffix(model.suffix + ".partial")
    received = 0
    try:
        with urlopen(MODEL_URL + "?download=true", timeout=30) as source, partial.open("xb") as target:
            while block := source.read(CHUNK):
                received += len(block)
                if received > MODEL_SIZE:
                    raise ValueError("The model exceeded its pinned size")
                target.write(block)
        verify(partial, MODEL_SIZE, MODEL_SHA256)
        partial.replace(model)
    except FileExistsError:
        raise ValueError(f"An incomplete download already exists at {partial}") from None
    except Exception:
        partial.unlink(missing_ok=True)
        raise


def provision(args, name, size):
    if not re.fullmatch(r"[A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z][A-Za-z0-9_]*)+", args.package):
        raise ValueError("Use a valid Android application ID")
    adb = [args.adb]
    if args.serial:
        adb.extend(["-s", args.serial])
    # adb push is binary even when Windows adb shell opens host stdin in text mode.
    app_dir = "no_backup/qwen3"
    target = f"{app_dir}/{name}"
    partial = target + ".adb.partial"
    staging = f"/data/local/tmp/wd2-qwen-{uuid4().hex}.gguf"
    try:
        subprocess.run([*adb, "push", str(args.model), staging], check=True)
        shell_command = (
            f"cat {staging} | run-as {args.package} sh -c "
            f"'mkdir -p {app_dir} && cat > {partial} "
            f"&& test \"$(stat -c %s {partial})\" = {size} && mv {partial} {target}'"
        )
        subprocess.run([*adb, "shell", shell_command], check=True)
    finally:
        subprocess.run([*adb, "shell", "rm", "-f", staging], check=False)
    print(f"Provisioned {target} in {args.package}; the app verifies SHA-256 before loading.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("model", type=Path, help="Local copy of the pinned stock Qwen GGUF")
    parser.add_argument("--download", action="store_true", help="Download if the local GGUF is absent")
    parser.add_argument("--provision", action="store_true", help="Copy into an installed debug APK")
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--serial", help="Device serial for adb -s")
    parser.add_argument("--package", default="com.example.worlddomination")
    parser.add_argument("--debug-sha256", help="Expected hash of a debug-only alternative GGUF")
    parser.add_argument("--debug-size", type=int, help="Expected byte size of a debug-only alternative GGUF")
    args = parser.parse_args()
    name, size, sha256 = MODEL_NAME, MODEL_SIZE, MODEL_SHA256
    if args.debug_sha256 is not None or args.debug_size is not None:
        if (not re.fullmatch(r"[0-9a-f]{64}", args.debug_sha256 or "")
                or args.debug_size is None or args.debug_size <= 0 or args.download):
            parser.error("A debug model needs both its pinned --debug-sha256 and --debug-size and cannot be downloaded")
        name, size, sha256 = f"qwen-debug-{args.debug_sha256}.gguf", args.debug_size, args.debug_sha256
    if not args.model.is_file() and args.download:
        download(args.model)
    verify(args.model, size, sha256)
    print(f"Verified {args.model} ({size} bytes, SHA-256 {sha256})", flush=True)
    if args.provision:
        provision(args, name, size)


if __name__ == "__main__":
    main()
