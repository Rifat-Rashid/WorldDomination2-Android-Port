"""Recover flag/city-free mobile terrain using the reference's ocean/terrain pipeline.

The reference and existing enhanced map/font assets are read-only. Requires
Pillow and resvg-py from the reference's rendering dependencies.
"""
import argparse
import io
import sys
from pathlib import Path
from PIL import Image, ImageChops, ImageFilter

WIDTH, HEIGHT = 1200, 730

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--reference-root',required=True,type=Path)
    args=parser.parse_args()
    sys.dont_write_bytecode=True
    sys.path.insert(0,str(args.reference_root.resolve()))
    import resvg_py
    from wd2.setup_menu import SetupMenu
    from wd2.display_list import MovieClip, Matrix
    from wd2.scene_renderer import SceneRenderer
    from wd2.surface_renderer import SurfaceRenderer
    menu=SetupMenu(0)
    menu.press('splashScreenMC');menu.press('BTN_allLeaders_screen1');menu.start_game()
    # MobileSkin.crop((358,159,48,50)): an authored ocean swatch fills wide gaps.
    root=SurfaceRenderer.partial(menu.tree,menu.tree.ordered());root.matrix=Matrix(a=3,d=3)
    ocean=Image.open(io.BytesIO(menu.renderer.png(root,(2400,1800),None))).convert('RGBA')
    ocean=ocean.crop((358*3,159*3,406*3,209*3)).resize((WIDTH,HEIGHT),Image.Resampling.BILINEAR)
    layer=Image.new('RGBA',(WIDTH*2,HEIGHT*2))
    battle=MovieClip(menu.document.symbols,5766)
    scale=HEIGHT*2/600
    margin=(WIDTH*2-800*scale)/2
    renderer=SceneRenderer(flash_masks=True)
    for node in battle.ordered():
        if not node.name.startswith('land0'):continue
        island=int(node.name[-1])
        # Existing map skins; flags/cities are independently dynamic Java layers.
        node.goto_and_stop(['jew','chr','mus','mus','chr'][island])
        shift=margin if island==0 else (0 if island in (1,3) else 2*margin)
        node.matrix=Matrix(a=scale,d=scale,tx=shift) @ node.matrix
        svg=renderer.svg(node,size=layer.size,background=None)
        if renderer.report['unsupported']:raise ValueError(renderer.report['unsupported'])
        image=Image.open(io.BytesIO(resvg_py.svg_to_bytes(svg_string=svg,skip_system_fonts=True))).convert('RGBA')
        bounds=image.getbbox()
        image=image.crop(bounds)
        # iphone_ui.draw_map removes the baked blue water and grid remnants.
        values=[]
        for r,g,b,a in getattr(image,'get_flattened_data',image.getdata)():
            blue=min(b-r-12,b-g-4)
            values.append(round(255*(1-max(0,min(1,blue/14)))))
        mask=Image.new('L',image.size);mask.putdata(values)
        mask=mask.filter(ImageFilter.MedianFilter(3))
        image.putalpha(ImageChops.multiply(image.getchannel('A'),mask))
        layer.alpha_composite(image,(bounds[0],bounds[1]))
    ocean.alpha_composite(layer.resize((WIDTH,HEIGHT),Image.Resampling.BILINEAR))
    output=Path(__file__).resolve().parents[1]/'app/src/main/assets/original/ui/battle-terrain.png'
    ocean.save(output,optimize=True)
    print(output)

if __name__=='__main__':main()
