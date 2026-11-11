#!/usr/bin/env python3
"""Validate release bundle contents and 64-bit ELF alignment without Android tools."""
from pathlib import Path
import argparse
import struct
import sys
import xml.etree.ElementTree as ET
from zipfile import ZipFile, BadZipFile

ANDROID = "{http://schemas.android.com/apk/res/android}"
ABIS = ("arm64-v8a", "armeabi-v7a", "x86", "x86_64")


def verify_elf64(data):
    if len(data) < 64 or data[:6] != b"\x7fELF\x02\x01":
        raise ValueError("Expected a little-endian ELF64 native library")
    offset = struct.unpack_from("<Q", data, 32)[0]
    size, count = struct.unpack_from("<HH", data, 54)
    if size < 56 or count == 0 or offset + size * count > len(data):
        raise ValueError("Invalid ELF program header table")
    loads = 0
    for index in range(count):
        start = offset + index * size
        if struct.unpack_from("<I", data, start)[0] != 1:
            continue
        loads += 1
        file_offset, address = struct.unpack_from("<QQ", data, start + 8)
        alignment = struct.unpack_from("<Q", data, start + 48)[0]
        if alignment < 16384 or alignment & (alignment - 1):
            raise ValueError("Native LOAD segment does not support 16 KB pages")
        if file_offset % 16384 != address % 16384:
            raise ValueError("Native LOAD offsets are incompatible with 16 KB pages")
    if loads == 0:
        raise ValueError("Native library has no LOAD segments")


def verify_manifest(text, require_upload=False):
    manifest = ET.fromstring(text)
    application = manifest.find("application")
    if application is None or application.get(ANDROID + "debuggable", "false") != "false":
        raise ValueError("Release application must not be debuggable")
    if int(manifest.get(ANDROID + "versionCode", "0")) <= 0:
        raise ValueError("Missing positive release version code")
    if not manifest.get(ANDROID + "versionName"):
        raise ValueError("Missing release version name")
    if require_upload and manifest.get("package", "com.example.").startswith("com.example."):
        raise ValueError("Choose the permanent Play application ID before upload")
    if any(p.get(ANDROID + "name") == "android.permission.INTERNET"
           for p in manifest.findall("uses-permission")):
        raise ValueError("The offline game must not request INTERNET")


def verify_bundle(path, audio_directory, require_upload=False):
    with ZipFile(path) as bundle:
        entries = set(bundle.namelist())
        for name in ("BundleConfig.pb", "base/manifest/AndroidManifest.xml", "base/dex/classes.dex"):
            if name not in entries:
                raise ValueError(f"Missing app bundle entry: {name}")
        for abi in ABIS:
            name = f"base/lib/{abi}/libgdx.so"
            if name not in entries:
                raise ValueError(f"Missing native library: {name}")
            if abi in ("arm64-v8a", "x86_64"):
                verify_elf64(bundle.read(name))
        sources = sorted(audio_directory.glob("*.mp3.gz"))
        if not sources:
            raise ValueError("No original audio sources found")
        for source in sources:
            name = f"base/assets/original/runtime/audio/{source.name[:-3]}"
            if name not in entries:
                raise ValueError(f"Missing original audio: {name}")
        required_ui = ("terrain/manifest.json", "terrain/ocean.png", "battle-terrain.png", "neutral-button.png", "resource-menu-hd.png", "message-well.png",
                       "action-tubes.png", "technology-hd/technology-0.png",
                       "confirm-turn.png", "turn-announcements.png", "turn-announcements.json",
                       "action-menus/menus.json", "action-menus/diplomacy.png",
                       "action-menus/propaganda2.png", "action-menus/defense-antiAir.png",
                       "action-menus/attack2-soldier1.png", "action-menus/preAttack-rocket1.png",
                       "action-menus/preAttack3-warhead.png", "action-menus/diplomacyOffer.png",
                       "action-menus/religion-chr.png", "action-menus/media-opinion.png",
                       "action-menus/skip_action.png", "action-menus/production_error.png",
                       "action-menus/research_error-chr.png", "action-menus/research_error-mus.png",
                       "action-menus/research_error-bhu.png", "action-menus/research_error-com.png",
                       "action-menus/research_error-jew.png")
        for image in required_ui:
            name = "base/assets/original/ui/" + image
            if name not in entries:
                raise ValueError(f"Latest UI artwork missing from beta: {image}")
        for island in range(5):
            for faith in ("chr", "bhu", "com", "jew", "mus"):
                name = f"base/assets/original/ui/terrain/island-{island}-{faith}.png"
                if name not in entries:
                    raise ValueError(f"Original terrain variant missing from beta: {name}")
        forbidden = (".swf", ".py", ".pyc", ".jks", ".keystore", ".aab", ".apk")
        for name in entries:
            if name.lower().endswith(forbidden) or "/analysis/" in name.lower():
                raise ValueError(f"Development/reference file packaged: {name}")
        if require_upload and not any(n.startswith("META-INF/") and
                                      n.endswith((".RSA", ".EC", ".DSA")) for n in entries):
            raise ValueError("App bundle is unsigned; an upload key is required")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("bundle", type=Path)
    parser.add_argument("--audio-directory", type=Path,
                        default=Path("app/src/main/assets/original/runtime/audio"))
    parser.add_argument("--manifest", type=Path, required=True,
                        help="XML produced by bundletool dump manifest")
    parser.add_argument("--require-upload", action="store_true")
    args = parser.parse_args()
    try:
        verify_manifest(args.manifest.read_text(encoding="utf-8-sig"), args.require_upload)
        verify_bundle(args.bundle, args.audio_directory, args.require_upload)
    except (ValueError, OSError, BadZipFile, ET.ParseError, struct.error) as error:
        print(f"Release validation failed: {error}", file=sys.stderr)
        return 1
    print("Verified release manifest, latest UI, original audio, four ABIs, and 16 KB ELF alignment")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
