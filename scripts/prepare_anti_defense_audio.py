"""Rebuild source-trimmed anti-defense WAVs; normal Android builds use committed outputs.

Requires pygame-ce 2.5.8 for the pinned SDL decode. The reference SWF is read-only.
"""
import argparse
import gzip
import hashlib
import io
import json
import os
from pathlib import Path
import struct
import wave


SWF_SHA256 = 'fe60c4d8793a23e7ddd59ddcb406b5471540e28d64f7552d17308fbb269e6fe1'
SOUNDS = (
    (90, '90_building_Anti rocket fire 4.wav.mp3.gz', 'anti-rocket-fire.wav', 1,
     '1e6cf34c59bb264a823751cdc7008ade0a576cc04dae154145d47edb8ac4b53c'),
    (91, '91_building_Anti air rotate.wav.mp3.gz', 'anti-air-rotate.wav', 1,
     '6720ea165043edff08086eff2583cf2e871a82e9beb2eb482d26857be9e681bc'),
    (94, '94_building_Anti air gun.wav.mp3.gz', 'anti-air-gun-eight.wav', 8,
     '907c14a00e834f034365bacc9cab52ad9fccbf6bacdec8259526269c8734b01c'),
)
RATE = 22050
EXPECTED_PCM_SHA256 = {
    90: 'c3346b63f6d8dbcbb11718559880b1690ffcf034bf9dda5755b196c2d36aeb64',
    91: '721221e3cc77a98254e87aaa98b2bbdf9ef3882f45ee5a3254a652c0e472ded6',
    94: '72fc294128c7ea07214fabffa893d94048d9b4a10e6e7a7ee067a9241ce6e8db',
}
EXPECTED_WAV_SHA256 = {
    90: 'b8a72d9c1a20020081a5efc857cf2a4578069ffa3910536a2bfd8269d577fb91',
    91: 'af2f59726158ef90bd325fe35e8cd53dc60bbfb8d9916074495955ce6d13cc05',
    94: '37612101549338606cf0dc68bf2a1e53e83447d5568e67fee78d799072abf8c4',
}


def sha(data):
    return hashlib.sha256(data).hexdigest()


def embedded_sounds(raw):
    if raw[:3] != b'FWS':
        raise ValueError('Expected the pinned uncompressed source SWF')
    rect_bits = raw[8] >> 3
    offset = 8 + (5 + 4 * rect_bits + 7) // 8 + 4
    found = {}
    while offset + 2 <= len(raw):
        header = struct.unpack_from('<H', raw, offset)[0]
        offset += 2
        tag, length = header >> 6, header & 63
        if length == 63:
            length = struct.unpack_from('<I', raw, offset)[0]
            offset += 4
        payload = raw[offset:offset + length]
        if len(payload) != length:
            raise ValueError('Truncated source tag')
        offset += length
        if tag == 14:
            sound_id = struct.unpack_from('<H', payload)[0]
            if sound_id in (90, 91, 94):
                if payload[2] != 0x2a:
                    raise ValueError('Unexpected source MP3 format')
                found[sound_id] = {
                    'sampleCount': struct.unpack_from('<I', payload, 3)[0],
                    'seekSamples': struct.unpack_from('<h', payload, 7)[0],
                    'mp3': payload[9:],
                }
        if tag == 0:
            break
    if set(found) != {90, 91, 94}:
        raise ValueError('Missing original anti-defense sounds')
    return found


def wav_bytes(samples):
    output = io.BytesIO()
    with wave.open(output, 'wb') as writer:
        writer.setnchannels(1)
        writer.setsampwidth(2)
        writer.setframerate(RATE)
        writer.writeframes(samples)
    return output.getvalue()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source-swf', type=Path, required=True)
    args = parser.parse_args()
    raw = args.source_swf.read_bytes()
    if sha(raw) != SWF_SHA256:
        raise ValueError('Source SWF hash differs from pinned original')
    source_sounds = embedded_sounds(raw)

    # SDL decodes into memory only. No playback device or source-directory write.
    os.environ['SDL_AUDIODRIVER'] = 'dummy'
    import pygame
    if pygame.version.ver != '2.5.8':
        raise ValueError('Pinned pygame-ce decoder version is 2.5.8')
    pygame.mixer.init(frequency=RATE, size=-16, channels=1)
    if pygame.mixer.get_init() != (RATE, -16, 1):
        raise ValueError('Decoder output must be source-rate mono PCM16')
    root = Path(__file__).resolve().parents[1]
    originals = root / 'app/src/main/assets/original/runtime/audio'
    output = root / 'app/src/main/assets/generated/audio'
    generated = {}
    manifest = {
        'sourceSwfSha256': SWF_SHA256,
        'decoder': {'pygameCe': pygame.version.ver,
                    'sdl': '.'.join(map(str, pygame.get_sdl_version())),
                    'mixer': list(pygame.mixer.get_init())},
        'format': {'sampleRate': RATE, 'channels': 1, 'bitsPerSample': 16},
        'sounds': [],
        'limitation': 'SDL PCM sample values are not proven bit-identical to Flash decoding.',
    }
    try:
        for sound_id, original_name, wav_name, repeats, mp3_sha in SOUNDS:
            source = source_sounds[sound_id]
            original = (originals / original_name).read_bytes()
            if gzip.decompress(original) != source['mp3'] or sha(source['mp3']) != mp3_sha:
                raise ValueError('Packaged original MP3 differs from source sound ' + str(sound_id))
            decoded = pygame.mixer.Sound(file=io.BytesIO(source['mp3'])).get_raw()
            start, count = source['seekSamples'], source['sampleCount']
            if len(decoded) % 2 or start < 0 or start + count > len(decoded) // 2:
                raise ValueError('Invalid decoded sample bounds for ' + str(sound_id))
            period = decoded[2 * start:2 * (start + count)]
            data = wav_bytes(period * repeats)
            if sha(period) != EXPECTED_PCM_SHA256[sound_id] or sha(data) != EXPECTED_WAV_SHA256[sound_id]:
                raise ValueError('Decoder output differs from pinned PCM for ' + str(sound_id))
            generated[wav_name] = data
            manifest['sounds'].append({
                'sourceId': sound_id, 'originalAsset': original_name,
                'originalCompressedSha256': sha(original), 'mp3Sha256': mp3_sha,
                'decodedSamples': len(decoded) // 2, 'seekSamples': start,
                'sourceSampleCount': count, 'repeatCount': repeats,
                'outputSamples': count * repeats, 'periodPcmSha256': sha(period),
                'wavFile': wav_name, 'wavBytes': len(data), 'wavSha256': sha(data),
            })
    finally:
        pygame.mixer.quit()
    output.mkdir(parents=True, exist_ok=True)
    for name, data in generated.items():
        (output / name).write_bytes(data)
    (output / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({'output': str(output), 'sounds': len(manifest['sounds'])}))


if __name__ == '__main__':
    main()
