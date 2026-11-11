"""Export registered source terrain variants without cities, flags or UI.

Requires Pillow and resvg-py. The reference tree is read-only; all generated
files go to this repository's separate original/ui/terrain asset directory.
Run with python -B scripts/prepare_battlefield_terrain.py --reference-root PATH.
"""
import argparse
import hashlib
import io
import json
import sys
from pathlib import Path

from PIL import Image, ImageChops, ImageFilter

SOURCE_WIDTH = 800
SOURCE_HEIGHT = 600
RASTER_SCALE = 2
FAITHS = ("chr", "bhu", "com", "jew", "mus")
LAND_SYMBOLS = (5253, 5237, 5221, 5205, 5189)


def remove_authored_water(image):
    """The iphone_ui.draw_map blue-remnant mask, using straight-alpha output."""
    values = []
    for red, green, blue, alpha in getattr(image, "get_flattened_data", image.getdata)():
        excess = min(blue - red - 12, blue - green - 4)
        values.append(round(255 * (1 - max(0, min(1, excess / 14)))))
    mask = Image.new("L", image.size)
    mask.putdata(values)
    mask = mask.filter(ImageFilter.MedianFilter(3))
    # The source's pygame surface is premultiplied; PNG/resvg are straight-alpha.
    # Apply the mask to alpha only here to obtain the same composite over ocean.
    image.putalpha(ImageChops.multiply(image.getchannel("A"), mask))
    return image


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--reference-root", required=True, type=Path)
    args = parser.parse_args()
    reference = args.reference_root.resolve()
    output = Path(__file__).resolve().parents[1] / "app/src/main/assets/original/ui/terrain"
    if reference == output or reference in output.parents:
        raise ValueError("Output must stay outside the read-only reference")
    sys.dont_write_bytecode = True
    sys.path.insert(0, str(reference))
    import resvg_py
    from wd2.display_list import Matrix, MovieClip
    from wd2.scene_renderer import SceneRenderer
    from wd2.setup_menu import SetupMenu
    from wd2.surface_renderer import SurfaceRenderer

    output.mkdir(parents=True, exist_ok=True)
    menu = SetupMenu(0)
    menu.press("splashScreenMC")
    menu.press("BTN_allLeaders_screen1")
    menu.start_game()

    # MobileSkin.crop((358,159,48,50)): use the authored water, not a flat color.
    root = SurfaceRenderer.partial(menu.tree, menu.tree.ordered())
    root.matrix = Matrix(a=3, d=3)
    stage = Image.open(io.BytesIO(menu.renderer.png(root, (2400, 1800), None))).convert("RGBA")
    ocean = stage.crop((358 * 3, 159 * 3, 406 * 3, 209 * 3))
    ocean.save(output / "ocean.png", optimize=True)

    manifest = {
        "format": 1,
        "sourceStage": [SOURCE_WIDTH, SOURCE_HEIGHT],
        "rasterScale": RASTER_SCALE,
        "sourceSceneSha256": hashlib.sha256((reference / "assets/runtime/scene.json.gz").read_bytes()).hexdigest(),
        "sourceRule": "View.setMapFrames: original player religion, including defeated players; absent slots are ocean",
        "ocean": {"file": "ocean.png", "stageCrop": [358, 159, 48, 50], "rasterScale": 3},
        "islands": [],
    }
    renderer = SceneRenderer(flash_masks=True)
    canvas_size = (SOURCE_WIDTH * RASTER_SCALE, SOURCE_HEIGHT * RASTER_SCALE)
    for island, symbol_id in enumerate(LAND_SYMBOLS):
        entry = {"island": island, "symbol": symbol_id, "variants": {}}
        for faith in FAITHS:
            # A fresh tree prevents timeline state leaking between faith variants.
            battle = MovieClip(menu.document.symbols, 5766)
            node = next(n for n in battle.ordered() if n.name == "land0" + str(island))
            if node.character_id != symbol_id:
                raise ValueError("Unexpected source land symbol")
            node.goto_and_stop(faith)
            node.matrix = Matrix(a=RASTER_SCALE, d=RASTER_SCALE) @ node.matrix
            svg = renderer.svg(node, size=canvas_size, background=None)
            if renderer.report["unsupported"]:
                raise ValueError(renderer.report["unsupported"])
            image = Image.open(io.BytesIO(resvg_py.svg_to_bytes(
                svg_string=svg, skip_system_fonts=True))).convert("RGBA")
            bounds = image.getbbox()
            if bounds is None:
                raise ValueError("Source terrain frame is empty")
            image = remove_authored_water(image.crop(bounds))
            # Crop again after blue-water removal, retaining source registration.
            visible = image.getbbox()
            if visible is None:
                raise ValueError("Terrain mask removed the complete island")
            image = image.crop(visible)
            left = bounds[0] + visible[0]
            top = bounds[1] + visible[1]
            name = "island-" + str(island) + "-" + faith + ".png"
            image.save(output / name, optimize=True)
            frame = menu.document.symbols[symbol_id].timeline.labels[faith]
            entry["variants"][faith] = {
                "file": name,
                "frame": frame,
                "shape": node.ordered()[0].character_id,
                "sourceOffset": [left / RASTER_SCALE, top / RASTER_SCALE],
                "pixelSize": list(image.size),
                "sha256": hashlib.sha256((output / name).read_bytes()).hexdigest(),
            }
            print(name, image.size, (left / RASTER_SCALE, top / RASTER_SCALE))
        manifest["islands"].append(entry)
    manifest["ocean"]["sha256"] = hashlib.sha256((output / "ocean.png").read_bytes()).hexdigest()
    (output / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    print("Exported 25 terrain variants and one authored ocean swatch to", output)


if __name__ == "__main__":
    main()
