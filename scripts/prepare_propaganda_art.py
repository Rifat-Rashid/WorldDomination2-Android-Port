"""Recover the source dialog glass and embedded strength font for Android.

Usage: python scripts/prepare_propaganda_art.py --reference-root PATH
Requires the reference project's render dependencies and Pillow. The reference
is read-only; all output goes to this repository's packaged assets.
"""
import argparse
import gzip
import io
import os
from pathlib import Path
import sys

from PIL import Image, ImageDraw, ImageFont


def export_frame(reference_root, output):
    sys.dont_write_bytecode = True
    os.environ["SDL_VIDEODRIVER"] = "dummy"
    os.environ["SDL_AUDIODRIVER"] = "dummy"
    sys.path.insert(0, str(reference_root))
    import pygame
    from wd2.display_list import Matrix
    from wd2.runtime import get
    from wd2.setup_menu import SetupMenu
    from wd2.surface_renderer import SurfaceRenderer

    pygame.init()
    pygame.display.set_mode((1, 1), pygame.HIDDEN)
    menu = SetupMenu(3)
    try:
        menu.press("splashScreenMC")
        menu.press("BTN_allLeaders_screen1")
        menu.start_game()
        menu.press("btn8")
        center = get(menu.gameplay.interface, "centerScreen").node
        # The HUD paints its authored glass and frame over centerScreen.
        interface = SurfaceRenderer.partial(menu.gameplay.interface.node, [
            node for node in menu.gameplay.interface.node.ordered()
            if node.depth > center.depth
        ])
        root = SurfaceRenderer.partial(menu.tree, [interface])
        root.matrix = Matrix(a=3, d=3, tx=-205 * 3, ty=-135 * 3) @ root.matrix
        renderer = SurfaceRenderer(menu.renderer, pygame)
        sprite, position = renderer.paint(root, (), 0, (1095, 825))
        surface = pygame.Surface((1095, 825), pygame.SRCALPHA, 32)
        surface.blit(sprite, position, special_flags=pygame.BLEND_PREMULTIPLIED)
        image = Image.frombytes("RGBA", surface.get_size(), pygame.image.tobytes(surface, "RGBA"))
        # Runtime surfaces use premultiplied alpha; PNG/libGDX expect straight alpha.
        pixels = image.load()
        for y in range(image.height):
            for x in range(image.width):
                red, green, blue, alpha = pixels[x, y]
                if 0 < alpha < 255:
                    pixels[x, y] = tuple(min(255, round(channel * 255 / alpha))
                                         for channel in (red, green, blue)) + (alpha,)
        image.save(output / "propaganda-frame.png")
    finally:
        menu.audio.close()
        pygame.quit()


def export_font(repository_root, output):
    # Original centerScreen.text1 uses font symbol 3332, Arial_9pt_st, at 9pt.
    compressed = repository_root / "app/src/main/assets/original/runtime/fonts/3332_Arial_9pt_st.ttf.gz"
    font = ImageFont.truetype(io.BytesIO(gzip.decompress(compressed.read_bytes())), 36)
    characters = sorted(set("Propaganda strength: 0123456789%?"))
    atlas = Image.new("RGBA", (512, 128))
    draw = ImageDraw.Draw(atlas)
    lines = [
        'info face="Arial_9pt_st" size=36 bold=0 italic=0 charset="" unicode=1 '
        'stretchH=100 smooth=1 aa=1 padding=0,0,0,0 spacing=1,1',
        'common lineHeight=48 base=25 scaleW=512 scaleH=128 pages=1 packed=0',
        'page id=0 file="propaganda-source-36.png"',
        f"chars count={len(characters)}",
    ]
    x = y = 2
    for character in characters:
        left, top, right, bottom = font.getbbox(character)
        width, height = right - left, bottom - top
        if x + width + 2 > atlas.width:
            x, y = 2, y + 44
        if y + height > atlas.height:
            raise ValueError("Source glyphs exceed the font atlas")
        draw.text((x - left, y - top), character, font=font, fill="white")
        lines.append(f"char id={ord(character)} x={x} y={y} width={width} height={height} "
                     f"xoffset={left} yoffset={top} xadvance={round(font.getlength(character))} "
                     "page=0 chnl=15")
        x += width + 3
    (output / "fonts").mkdir(exist_ok=True)
    atlas.save(output / "fonts/propaganda-source-36.png")
    (output / "fonts/propaganda-source-36.fnt").write_text("\n".join(lines) + "\n", encoding="utf-8")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--reference-root", required=True, type=Path)
    arguments = parser.parse_args()
    repository_root = Path(__file__).resolve().parents[1]
    output = repository_root / "app/src/main/assets/original/ui"
    export_frame(arguments.reference_root.resolve(), output)
    export_font(repository_root, output)


if __name__ == "__main__":
    main()
