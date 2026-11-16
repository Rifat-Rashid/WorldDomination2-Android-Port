"""Export the original phone help scene and its actual controls at 3x.

Requires the read-only RealTest reference, its Python dependencies, and resvg_py.
Run with python -B; no files in the reference tree are written.
"""
import hashlib
import json
import os
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

sys.dont_write_bytecode = True
import resvg_py

ROOT = Path(__file__).resolve().parents[1]
REF = Path(os.environ.get('WD2_REFERENCE', 'C:/Users/Seize/Downloads/RealTest')).resolve()
OUT = ROOT / 'app/src/main/assets/original/ui/help'

def deny_reference_writes(event, args):
    if event != 'open' or not args or not isinstance(args[0], (str, bytes, os.PathLike)):
        return
    path = Path(os.fsdecode(args[0])).resolve()
    mode = args[1] if len(args) > 1 else None
    flags = args[2] if len(args) > 2 and isinstance(args[2], int) else 0
    if (path == REF or REF in path.parents) and (
            (isinstance(mode, str) and any(c in mode for c in 'wax+'))
            or flags & (os.O_WRONLY | os.O_RDWR | os.O_CREAT | os.O_TRUNC | os.O_APPEND)):
        raise PermissionError('reference is read-only: ' + str(path))

sys.addaudithook(deny_reference_writes)
sys.path.insert(0, str(REF))
from wd2.setup_menu import SetupMenu
from wd2.runtime import get, U
from wd2.surface_renderer import SurfaceRenderer

menu = SetupMenu(0)
for button in ('splashScreenMC', 'btn2', 'BTN_allLeaders_screen1', 'BTN_allLeaders_screen2'):
    menu.press(button)
game = menu.start_game()
menu.press('btn_help')
help_data = get(menu.gameplay.presenter, 'help_data')
pages = list(get(help_data, 'page_arr'))
OUT.mkdir(parents=True, exist_ok=True)
manifest = {'crop': [205, 135, 365, 275], 'scale': 3, 'pages': []}
for i, page in enumerate(pages):
    topic = get(page, '_pgName')
    game.model.set('userSelectedHelpTopic', topic)
    menu.rt.invoke(menu.gameplay.presenter, 'updateView')
    interface = menu.gameplay.interface.node
    center = interface.named('centerScreen')
    partial = SurfaceRenderer.partial(interface, list(interface.ordered()))
    root = SurfaceRenderer.partial(menu.tree, [partial])
    markup = menu.renderer.svg(root, background=None)
    svg = ET.fromstring(markup)
    svg.set('width', '1095'); svg.set('height', '825')
    svg.set('viewBox', '205 135 365 275')
    png = resvg_py.svg_to_bytes(svg_string=ET.tostring(svg, encoding='unicode'),
                                width=1095, height=825, skip_system_fonts=True)
    (OUT / (topic + '.png')).write_bytes(png)
    controls = []
    possible = []
    arr = get(page, '_btnArr')
    if arr is not U:
        possible.extend((get(item, '_mcName'), get(item, '_trgtString')) for item in arr)
    if get(page, '_btnIndex') is True:
        possible.append(('btnIndex', 'help_index'))
    if get(page, '_btnPrev') is True and i > 0:
        possible.append(('btnPrev', get(pages[i-1], '_pgName')))
    if get(page, '_btnNext') is True and i + 1 < len(pages):
        possible.append(('btnNext', get(pages[i+1], '_pgName')))
    for name, target in possible:
        node = center.named(name)
        if node is None or not node.visible:
            continue
        left, top, right, bottom = menu.renderer.control_bounds(node)
        controls.append({'name': name, 'target': target,
                         'bounds': [left-205, top-135, right-left, bottom-top]})
    manifest['pages'].append({'id': i, 'topic': topic, 'frame': center.frame,
                              'asset': topic+'.png', 'width': 1095, 'height': 825,
                              'sha256': hashlib.sha256(png).hexdigest(), 'controls': controls})
    print(topic, center.frame, len(controls), flush=True)
(OUT / 'manifest.json').write_text(json.dumps(manifest, indent=2)+'\n', encoding='utf-8')
menu.audio.close()
