import importlib.util
import io
from pathlib import Path
import struct
import tempfile
import unittest
from zipfile import ZipFile

spec = importlib.util.spec_from_file_location("bundle_check", Path(__file__).parents[1] / "verify_android_bundle.py")
check = importlib.util.module_from_spec(spec)
spec.loader.exec_module(check)


def elf(alignment=16384, file_offset=0, address=0):
    data = bytearray(120)
    data[:6] = b"\x7fELF\x02\x01"
    struct.pack_into("<Q", data, 32, 64)
    struct.pack_into("<HH", data, 54, 56, 1)
    struct.pack_into("<I", data, 64, 1)
    struct.pack_into("<QQ", data, 72, file_offset, address)
    struct.pack_into("<Q", data, 112, alignment)
    return data


class NativeReleaseTests(unittest.TestCase):
    def test_accepts_16k_aligned_native_library(self):
        check.verify_elf64(elf())

    def test_rejects_legacy_4k_alignment(self):
        with self.assertRaisesRegex(ValueError, "16 KB"):
            check.verify_elf64(elf(4096))

    def test_rejects_mismatched_segment_offsets(self):
        with self.assertRaisesRegex(ValueError, "offsets"):
            check.verify_elf64(elf(file_offset=4096))

    def test_rejects_truncated_or_non_elf_data(self):
        for data in (b"", b"not a shared object", elf()[:65]):
            with self.subTest(length=len(data)), self.assertRaises(ValueError):
                check.verify_elf64(data)

    def test_rejects_non_power_of_two_alignment(self):
        with self.assertRaises(ValueError):
            check.verify_elf64(elf(24576))

    def test_rejects_missing_load_segments(self):
        data = elf()
        struct.pack_into("<I", data, 64, 0)
        with self.assertRaisesRegex(ValueError, "LOAD"):
            check.verify_elf64(data)


class ReleaseManifestTests(unittest.TestCase):
    source = ('<manifest xmlns:android="http://schemas.android.com/apk/res/android" '
              'package="org.example.game" android:versionCode="2" android:versionName="0.1.0-beta.1">'
              '<application android:debuggable="false" /></manifest>')

    def test_accepts_non_debuggable_release(self):
        check.verify_manifest(self.source, True)

    def test_rejects_debuggable_release(self):
        with self.assertRaisesRegex(ValueError, "debuggable"):
            check.verify_manifest(self.source.replace('debuggable="false"', 'debuggable="true"'))

    def test_placeholder_id_is_allowed_for_ci_but_never_for_upload(self):
        text = self.source.replace("org.example.game", "com.example.worlddomination")
        check.verify_manifest(text)
        with self.assertRaisesRegex(ValueError, "permanent"):
            check.verify_manifest(text, True)

    def test_offline_release_cannot_silently_gain_internet(self):
        text = self.source.replace("<application", '<uses-permission android:name="android.permission.INTERNET" /><application')
        with self.assertRaisesRegex(ValueError, "INTERNET"):
            check.verify_manifest(text)

    def test_version_code_must_be_positive(self):
        with self.assertRaisesRegex(ValueError, "version code"):
            check.verify_manifest(self.source.replace('versionCode="2"', 'versionCode="0"'))


class GeneratedAudioBundleTests(unittest.TestCase):
    def make_bundle(self, audio_directory, missing=None, corrupt=None):
        entries = {
            "BundleConfig.pb": b"config",
            "base/manifest/AndroidManifest.xml": b"binary manifest",
            "base/dex/classes.dex": b"dex",
            "base/assets/original/runtime/audio/original.mp3": b"source",
        }
        for abi in check.ABIS:
            entries[f"base/lib/{abi}/libgdx.so"] = bytes(elf())
        for image in check.REQUIRED_UI:
            entries["base/assets/original/ui/" + image] = b"art"
        for island in range(5):
            for faith in ("chr", "bhu", "com", "jew", "mus"):
                entries[f"base/assets/original/ui/terrain/island-{island}-{faith}.png"] = b"art"
        generated = audio_directory.parents[2] / "generated/audio"
        for filename in check.GENERATED_AUDIO_FILES:
            name = "base/assets/generated/audio/" + filename
            entries[name] = (generated / filename).read_bytes()
        if missing is not None:
            del entries["base/assets/generated/audio/" + missing]
        if corrupt is not None:
            name = "base/assets/generated/audio/" + corrupt
            entries[name] += b"corrupt"
        archive = io.BytesIO()
        with ZipFile(archive, "w") as bundle:
            for name, data in entries.items():
                bundle.writestr(name, data)
        archive.seek(0)
        return archive

    def source_directory(self, root):
        assets = Path(root) / "app/src/main/assets"
        audio = assets / "original/runtime/audio"
        audio.mkdir(parents=True)
        (audio / "original.mp3.gz").write_bytes(b"source")
        generated = assets / "generated/audio"
        generated.mkdir(parents=True)
        for filename in check.GENERATED_AUDIO_FILES:
            (generated / filename).write_bytes(("source:" + filename).encode())
        return audio

    def test_accepts_all_generated_audio_with_exact_source_bytes(self):
        with tempfile.TemporaryDirectory() as temporary:
            audio = self.source_directory(temporary)
            check.verify_bundle(self.make_bundle(audio), audio)

    def test_rejects_each_missing_generated_audio_bundle_entry(self):
        with tempfile.TemporaryDirectory() as temporary:
            audio = self.source_directory(temporary)
            for filename in check.GENERATED_AUDIO_FILES:
                with self.subTest(filename=filename), self.assertRaisesRegex(
                        ValueError, "Missing generated audio from bundle"):
                    check.verify_bundle(self.make_bundle(audio, missing=filename), audio)

    def test_rejects_each_corrupt_generated_audio_bundle_entry(self):
        with tempfile.TemporaryDirectory() as temporary:
            audio = self.source_directory(temporary)
            for filename in check.GENERATED_AUDIO_FILES:
                with self.subTest(filename=filename), self.assertRaisesRegex(
                        ValueError, "Generated audio differs from source"):
                    check.verify_bundle(self.make_bundle(audio, corrupt=filename), audio)

    def test_rejects_missing_generated_audio_source(self):
        with tempfile.TemporaryDirectory() as temporary:
            audio = self.source_directory(temporary)
            archive = self.make_bundle(audio)
            (audio.parents[2] / "generated/audio/anti-air-gun-eight.wav").unlink()
            with self.assertRaisesRegex(ValueError, "Missing generated audio source"):
                check.verify_bundle(archive, audio)


if __name__ == "__main__":
    unittest.main()
