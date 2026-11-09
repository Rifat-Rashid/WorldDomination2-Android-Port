import importlib.util
from pathlib import Path
import struct
import unittest

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


if __name__ == "__main__":
    unittest.main()
