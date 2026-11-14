"""Verify committed source-trimmed WAVs without a decoder or reference checkout."""
import gzip
import hashlib
import json
from pathlib import Path
import unittest
import wave


ROOT = Path(__file__).resolve().parents[2]
AUDIO = ROOT / 'app/src/main/assets/generated/audio'
ORIGINAL = ROOT / 'app/src/main/assets/original/runtime/audio'
EXPECTED = {
    90: (28938, 1672, 1, 'b8a72d9c1a20020081a5efc857cf2a4578069ffa3910536a2bfd8269d577fb91'),
    91: (44119, 1672, 1, 'af2f59726158ef90bd325fe35e8cd53dc60bbfb8d9916074495955ce6d13cc05'),
    94: (1643, 1672, 8, '37612101549338606cf0dc68bf2a1e53e83447d5568e67fee78d799072abf8c4'),
}


def sha(data):
    return hashlib.sha256(data).hexdigest()


class DerivedAntiDefenseAudioTest(unittest.TestCase):
    def test_originals_and_repeat_periods(self):
        manifest = json.loads((AUDIO / 'manifest.json').read_text(encoding='utf-8'))
        self.assertEqual('fe60c4d8793a23e7ddd59ddcb406b5471540e28d64f7552d17308fbb269e6fe1',
                         manifest['sourceSwfSha256'])
        self.assertEqual({'sampleRate': 22050, 'channels': 1, 'bitsPerSample': 16},
                         manifest['format'])
        self.assertEqual({90, 91, 94}, {sound['sourceId'] for sound in manifest['sounds']})
        for sound in manifest['sounds']:
            sound_id = sound['sourceId']
            with self.subTest(sound_id=sound_id):
                count, seek, repeats, expected_wav_sha = EXPECTED[sound_id]
                self.assertEqual((count, seek, repeats),
                                 (sound['sourceSampleCount'], sound['seekSamples'],
                                  sound['repeatCount']))
                original = (ORIGINAL / sound['originalAsset']).read_bytes()
                self.assertEqual(sound['originalCompressedSha256'], sha(original))
                self.assertEqual(sound['mp3Sha256'], sha(gzip.decompress(original)))
                data = (AUDIO / sound['wavFile']).read_bytes()
                self.assertEqual(expected_wav_sha, sound['wavSha256'])
                self.assertEqual(sound['wavSha256'], sha(data))
                self.assertEqual(44 + 2 * count * repeats, len(data))
                self.assertEqual(sound['wavBytes'], len(data))
                with wave.open(str(AUDIO / sound['wavFile']), 'rb') as reader:
                    self.assertEqual((1, 2, 22050, count * repeats),
                                     (reader.getnchannels(), reader.getsampwidth(),
                                      reader.getframerate(), reader.getnframes()))
                    pcm = reader.readframes(count * repeats)
                period = pcm[:2 * count]
                self.assertEqual(sound['periodPcmSha256'], sha(period))
                self.assertEqual(period * repeats, pcm)
                self.assertAlmostEqual(count * repeats / 22050,
                                       len(pcm) / (2 * 22050), places=12)


if __name__ == '__main__':
    unittest.main()
