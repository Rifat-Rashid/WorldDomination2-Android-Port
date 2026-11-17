"""Verify the encoded assets support the Sound handle retention bound."""
import gzip
from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[2]


class AudioHandleRetentionTests(unittest.TestCase):
    def test_all_cues_finish_before_the_encoded_byte_retention_bound(self):
        source = (ROOT / "app/src/main/java/com/example/worlddomination/game/audio/OriginalAudio.java").read_text()
        assets = re.findall(r'\("([^"\n]+\.mp3.gz)"\)', source)
        self.assertGreater(len(assets), 40)
        for asset in assets:
            with self.subTest(asset=asset):
                encoded = gzip.decompress((ROOT / "app/src/main/assets/original/runtime/audio" / asset).read_bytes())
                offset = 0
                if encoded[:3] == b"ID3":
                    offset = 10 + sum((encoded[6 + i] & 127) << (7 * (3 - i)) for i in range(4))
                seconds = 0.0
                frames = 0
                while offset + 4 <= len(encoded):
                    header = int.from_bytes(encoded[offset:offset + 4], "big")
                    version, layer = (header >> 19) & 3, (header >> 17) & 3
                    bitrate_index, sample_index = (header >> 12) & 15, (header >> 10) & 3
                    if header >> 21 != 2047:
                        break
                    self.assertIn(version, (0, 2, 3))
                    self.assertEqual(1, layer, "Packaged cues must be MPEG Layer III")
                    self.assertIn(bitrate_index, range(1, 15), "Free-format has no duration bound")
                    self.assertIn(sample_index, range(3))
                    sample_rate = (44100, 48000, 32000)[sample_index] // {3: 1, 2: 2, 0: 4}[version]
                    bitrates = ((0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320)
                                if version == 3 else (0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160))
                    frame_bytes = ((144 if version == 3 else 72) * bitrates[bitrate_index] * 1000
                                   // sample_rate + ((header >> 9) & 1))
                    self.assertLessEqual(offset + frame_bytes, len(encoded))
                    seconds += (1152 if version == 3 else 576) / sample_rate
                    frames += 1
                    offset += frame_bytes
                self.assertGreater(frames, 0)
                tail = encoded[offset:]
                self.assertTrue(not tail or all(byte == 0 for byte in tail)
                                or (len(tail) == 128 and tail[:3] == b"TAG"))
                self.assertLess(seconds, len(encoded) / 1000 + 1,
                                "A native Sound handle must outlive its full decoded cue")


if __name__ == "__main__":
    unittest.main()
