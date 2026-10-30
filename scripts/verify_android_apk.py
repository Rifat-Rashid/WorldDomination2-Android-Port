#!/usr/bin/env python3
"""Check that the Android APK contains original audio and required libGDX natives."""

from pathlib import Path
import sys
from zipfile import BadZipFile, ZipFile


EXPECTED_ANDROID_ABIS = ("arm64-v8a", "armeabi-v7a", "x86", "x86_64")


def main() -> int:
    if len(sys.argv) != 3:
        print(
            "usage: verify_android_apk.py <apk-path> <original-audio-directory>",
            file=sys.stderr,
        )
        return 2

    apk_path = Path(sys.argv[1])
    audio_directory = Path(sys.argv[2])
    audio_sources = sorted(audio_directory.glob("*.mp3.gz"))
    if not audio_sources:
        print(f"No original compressed MP3 sources found under {audio_directory}", file=sys.stderr)
        return 1

    asset_root = audio_directory.parents[2]
    try:
        with ZipFile(apk_path) as apk:
            entries = set(apk.namelist())
            for source in audio_sources:
                relative_path = source.relative_to(asset_root).as_posix()[:-3]
                packaged_path = f"assets/{relative_path}"
                if packaged_path not in entries:
                    print(f"Missing packaged audio asset: {packaged_path}", file=sys.stderr)
                    return 1

                data = apk.read(packaged_path)
                has_mpeg_sync = len(data) >= 2 and data[0] == 0xFF and data[1] & 0xE0 == 0xE0
                if not (data.startswith(b"ID3") or has_mpeg_sync):
                    print(f"Packaged audio is not valid MP3 data: {packaged_path}", file=sys.stderr)
                    return 1

            for abi in EXPECTED_ANDROID_ABIS:
                library_path = f"lib/{abi}/libgdx.so"
                if library_path not in entries:
                    print(f"Missing libGDX Android native library: {library_path}", file=sys.stderr)
                    return 1

    except (BadZipFile, OSError) as error:
        print(f"Unable to read APK {apk_path}: {error}", file=sys.stderr)
        return 1

    print(
        f"Verified {len(audio_sources)} original MP3 assets and libgdx.so for "
        f"{len(EXPECTED_ANDROID_ABIS)} Android ABIs in {apk_path}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
