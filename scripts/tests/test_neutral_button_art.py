"""Guard the shared button skin against baked labels and inverted gloss."""
from pathlib import Path
import struct
import unittest
import zlib


def rgba_png(path):
    data = path.read_bytes()
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        raise ValueError("Invalid PNG")
    width, height, depth, color = struct.unpack_from(">IIBB", data, 16)
    if (depth, color) != (8, 6):
        raise ValueError("Expected RGBA8 button artwork")
    encoded = bytearray()
    offset = 8
    while offset < len(data):
        length = struct.unpack_from(">I", data, offset)[0]
        if data[offset + 4:offset + 8] == b"IDAT":
            encoded.extend(data[offset + 8:offset + 8 + length])
        offset += 12 + length
    raw = zlib.decompress(encoded)
    stride = width * 4
    previous = bytearray(stride)
    rows = []
    for y in range(height):
        offset = y * (stride + 1)
        kind = raw[offset]
        row = bytearray(raw[offset + 1:offset + 1 + stride])
        for x in range(stride):
            left = row[x - 4] if x >= 4 else 0
            up = previous[x]
            corner = previous[x - 4] if x >= 4 else 0
            prediction = left + up - corner
            distances = (abs(prediction - left), abs(prediction - up),
                         abs(prediction - corner))
            paeth = (left, up, corner)[distances.index(min(distances))]
            filters = (0, left, up, (left + up) // 2, paeth)
            row[x] = (row[x] + filters[kind]) & 255
        rows.append(row)
        previous = row
    return width, height, rows


class NeutralButtonArtworkTests(unittest.TestCase):
    def setUp(self):
        root = Path(__file__).resolve().parents[2]
        self.width, self.height, self.rows = rgba_png(
            root / "app/src/main/assets/original/ui/neutral-button.png")

    def pixel(self, x, y):
        return self.rows[y][x * 4:x * 4 + 4]

    def test_shared_button_interior_has_no_baked_action_text(self):
        for y in range(17, 60):
            background = self.pixel(40, y)
            for x in range(40, self.width - 40):
                self.assertEqual(background, self.pixel(x, y), (x, y))

    def test_source_proportions_and_top_highlight_are_retained_at_4x(self):
        self.assertEqual((508, 72), (self.width, self.height))
        self.assertGreater(self.pixel(254, 12)[2], self.pixel(254, 52)[2])


if __name__ == "__main__":
    unittest.main()
